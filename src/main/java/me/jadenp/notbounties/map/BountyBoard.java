package me.jadenp.notbounties.map;

import me.jadenp.notbounties.Bounty;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.utils.ConfigOptions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public class BountyBoard {
    private final Location location;
    private final BlockFace direction;
    private final int rank;
    private UUID lastUUID = null;
    private ItemFrame frame = null;
    double lastBounty = 0;

    public BountyBoard(Location location, BlockFace direction, int rank) {

        this.location = location;
        this.direction = direction;
        this.rank = rank;

    }

    public void update(Bounty bounty) throws IOException {
        if (!location.getChunk().isLoaded())
            return;
        if (bounty == null) {
            lastUUID = null;
            lastBounty = 0;
            remove();
            return;
        }
        if (ConfigOptions.updateName == 2 || bounty.getUUID() != lastUUID || (ConfigOptions.updateName == 1 && lastBounty != bounty.getTotalBounty())) {
            lastUUID = bounty.getUUID();
            lastBounty = bounty.getTotalBounty();
            remove();
        }
        if (frame == null) {
            EntityType type = ConfigOptions.boardGlow && NotBounties.serverVersion >= 17 ? EntityType.GLOW_ITEM_FRAME : EntityType.ITEM_FRAME;
            try {
                frame = (ItemFrame) Objects.requireNonNull(location.getWorld()).spawnEntity(location, type);
                frame.getPersistentDataContainer().set(NotBounties.namespacedKey, PersistentDataType.STRING, NotBounties.sessionKey);
                frame.setFacingDirection(direction, true);
                //frame.setRotation(Rotation.NONE);
                ItemStack map = BountyMap.getMap(bounty);
                ItemMeta mapMeta = map.getItemMeta();
                assert mapMeta != null;
                mapMeta.setDisplayName(ConfigOptions.parse(ConfigOptions.boardName, bounty.getName(), bounty.getTotalBounty(), Bukkit.getOfflinePlayer(bounty.getUUID())));
                map.setItemMeta(mapMeta);
                frame.setItem(map);
                frame.setInvulnerable(true);
                frame.setVisible(!ConfigOptions.boardInvisible);
                frame.setFixed(true);
            } catch (IllegalArgumentException ignored) {
                // this is thrown when there is no space to place the board
            }
        }


    }



    public ItemFrame getFrame() {
        return frame;
    }

    public BlockFace getDirection() {
        return direction;
    }

    public Location getLocation() {
        return location;
    }

    public void remove() {
        if (frame != null) {
            frame.setItem(null);
            frame.remove();
            frame = null;
        }
    }

    public int getRank() {
        return rank;
    }
}
