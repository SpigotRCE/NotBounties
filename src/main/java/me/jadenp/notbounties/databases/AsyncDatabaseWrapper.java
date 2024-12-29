package me.jadenp.notbounties.databases;

import me.jadenp.notbounties.Bounty;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.utils.DataManager;
import me.jadenp.notbounties.utils.Inconsistent;
import me.jadenp.notbounties.utils.PlayerStat;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class AsyncDatabaseWrapper extends NotBountiesDatabase {

    private static final Map<String, ActiveAsyncTask> taskList = new HashMap<>();

    /**
     * Gets a delay for an async task as to not overlap any other tasks with the same period.
     * @param name Name of the database to create the task for.
     * @param period The length in milliseconds between task execution.
     * @return The time in milliseconds when the task should start
     */
    private static synchronized long getSafeStartTime(String name, long period) {
        // a sorted list of all the start delays for the tasks that have synchronous periods
        List<Long> samePeriods = taskList.entrySet().stream().filter(e -> !e.getKey().equals(name) && (e.getValue().period() % period == 0 || period % e.getValue().period() == 0)).map(e -> e.getValue().start() % period).sorted().toList();
        if (samePeriods.isEmpty()) {
            // no other tasks with same period
            taskList.put(name, new ActiveAsyncTask(System.currentTimeMillis() + period, period));
            return System.currentTimeMillis() + period;
        }
        long[] startPoints = new long[samePeriods.size() + 2];
        // add points outside the valid bounds (0-period) to represent that the list wraps around
        startPoints[0] = -period + samePeriods.get(samePeriods.size()-1);
        startPoints[startPoints.length-1] = period + samePeriods.get(0);
        // find the maximum distance between points
        long maxDistance = 0;
        long maxMidpoint = 0;
        for (int i = 0; i < startPoints.length-1; i++) {
            long distance = startPoints[i+1] - startPoints[i];
            if (distance > maxDistance && startPoints[i] + distance / 2 > 0 && startPoints[i] + distance / 2 < period) {
                // greater distance & midpoint is within bounds
                maxDistance = distance;
                maxMidpoint = startPoints[i] + distance / 2;
            }
        }
        // the amount of time in ms of when to start the task
        long start = System.currentTimeMillis() - System.currentTimeMillis() % period + maxMidpoint + period;
        taskList.put(name, new ActiveAsyncTask(start, period));
        return start;
    }

    private final NotBountiesDatabase database;
    private BukkitTask asyncUpdateTask = null;
    private static final long MIN_UPDATE_INTERVAL = 20000L; // the minimum amount of time between database updates
    private int refreshInterval;
    private long lastOnlinePlayerRequest = 0;
    private final Map<UUID, PlayerStat> statChanges = new HashMap<>();
    private Map<UUID, String> onlinePlayers = new HashMap<>();

    public AsyncDatabaseWrapper(NotBountiesDatabase database) {
        this.database = database;
    }

    private void setAsyncUpdateTask() {
        refreshInterval = database.getRefreshInterval();
        if (asyncUpdateTask != null)
            asyncUpdateTask.cancel();
        long startTime = getSafeStartTime(database.getName(), refreshInterval * 1000L);
        asyncUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                readDatabaseData(true);
            }
        }.runTaskTimerAsynchronously(NotBounties.getInstance(), (startTime - System.currentTimeMillis()) / 50L, refreshInterval * 20L);
    }

    /**
     * Cancels the async update task and sets it to a null value
     */
    private void stopUpdating() {
        if (asyncUpdateTask != null)
            asyncUpdateTask.cancel();
        taskList.remove(database.getName());
        asyncUpdateTask = null;
    }

    /**
     * Reads the data from the database and updates the local data.
     * @param sync Whether the read should be synchronous or asynchronous.
     */
    public void readDatabaseData(boolean sync) {
        if (isConnected() && System.currentTimeMillis() - database.getLastSync() > Math.min(refreshInterval * 1000L, MIN_UPDATE_INTERVAL)) {
            if (sync) {
                updateData();
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        updateData();
                    }
                }.runTaskAsynchronously(NotBounties.getInstance());
            }
        }

    }

    /**
     * Update the data in this database with the specified values.
     * @param databaseBounties Bounties in the database.
     * @param databaseStats Stats in the database.
     */
    public void updateData(List<Bounty> databaseBounties, Map<UUID, PlayerStat> databaseStats) {

        NotBounties.debugMessage("Receiving " + database.getName() + " data.", false);
        LocalData localData = DataManager.getLocalData();
        localData.setStats(databaseStats);

        List<Bounty>[] dataChanges = Inconsistent.getAsyncronousObjects(localData.getAllBounties(2), databaseBounties, database.getLastSync());
        // these bounties should be added/removed to local data
        List<Bounty> databaseAdded = dataChanges[0];
        List<Bounty> databaseRemoved = dataChanges[1];
        // these bounties should be added/removed to the database
        List<Bounty> localAdded = dataChanges[2];
        List<Bounty> localRemoved = dataChanges[3];
        // apply consistency changes
        localData.addBounty(databaseAdded);
        localData.removeBounty(databaseRemoved);
        // these should be empty because databases should be updated immediately on a change.
        addBounty(localAdded);
        removeBounty(localRemoved);

        database.setLastSync(System.currentTimeMillis());

    }

    private void updateData() {
        if (isConnected()) {
            try {
                List<Bounty> databaseBounties = database.getAllBounties(2);
                Map<UUID, PlayerStat> databaseStats = database.getAllStats();
                updateData(databaseBounties, databaseStats);
            } catch (IOException e) {
                disconnect();
            }
        }
    }

    public void disconnect() {
        if (isConnected()) {
            database.disconnect();
            Bukkit.getLogger().warning(() -> "Disconnected from " + database.getName() + ".");
        }
    }

    public NotBountiesDatabase getDatabase() {
        return database;
    }


    /**
     * Adds stats to the statChanges queue
     * @param uuid UUID of the player that the changes are for
     * @param stats Changes in the player's stats
     */
    @Override
    public void addStats(UUID uuid, PlayerStat stats) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isConnected()) {
                    if (System.currentTimeMillis() - getLastSync() < DataManager.CONNECTION_REMEMBRANCE_MS) {
                        if (statChanges.containsKey(uuid)) {
                            statChanges.replace(uuid, statChanges.get(uuid).combineStats(stats));
                        } else {
                            statChanges.put(uuid, stats);
                        }
                    }
                    return;
                }
                if (isPermDatabase())
                    stats.setServerID(DataManager.GLOBAL_SERVER_ID);
                database.addStats(uuid, stats);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public @NotNull PlayerStat getStats(UUID uuid) throws IOException {
        return database.getStats(uuid);
    }

    @Override
    public Map<UUID, PlayerStat> getAllStats() throws IOException {
        return database.getAllStats();
    }

    @Override
    public void addStats(Map<UUID, PlayerStat> playerStats) {
        if (!playerStats.isEmpty())
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isPermDatabase())
                        playerStats.forEach((k, v) -> v.setServerID(DataManager.GLOBAL_SERVER_ID));
                    database.addStats(playerStats);
                    statChanges.clear();
                }
            }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public void addBounty(List<Bounty> bounties) {
        if (!bounties.isEmpty())
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (isPermDatabase())
                        bounties.forEach(bounty -> bounty.setServerID(DataManager.GLOBAL_SERVER_ID));
                    database.addBounty(bounties);
                }
            }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public void removeBounty(List<Bounty> bounties) {
        if (!bounties.isEmpty())
            new BukkitRunnable() {
                @Override
                public void run() {
                    database.removeBounty(bounties);
                }
            }.runTaskAsynchronously(NotBounties.getInstance());
    }

    /**
     * Adds a bounty to the database.
     * @param bounty Bounty to be added
     * @return The bounty that was provided. It will be updated with new setters asynchronously
     */
    @Override
    public Bounty addBounty(@NotNull Bounty bounty) {
        if (isConnected()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Bounty newbounty = database.addBounty(bounty);
                        if (newbounty != null && !newbounty.equals(bounty)) {
                            bounty.getSetters().clear();
                            bounty.getSetters().addAll(newbounty.getSetters());
                        }
                        if (isPermDatabase())
                            bounty.setServerID(DataManager.GLOBAL_SERVER_ID);
                    } catch (IOException e) {
                        disconnect();
                    }
                }
            }.runTaskAsynchronously(NotBounties.getInstance());
        }
        return bounty;
    }

    @Override
    public void replaceBounty(UUID uuid, @Nullable Bounty bounty) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.replaceBounty(uuid, bounty);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public @Nullable Bounty getBounty(UUID uuid) throws IOException{
        try {
            return database.getBounty(uuid);
        } catch (IOException e) {
            disconnect();
            throw e;
        }
    }

    @Override
    public void removeBounty(UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.removeBounty(uuid);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public void removeBounty(Bounty bounty) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.removeBounty(bounty);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public List<Bounty> getAllBounties(int sortType) {
        try {
            return database.getAllBounties(sortType);
        } catch (IOException e) {
            disconnect();
            return DataManager.getLocalData().getAllBounties(sortType);
        }
    }

    public Map<UUID, PlayerStat> getStatChanges() {
        return statChanges;
    }

    @Override
    public String getName() {
        return database.getName();
    }

    @Override
    public boolean isConnected() {
        try {
            return database.isConnected();
        } catch (NoClassDefFoundError e) {
            // Couldn't load a dependency.
            // This will be thrown if unable to use Spigot's library loader
            NotBounties.debugMessage("One or more dependencies could not be downloaded to use the database: " + database.getName(), true);
            return false;
        }
    }


    /**
     * Attempts to reconnect to the database
     * @return True if the connection was successful
     */
    @Override
    public boolean connect() {
        boolean success = database.connect();
        if (!success) {
            // database is no longer connected
            // stop attempting to receive data
            stopUpdating();
        } else {
            // restart update task
            setAsyncUpdateTask();
            Bukkit.getLogger().info("[NotBounties] Connected to " + database.getName() + "!");
        }
        return success;
    }

    @Override
    public boolean hasConnectedBefore() {
        return database.hasConnectedBefore();
    }

    @Override
    public int getRefreshInterval() {
        return database.getRefreshInterval();
    }

    @Override
    public long getLastSync() {
        return database.getLastSync();
    }

    @Override
    public void setLastSync(long lastSync) {
        database.setLastSync(lastSync);
    }

    @Override
    public Map<UUID, String> getOnlinePlayers() {
        if (System.currentTimeMillis() - lastOnlinePlayerRequest > MIN_UPDATE_INTERVAL) {
            try {
                onlinePlayers = database.getOnlinePlayers();
                lastOnlinePlayerRequest = System.currentTimeMillis();
            } catch (IOException e) {
                onlinePlayers.clear();
                disconnect();
            }
        }
        Map<UUID, String> currentPlayers = new HashMap<>(onlinePlayers);
        Bukkit.getOnlinePlayers().forEach(player -> currentPlayers.put(player.getUniqueId(), player.getName()));
        return currentPlayers;
    }

    @Override
    public int getPriority() {
        return database.getPriority();
    }

    @Override
    public void reloadConfig() {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.reloadConfig();
            }
        }.runTaskAsynchronously(NotBounties.getInstance());

    }

    @Override
    public void shutdown() {
        database.shutdown();
    }

    @Override
    public void notifyBounty(UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.notifyBounty(uuid);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public void login(UUID uuid, String playerName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.login(uuid, playerName);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    @Override
    public void logout(UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                database.logout(uuid);
            }
        }.runTaskAsynchronously(NotBounties.getInstance());
    }

    public boolean isPermDatabase() {
        return !(database instanceof TempDatabase);
    }
}

record ActiveAsyncTask(long start, long period) { }
