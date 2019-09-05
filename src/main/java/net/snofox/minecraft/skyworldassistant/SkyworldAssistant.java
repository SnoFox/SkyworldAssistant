package net.snofox.minecraft.skyworldassistant;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Josh on 2018-12-15
 */
public class SkyworldAssistant extends JavaPlugin implements Listener {
    final Map<String, String> worldMap = new HashMap<>();
    final Map<String, String> worldMapInverse = new HashMap<>();
    final int syncTimer = -1;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        final ConfigurationSection worldMapConfig = getConfig().getConfigurationSection("worlds");
        for(final String world : worldMapConfig.getKeys(false)) {
            final String skyWorld = worldMapConfig.getString(world);
            if(getServer().getWorld(world) == null || getServer().getWorld(skyWorld) == null) {
                getLogger().warning("Ignoring mapping for " + world + ": World does not exist");
                continue;
            }
            worldMap.put(world, skyWorld);
            worldMapInverse.put(skyWorld, world);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new SyncTask(), 20, 20);
        getLogger().info("Hello, worlds! Config loaded. Listeners registered.");
    }

    class SyncTask implements Runnable {
        public void run() {
            for(final Map.Entry<String, String> worldEntry : worldMap.entrySet()) {
                final World overworld = getServer().getWorld(worldEntry.getKey());
                final World skyworld = getServer().getWorld(worldEntry.getValue());
                if(overworld == null || skyworld == null) continue;
                skyworld.setFullTime(overworld.getFullTime());
                skyworld.setThundering(overworld.isThundering());
                skyworld.setStorm(overworld.hasStorm());
            }
        }
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent ev) {
        final Location to = ev.getTo();
        final Location from = ev.getFrom();
        if(to == null || to.getWorld() == null) return; // ?? Ok IntelliJ
        if(from.getY() >= 256) return;
        if(
                (to.getY() >= 256 && isOverworld(to.getWorld()))
                || (to.getY() < 0 && !isOverworld(to.getWorld()))
        ) {
            final Player p = ev.getPlayer();
            doTeleport(to, p, isOverworld(to.getWorld()));
        }
    }

    @EventHandler
    public void onBedEnter(final PlayerBedEnterEvent ev) {
        final World world = ev.getBed().getWorld();
        if(!worldMapInverse.containsKey(world.getName())) return;
        getLogger().info("Result is " + ev.getBedEnterResult());
        ev.setUseBed(Event.Result.DENY);
        try {
            ev.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("You feel too restless sleeping so high up"));
        } catch (NoSuchMethodError ex) {
            // No-op; Spigot API isn't available in this environment
        }
    }

    @EventHandler
    public void onPortalCreate(final PortalCreateEvent ev) {
        if(worldMapInverse.containsKey(ev.getWorld().getName()))
            ev.setCancelled(true);
    }

    private boolean isOverworld(final World world) {
        return worldMap.containsKey(world.getName());
    }

    private void doTeleport(final Location currentLocation, final Player p, final boolean travelingFromOverworld) {
        final Map<String, String> directionMap = (travelingFromOverworld ? worldMap : worldMapInverse);
        final World destination = getServer().getWorld(directionMap.get(currentLocation.getWorld().getName()));
        final double newY = (travelingFromOverworld ? getConfig().getInt("spawnY") : destination.getMaxHeight());
        final Location newLocation = new Location(destination, currentLocation.getX(), newY, currentLocation.getZ(),
                currentLocation.getYaw(), currentLocation.getPitch());
        newLocation.add(0, .5, 0);
        if(travelingFromOverworld && !p.isFlying() && !p.isGliding()) createSafeSpot(newLocation);
        final Vector playerVelocity = p.getVelocity();
        p.teleport(newLocation);
        if(p.isGliding()) p.setVelocity(playerVelocity);
    }

    private void createSafeSpot(final Location loc) {
        final Location feetLocation = loc.clone().subtract(0, 1, 0);
        final Block legBlock = loc.getBlock();
        final Location headLocation = loc.clone().add(0, 1, 0);
        final Block headBlock = headLocation.getBlock();
        final Block footBlock = feetLocation.getBlock();
        if(footBlock.isPassable()) footBlock.setType(Material.COBBLESTONE);
        if(!headBlock.isPassable()) headBlock.setType(Material.AIR);
        if(!legBlock.isPassable()) legBlock.setType(Material.AIR);
    }
}
