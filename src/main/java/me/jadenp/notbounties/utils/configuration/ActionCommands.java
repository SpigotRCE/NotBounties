package me.jadenp.notbounties.utils.configuration;

import me.jadenp.notbounties.Bounty;
import me.jadenp.notbounties.Leaderboard;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.ui.gui.GUI;
import me.jadenp.notbounties.ui.gui.GUIOptions;
import me.jadenp.notbounties.ui.gui.PlayerGUInfo;
import me.jadenp.notbounties.ui.gui.displayItems.PlayerItem;
import me.jadenp.notbounties.utils.BountyManager;
import me.jadenp.notbounties.utils.CommandPrompt;
import me.jadenp.notbounties.utils.externalAPIs.PlaceholderAPIClass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static me.jadenp.notbounties.ui.gui.GUI.openGUI;
import static me.jadenp.notbounties.ui.gui.GUI.playerInfo;
import static me.jadenp.notbounties.utils.configuration.ConfigOptions.papiEnabled;
import static me.jadenp.notbounties.utils.configuration.LanguageOptions.prefix;
import static me.jadenp.notbounties.utils.configuration.NumberFormatting.*;

public class ActionCommands {
    private static List<String> bountyClaimCommands;
    private static List<String> bigBountyCommands;
    private static List<String> bountySetCommands;

    public static void loadConfiguration(List<String> bountyClaimCommands, List<String> bigBountyCommands, List<String> bountySetCommands) {
        ActionCommands.bountyClaimCommands = bountyClaimCommands;
        ActionCommands.bigBountyCommands = bigBountyCommands;
        ActionCommands.bountySetCommands = bountySetCommands;
    }

