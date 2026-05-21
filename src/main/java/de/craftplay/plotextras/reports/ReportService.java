package de.craftplay.plotextras.reports;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class ReportService {

    private final CraftplayPlotExtrasPlugin plugin;
    private final PlotSquaredFlagService flagService;
    private final Map<String, PlotReport> reports = new LinkedHashMap<>();
    private File file;
    private boolean enabled;
    private boolean asyncSaves;

    public ReportService(final CraftplayPlotExtrasPlugin plugin, final PlotSquaredFlagService flagService) {
        this.plugin = plugin;
        this.flagService = flagService;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("reports.enabled", true);
        asyncSaves = plugin.getConfig().getBoolean("technical.async-yaml-saves", true);
        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("reports.data-file", "reports.yml"));
        if (!file.exists()) {
            createDefaultFile();
        }
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<PlotReport> create(final Player reporter, final String category, final String message, final String priority) {
        if (!enabled) {
            plugin.getLanguageManager().send(reporter, "reports-disabled");
            return Optional.empty();
        }
        if (!reporter.hasPermission("craftplayplotextras.reports.create")) {
            plugin.getLanguageManager().send(reporter, "no-permission");
            return Optional.empty();
        }
        final Optional<PlotContext> context = flagService.currentPlotContext(reporter);
        if (!context.isPresent() || !context.get().isComplete()) {
            plugin.getLanguageManager().send(reporter, "no-plot");
            return Optional.empty();
        }
        final PlotContext plot = context.get();
        final String id = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-" + Integer.toHexString(reports.size() + 1).toUpperCase(Locale.ROOT);
        final PlotReport report = new PlotReport(
                id,
                reporter.getUniqueId(),
                reporter.getName(),
                System.currentTimeMillis(),
                plot.getWorldName(),
                plot.getPlotId(),
                plot.getPlotIds().isEmpty() ? plot.getPlotId() : String.join(", ", plot.getPlotIds()),
                plot.getMergeType(),
                plot.getOwnerName(),
                category,
                message,
                priority,
                "open",
                "",
                "",
                0L
        );
        reports.put(id, report);
        save();
        final Map<String, String> placeholders = placeholders(report);
        plugin.getLanguageManager().send(reporter, "report-created", placeholders);
        notifyTeam(report);
        return Optional.of(report);
    }

    public List<PlotReport> list(final String status) {
        final String normalized = status == null || status.trim().isEmpty() ? "open" : status.toLowerCase(Locale.ROOT);
        final List<PlotReport> result = new ArrayList<>();
        for (final PlotReport report : reports.values()) {
            if (!"all".equals(normalized) && !report.getStatus().equalsIgnoreCase(normalized)) {
                continue;
            }
            result.add(report);
        }
        result.sort(Comparator.comparingLong(PlotReport::getCreatedAt).reversed());
        return result;
    }

    public Optional<PlotReport> find(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(reports.get(id));
    }

    public boolean close(final Player handler, final String id) {
        if (!canManage(handler)) {
            return false;
        }
        return updateStatus(handler, id, "closed", "report-closed");
    }

    public boolean reopen(final Player handler, final String id) {
        if (!canManage(handler)) {
            return false;
        }
        return updateStatus(handler, id, "open", "report-reopened");
    }

    public boolean setPriority(final Player handler, final String id, final String priority) {
        if (!canManage(handler)) {
            return false;
        }
        final PlotReport report = reports.get(id);
        if (report == null) {
            plugin.getLanguageManager().send(handler, "report-not-found");
            return false;
        }
        final PlotReport updated = report.withPriority(priority == null ? "normal" : priority);
        reports.put(id, updated);
        save();
        plugin.getLanguageManager().send(handler, "report-priority-changed", placeholders(updated));
        return true;
    }

    public boolean setNote(final Player handler, final String id, final String note) {
        if (!canManage(handler)) {
            return false;
        }
        final PlotReport report = reports.get(id);
        if (report == null) {
            plugin.getLanguageManager().send(handler, "report-not-found");
            return false;
        }
        final PlotReport updated = report.withNote(note, handler.getName(), System.currentTimeMillis());
        reports.put(id, updated);
        save();
        plugin.getLanguageManager().send(handler, "report-note-saved", placeholders(updated));
        return true;
    }

    public Map<String, String> placeholders(final PlotReport report) {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", report.getId());
        placeholders.put("reporter", report.getReporterName());
        placeholders.put("created_at", formatDate(report.getCreatedAt()));
        placeholders.put("world", report.getWorld());
        placeholders.put("plot", report.getPlot());
        placeholders.put("plots", report.getPlots());
        placeholders.put("merge", report.getMerge());
        placeholders.put("owner", report.getOwner());
        placeholders.put("category", report.getCategory());
        placeholders.put("message", report.getMessage());
        placeholders.put("priority", report.getPriority());
        placeholders.put("status", report.getStatus());
        placeholders.put("note", report.getNote().isEmpty() ? "-" : report.getNote());
        placeholders.put("handled_by", report.getHandledBy().isEmpty() ? "-" : report.getHandledBy());
        placeholders.put("handled_at", report.getHandledAt() <= 0L ? "-" : formatDate(report.getHandledAt()));
        return placeholders;
    }

    private boolean updateStatus(final Player handler, final String id, final String status, final String messageKey) {
        final PlotReport report = reports.get(id);
        if (report == null) {
            plugin.getLanguageManager().send(handler, "report-not-found");
            return false;
        }
        final PlotReport updated = report.withStatus(status, handler.getName(), System.currentTimeMillis());
        reports.put(id, updated);
        save();
        plugin.getLanguageManager().send(handler, messageKey, placeholders(updated));
        return true;
    }

    private boolean canManage(final Player player) {
        if (player.hasPermission("craftplayplotextras.reports.manage")) {
            return true;
        }
        plugin.getLanguageManager().send(player, "no-permission");
        return false;
    }

    private void load() {
        reports.clear();
        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        if (configuration.getInt("file-version", 0) < 1) {
            configuration.set("file-version", 1);
            saveConfiguration(configuration);
        }
        final ConfigurationSection section = configuration.getConfigurationSection("reports");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final String path = "reports." + id + ".";
            final String uuidText = configuration.getString(path + "reporter-uuid", "");
            UUID reporterUuid = null;
            try {
                reporterUuid = uuidText == null || uuidText.isEmpty() ? null : UUID.fromString(uuidText);
            } catch (final IllegalArgumentException ignored) {
                reporterUuid = null;
            }
            reports.put(id, new PlotReport(
                    id,
                    reporterUuid,
                    configuration.getString(path + "reporter-name", ""),
                    configuration.getLong(path + "created-at", 0L),
                    configuration.getString(path + "world", ""),
                    configuration.getString(path + "plot", ""),
                    configuration.getString(path + "plots", ""),
                    configuration.getString(path + "merge", ""),
                    configuration.getString(path + "owner", ""),
                    configuration.getString(path + "category", ""),
                    configuration.getString(path + "message", ""),
                    configuration.getString(path + "priority", "normal"),
                    configuration.getString(path + "status", "open"),
                    configuration.getString(path + "note", ""),
                    configuration.getString(path + "handled-by", ""),
                    configuration.getLong(path + "handled-at", 0L)
            ));
        }
    }

    private void save() {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().header("Report-Datendatei von CraftplayPlotExtras.\n"
                + "Reports werden über das GUI erstellt und von Teammitgliedern verwaltet.\n"
                + "Diese Datei wird automatisch gepflegt.");
        configuration.set("file-version", 1);
        for (final PlotReport report : reports.values()) {
            final String path = "reports." + report.getId() + ".";
            configuration.set(path + "reporter-uuid", report.getReporterUuid() == null ? "" : report.getReporterUuid().toString());
            configuration.set(path + "reporter-name", report.getReporterName());
            configuration.set(path + "created-at", report.getCreatedAt());
            configuration.set(path + "world", report.getWorld());
            configuration.set(path + "plot", report.getPlot());
            configuration.set(path + "plots", report.getPlots());
            configuration.set(path + "merge", report.getMerge());
            configuration.set(path + "owner", report.getOwner());
            configuration.set(path + "category", report.getCategory());
            configuration.set(path + "message", report.getMessage());
            configuration.set(path + "priority", report.getPriority());
            configuration.set(path + "status", report.getStatus());
            configuration.set(path + "note", report.getNote());
            configuration.set(path + "handled-by", report.getHandledBy());
            configuration.set(path + "handled-at", report.getHandledAt());
        }
        saveConfiguration(configuration);
    }

    private void saveConfiguration(final YamlConfiguration configuration) {
        if (asyncSaves) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    configuration.save(file);
                } catch (final IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "Reports konnten nicht gespeichert werden: " + file.getPath(), exception);
                }
            });
            return;
        }
        try {
            configuration.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Reports konnten nicht gespeichert werden: " + file.getPath(), exception);
        }
    }

    private void createDefaultFile() {
        final File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Report-Ordner konnte nicht erstellt werden: " + parent.getPath());
        }
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().header("Report-Datendatei von CraftplayPlotExtras.\n"
                + "Reports werden über das GUI erstellt und von Teammitgliedern verwaltet.\n"
                + "Diese Datei wird automatisch gepflegt.");
        configuration.set("file-version", 1);
        try {
            configuration.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Reports konnten nicht gespeichert werden: " + file.getPath(), exception);
        }
    }

    private void notifyTeam(final PlotReport report) {
        final Map<String, String> placeholders = placeholders(report);
        for (final Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("craftplayplotextras.reports.manage")) {
                plugin.getLanguageManager().send(online, "report-team-notify", placeholders);
            }
        }
    }

    private String formatDate(final long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
}
