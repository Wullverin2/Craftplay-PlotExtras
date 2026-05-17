package de.craftplay.plotextras.plotmeta;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotMetaService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private YamlConfiguration data;

    public PlotMetaService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-meta.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner für Plot-Metadaten konnte nicht erstellt werden.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public Map<String, String> placeholders(final Plot plot) {
        final Plot basePlot = base(plot);
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("plot_status", status(basePlot));
        placeholders.put("plot_status_display", statusDisplay(status(basePlot)));
        placeholders.put("plot_owner_note", ownerNote(basePlot));
        placeholders.put("plot_team_note", teamNote(basePlot));
        placeholders.put("plot_visits", String.valueOf(visits(basePlot)));
        placeholders.put("plot_likes", String.valueOf(likes(basePlot)));
        placeholders.put("plot_last_visitor", string(basePlot, "last-visitor", "-"));
        placeholders.put("plot_last_visit", string(basePlot, "last-visit", "-"));
        return placeholders;
    }

    public void recordVisit(final Plot plot, final Player player) {
        if (!featureToggleService.isEnabled("player.plot-visits")) {
            return;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null || player == null) {
            return;
        }
        final String path = path(basePlot);
        data.set(path + ".world", basePlot.getWorldName());
        data.set(path + ".plot-id", basePlot.getId().toString());
        data.set(path + ".visits", visits(basePlot) + 1);
        data.set(path + ".last-visitor", player.getName());
        data.set(path + ".last-visit", Instant.now().toString());
        save();
    }

    public boolean toggleLike(final Plot plot, final Player player) {
        if (!featureToggleService.isEnabled("player.plot-likes")) {
            return false;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null || player == null) {
            return false;
        }
        final String path = path(basePlot) + ".likes";
        final Set<String> likes = new HashSet<>(data.getStringList(path));
        final String playerId = player.getUniqueId().toString();
        final boolean liked;
        if (likes.contains(playerId)) {
            likes.remove(playerId);
            liked = false;
        } else {
            likes.add(playerId);
            liked = true;
        }
        data.set(path, likes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        save();
        return liked;
    }

    public boolean setOwnerNote(final Plot plot, final String note) {
        if (!featureToggleService.isEnabled("player.plot-notes")) {
            return false;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return false;
        }
        data.set(path(basePlot) + ".owner-note", note == null || note.isBlank() ? null : note);
        save();
        return true;
    }

    public boolean setTeamNote(final Plot plot, final String note) {
        if (!featureToggleService.isEnabled("team.notes")) {
            return false;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return false;
        }
        data.set(path(basePlot) + ".team-note", note == null || note.isBlank() ? null : note);
        save();
        return true;
    }

    public boolean setStatus(final Plot plot, final String status) {
        if (!featureToggleService.isEnabled("player.plot-status")) {
            return false;
        }
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return false;
        }
        data.set(path(basePlot) + ".status", normalizeStatus(status));
        save();
        return true;
    }

    public boolean canManageTeamMeta(final Player player) {
        return featureToggleService.isEnabled("team.notes")
                && (player.hasPermission("craftplayplotextras.plotmeta.team") || player.hasPermission("craftplayplotextras.admin"));
    }

    public boolean canSetStatus(final Player player) {
        return featureToggleService.isEnabled("player.plot-status")
                && (player.hasPermission("craftplayplotextras.plotmeta.status") || player.hasPermission("craftplayplotextras.admin"));
    }

    private String status(final Plot plot) {
        return normalizeStatus(string(plot, "status", "normal"));
    }

    private String statusDisplay(final String status) {
        return switch (normalizeStatus(status)) {
            case "private" -> "Privat";
            case "public" -> "Öffentlich";
            case "team-checked" -> "Team-geprüft";
            case "under-watch" -> "Unter Beobachtung";
            case "locked" -> "Gesperrt";
            case "maintenance" -> "Wartung";
            default -> "Normal";
        };
    }

    private String ownerNote(final Plot plot) {
        return string(plot, "owner-note", "-");
    }

    private String teamNote(final Plot plot) {
        return string(plot, "team-note", "-");
    }

    private int visits(final Plot plot) {
        return integer(plot, "visits", 0);
    }

    private int likes(final Plot plot) {
        if (plot == null) {
            return 0;
        }
        return data.getStringList(path(plot) + ".likes").size();
    }

    private String string(final Plot plot, final String key, final String fallback) {
        if (plot == null) {
            return fallback;
        }
        return data.getString(path(plot) + "." + key, fallback);
    }

    private int integer(final Plot plot, final String key, final int fallback) {
        if (plot == null) {
            return fallback;
        }
        return data.getInt(path(plot) + "." + key, fallback);
    }

    private Plot base(final Plot plot) {
        return plot == null ? null : plot.getBasePlot(false);
    }

    private String path(final Plot plot) {
        final Plot basePlot = base(plot);
        return "plots." + sanitize(basePlot.getWorldName()) + "_" + sanitize(basePlot.getId().toDashSeparatedString());
    }

    private String sanitize(final String value) {
        return value == null ? "-" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private String normalizeStatus(final String value) {
        final String normalized = value == null ? "normal" : value.toLowerCase(Locale.ROOT).replace('_', '-').trim();
        return switch (normalized) {
            case "private", "public", "team-checked", "under-watch", "locked", "maintenance" -> normalized;
            default -> "normal";
        };
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plot-Metadaten konnten nicht gespeichert werden.", exception);
        }
    }
}
