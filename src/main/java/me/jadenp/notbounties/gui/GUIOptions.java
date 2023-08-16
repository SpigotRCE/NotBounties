package me.jadenp.notbounties.gui;

import me.jadenp.notbounties.Bounty;
import me.jadenp.notbounties.Setter;
import me.jadenp.notbounties.utils.ConfigOptions;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.utils.NumberFormatting;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.regex.Matcher;

import static me.jadenp.notbounties.utils.ConfigOptions.*;
import static me.jadenp.notbounties.utils.NumberFormatting.*;

public class GUIOptions {
    private final List<Integer> playerSlots; // this size of this is how many player slots per page
    private final int size;
    private final int sortType;
    private final CustomItem[] customItems;
    private final String name;
    private final boolean removePageItems;
    private final String headName;
    private final List<String> headLore;
    private final boolean addPage;
    private final String type;
    private final Map<Integer, CustomItem> pageReplacements = new HashMap<>();

    public GUIOptions(ConfigurationSection settings){
        type = settings.getName();
        name = color(settings.getString("gui-name"));
        playerSlots = new ArrayList<>();
        size = settings.getInt("size");
        addPage = settings.getBoolean("add-page");
        sortType = settings.isSet("sort-type") ? settings.getInt("sort-type") : 1;
        removePageItems = settings.getBoolean("remove-page-items");
        headName = settings.getString("head-name");
        headLore = settings.getStringList("head-lore");
        customItems = new CustomItem[size];
        for (String key : Objects.requireNonNull(settings.getConfigurationSection("layout")).getKeys(false)) {
            String item = settings.getString("layout." + key + ".item");
            int[] slots = getRange(settings.getString("layout." + key + ".slot"));
            //Bukkit.getLogger().info(item);
            if (ConfigOptions.customItems.containsKey(item)) {
                CustomItem customItem = ConfigOptions.customItems.get(item);
                for (int i : slots){
                    //Bukkit.getLogger().info(i + "");
                    if (customItems[i] != null){
                        if (getPageType(customItem.getCommands()) > 0){
                            pageReplacements.put(i, customItems[i]);
                        }
                    }
                    customItems[i] = customItem;
                }
            } else {
                // unknown item
                Bukkit.getLogger().warning("Unknown item \"" + item + "\" in gui: " + settings.getName());
            }

        }
        for (String bSlots : settings.getStringList("player-slots")) {
            int[] range = getRange(bSlots);
            for (int j : range) {
                playerSlots.add(j);
            }
        }
    }

    public boolean isAddPage() {
        return addPage;
    }

    public CustomItem getCustomItem(int slot, int page, int entryAmount) {
        if (slot >= customItems.length)
            return null;
        CustomItem item = customItems[slot];
        // next
        if (getPageType(item.getCommands()) == 1 && page * playerSlots.size() >= entryAmount){
            if (pageReplacements.containsKey(slot))
                return pageReplacements.get(slot);
            return null;
        }
        // back
        if (getPageType(item.getCommands()) == 2 && page == 1){
            if (pageReplacements.containsKey(slot))
                return pageReplacements.get(slot);
            return null;
        }
        return item;
    }

