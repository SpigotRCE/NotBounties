package me.jadenp.notbounties;

import me.jadenp.notbounties.utils.BountyManager;
import me.jadenp.notbounties.utils.DataManager;
import me.jadenp.notbounties.utils.configuration.NumberFormatting;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static me.jadenp.notbounties.utils.configuration.ConfigOptions.hiddenNames;
import static me.jadenp.notbounties.utils.configuration.LanguageOptions.*;

public enum Leaderboard {
    //(all/kills/claimed/deaths/set/immunity)
    ALL(true),
    KILLS(false),
    CLAIMED(true),
    DEATHS(false),
    SET(false),
    IMMUNITY(true),
    CURRENT(true);

    private final boolean money;
    Leaderboard(boolean decimals){
        this.money = decimals;
    }

    public boolean isMoney() {
        return money;
    }

    /**
     * Gets the stat from either local storage or the database if connected
     * @param uuid UUID of the player
     * @return stat
     */
    public double getStat(UUID uuid){
        return DataManager.getStat(uuid, this);
    }

    /**
     * Correctly displays the player's stat
     *
     * @param shorten if the message is in a shortened form
     * @param player Player to display to
     */
    public void displayStats(OfflinePlayer player, boolean shorten){
        String msg = parseStats(prefix + getStatMsg(shorten), player);
        if (player.isOnline()) {
            Player p = player.getPlayer();
            assert p != null;
            p.sendMessage(msg);
        }

    }

    public void displayStats(OfflinePlayer statOwner, OfflinePlayer receiver, boolean shorten) {
        String msg = parseStats(prefix + getStatMsg(shorten), statOwner);
        if (receiver.isOnline()) {
            Player p = receiver.getPlayer();
            assert p != null;
            p.sendMessage(msg);
        }
    }


    public String getFormattedStat(UUID uuid){
        if (money) {
            return NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(getStat(uuid)) + NumberFormatting.currencySuffix;
        }
        return NumberFormatting.formatNumber(getStat(uuid));
    }

    public String getStatMsg(boolean shorten){
        return switch (this) {
            case ALL -> shorten ? bountyStatAllShort : bountyStatAllLong;
            case KILLS -> shorten ? bountyStatKillsShort : bountyStatKillsLong;
            case CLAIMED -> shorten ? bountyStatClaimedShort : bountyStatClaimedLong;
            case DEATHS -> shorten ? bountyStatDeathsShort : bountyStatDeathsLong;
            case SET -> shorten ? bountyStatSetShort : bountyStatSetLong;
            case IMMUNITY -> shorten ? bountyStatImmunityShort : bountyStatImmunityLong;
            case CURRENT -> shorten ? listTotal : checkBounty;
        };
    }

    /**
     * Gets the top stats of the leaderboard type in descending order
     * @param amount Amount of values you want returned
     * @return Map of UUID and stat value in descending order
     */
    public Map<UUID, Double> getTop(int skip, int amount){
        LinkedHashMap<UUID, Double> top = new LinkedHashMap<>();
        if (this == Leaderboard.CURRENT) {
            for (Bounty bounty : BountyManager.getPublicBounties(2)) {
                if (amount == 0)
                    return top;
                if (skip == 0) {
                    top.put(bounty.getUUID(), bounty.getTotalDisplayBounty());
                    amount--;
                } else {
                    skip--;
                }
            }
        } else {
            LinkedHashMap<UUID, Double> map = sortByValue(getStatMap());
            for (Map.Entry<UUID, Double> entry : map.entrySet()){
                String name = NotBounties.getPlayerName(entry.getKey());
                if (hiddenNames.contains(name))
                    continue;
                if (amount == 0)
                    return top;
                if (skip == 0) {
                    top.put(entry.getKey(), entry.getValue());
                    amount--;
                } else {
                    skip--;
                }
            }
        }
        return top;
    }

