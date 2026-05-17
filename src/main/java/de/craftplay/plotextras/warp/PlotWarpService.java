package de.craftplay.plotextras.warp;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

public final class PlotWarpService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private YamlConfiguration data;

    public PlotWarpService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-warps.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner für Plot-Warps konnte nicht erstellt werden.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public List<PlotWarpEntry> listWarps(final Plot plot) {
        if (!featureToggleService.isEnabled("player.plot-warps")) {
            return List.of();
        }
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return List.of();
        }
        final ConfigurationSection section = data.getConfigurationSection(path(basePlot) + ".warps");
        if (section == null) {
            return List.of();
        }
        final List<PlotWarpEntry> entries = new ArrayList<>();
        for (final String id : section.getKeys(false)) {
            readWarp(basePlot, id).ifPresent(entries::add);
        }
        entries.sort(Comparator.comparing(PlotWarpEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public Optional<PlotWarpEntry> getWarp(final Plot plot, final String warpId) {
        final Plot basePlot = base(plot);
        if (basePlot == null || warpId == null) {
            return Optional.empty();
        }
        return readWarp(basePlot, normalizeId(warpId));
    }

    public boolean setWarp(final Plot plot, final String rawId, final Location location) {
        if (!featureToggleService.isEnabled("player.plot-warps.set")) {
            return false;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null || location == null || location.getWorld() == null) {
            return false;
        }
        final String id = normalizeId(rawId);
        if (id.isBlank()) {
            return false;
        }
        final String path = path(basePlot) + ".warps." + id;
        data.set(path + ".display-name", displayName(rawId));
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
        save();
        return true;
    }

    public boolean deleteWarp(final Plot plot, final String rawId) {
        if (!featureToggleService.isEnabled("player.plot-warps.delete")) {
            return false;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return false;
        }
        final String id = normalizeId(rawId);
        if (data.getConfigurationSection(path(basePlot) + ".warps." + id) == null) {
            return false;
        }
        data.set(path(basePlot) + ".warps." + id, null);
        save();
        return true;
    }

    public boolean teleport(final Player player, final Plot plot, final String rawId) {
        if (!featureToggleService.isEnabled("player.plot-warps.teleport")) {
            return false;
        }
        final Optional<PlotWarpEntry> warp = getWarp(plot, rawId);
        if (warp.isEmpty()) {
            return false;
        }
        final World world = Bukkit.getWorld(warp.get().world());
        if (world == null) {
            return false;
        }
        return player.teleport(warp.get().toLocation(world));
    }

    public String normalizeId(final String rawId) {
        return rawId == null ? "" : rawId.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]", "-");
    }

    private Optional<PlotWarpEntry> readWarp(final Plot plot, final String id) {
        final ConfigurationSection section = data.getConfigurationSection(path(plot) + ".warps." + id);
        if (section == null) {
            return Optional.empty();
        }
        return Optional.of(new PlotWarpEntry(
                id,
                section.getString("display-name", id),
                plotKey(plot),
                section.getString("world", plot.getWorldName()),
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        ));
    }

    private Plot base(final Plot plot) {
        return plot == null ? null : plot.getBasePlot(false);
    }

    private String path(final Plot plot) {
        final Plot basePlot = base(plot);
        return "plots." + sanitize(basePlot.getWorldName()) + "_" + sanitize(basePlot.getId().toDashSeparatedString());
    }

    private String plotKey(final Plot plot) {
        final Plot basePlot = base(plot);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private String sanitize(final String value) {
        return value == null ? "-" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private String displayName(final String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "Warp";
        }
        return rawId.trim();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plot-Warps konnten nicht gespeichert werden.", exception);
        }
    }
}
