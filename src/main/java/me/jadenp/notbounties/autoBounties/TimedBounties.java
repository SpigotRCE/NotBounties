package me.jadenp.notbounties.autoBounties;

import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.utils.LiteBansClass;
import me.jadenp.notbounties.utils.Whitelist;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static me.jadenp.notbounties.utils.BountyManager.*;
import static me.jadenp.notbounties.utils.ConfigOptions.liteBansEnabled;

public class TimedBounties {
    // time is in SECONDS
    private static long time;
    private static double bountyIncrease;
    private static boolean resetOnDeath;
    private static double maxBounty;
    private static boolean offlineTracking = false;
    /**
     * When the next bounty should be set for every player.
     * When offlineTracking is true, the time in milliseconds when the next bounty should be set is always stored as the value
     * When offlineTracking is false, the time in milliseconds until the bounty should be set is stored when the player is offline
     * Online players will ALWAYS have the value be the time when the next bounty should be set
     */
    private static Map<UUID, Long> nextBounties = new HashMap<>();

    public static void loadConfiguration(ConfigurationSection timedBounties) {
        time  = timedBounties.getInt("time");
        resetOnDeath = timedBounties.getBoolean("reset-on-death");
        bountyIncrease = timedBounties.getDouble("bounty-increase");
        maxBounty = timedBounties.getDouble("max-bounty");
        boolean updatedOfflineTracking = timedBounties.getBoolean("offline-tracking");
        if (!offlineTracking && updatedOfflineTracking) {
            // convert to global time
            Map<UUID, Long> updatedNextBounties = new HashMap<>();
            for (Map.Entry<UUID, Long> entry : nextBounties.entrySet()) {
                // only convert if the player is offline, otherwise the value should already be in global time
                if (!Bukkit.getOfflinePlayer(entry.getKey()).isOnline())
                    updatedNextBounties.put(entry.getKey(), entry.getValue() + System.currentTimeMillis());
            }
            nextBounties = updatedNextBounties;
        } else if (offlineTracking && !updatedOfflineTracking) {
            // convert to local time
            Map<UUID, Long> updatedNextBounties = new HashMap<>();
            for (Map.Entry<UUID, Long> entry : nextBounties.entrySet()) {
                if (!Bukkit.getOfflinePlayer(entry.getKey()).isOnline())
                    updatedNextBounties.put(entry.getKey(), entry.getValue() - System.currentTimeMillis());
            }
            nextBounties = updatedNextBounties;
        }
        offlineTracking = updatedOfflineTracking;
        if (time == 0 || bountyIncrease == 0)
            nextBounties.clear();
    }

    public static void update() {
        if (time == 0 || bountyIncrease == 0)
            return;
        Map<UUID, Long> nextBountiesCopy = Map.copyOf(nextBounties);
        for (Map.Entry<UUID, Long> entry : nextBountiesCopy.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            if (!offlineTracking && !player.isOnline())
                continue;
            if (System.currentTimeMillis() > entry.getValue()) {
                // set bounty
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isBanned() || (liteBansEnabled && !new LiteBansClass().isPlayerNotBanned(player.getUniqueId()))) {
                            nextBounties.remove(entry.getKey());
                            return;
                        }
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline() || offlineTracking)
                                    nextBounties.replace(entry.getKey(), System.currentTimeMillis() + time * 1000);
                                else nextBounties.replace(entry.getKey(), time * 1000);
                                if (!hasBounty(player) || !isMaxed(Objects.requireNonNull(getBounty(player)).getTotalBounty()))
                                    addBounty(player, bountyIncrease, new Whitelist(new ArrayList<>(), false));
                            }
                        }.runTask(NotBounties.getInstance());

                    }
                }.runTaskAsynchronously(NotBounties.getInstance());
            }
        }
    }

    public static boolean isMaxed(double totalBounty) {
        return !(maxBounty == 0 || totalBounty < maxBounty);
    }

    public static void login(Player player) {
        if (time != 0 && bountyIncrease != 0) {
            if (nextBounties.containsKey(player.getUniqueId())) {
                if (!offlineTracking)
                    nextBounties.replace(player.getUniqueId(), nextBounties.get(player.getUniqueId()) + System.currentTimeMillis());
            } else {
                nextBounties.put(player.getUniqueId(), System.currentTimeMillis() + time * 1000);
            }
        }
    }

    public static void onDeath(Player player) {
        if (resetOnDeath && time != 0 && bountyIncrease != 0)
            nextBounties.put(player.getUniqueId(), System.currentTimeMillis() + time * 1000);
    }

    public static void logout(Player player) {
        if (time != 0 && bountyIncrease != 0 && !offlineTracking && nextBounties.containsKey(player.getUniqueId()))
            nextBounties.replace(player.getUniqueId(), nextBounties.get(player.getUniqueId()) - System.currentTimeMillis());
    }
    public static void setNextBounties(Map<UUID, Long> nextBounties) {
        TimedBounties.nextBounties = nextBounties;
    }

    public static Map<UUID, Long> getNextBounties() {
        Map<UUID, Long> timeUntilNextBounty = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : nextBounties.entrySet()) {
            if (offlineTracking || Bukkit.getOfflinePlayer(entry.getKey()).isOnline())
                timeUntilNextBounty.put(entry.getKey(), entry.getValue() - System.currentTimeMillis());
            else
                timeUntilNextBounty.put(entry.getKey(), entry.getValue());
        }
        return timeUntilNextBounty;
    }

    public static long getUntilNextBounty(UUID uuid) {
        if (nextBounties.containsKey(uuid)) {
            if (!offlineTracking && !Bukkit.getOfflinePlayer(uuid).isOnline())
                return nextBounties.get(uuid);
            return nextBounties.get(uuid) - System.currentTimeMillis();
        }
        return -1;
    }
}