    public static void executeBountyClaim(Player player, Player killer, Bounty bounty) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String command : bountyClaimCommands) {
                    execute(player, killer, bounty, command);
                }
            }
        }.runTaskLater(NotBounties.getInstance(), 10);
    }

    public static void executeBountySet(UUID receiver, Player setter, Bounty bounty) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String command : bountySetCommands) {
                    command = command.replace("{receiever}", NotBounties.getPlayerName(receiver));
                    execute(setter, setter, bounty, command);
                }
            }
        }.runTaskLater(NotBounties.getInstance(), 10);
    }

    public static void executeCommands(Player player, List<String> commands) {
        Bounty bounty = BountyManager.getBounty(player.getUniqueId());
        for (String command : commands) {
            execute(player, player, bounty, command);
        }
    }

    public static void executeBigBounty(Player player, Bounty bounty) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String command : bigBountyCommands) {
                    execute(player, player, bounty, command);
                }
            }
        }.runTaskLater(NotBounties.getInstance(), 10);
    }

    private static void execute(Player player, Player killer, @Nullable Bounty bounty, String command) {
        if (command.isEmpty())
            return;
        if (NotBounties.debug)
            Bukkit.getLogger().info("[NotBountiesDebug] Executing Command for " + player.getName() + " : " + command);
        PlayerGUInfo info = playerInfo.containsKey(player.getUniqueId()) ? playerInfo.get(player.getUniqueId()) : new PlayerGUInfo(1, "", new Object[0], new ArrayList<>(), "");
        double totalBounty = bounty != null ? bounty.getTotalDisplayBounty() : 0;
        double bountyCurrency = bounty != null ? bounty.getTotalBounty() : 0;
        double bountyItemValues = bounty != null ? NumberFormatting.getTotalValue(bounty.getTotalItemBounty()) : 0;
        String bountyItems = bounty != null ? NumberFormatting.listItems(bounty.getTotalItemBounty(), ':') : "";
        String killerName = killer != null ? killer.getName() : "";

        command = command.replace("{player}", (player.getName()));
        command = command.replace("{killer}", (killerName));
        command = command.replace("{amount}", (NumberFormatting.getValue(totalBounty)));
        command = command.replace("{bounty}", (NumberFormatting.formatNumber(totalBounty)));
        command = command.replace("{bounty_currency}", (NumberFormatting.formatNumber(bountyCurrency)));
        command = command.replace("{bounty_item_values}", (NumberFormatting.formatNumber(bountyItemValues)));
        command = command.replace("{bounty_items}", (bountyItems));
        command = command.replace("{cost}", (NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(totalBounty) + NumberFormatting.currencySuffix));
        command = command.replace("{page}", (info.page() + ""));
        command = command.replace("{min_bounty}", getValue(ConfigOptions.minBounty));
        if (info.data().length > 0) {
            Object[] data = info.data();
            if (info.guiType().equals("bounty-item-select") && player.getOpenInventory().getTitle().equals(info.title())) {
                // update data with current item contentse
                GUIOptions guiOptions = GUI.getGUI("bounty-item-select");
                if (guiOptions == null)
                    return; // this shouldn't be reached
                // read current inventory
                ItemStack[] currentContents = new ItemStack[GUI.getMaxBountyItemSlots()];
                for (int i = 0; i < currentContents.length; i++) {
                    currentContents[i] = player.getOpenInventory().getTopInventory().getContents()[guiOptions.getPlayerSlots().get(i + 1)];
                }
                // get all items
                ItemStack[][] allItems = data.length > 1 && data[1] instanceof ItemStack[][] itemStacks ? itemStacks : new ItemStack[GUI.getMaxBountyItemSlots()][(int) info.page()];
                allItems[(int) info.page() - 1] = currentContents; // set current content
                data = new Object[]{data[0], allItems};
                playerInfo.replace(player.getUniqueId(), new PlayerGUInfo(info.page(), info.guiType(), data, info.displayItems(), info.title()));
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null)
                    data[i] = "";
                if (info.guiType().equals("bounty-item-select")) {
                    if (i == 0)
                        // player uuid to set bounty on
                        builder.append(data[0]).append(' ');
                    else if (i > 1)
                        builder.append(",");
                    if (i > 0) {
                        // items to set bounty
                        ItemStack[][] allItems = (ItemStack[][]) data[i];
                        List<ItemStack> listItems = new ArrayList<>();
                        Arrays.stream(allItems).forEach(itemStacks -> Arrays.stream(itemStacks).filter(Objects::nonNull).forEach(listItems::add));
                        builder.append(NumberFormatting.listItems(listItems, ':'));
                    }
                } else {
                    if (i > 0)
                        builder.append(' ');
                    builder.append(data[i].toString());
                }
            }
            command = command.replace("{data}", (builder.toString()));

        }



        // check for {slot<x>}
        while (player.getOpenInventory().getType() != InventoryType.CRAFTING && command.contains("{slot") && command.substring(command.indexOf("{slot")).contains("}")) {
            String replacement = "";
            try {
                int slot = Integer.parseInt(command.substring(command.indexOf("{slot") + 5, command.substring(command.indexOf("{slot")).indexOf("}") + command.substring(0, command.indexOf("{slot")).length()));
                ItemStack item = player.getOpenInventory().getTopInventory().getContents()[slot];
                if (item != null) {
                    if (item.getType() == Material.PLAYER_HEAD) {
                        SkullMeta meta = (SkullMeta) item.getItemMeta();
                        assert meta != null;
                        OfflinePlayer p = meta.getOwningPlayer();
                        if (p != null) {
                            replacement = NotBounties.getPlayerName(p.getUniqueId());
                        } else {
                            if (!info.guiType().isEmpty()) {
                               GUIOptions guiOptions = GUI.getGUI(info.guiType());
                               if (guiOptions != null) {
                                   if (guiOptions.getPlayerSlots().contains(slot) && info.displayItems().get(guiOptions.getPlayerSlots().indexOf(slot)) instanceof PlayerItem playerItem) {
                                        replacement = NotBounties.getPlayerName(playerItem.getUuid());
                                   }
                               } else {
                                   Bukkit.getLogger().warning("Invalid player for slot " + slot);
                               }
                            } else {
                                Bukkit.getLogger().warning("Invalid player for slot " + slot);
                            }

                        }
                    }
                    if (replacement.isEmpty()) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null)
                            replacement = meta.getDisplayName();
                    }
                }
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("Error getting slot in command: \n" + command);
            }
            command = command.substring(0, command.indexOf("{slot")) + replacement + command.substring(command.substring(command.indexOf("{slot")).indexOf("}") + command.substring(0, command.indexOf("{slot")).length() + 1);
        }

        // check for {player<x>}
        while (command.contains("{player") && command.substring(command.indexOf("{player")).contains("}")) {
            String replacement = "";
            String slotString = command.substring(command.indexOf("{player") + 7, command.substring(command.indexOf("{player")).indexOf("}") + command.substring(0, command.indexOf("{player")).length());
            try {
                int slot = Integer.parseInt(slotString);
                if (info.displayItems().size() > slot-1 && info.displayItems().get(slot-1) instanceof PlayerItem playerItem) {
                    replacement = NotBounties.getPlayerName(playerItem.getUuid());
                }
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("Error getting player in command: \n" + command);
            }
            command = command.replace(("{player" + slotString + "}"), (replacement));
        }

        boolean canceled = false;
        int loops = 100; // to stop an infinite loop if the command isn't formatted correctly
        while (command.startsWith("<(") || command.startsWith(">(") || command.startsWith("@(") || command.startsWith("!@(") || command.startsWith("~player(") || command.startsWith("~killer(") || (player.getUniqueId().equals(killer.getUniqueId()) && (command.startsWith("@") || command.startsWith("!@")))) {
            if (command.startsWith("<(") && command.contains(") ")) {
                double amount;
                try {
                    amount = NumberFormatting.tryParse(command.substring(2, command.indexOf(") ")));
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                command = command.substring(command.indexOf(") ") + 2);
                if (totalBounty >= amount)
                    canceled = true;
            } else if (command.startsWith(">(") && command.contains(") ")) {
                double amount;
                try {
                    amount = NumberFormatting.tryParse(command.substring(2, command.indexOf(") ")));
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                command = command.substring(command.indexOf(") ") + 2);
                if (totalBounty <= amount)
                    canceled = true;
            }

            if (command.startsWith("@(player)")) {
                String permission = command.substring(9, command.indexOf(" "));
                if (!player.hasPermission(permission))
                    canceled = true;
                command = command.substring(command.indexOf(" ") + 1);
            } else if (command.startsWith("!@(player)")) {
                String permission = command.substring(10, command.indexOf(" "));
                if (player.hasPermission(permission))
                    canceled = true;
                command = command.substring(command.indexOf(" ") + 1);
            } else if (command.startsWith("@(killer)")) {
                String permission = command.substring(9, command.indexOf(" "));
                if (!killer.hasPermission(permission))
                    canceled = true;
                command = command.substring(command.indexOf(" ") + 1);
            } else if (command.startsWith("!@(killer)")) {
                String permission = command.substring(10, command.indexOf(" "));
                if (killer.hasPermission(permission))
                    canceled = true;
                command = command.substring(command.indexOf(" ") + 1);
            } else if (player.getUniqueId().equals(killer.getUniqueId())) {
                if (command.startsWith("@")) {
                    String permission = command.substring(1, command.indexOf(" "));
                    if (!player.hasPermission(permission))
                        canceled = true;
                    command = command.substring(command.indexOf(" ") + 1);
                } else if (command.startsWith("!@")) {
                    String permission = command.substring(2, command.indexOf(" "));
                    if (player.hasPermission(permission))
                        canceled = true;
                    command = command.substring(command.indexOf(" ") + 1);
                }
            }

            if (command.startsWith("~player(") && command.contains(") ")) {
                String requirement = command.substring(8, command.indexOf(") "));
                if (!isRequirementCompleted(requirement, player))
                    canceled = true;
                command = command.substring(command.indexOf(") ") + 2);
            } else if (command.startsWith("~killer(") && command.contains(") ")) {
                String requirement = command.substring(8, command.indexOf(") "));
                if (!isRequirementCompleted(requirement, killer))
                    canceled = true;
                command = command.substring(command.indexOf(") ") + 2);
            }

            if (loops == 0) {
                Bukkit.getLogger().warning("[NotBounties] Could not complete bounty claim command! A conditional is not formatted correctly!");
                Bukkit.getLogger().warning("here -> " + command);
                canceled = true;
            }
            loops--;
            if (canceled)
                break;
        }
        if (canceled)
            return;

        if (NotBounties.debug)
          Bukkit.getLogger().info("[NotBountiesDebug] Parsed Action: \"" + command + "\"");

        if (command.startsWith("[player] ") || command.startsWith("[p] ")) {
            if (papiEnabled)
                command = new PlaceholderAPIClass().parse(player, command);
            if (command.startsWith("[player] "))
                command = command.substring(9);
            else
                command = command.substring(4);
            Bukkit.dispatchCommand(player,command);
        } else if (command.startsWith("[killer] ")) {
            if (papiEnabled)
                command = new PlaceholderAPIClass().parse(killer, command);
            Bukkit.dispatchCommand(killer, command.substring(9));
        } else if (command.startsWith("[message_player] ")) {
            String message = command.substring(17);
            player.sendMessage(LanguageOptions.parse(prefix + message, player));
        } else if (command.startsWith("[message_killer] ")) {
            String message = command.substring(17);
            killer.sendMessage(LanguageOptions.parse(prefix + message, killer));
        } else if (command.startsWith("[broadcast] ")) {
            String message = command.substring(12);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!NotBounties.disableBroadcast.contains(p.getUniqueId())) {
                    p.sendMessage(LanguageOptions.parse(prefix + message, killer));
                }
            }
        } else if (command.startsWith("[sound_player] ")) {
            command = command.substring(8);
            double volume = 1;
            double pitch = 1;
            String sound;
            if (command.contains(" ")) {
                sound = command.substring(0, command.indexOf(" "));
                command = command.substring(sound.length() + 1);
                try {
                    if (command.contains(" ")) {
                        volume = NumberFormatting.tryParse(command.substring(0, command.indexOf(" ")));
                        command = command.substring(command.indexOf(" ") + 1);
                        pitch = NumberFormatting.tryParse(command);
                    } else {
                        volume = NumberFormatting.tryParse(command);
                    }
                } catch (NumberFormatException e) {
                    Bukkit.getLogger().warning("[NotBounties] Unknown number for [sound_player] command in bounty-claim-commands : " + command);
                    return;
                }
            } else {
                sound = command;
            }
            Sound realSound;
            try {
                realSound = Sound.valueOf(sound.toUpperCase());
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[NotBounties] Unknown sound for [sound_player] command in bounty-claim-commands : " + sound);
                return;
            }
            player.playSound(player.getEyeLocation(), realSound, (float) volume, (float) pitch);
        } else if (command.startsWith("[sound_killer] ")) {
            command = command.substring(8);
            double volume = 1;
            double pitch = 1;
            String sound;
            if (command.contains(" ")) {
                sound = command.substring(0, command.indexOf(" "));
                command = command.substring(sound.length() + 1);
                try {
                    if (command.contains(" ")) {
                        volume = NumberFormatting.tryParse(command.substring(0, command.indexOf(" ")));
                        command = command.substring(command.indexOf(" ") + 1);
                        pitch = NumberFormatting.tryParse(command);
                    } else {
                        volume = NumberFormatting.tryParse(command);
                    }
                } catch (NumberFormatException e) {
                    Bukkit.getLogger().warning("[NotBounties] Unknown number for [sound_killer] command in bounty-claim-commands : " + command);
                    return;
                }
            } else {
                sound = command;
            }
            Sound realSound;
            try {
                realSound = Sound.valueOf(sound.toUpperCase());
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[NotBounties] Unknown sound for [sound_killer] command in bounty-claim-commands : " + sound);
                return;
            }
            killer.playSound(killer.getEyeLocation(), realSound, (float) volume, (float) pitch);
        } else if (command.startsWith("[sound] ")) {
            command = command.substring(8);
            double volume = 1;
            double pitch = 1;
            String sound;
            if (command.contains(" ")) {
                sound = command.substring(0, command.indexOf(" "));
                command = command.substring(sound.length() + 1);
                try {
                    if (command.contains(" ")) {
                        volume = NumberFormatting.tryParse(command.substring(0, command.indexOf(" ")));
                        command = command.substring(command.indexOf(" ") + 1);
                        pitch = NumberFormatting.tryParse(command);
                    } else {
                        volume = NumberFormatting.tryParse(command);
                    }
                } catch (NumberFormatException e) {
                    Bukkit.getLogger().warning("[NotBounties] Unknown number for [sound] command in bounty-claim-commands : " + command);
                    return;
                }
            } else {
                sound = command;
            }
            Sound realSound;
            try {
                realSound = Sound.valueOf(sound.toUpperCase());
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[NotBounties] Unknown sound for [sound] command in bounty-claim-commands : " + sound);
                return;
            }
            killer.getWorld().playSound(killer.getLocation(), realSound, (float) volume, (float) pitch);
        } else if (command.startsWith("[gui]")) {
            int amount = 1; // default page
            command = command.substring(6); // remove "[gui] "
            String guiName = command;
            if (guiName.contains(" ")) {
                // has additional parameters
                try {
                    String number = command.substring(command.indexOf(" ") + 1); // get everything after the gui name
                    command = command.substring(command.indexOf(" ") + 1); // remove gui name from command
                    if (number.contains(" ")) {
                        // has more parameters than just page number
                        amount = Integer.parseInt(number.substring(0, number.indexOf(" "))); // parse number
                        command = command.substring(command.indexOf(" ") + 1); // remove number from command
                    } else {
                        amount = Integer.parseInt(number); // parse number
                        command = ""; // no more arguments after
                    }
                } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                    // could not parse number or there was only a space and no number
                }
                guiName = guiName.substring(0, guiName.indexOf(" ")); // change gui name to remove number

            }
            if (guiName.equalsIgnoreCase("leaderboard")) {
                // this GUI can have the leaderboard type parameter
                // ex: [gui] leaderboard 1 kills
                Leaderboard l = Leaderboard.ALL; // default leaderboard
                try {
                    // try to parse leaderboard type from last parameter
                    l = Leaderboard.valueOf(command.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // could not parse leaderboard type - default leaderboard is used
                }
                openGUI(player, guiName, amount, l); // open gui
            } else if (guiName.equalsIgnoreCase("set-bounty") || guiName.equalsIgnoreCase("set-whitelist")) {
                // these GUIs can have the offline parameter
                // ex: [gui] set-bounty 1 offline
                if (!command.isEmpty()) {
                    // additional parameters present
                    // check if the current GUI already has the offline parameter
                    // if so, set the last argument to nothing to toggle it
                    if (command.equalsIgnoreCase("offline") && info.data().length > 0 && info.data()[0] instanceof String && ((String) info.data()[0]).equalsIgnoreCase("offline")) {
                        command = "";
                    }
                    openGUI(player, guiName, amount, command);
                } else {
                    openGUI(player, guiName, amount);
                }
            } else {
                if (!command.isEmpty())
                    openGUI(player, guiName, amount, command);
                else
                    openGUI(player, guiName, amount);
            }
        } else if (command.startsWith("[cprompt] ") || command.startsWith("[pprompt] ")) {
            boolean playerPrompt = command.startsWith("[pprompt] ");
            command = command.substring(10);
            player.closeInventory();
            Prompt.addCommandPrompt(player.getUniqueId(), new CommandPrompt(player, command, playerPrompt));
        } else if (command.startsWith("[close]")) {
            player.getOpenInventory().close();
            playerInfo.remove(player.getUniqueId());  // would only do something for bedrock players
        } else if (command.startsWith("[next]")) {
            int amount = 1;
            try {
                amount = Integer.parseInt(command.substring(7));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
            }
            if (info.guiType().equals("select-price")) {
                GUIOptions gui = GUI.getGUI("select-price");
                String uuid;
                if (info.data().length > 0) {
                    uuid = (String) info.data()[0];
                } else {
                    if (gui != null)
                        uuid = ((PlayerItem) info.displayItems().get(0)).getUuid().toString();
                    else
                        // select-price GUI hasn't been set up
                        uuid = new UUID(0,0).toString();
                }
                openGUI(player, info.guiType(), info.page() + amount, uuid);
            } else if (info.guiType().equals("leaderboard")) {
                Leaderboard leaderboard = (Leaderboard) info.data()[0];
                openGUI(player, info.guiType(), info.page() + amount, leaderboard);
            } else if (info.guiType().equals("bounty-item-select")) {
                // add current items to data
                GUIOptions gui = GUI.getGUI("bounty-item-select");
                if (gui == null) {
                    // gui not set up
                    Bukkit.getLogger().warning("[NotBounties] bounty-item-select GUI not set up.");
                    return;
                }

                // get current inventory items from gui info
                ItemStack[][] allItems = (ItemStack[][]) info.data()[1];
                // get all items in player slots except first one
                ItemStack[] items = new ItemStack[allItems[0].length];
                for (int i = 1; i < gui.getPlayerSlots().size(); i++) {
                    ItemStack item = player.getOpenInventory().getItem(gui.getPlayerSlots().get(i));
                    items[i-1] = item;
                }
                // create new array to fit a new page if needed
                ItemStack[][] newItems = new ItemStack[(int) Math.max(info.page() + amount, allItems.length)][allItems[0].length];
                // transfer all items into new items
                System.arraycopy(allItems,0,newItems,0,allItems.length);
                // set the current page's items in the array
                newItems[(int) (info.page()-1)] = items;

                // open GUI
                openGUI(player, info.guiType(), info.page() + amount, info.data()[0], newItems);
            } else if (!info.guiType().isEmpty()){
                openGUI(player, info.guiType(), info.page() + amount, info.data());
            }

        } else if (command.startsWith("[back]")) {
            int amount = 1;
            try {
                amount = Integer.parseInt(command.substring(7));
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
            }
            switch (info.guiType()) {
                case "select-price" -> {
                    GUIOptions gui = GUI.getGUI("select-price");
                    String uuid;
                    if (info.data().length > 0) {
                        uuid = (String) info.data()[0];
                    } else {
                        if (gui != null)
                            uuid = ((PlayerItem) info.displayItems().get(0)).getUuid().toString();
                        else
                            // select-price GUI hasn't been set up
                            uuid = new UUID(0, 0).toString();
                    }
                    openGUI(player, info.guiType(), info.page() - amount, uuid);
                }
                case "leaderboard" -> {
                    Leaderboard leaderboard = (Leaderboard) info.data()[0];
                    openGUI(player, info.guiType(), info.page() - amount, leaderboard);
                }
                case "bounty-item-select" -> {
                    // add current items to data
                    GUIOptions gui = GUI.getGUI("bounty-item-select");
                    if (gui == null) {
                        // gui not set up
                        Bukkit.getLogger().warning("[NotBounties] bounty-item-select GUI not set up.");
                        return;
                    }
                    // get current inventory items from gui info
                    ItemStack[][] allItems = (ItemStack[][]) info.data()[1];
                    // get all items in player slots except first one
                    ItemStack[] items = new ItemStack[allItems[0].length];
                    for (int i = 1; i < gui.getPlayerSlots().size(); i++) {
                        ItemStack item = player.getOpenInventory().getItem(gui.getPlayerSlots().get(i));
                        items[i-1] = item;
                    }
                    // change current page in current inventory items
                    allItems[(int) (info.page()-1)] = items;
                    // open GUI
                    openGUI(player, info.guiType(), info.page() - amount, info.data()[0], allItems);
                }
                default -> openGUI(player, info.guiType(), info.page() - amount, info.data());
            }
        } else if (command.equalsIgnoreCase("[offline]")) {
            if (info.data().length > 0 && info.data()[0] instanceof String && ((String) info.data()[0]).equalsIgnoreCase("offline"))
                openGUI(player, info.guiType(), info.page());
            else
                openGUI(player, info.guiType(), info.page(), "offline");

        } else if (command.equalsIgnoreCase("<respawn>")) {
            if (player.isDead())
                player.spigot().respawn();
        } else {
            if (papiEnabled)
                command = new PlaceholderAPIClass().parse(player, command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }


    public static boolean isRequirementCompleted(String requirement, OfflinePlayer player) {
        try {
            String placeholder = requirement.substring(0, requirement.indexOf(" "));
            String operator = requirement.substring(requirement.indexOf(" ") + 1, requirement.lastIndexOf(" "));
            String value = requirement.substring(requirement.lastIndexOf(" ") + 1);
            Object parsedValue = parseValue(value);

            if (placeholder.contains("%") && papiEnabled) {
                String parsed = LanguageOptions.parse(placeholder, player);
                Object parsedPlaceholder = parseValue(parsed);

                // value types don't match
                if (parsedValue instanceof Boolean && !(parsedPlaceholder instanceof Boolean)
                        || parsedValue instanceof Integer && !(parsedPlaceholder instanceof Integer)
                        || parsedValue instanceof Double && !(parsedPlaceholder instanceof Double)
                        || parsedValue instanceof String && !(parsedPlaceholder instanceof String)) {
                    return false;
                }
                return compareObjects(parsedValue, parsedPlaceholder, operator);
            } else {
                int customModelData = -1;
                if (placeholder.contains("<") && placeholder.contains(">"))
                    try {
                        customModelData = (int) tryParse(placeholder.substring(placeholder.indexOf("<") + 1, placeholder.indexOf(">")));
                        placeholder = placeholder.substring(0, placeholder.indexOf("<"));
                    } catch (NumberFormatException e) {
                        Bukkit.getLogger().warning("[NotRanks] Could not get custom model data from " + placeholder);
                        Bukkit.getLogger().warning(e.toString());
                    }
                // check if it is a material
                if (player.isOnline() && player.getPlayer() != null) {
                    Material m = Material.getMaterial(placeholder);
                    if (m != null && (parsedValue instanceof Integer || parsedValue instanceof Double)) {
                            int reqValue = parsedValue instanceof Double ? ((Double) parsedValue).intValue() : (int) parsedValue;
                            int playerValue = checkAmount(player.getPlayer(), m, customModelData);
                            return compareObjects(reqValue, playerValue, operator);
                        }

                }
            }
        } catch (IndexOutOfBoundsException e) {
            Bukkit.getLogger().warning("Could not check requirement: " + requirement + "\nIs it formatted properly?");
        }
        return false;
    }

    private static Object parseValue(String str) {
        if (str.equalsIgnoreCase("true"))
            return true;
        if (str.equalsIgnoreCase("false"))
            return false;

        try {
            return tryParse(str);
        } catch (NumberFormatException e) {
            return str;
        }
    }

    public static boolean compareObjects(Object parsedValue, Object parsedPlaceholder, String operator) {
        if (parsedValue instanceof Boolean) {
            boolean a = (boolean) parsedValue;
            boolean b = (boolean) parsedPlaceholder;
            if (operator.equalsIgnoreCase("!=")) {
                return a != b;
            }
            return a == b;
        } else if (parsedValue instanceof Integer) {
            int a = (int) parsedValue;
            int b = (int) parsedPlaceholder;
            if (operator.equalsIgnoreCase("!=")) {
                return a != b;
            }
            if (operator.equalsIgnoreCase("=")) {
                return a == b;
            }
            if (operator.equalsIgnoreCase(">=")) {
                return b >= a;
            }
            if (operator.equalsIgnoreCase("<=")) {
                return b <= a;
            }
            if (operator.equalsIgnoreCase(">")) {
                return b > a;
            }
            if (operator.equalsIgnoreCase("<")) {
                return b < a;
            }
            return b >= a;
        } else if (parsedValue instanceof Double) {
            double a = (double) parsedValue;
            double b = (double) parsedPlaceholder;
            if (operator.equalsIgnoreCase("!=")) {
                return a != b;
            }
            if (operator.equalsIgnoreCase("=")) {
                return a == b;
            }
            if (operator.equalsIgnoreCase(">=")) {
                return b >= a;
            }
            if (operator.equalsIgnoreCase("<=")) {
                return b <= a;
            }
            if (operator.equalsIgnoreCase(">")) {
                return b > a;
            }
            if (operator.equalsIgnoreCase("<")) {
                return b < a;
            }
            return b >= a;
        } else if (parsedValue instanceof String) {
            String a = (String) parsedValue;
            String b = (String) parsedPlaceholder;
            if (operator.equalsIgnoreCase("!=")) {
                return !a.equalsIgnoreCase(b);
            }
            return a.equalsIgnoreCase(b);
        }
        return false;
    }
}
