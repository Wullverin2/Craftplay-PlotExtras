package de.craftplay.plotextras.moderation;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.plot.Plot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class PlotModerationService implements Listener {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private final Map<String, FreezeEntry> frozenPlots = new LinkedHashMap<>();
    private YamlConfiguration data;

    public PlotModerationService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-moderation.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner für Plot-Moderation konnte nicht erstellt werden.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        reloadFrozenPlots();
    }

    public boolean canModerate(final Player player) {
        return featureToggleService.isEnabled("team.moderation")
                && (player.hasPermission("craftplayplotextras.moderation.manage") || player.hasPermission("craftplayplotextras.admin"));
    }

    public boolean canBypass(final Player player) {
        return player.hasPermission("craftplayplotextras.moderation.bypass") || player.hasPermission("craftplayplotextras.admin");
    }

    public boolean freeze(final Player actor, final Plot plot, final String reason) {
        if (plot == null || actor == null || !canModerate(actor) || !featureToggleService.isEnabled("team.moderation.freeze")) {
            return false;
        }
        final Plot basePlot = plot.getBasePlot(false);
        final String key = plotKey(basePlot);
        final FreezeEntry entry = new FreezeEntry(key, basePlot.getWorldName(), basePlot.getId().toString(), actor.getName(), Instant.now(), blank(reason, "Teamprüfung"));
        frozenPlots.put(key, entry);
        saveFrozen(entry);
        return true;
    }

    public boolean unfreeze(final Player actor, final Plot plot) {
        if (plot == null || actor == null || !canModerate(actor) || !featureToggleService.isEnabled("team.moderation.freeze")) {
            return false;
        }
        final String key = plotKey(plot);
        if (frozenPlots.remove(key) == null) {
            return false;
        }
        data.set("frozen." + sanitize(key), null);
        save();
        return true;
    }

    public boolean isFrozen(final Plot plot) {
        return plot != null && frozenPlots.containsKey(plotKey(plot));
    }

    public List<String> listFrozen() {
        return frozenPlots.values().stream()
                .map(entry -> entry.plotKey() + " | " + entry.reason() + " | " + entry.actor())
                .toList();
    }

    public int cleanup(final Player actor, final Plot plot, final String rawMode) {
        if (plot == null || actor == null || !canModerate(actor) || !featureToggleService.isEnabled("team.moderation.cleanup")) {
            return -1;
        }
        final World world = plugin.getServer().getWorld(plot.getWorldName());
        if (world == null) {
            return -1;
        }
        final String mode = rawMode == null ? "drops" : rawMode.toLowerCase(Locale.ROOT);
        int removed = 0;
        for (final CuboidRegion region : regions(plot)) {
            for (final Entity entity : world.getNearbyEntities(toBoundingBox(world, region))) {
                if (!contains(region, entity.getLocation()) || entity instanceof Player || !matchesCleanupMode(entity, mode)) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (shouldBlock(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (shouldBlock(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Location location = event.getClickedBlock() == null ? event.getPlayer().getLocation() : event.getClickedBlock().getLocation();
        if (shouldBlock(event.getPlayer(), location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        final Player player = event.getPlayer();
        if (player != null && shouldBlock(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(final HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && shouldBlock(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        if (!featureToggleService.isEnabled("team.moderation.freeze") || !featureToggleService.isEnabled("team.moderation.freeze.block-spawns")) {
            return;
        }
        final Plot plot = getPlot(event.getLocation());
        if (isFrozen(plot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(final BlockRedstoneEvent event) {
        if (!featureToggleService.isEnabled("team.moderation.freeze") || !featureToggleService.isEnabled("team.moderation.freeze.block-redstone")) {
            return;
        }
        final Plot plot = getPlot(event.getBlock().getLocation());
        if (isFrozen(plot)) {
            event.setNewCurrent(0);
        }
    }

    private boolean shouldBlock(final Player player, final Location location) {
        if (!featureToggleService.isEnabled("team.moderation.freeze") || canBypass(player)) {
            return false;
        }
        return isFrozen(getPlot(location));
    }

    private boolean matchesCleanupMode(final Entity entity, final String mode) {
        return switch (mode) {
            case "drops", "items" -> entity instanceof Item || entity instanceof ExperienceOrb;
            case "projectiles" -> entity instanceof Projectile;
            case "monsters", "mobs" -> entity instanceof Monster;
            case "animals" -> entity instanceof Animals;
            case "vehicles" -> entity instanceof Vehicle;
            case "all" -> true;
            default -> false;
        };
    }

    private Plot getPlot(final Location location) {
        try {
            return Plot.getPlot(BukkitUtil.adapt(location));
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    private List<CuboidRegion> regions(final Plot plot) {
        final List<CuboidRegion> regions = new ArrayList<>();
        for (final Plot connectedPlot : plot.getBasePlot(false).getConnectedPlots()) {
            try {
                regions.add(connectedPlot.getLargestRegion());
            } catch (final RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read PlotSquared region for plot cleanup.", exception);
            }
        }
        return regions;
    }

    private BoundingBox toBoundingBox(final World world, final CuboidRegion region) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        return new BoundingBox(min.getX(), world.getMinHeight(), min.getZ(), max.getX() + 1D, world.getMaxHeight(), max.getZ() + 1D);
    }

    private boolean contains(final CuboidRegion region, final Location location) {
        return region.contains(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    private void reloadFrozenPlots() {
        frozenPlots.clear();
        final ConfigurationSection section = data.getConfigurationSection("frozen");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(key);
            if (entrySection == null) {
                continue;
            }
            try {
                final FreezeEntry entry = new FreezeEntry(
                        entrySection.getString("plot-key", "-"),
                        entrySection.getString("world", "-"),
                        entrySection.getString("plot-id", "-"),
                        entrySection.getString("actor", "System"),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        entrySection.getString("reason", "Teamprüfung")
                );
                frozenPlots.put(entry.plotKey(), entry);
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungültiger Freeze-Eintrag ignoriert: " + key);
            }
        }
    }

    private void saveFrozen(final FreezeEntry entry) {
        final String path = "frozen." + sanitize(entry.plotKey());
        data.set(path + ".plot-key", entry.plotKey());
        data.set(path + ".world", entry.world());
        data.set(path + ".plot-id", entry.plotId());
        data.set(path + ".actor", entry.actor());
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".reason", entry.reason());
        save();
    }

    private String plotKey(final Plot plot) {
        final Plot basePlot = plot.getBasePlot(false);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private String sanitize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private String blank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plot-Moderation konnte nicht gespeichert werden.", exception);
        }
    }

    private record FreezeEntry(String plotKey, String world, String plotId, String actor, Instant createdAt, String reason) {
    }
}