    /**
     * Construct a map of this specific leaderboard stat.
     * Will not work for the CURRENT leaderboard
     * @return A map containing all stats for a specific type.
     */
    public @NotNull Map<UUID, Double> getStatMap() {
        LinkedHashMap<UUID, Double> map = new LinkedHashMap<>();
        if (this == Leaderboard.CURRENT) {
            return map;
        }
        for (Map.Entry<UUID, Double[]> entry : DataManager.getAllStats().entrySet()) {
            double value = 0;
            switch (this) {
                case KILLS -> value = entry.getValue()[0];
                case SET -> value = entry.getValue()[1];
                case DEATHS -> value = entry.getValue()[2];
                case ALL -> value = entry.getValue()[3];
                case IMMUNITY -> value = entry.getValue()[4];
                case CLAIMED -> value = entry.getValue()[5];
                default -> {
                    // will never be reached unless a new stat is added, then value defaults to 0
                }
            }
            map.put(entry.getKey(), value);
        }
        return map;
    }

    public void displayTopStat(CommandSender sender, int amount){
        if (sender instanceof Player player)
            sender.sendMessage(parse(bountyTopTitle, player));
        else
            sender.sendMessage(parse(bountyTopTitle, null));
        boolean useCurrency = this == Leaderboard.IMMUNITY || this == Leaderboard.CLAIMED || this == Leaderboard.ALL;
        Map<UUID, Double> map = getTop(0, amount);
        int i = 0;
        for (Map.Entry<UUID, Double> entry : map.entrySet()){
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            String name = NotBounties.getPlayerName(entry.getKey());
            sender.sendMessage(parseBountyTopString(i + 1, name, entry.getValue(), useCurrency, p));
            i++;
        }
        sender.sendMessage(ChatColor.GRAY + "" + ChatColor.STRIKETHROUGH + "                                                   ");
    }


    private String parseStats(String text, OfflinePlayer player){
        text = text.replace("{amount}", (getFormattedStat(player.getUniqueId())));

        return parse(text, player);
    }

    private static LinkedHashMap<UUID, Double> sortByValue(Map<UUID, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<UUID, Double>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        list.sort((o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));

        // put data from sorted list to hashmap
        LinkedHashMap<UUID, Double> temp = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
    private static LinkedHashMap<UUID, Double> sortByName(Map<UUID, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<UUID, Double>> list = new LinkedList<>(hm.entrySet());

        // Sort the list
        list.sort((o1, o2) -> (NotBounties.getPlayerName(o2.getKey())).compareTo(NotBounties.getPlayerName(o1.getKey())));

        // put data from sorted list to hashmap
        LinkedHashMap<UUID, Double> temp = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    public static String parseBountyTopString(int rank, @NotNull String playerName, double amount, boolean useCurrency, OfflinePlayer player){
        String text = bountyTop;
        text = text.replace("{rank}", rank + "");
        text = text.replace("{player}", playerName);
        if (useCurrency)
            text = text.replace("{amount}", (NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(amount) + NumberFormatting.currencySuffix));
        else
            text = text.replace("{amount}", (NumberFormatting.formatNumber(amount)));

        return parse(text, player);
    }

    public Map<UUID, Double> getSortedList(int skip, int amount, int sortType) {
        LinkedHashMap<UUID, Double> top = (LinkedHashMap<UUID, Double>) getTop(skip, amount);
        if (sortType == 2)
            top = reverseMap(top);
        if (sortType == 3)
            top = sortByName(top);
        if (sortType == 4)
            top = reverseMap(sortByName(top));
        return top;
    }
    public Map<UUID, String> getFormattedList(int skip, int amount, int sortType){
        LinkedHashMap<UUID, Double> top = (LinkedHashMap<UUID, Double>) getSortedList(skip, amount, sortType);
        LinkedHashMap<UUID, String> formattedList = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> entry : top.entrySet()){
            if (this.isMoney()) {
                formattedList.put(entry.getKey(), NumberFormatting.currencyPrefix + NumberFormatting.getValue(entry.getValue()) + NumberFormatting.currencySuffix);
            } else {
                formattedList.put(entry.getKey(), NumberFormatting.getValue(entry.getValue()));
            }
        }
        return formattedList;
    }

    private static LinkedHashMap<UUID, Double> reverseMap(LinkedHashMap<UUID, Double> map){
        List<UUID> keys = new ArrayList<>(map.keySet());
        Collections.reverse(keys);
        LinkedHashMap<UUID, Double> newMap = new LinkedHashMap<>();
        for (UUID key : keys){
            newMap.put(key, map.get(key));
        }
        return newMap;
    }

    @Override
    public String toString() {
        return this.name().charAt(0) + this.name().substring(1).toLowerCase();
    }
}