    /**
     * Get the formatted custom inventory
     * @param player Player that owns the inventory and will be parsed for any placeholders
     * @param page Page of gui - This will change the items in player slots and page items if enabled
     * @return Custom GUI Inventory
     */
    public Inventory createInventory(Player player, int page, LinkedHashMap<String, String> values, String... replacements){
        OfflinePlayer[] playerItems = new OfflinePlayer[values.size()];
        String[] amount = new String[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()){
            playerItems[index] = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
            amount[index] = entry.getValue();
            index++;
        }
        // this is for adding more replacements in the future
        int desiredLength = 1;
        if (replacements.length < desiredLength)
            replacements = new String[desiredLength];
        for (int i = 0; i < replacements.length; i++) {
            if (replacements[i] == null)
                replacements[i] = "";
        }

        if (page < 1) {
            page = 1;
            GUI.playerInfo.replace(player.getUniqueId(), new PlayerGUInfo(page, GUI.playerInfo.get(player.getUniqueId()).getData()));
        }
        String name = addPage ? this.name + " " + page : this.name;
        Inventory inventory = Bukkit.createInventory(player, size, name);
        ItemStack[] contents = inventory.getContents();
        // set up regular items
        for (int i = 0; i < contents.length; i++) {
            if (customItems[i] == null)
                continue;
            // check if item is a page item
            if (removePageItems){
                // next
                if (getPageType(customItems[i].getCommands()) == 1 && page * playerSlots.size() >= amount.length){
                    contents[i] = pageReplacements.get(i).getFormattedItem(player, replacements);
                    continue;
                }
                // back
                if (getPageType(customItems[i].getCommands()) == 2 && page == 1){
                    contents[i] = pageReplacements.get(i).getFormattedItem(player, replacements);
                    continue;
                }
            }
            contents[i] = customItems[i].getFormattedItem(player, replacements);
        }
        // set up player slots
        for (int i = type.equals("select-price") ? 0 : (page-1) * playerSlots.size(); i < Math.min(playerSlots.size(), playerItems.length); i++){
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            assert meta != null;
            final OfflinePlayer p = playerItems[i];
            meta.setOwningPlayer(p);
            long time = System.currentTimeMillis();
            if (type.equals("bounty-gui")){
                amount[i] = currencyPrefix + NumberFormatting.formatNumber(tryParse(amount[i])) + currencySuffix;
                time = Objects.requireNonNull(NotBounties.getInstance().getBounty(p)).getLatestSetter();
            } else {
                amount[i] = formatNumber(amount[i]);
            }

            final String finalAmount = amount[i];
            final int rank = i + 1;
            String[] finalReplacements = replacements;
            String playerName = p.getName() != null ? p.getName() : "<?>";
            List<String> lore = new ArrayList<>(headLore);
            double total = parseCurrency(finalAmount) * (bountyTax + 1) + NotBounties.getInstance().getPlayerWhitelist(player.getUniqueId()).size() * bountyWhitelistCost;
            try {
                meta.setDisplayName(parse(color(headName.replaceAll("\\{amount}", Matcher.quoteReplacement(finalAmount)).replaceAll("\\{rank}", Matcher.quoteReplacement(rank + "")).replaceAll("\\{leaderboard}", Matcher.quoteReplacement(finalReplacements[0])).replaceAll("\\{player}", Matcher.quoteReplacement(playerName)).replaceAll("\\{amount_tax}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(total) + NumberFormatting.currencySuffix))), time, p));
                long finalTime = time;
                lore.replaceAll(s -> parse(color(s.replaceAll("\\{amount}", Matcher.quoteReplacement(finalAmount)).replaceAll("\\{rank}", Matcher.quoteReplacement(rank + "")).replaceAll("\\{leaderboard}", Matcher.quoteReplacement(finalReplacements[0])).replaceAll("\\{player}", Matcher.quoteReplacement(playerName)).replaceAll("\\{amount_tax}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(total) + NumberFormatting.currencySuffix))), finalTime, p));
            } catch (IllegalArgumentException e){
                Bukkit.getLogger().warning("Error parsing name and lore for player item! This is usually caused by a typo in the config.");
            }
            // extra lore stuff
            if (type.equals("bounty-gui")) {
                if (player.hasPermission("notbounties.admin")) {
                    lore.add(ChatColor.RED + "Left Click" + ChatColor.GRAY + " to Remove");
                    lore.add(ChatColor.YELLOW + "Right Click" + ChatColor.GRAY + " to Edit");
                    lore.add("");
                } else {
                    if (buyBack) {
                        if (p.getUniqueId().equals(player.getUniqueId())) {
                            buyBackLore.stream().map(bbLore -> parse(bbLore, (parseCurrency(finalAmount) * buyBackInterest), player)).forEach(lore::add);
                            lore.add("");
                        }
                    }
                }
                // whitelist
                Bounty bounty = NotBounties.getInstance().getBounty(p);
                assert bounty != null;
                if (bounty.getAllWhitelists().contains(player.getUniqueId())) {
                    lore.add(parse(speakings.get(63), player));
                    lore.add("");
                } else if (player.hasPermission("notbounties.admin")) {
                    // not whitelisted
                    for (Setter setter : bounty.getSetters()) {
                        if (!setter.canClaim(player)) {
                            lore.addAll(notWhitelistedLore);
                            lore.add("");
                            break;
                        }
                    }
                }
            }
            if (type.equalsIgnoreCase("set-whitelist"))
                if (NotBounties.getInstance().getPlayerWhitelist(player.getUniqueId()).contains(p.getUniqueId())) {
                    skull.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    lore.add(parse(speakings.get(59), player));
                }
            meta.setLore(lore);
            skull.setItemMeta(meta);
            contents[playerSlots.get(i)] = skull;
        }
        inventory.setContents(contents);
        return inventory;
        
    }





    public int getSortType() {
        return sortType;
    }

    public List<Integer> getPlayerSlots() {
        return playerSlots;
    }

    public String getName() {
        return name;
    }

    /**
     * Get an array of desired numbers from a string from (x)-(y). Both x and y are inclusive.
     * <p>"1" -> [1]</p>
     * <p>"3-6" -> [3, 4, 5, 6]</p>
     * @param str String to parse
     * @return desired range of numbers sorted numerically or an empty list if there is a formatting error
     */
    private static int[] getRange(String str){
        try {
            // check if it is a single number
            int i = Integer.parseInt(str);
            // return if an exception was not thrown
            return new int[]{i};
        } catch (NumberFormatException e){
            // there is a dash we need to get out
        }
        String[] split = str.split("-");
        try {
            int bound1 = Integer.parseInt(split[0]);
            int bound2 = Integer.parseInt(split[1]);
            int[] result = new int[Math.abs(bound1 - bound2) + 1];

            for (int i = Math.min(bound1, bound2); i < Math.max(bound1, bound2) + 1; i++) {
                //Bukkit.getLogger().info(i + "");
                result[i-Math.min(bound1, bound2)] = i;
            }
            return result;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e){
            // formatting error
            return new int[0];
        }
    }

    /**
     * Parses through commands for "[next]" and "[back]"
     * @param commands Commands of the CustomItem
     * @return 1 for next, 2 for back, 0 for no page item
     */
    public static int getPageType(List<String> commands){
        for (String command : commands){
            if (command.startsWith("[next]"))
                return 1;
            if (command.startsWith("[back]"))
                return 2;
        }
        return 0;
    }

    public String getType() {
        return type;
    }
}

