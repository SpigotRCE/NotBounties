package me.jadenp.notbounties.utils.configuration.auto_bounties;

import me.jadenp.notbounties.data.Whitelist;
import me.jadenp.notbounties.utils.DataManager;
import me.jadenp.notbounties.utils.configuration.ConfigOptions;
import me.jadenp.notbounties.utils.configuration.Immunity;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

import static me.jadenp.notbounties.utils.BountyManager.*;
import static me.jadenp.notbounties.utils.BountyManager.getBounty;
import static me.jadenp.notbounties.utils.configuration.LanguageOptions.*;

public class MurderBounties {

    private MurderBounties(){}

    /**
     * The minimum time between bounties being placed on a player for murder.
     */
    private static int murderCooldown;
    /**
     * The amount of currency that the bounty increases by.
     */
    private static double murderBountyIncrease;
    /**
     * Whether bounties claimed should not set a bounty on the killer for murder.
     */
    private static boolean murderExcludeClaiming;
    /**
     * A map of the killer and the player they have killed with the time they were killed.
     * (Killer, (Player, Time))
     */
    private static Map<UUID, Map<UUID, Long>> playerKills = new HashMap<>();

    public static void loadConfiguration(ConfigurationSection murderBounties) {
        murderCooldown = murderBounties.getInt("player-cooldown");
        murderBountyIncrease = murderBounties.getDouble("bounty-increase");
        murderExcludeClaiming = murderBounties.getBoolean("exclude-claiming");
    }

    /**
     * Removes old player kills from playerKills HashMap
     */
    public static void cleanPlayerKills() {
        Map<UUID, Map<UUID, Long>> updatedMap = new HashMap<>();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : playerKills.entrySet()) {
            Map<UUID, Long> deaths = entry.getValue();
            deaths.entrySet().removeIf(entry1 -> entry1.getValue() < System.currentTimeMillis() - murderCooldown * 1000L);
            updatedMap.put(entry.getKey(), deaths);
        }
        updatedMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        playerKills = updatedMap;
    }

    /**
     * Checks if a bounty should be placed on the killer for murder, and places one if necessary.
     * @param player Player that was killed.
     * @param killer Player that killed.
     */
    public static void killPlayer(Player player, Player killer) {
        // check if we should increase the killer's bounty
        if (isEnabled() && !killer.hasMetadata("NPC")) { // don't raise bounty on a npc
            // check immunity
            if (!ConfigOptions.autoBountyOverrideImmunity && Immunity.getAppliedImmunity(killer, murderBountyIncrease) != Immunity.ImmunityType.DISABLE || hasImmunity(killer))
                return;
            if ((!playerKills.containsKey(killer.getUniqueId()) ||
                    !playerKills.get(killer.getUniqueId()).containsKey(player.getUniqueId()) ||
                    playerKills.get(killer.getUniqueId()).get(player.getUniqueId()) < System.currentTimeMillis() - murderCooldown * 1000L) && (!murderExcludeClaiming || !hasBounty(player.getUniqueId()) || Objects.requireNonNull(getBounty(player.getUniqueId())).getTotalDisplayBounty(killer) < 0.01)) {
                // increase
                addBounty(killer, murderBountyIncrease, new ArrayList<>(), new Whitelist(new ArrayList<>(), false));
                killer.sendMessage(parse(getPrefix() + getMessage("murder"), Objects.requireNonNull(getBounty(killer.getUniqueId())).getTotalDisplayBounty(), player));
                Map<UUID, Long> kills = playerKills.containsKey(killer.getUniqueId()) ? playerKills.get(killer.getUniqueId()) : new HashMap<>();
                kills.put(player.getUniqueId(), System.currentTimeMillis());
                playerKills.put(killer.getUniqueId(), kills);
            }

        }

    }

    public static boolean isEnabled() {
        return murderBountyIncrease > 0;
    }

    private static boolean hasImmunity(OfflinePlayer player) {
        if (player.isOnline())
            return Objects.requireNonNull(player.getPlayer()).hasPermission("notbounties.immunity.murder");
        return DataManager.getPlayerData(player.getUniqueId()).hasMurderImmunity();
    }

    public static void logout(Player player) {
        DataManager.getPlayerData(player.getUniqueId()).setMurderImmunity(player.hasPermission("notbounties.immunity.murder"));
    }
}
