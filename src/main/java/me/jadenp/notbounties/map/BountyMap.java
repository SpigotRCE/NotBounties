package me.jadenp.notbounties.map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import me.jadenp.notbounties.Bounty;
import me.jadenp.notbounties.NotBounties;
import me.jadenp.notbounties.utils.ConfigOptions;
import me.jadenp.notbounties.utils.NumberFormatting;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class BountyMap implements Listener {

    public static BufferedImage bountyPoster;
    public static BufferedImage deadBounty;
    private static Font playerFont;
    public static File posterDirectory;
    public static BiMap<Integer, UUID> mapIDs = HashBiMap.create();
    private static File mapData;

    public static void initialize(){
        posterDirectory = new File(NotBounties.getInstance().getDataFolder() + File.separator + "posters");
        posterDirectory.mkdir();
        if (!new File(posterDirectory + File.separator + "playerfont.ttf").exists())
            NotBounties.getInstance().saveResource("posters/playerfont.ttf", false);
        if (!new File(posterDirectory + File.separator + "bounty poster.png").exists())
            NotBounties.getInstance().saveResource("posters/bounty poster.png", false);
        if (!new File(posterDirectory + File.separator + "poster template.png").exists())
            NotBounties.getInstance().saveResource("posters/poster template.png", false);
        if (!new File(posterDirectory + File.separator + "dead bounty.png").exists())
            NotBounties.getInstance().saveResource("posters/dead bounty.png", false);
        if (!new File(posterDirectory + File.separator + "READ_ME.txt").exists())
            NotBounties.getInstance().saveResource("posters/READ_ME.txt", false);
        mapData = new File(posterDirectory + File.separator + "mapdata.yml");
        if (!mapData.exists())
            NotBounties.getInstance().saveResource("posters/mapdata.yml", false);

        try {
            bountyPoster = ImageIO.read(new File(posterDirectory + File.separator + "bounty poster.png"));
            deadBounty = ImageIO.read(new File(posterDirectory + File.separator + "dead bounty.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // load mapIDs
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(mapData);
        for (String key : configuration.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int id = configuration.getInt(key);
                mapIDs.put(id, uuid);
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("Found a key in mapdata.yml that isn't a UUID. \"" + key + "\". This will be overwritten in 5 minutes.");
            }
        }
    }

    public static void save() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<Integer, UUID> entry : mapIDs.entrySet()) {
            configuration.set(entry.getValue().toString(), entry.getKey());
        }
        configuration.save(mapData);
    }

    @EventHandler
    public void onMapInitialize(MapInitializeEvent event) throws IOException {
        MapView mapView = event.getMap();
        if (mapIDs.containsKey(mapView.getId())) {
            if (!event.getMap().isVirtual()) {
                UUID uuid = mapIDs.get(mapView.getId());
                mapView.setLocked(ConfigOptions.lockMap);
                mapView.getRenderers().forEach(mapView::removeRenderer);
                mapView.addRenderer(new Renderer(uuid));
            }
        }
    }

    public static void loadFont() {
        try {
            playerFont = Font.createFont(Font.TRUETYPE_FONT, new File(BountyMap.posterDirectory + File.separator + "playerfont.ttf"));
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
        }
    }
    public static Font getPlayerFont(float fontSize, boolean bold) {
        if (bold)
            return playerFont.deriveFont(fontSize).deriveFont(Collections.singletonMap(TextAttribute.WEIGHT, TextAttribute.WEIGHT_ULTRABOLD));
        return playerFont.deriveFont(fontSize);
    }
    public BountyMap(){}

    public static void giveMap(Player player, Bounty bounty) throws IOException {
        MapView mapView;
        if (mapIDs.containsValue(bounty.getUUID())){
            int id = mapIDs.inverse().get(bounty.getUUID());
            mapView = Bukkit.getMap(id);
            if (mapView == null) {
                mapIDs.remove(id);
                mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
            }
        } else {
            mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
            mapIDs.put(mapView.getId(), bounty.getUUID());
        }
        mapView.setUnlimitedTracking(false);
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.setLocked(ConfigOptions.lockMap);
        mapView.setTrackingPosition(false);
        mapView.addRenderer(new Renderer(bounty.getUUID()));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        assert meta != null;
        meta.setMapView(mapView);
        meta.setDisplayName(ConfigOptions.parse(ConfigOptions.speakings.get(66), bounty.getName(), Bukkit.getOfflinePlayer(bounty.getUUID())));
        ArrayList<String> lore = new ArrayList<>();
        for (String str : ConfigOptions.mapLore) {
            lore.add(ConfigOptions.parse(str, bounty.getName(), bounty.getTotalBounty(), bounty.getLatestSetter(), Bukkit.getOfflinePlayer(bounty.getUUID())));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        mapItem.setItemMeta(meta);

        NumberFormatting.givePlayer(player, mapItem, 1);
    }
    public static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPreMultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPreMultiplied, null);
    }


}
