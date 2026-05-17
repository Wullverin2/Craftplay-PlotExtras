package de.craftplay.plotextras.audit;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class AuditLogService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private final Map<String, AuditLogEntry> entries = new LinkedHashMap<>();
    private YamlConfiguration data;
    private int maxEntries;

    public AuditLogService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/audit-log.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner für den Auditlog konnte nicht erstellt werden.");
        }
        maxEntries = Math.max(100, plugin.getConfig().getInt("audit-log.max-entries", 5000));
        data = YamlConfiguration.loadConfiguration(dataFile);
        reloadEntries();
    }

    public boolean canView(final CommandSender sender) {
        return featureToggleService.isEnabled("team.audit-log")
                && (sender.hasPermission("craftplayplotextras.audit.view") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public void log(final CommandSender actor, final Plot plot, final String action, final String details) {
        final String actorName = actor == null ? "System" : actor.getName();
        log(actorName, plot, action, details);
    }

    public void log(final Player actor, final Plot plot, final String action, final String details) {
        log(actor == null ? "System" : actor.getName(), plot, action, details);
    }

    public void log(final String actor, final Plot plot, final String action, final String details) {
        if (!plugin.getConfig().getBoolean("audit-log.enabled", true) || !featureToggleService.isEnabled("team.audit-log")) {
            return;
        }
        final Instant now = Instant.now();
        final Plot basePlot = plot == null ? null : plot.getBasePlot(false);
        final String id = ("audit-" + Long.toString(System.currentTimeMillis(), 36) + "-" + Integer.toHexString(entries.size())).toLowerCase(Locale.ROOT);
        final AuditLogEntry entry = new AuditLogEntry(
                id,
                now,
                blankToDefault(actor, "System"),
                blankToDefault(action, "unbekannt"),
                blankToDefault(details, "-"),
                basePlot == null ? "-" : basePlot.getWorldName(),
                basePlot == null ? "-" : basePlot.getId().toString(),
                basePlot == null ? "-" : plotKey(basePlot)
        );
        entries.put(entry.id(), entry);
        saveEntry(entry);
        prune();
    }

    public List<AuditLogEntry> listRecent(final int limit) {
        return entries.values().stream()
                .sorted(Comparator.comparing(AuditLogEntry::createdAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public List<AuditLogEntry> listForPlot(final Plot plot, final int limit) {
        if (plot == null) {
            return List.of();
        }
        final String key = plotKey(plot);
        return entries.values().stream()
                .filter(entry -> entry.plotKey().equalsIgnoreCase(key))
                .sorted(Comparator.comparing(AuditLogEntry::createdAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public Optional<AuditLogEntry> get(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(id.toLowerCase(Locale.ROOT)));
    }

    private void reloadEntries() {
        entries.clear();
        final ConfigurationSection section = data.getConfigurationSection("entries");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                final AuditLogEntry entry = new AuditLogEntry(
                        id.toLowerCase(Locale.ROOT),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        entrySection.getString("actor", "System"),
                        entrySection.getString("action", "unbekannt"),
                        entrySection.getString("details", "-"),
                        entrySection.getString("world", "-"),
                        entrySection.getString("plot-id", "-"),
                        entrySection.getString("plot-key", "-")
                );
                entries.put(entry.id(), entry);
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungültiger Auditlog-Eintrag ignoriert: " + id);
            }
        }
    }

    private void saveEntry(final AuditLogEntry entry) {
        final String path = "entries." + entry.id();
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".actor", entry.actor());
        data.set(path + ".action", entry.action());
        data.set(path + ".details", entry.details());
        data.set(path + ".world", entry.world());
        data.set(path + ".plot-id", entry.plotId());
        data.set(path + ".plot-key", entry.plotKey());
        save();
    }

    private void prune() {
        if (entries.size() <= maxEntries) {
            return;
        }
        final List<AuditLogEntry> oldestFirst = new ArrayList<>(entries.values());
        oldestFirst.sort(Comparator.comparing(AuditLogEntry::createdAt));
        while (entries.size() > maxEntries && !oldestFirst.isEmpty()) {
            final AuditLogEntry removed = oldestFirst.remove(0);
            entries.remove(removed.id());
            data.set("entries." + removed.id(), null);
        }
        save();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Der Auditlog konnte nicht gespeichert werden.", exception);
        }
    }

    private String plotKey(final Plot plot) {
        final Plot basePlot = plot.getBasePlot(false);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private String blankToDefault(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
