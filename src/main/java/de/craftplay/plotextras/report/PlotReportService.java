package de.craftplay.plotextras.report;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotReportService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private final Map<String, PlotReportEntry> reports = new LinkedHashMap<>();
    private YamlConfiguration data;

    public PlotReportService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-reports.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner für Plot-Meldungen konnte nicht erstellt werden.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        reloadEntries();
    }

    public boolean canCreate(final Player player) {
        return featureToggleService.isEnabled("player.reports")
                && player.hasPermission("craftplayplotextras.report.create");
    }

    public boolean canView(final CommandSender sender) {
        return featureToggleService.isEnabled("team.reports")
                && (sender.hasPermission("craftplayplotextras.report.view") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public boolean canClose(final CommandSender sender) {
        return featureToggleService.isEnabled("team.reports.close")
                && (sender.hasPermission("craftplayplotextras.report.close") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public PlotReportEntry create(final Player reporter, final Plot plot, final String reason) {
        final Plot basePlot = base(plot);
        if (reporter == null || basePlot == null || !canCreate(reporter)) {
            return null;
        }
        final Instant now = Instant.now();
        final String id = ("report-" + Long.toString(System.currentTimeMillis(), 36)).toLowerCase(Locale.ROOT);
        final PlotReportEntry entry = new PlotReportEntry(
                id,
                now,
                reporter.getUniqueId(),
                reporter.getName(),
                basePlot.getWorldName(),
                basePlot.getId().toString(),
                plotKey(basePlot),
                ownerName(basePlot.getOwnerAbs()),
                blank(reason, "Keine Begründung angegeben."),
                "open",
                "-",
                null,
                "-"
        );
        reports.put(entry.id(), entry);
        saveEntry(entry);
        return entry;
    }

    public List<PlotReportEntry> listOpen() {
        return reports.values().stream()
                .filter(entry -> entry.status().equalsIgnoreCase("open"))
                .sorted(Comparator.comparing(PlotReportEntry::createdAt).reversed())
                .toList();
    }

    public List<PlotReportEntry> listAll() {
        return reports.values().stream()
                .sorted(Comparator.comparing(PlotReportEntry::createdAt).reversed())
                .toList();
    }

    public Optional<PlotReportEntry> get(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(reports.get(id.toLowerCase(Locale.ROOT)));
    }

    public boolean close(final CommandSender sender, final String id, final String note) {
        if (!canClose(sender)) {
            return false;
        }
        final Optional<PlotReportEntry> existing = get(id);
        if (existing.isEmpty()) {
            return false;
        }
        final PlotReportEntry old = existing.get();
        final PlotReportEntry entry = new PlotReportEntry(
                old.id(),
                old.createdAt(),
                old.reporterUuid(),
                old.reporterName(),
                old.world(),
                old.plotId(),
                old.plotKey(),
                old.ownerName(),
                old.reason(),
                "closed",
                sender.getName(),
                Instant.now(),
                blank(note, "Erledigt.")
        );
        reports.put(entry.id(), entry);
        saveEntry(entry);
        return true;
    }

    private void reloadEntries() {
        reports.clear();
        final ConfigurationSection section = data.getConfigurationSection("reports");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                final String handledAtText = entrySection.getString("handled-at", "");
                final PlotReportEntry entry = new PlotReportEntry(
                        id.toLowerCase(Locale.ROOT),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        UUID.fromString(entrySection.getString("reporter-uuid", new UUID(0L, 0L).toString())),
                        entrySection.getString("reporter-name", "-"),
                        entrySection.getString("world", "-"),
                        entrySection.getString("plot-id", "-"),
                        entrySection.getString("plot-key", "-"),
                        entrySection.getString("owner-name", "-"),
                        entrySection.getString("reason", "-"),
                        entrySection.getString("status", "open"),
                        entrySection.getString("handled-by", "-"),
                        handledAtText == null || handledAtText.isBlank() ? null : Instant.parse(handledAtText),
                        entrySection.getString("note", "-")
                );
                reports.put(entry.id(), entry);
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungültige Plot-Meldung ignoriert: " + id);
            }
        }
    }

    private void saveEntry(final PlotReportEntry entry) {
        final String path = "reports." + entry.id();
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".reporter-uuid", entry.reporterUuid().toString());
        data.set(path + ".reporter-name", entry.reporterName());
        data.set(path + ".world", entry.world());
        data.set(path + ".plot-id", entry.plotId());
        data.set(path + ".plot-key", entry.plotKey());
        data.set(path + ".owner-name", entry.ownerName());
        data.set(path + ".reason", entry.reason());
        data.set(path + ".status", entry.status());
        data.set(path + ".handled-by", entry.handledBy());
        data.set(path + ".handled-at", entry.handledAt() == null ? null : entry.handledAt().toString());
        data.set(path + ".note", entry.note());
        save();
    }

    private Plot base(final Plot plot) {
        return plot == null ? null : plot.getBasePlot(false);
    }

    private String plotKey(final Plot plot) {
        final Plot basePlot = base(plot);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private String ownerName(final UUID ownerUuid) {
        if (ownerUuid == null) {
            return "-";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        return offlinePlayer.getName() == null ? ownerUuid.toString() : offlinePlayer.getName();
    }

    private String blank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plot-Meldungen konnten nicht gespeichert werden.", exception);
        }
    }
}
