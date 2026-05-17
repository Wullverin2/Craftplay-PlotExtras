package de.craftplay.plotextras.performance;

import com.plotsquared.core.plot.Plot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class PlotPerformanceService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;

    public PlotPerformanceService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
    }

    public boolean canView(final org.bukkit.command.CommandSender sender) {
        return featureToggleService.isEnabled("team.performance")
                && (sender.hasPermission("craftplayplotextras.performance.view") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public PlotPerformanceSnapshot snapshot(final Plot plot) {
        if (plot == null || !featureToggleService.isEnabled("team.performance")) {
            return new PlotPerformanceSnapshot("-", 0, Map.of(), List.of("Kein Plot gefunden."));
        }
        final World world = plugin.getServer().getWorld(plot.getWorldName());
        if (world == null) {
            return new PlotPerformanceSnapshot(plotKey(plot), 0, Map.of(), List.of("Welt ist nicht geladen."));
        }

        final Map<String, Integer> entityCounts = new LinkedHashMap<>();
        int total = 0;
        for (final CuboidRegion region : regions(plot)) {
            for (final Entity entity : world.getNearbyEntities(toBoundingBox(world, region))) {
                if (!contains(region, entity.getLocation())) {
                    continue;
                }
                total++;
                entityCounts.merge(entity.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }

        final List<String> warnings = new ArrayList<>();
        final int warningAt = Math.max(1, plugin.getConfig().getInt("team-tools.performance.entity-warning-threshold", 150));
        final int criticalAt = Math.max(warningAt, plugin.getConfig().getInt("team-tools.performance.entity-critical-threshold", 300));
        if (total >= criticalAt) {
            warnings.add("Kritisch viele Entities: " + total);
        } else if (total >= warningAt) {
            warnings.add("Viele Entities: " + total);
        }
        entityCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= plugin.getConfig().getInt("team-tools.performance.single-type-warning-threshold", 80))
                .forEach(entry -> warnings.add("Viele " + entry.getKey() + ": " + entry.getValue()));
        if (warnings.isEmpty()) {
            warnings.add("Keine Auffälligkeiten.");
        }
        return new PlotPerformanceSnapshot(plotKey(plot), total, entityCounts, warnings);
    }

    private List<CuboidRegion> regions(final Plot plot) {
        final List<CuboidRegion> regions = new ArrayList<>();
        for (final Plot connectedPlot : plot.getBasePlot(false).getConnectedPlots()) {
            try {
                regions.add(connectedPlot.getLargestRegion());
            } catch (final RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read PlotSquared region for performance snapshot.", exception);
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

    private String plotKey(final Plot plot) {
        final Plot basePlot = plot.getBasePlot(false);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }
}
