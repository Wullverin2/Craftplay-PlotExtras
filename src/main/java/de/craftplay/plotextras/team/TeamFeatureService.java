package de.craftplay.plotextras.team;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.backup.PlotBackupMetadata;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TeamFeatureService {

    private final CraftplayPlotExtrasPlugin plugin;
    private YamlConfiguration configuration;
    private String dataFile;
    private boolean saveQueued;

    public TeamFeatureService(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        dataFile = plugin.getConfig().getString("team-features.data-file", "teamdata.yml");
        configuration = plugin.getStorageService().load("teamdata", dataFile);
        if (configuration.getInt("file-version", 0) < 1) {
            configuration.set("file-version", 1);
            saveNow();
        }
    }

    public void runCommand(final Player player, final String payload) {
        if (!plugin.getConfig().getBoolean("team-features.enabled", true)) {
            plugin.getLanguageManager().send(player, "team-feature-disabled");
            return;
        }
        if (payload == null || payload.trim().isEmpty()) {
            plugin.getLanguageManager().send(player, "chat-input-invalid");
            return;
        }
        final String action = payload.trim().toLowerCase(Locale.ROOT);
        if ("lock".equals(action)) {
            toggleLock(player);
            return;
        }
        if ("freeze".equals(action)) {
            toggleFreeze(player);
            return;
        }
        if ("audit".equals(action)) {
            showAudit(player);
            return;
        }
        if ("lagscan".equals(action)) {
            logCurrentPlotAction(player, "lagscan");
            plugin.getPlotFutureService().runCommand(player, "redstone");
            return;
        }
        if ("heatmap".equals(action)) {
            logCurrentPlotAction(player, "heatmap");
            plugin.getPlotFutureService().runCommand(player, "heatmap");
            return;
        }
        if ("activity".equals(action)) {
            showActivity(player);
            return;
        }
        if ("analysis".equals(action)) {
            showAnalysis(player);
            return;
        }
        plugin.getLanguageManager().send(player, "chat-input-invalid");
    }

    public boolean isLockedLocation(final Location location) {
        return containsState(location, "locked");
    }

    public boolean isFrozenLocation(final Location location) {
        return containsState(location, "frozen");
    }

    public boolean canBypassLock(final Player player) {
        return player.hasPermission("craftplayplotextras.team.bypass-lock")
                || player.hasPermission("craftplayplotextras.admin");
    }

    public boolean canBypassFreeze(final Player player) {
        return player.hasPermission("craftplayplotextras.team.bypass-freeze")
                || player.hasPermission("craftplayplotextras.admin");
    }

    public void audit(final Player actor, final String action, final PlotContext context, final String note) {
        if (!plugin.getConfig().getBoolean("team-features.audit.enabled", true)) {
            return;
        }
        final String id = System.currentTimeMillis() + "-" + Math.abs(UUID.randomUUID().getLeastSignificantBits());
        final String path = "audit." + id + ".";
        configuration.set(path + "time", System.currentTimeMillis());
        configuration.set(path + "action", action == null ? "" : action);
        configuration.set(path + "actor.uuid", actor == null ? "" : actor.getUniqueId().toString());
        configuration.set(path + "actor.name", actor == null ? "Konsole" : actor.getName());
        configuration.set(path + "world", context == null ? "" : context.getWorldName());
        configuration.set(path + "plot", context == null ? "" : context.getPlotId());
        configuration.set(path + "owner", context == null ? "" : context.getOwnerName());
        configuration.set(path + "merge", context == null ? "" : context.getMergeType());
        configuration.set(path + "note", note == null ? "" : note);
        trimAudit();
        saveSoon();
    }

    private void toggleLock(final Player player) {
        if (!checkTeamPermission(player, "craftplayplotextras.team.lock")) {
            return;
        }
        if (!plugin.getConfig().getBoolean("team-features.lock.enabled", true)) {
            plugin.getLanguageManager().send(player, "team-feature-disabled");
            return;
        }
        final Optional<PlotContext> optionalContext = requirePlot(player);
        if (!optionalContext.isPresent()) {
            return;
        }
        final PlotContext context = optionalContext.get();
        final String key = plotKey(context);
        writePlotIdentity(key, context);
        final boolean enabled = !configuration.getBoolean(plotPath(key, "locked"), false);
        configuration.set(plotPath(key, "locked"), enabled);
        configuration.set(plotPath(key, "locked-by.uuid"), player.getUniqueId().toString());
        configuration.set(plotPath(key, "locked-by.name"), player.getName());
        configuration.set(plotPath(key, "locked-at"), System.currentTimeMillis());
        saveRegions(key, context);
        audit(player, enabled ? "lock" : "unlock", context, "");
        saveSoon();
        plugin.getLanguageManager().send(player, enabled ? "team-plot-locked" : "team-plot-unlocked", plotPlaceholders(context));
    }

    private void toggleFreeze(final Player player) {
        if (!checkTeamPermission(player, "craftplayplotextras.team.freeze")) {
            return;
        }
        if (!plugin.getConfig().getBoolean("team-features.freeze.enabled", true)) {
            plugin.getLanguageManager().send(player, "team-feature-disabled");
            return;
        }
        final Optional<PlotContext> optionalContext = requirePlot(player);
        if (!optionalContext.isPresent()) {
            return;
        }
        final PlotContext context = optionalContext.get();
        final String key = plotKey(context);
        writePlotIdentity(key, context);
        final boolean enabled = !configuration.getBoolean(plotPath(key, "frozen"), false);
        configuration.set(plotPath(key, "frozen"), enabled);
        configuration.set(plotPath(key, "frozen-by.uuid"), player.getUniqueId().toString());
        configuration.set(plotPath(key, "frozen-by.name"), player.getName());
        configuration.set(plotPath(key, "frozen-at"), System.currentTimeMillis());
        saveRegions(key, context);
        audit(player, enabled ? "freeze" : "unfreeze", context, "");
        saveSoon();
        plugin.getLanguageManager().send(player, enabled ? "team-plot-frozen" : "team-plot-unfrozen", plotPlaceholders(context));
    }

    private void showAudit(final Player player) {
        if (!checkTeamPermission(player, "craftplayplotextras.team.audit")) {
            return;
        }
        final List<AuditEntry> entries = auditEntries();
        plugin.getLanguageManager().send(player, "team-audit-header");
        if (entries.isEmpty()) {
            plugin.getLanguageManager().send(player, "future-list-empty");
            return;
        }
        final int limit = Math.max(1, plugin.getConfig().getInt("team-features.audit.show-limit", 10));
        int shown = 0;
        for (final AuditEntry entry : entries) {
            if (shown >= limit) {
                break;
            }
            plugin.getLanguageManager().send(player, "team-audit-line", entry.placeholders());
            shown++;
        }
    }

    private void showActivity(final Player player) {
        if (!checkTeamPermission(player, "craftplayplotextras.team.activity")) {
            return;
        }
        logCurrentPlotAction(player, "activity");
        plugin.getPlotFutureService().runCommand(player, "visitors");
        plugin.getPlotFutureService().runCommand(player, "ranking");
    }

    private void showAnalysis(final Player player) {
        if (!checkTeamPermission(player, "craftplayplotextras.team.analysis")) {
            return;
        }
        final Optional<PlotContext> optionalContext = requirePlot(player);
        if (!optionalContext.isPresent()) {
            return;
        }
        final PlotContext context = optionalContext.get();
        final String key = plotKey(context);
        writePlotIdentity(key, context);
        saveRegions(key, context);
        final int backups = countBackups(context);
        final Map<String, String> placeholders = plotPlaceholders(context);
        placeholders.put("locked", configuration.getBoolean(plotPath(key, "locked"), false) ? "ja" : "nein");
        placeholders.put("frozen", configuration.getBoolean(plotPath(key, "frozen"), false) ? "ja" : "nein");
        placeholders.put("backups", String.valueOf(backups));
        plugin.getLanguageManager().send(player, "team-analysis", placeholders);
        audit(player, "analysis", context, "");
        saveSoon();
    }

    private void logCurrentPlotAction(final Player player, final String action) {
        final Optional<PlotContext> optionalContext = plugin.getPlotSquaredFlagService().currentPlotContext(player);
        if (optionalContext.isPresent()) {
            audit(player, action, optionalContext.get(), "");
        }
    }

    private boolean containsState(final Location location, final String state) {
        if (!plugin.getConfig().getBoolean("team-features.enabled", true)
                || location == null || location.getWorld() == null || configuration == null) {
            return false;
        }
        final ConfigurationSection plots = configuration.getConfigurationSection("plots");
        if (plots == null) {
            return false;
        }
        for (final String key : plots.getKeys(false)) {
            final String base = "plots." + key + ".";
            if (!configuration.getBoolean(base + state, false)) {
                continue;
            }
            final ConfigurationSection regions = configuration.getConfigurationSection(base + "regions");
            if (regions == null) {
                continue;
            }
            for (final String regionKey : regions.getKeys(false)) {
                final String path = base + "regions." + regionKey + ".";
                final PlotRegion region = new PlotRegion(
                        configuration.getString(base + "world", ""),
                        configuration.getInt(path + "min-x"),
                        configuration.getInt(path + "min-y"),
                        configuration.getInt(path + "min-z"),
                        configuration.getInt(path + "max-x"),
                        configuration.getInt(path + "max-y"),
                        configuration.getInt(path + "max-z")
                );
                if (region.contains(location)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<PlotContext> requirePlot(final Player player) {
        final Optional<PlotContext> context = plugin.getPlotSquaredFlagService().currentPlotContext(player);
        if (!context.isPresent()) {
            plugin.getLanguageManager().send(player, "no-plot");
            return Optional.empty();
        }
        return context;
    }

    private boolean checkTeamPermission(final Player player, final String permission) {
        if (player.hasPermission(permission) || player.hasPermission("craftplayplotextras.admin")) {
            return true;
        }
        plugin.getLanguageManager().send(player, "no-permission");
        return false;
    }

    private void writePlotIdentity(final String key, final PlotContext context) {
        configuration.set(plotPath(key, "world"), context.getWorldName());
        configuration.set(plotPath(key, "plot"), context.getPlotId());
        configuration.set(plotPath(key, "owner.uuid"), context.getOwnerUuid() == null ? "" : context.getOwnerUuid().toString());
        configuration.set(plotPath(key, "owner.name"), context.getOwnerName());
        configuration.set(plotPath(key, "merge"), context.getMergeType());
        configuration.set(plotPath(key, "plots"), context.getPlotIds());
    }

    private void saveRegions(final String key, final PlotContext context) {
        configuration.set(plotPath(key, "regions"), null);
        int index = 0;
        for (final PlotRegion region : context.getRegions()) {
            final String path = plotPath(key, "regions." + index + ".");
            configuration.set(path + "min-x", region.getMinX());
            configuration.set(path + "min-y", region.getMinY());
            configuration.set(path + "min-z", region.getMinZ());
            configuration.set(path + "max-x", region.getMaxX());
            configuration.set(path + "max-y", region.getMaxY());
            configuration.set(path + "max-z", region.getMaxZ());
            index++;
        }
    }

    private int countBackups(final PlotContext context) {
        int amount = 0;
        for (final PlotBackupMetadata backup : plugin.getPlotBackupService().listBackups()) {
            if (context.getWorldName().equalsIgnoreCase(backup.getWorldName())
                    && context.getPlotId().equalsIgnoreCase(backup.getPlotId())) {
                amount++;
            }
        }
        return amount;
    }

    private List<AuditEntry> auditEntries() {
        final ConfigurationSection audit = configuration.getConfigurationSection("audit");
        if (audit == null) {
            return Collections.emptyList();
        }
        final List<AuditEntry> entries = new ArrayList<>();
        for (final String key : audit.getKeys(false)) {
            final String path = "audit." + key + ".";
            entries.add(new AuditEntry(
                    configuration.getLong(path + "time", 0L),
                    configuration.getString(path + "action", ""),
                    configuration.getString(path + "actor.name", ""),
                    configuration.getString(path + "world", ""),
                    configuration.getString(path + "plot", ""),
                    configuration.getString(path + "owner", ""),
                    configuration.getString(path + "merge", ""),
                    configuration.getString(path + "note", "")
            ));
        }
        entries.sort(Comparator.comparingLong(AuditEntry::getTime).reversed());
        return entries;
    }

    private void trimAudit() {
        final ConfigurationSection audit = configuration.getConfigurationSection("audit");
        if (audit == null) {
            return;
        }
        final int maxEntries = Math.max(10, plugin.getConfig().getInt("team-features.audit.max-entries", 500));
        final List<String> keys = new ArrayList<>(audit.getKeys(false));
        keys.sort((first, second) -> Long.compare(
                configuration.getLong("audit." + second + ".time", 0L),
                configuration.getLong("audit." + first + ".time", 0L)
        ));
        for (int index = maxEntries; index < keys.size(); index++) {
            configuration.set("audit." + keys.get(index), null);
        }
    }

    private Map<String, String> plotPlaceholders(final PlotContext context) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", context.getWorldName());
        placeholders.put("plot", context.getPlotId());
        placeholders.put("owner", context.getOwnerName());
        placeholders.put("merge", context.getMergeType());
        placeholders.put("plots", context.getPlotIds().isEmpty() ? context.getPlotId() : String.join(", ", context.getPlotIds()));
        return placeholders;
    }

    private String plotKey(final PlotContext context) {
        return context.getWorldName() + ";" + context.getPlotId();
    }

    private String plotPath(final String key, final String suffix) {
        return "plots." + safeKey(key) + "." + suffix;
    }

    private String safeKey(final String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private void saveSoon() {
        if (saveQueued) {
            return;
        }
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveQueued = false;
            saveNow();
        }, 20L);
    }

    private void saveNow() {
        if (configuration == null || dataFile == null) {
            return;
        }
        plugin.getStorageService().save("teamdata", dataFile, configuration);
    }

    private static final class AuditEntry {

        private final long time;
        private final String action;
        private final String actor;
        private final String world;
        private final String plot;
        private final String owner;
        private final String merge;
        private final String note;

        private AuditEntry(
                final long time,
                final String action,
                final String actor,
                final String world,
                final String plot,
                final String owner,
                final String merge,
                final String note
        ) {
            this.time = time;
            this.action = action;
            this.actor = actor;
            this.world = world;
            this.plot = plot;
            this.owner = owner;
            this.merge = merge;
            this.note = note;
        }

        private long getTime() {
            return time;
        }

        private Map<String, String> placeholders() {
            final Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", time <= 0L ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)));
            placeholders.put("action", action == null || action.isEmpty() ? "-" : action);
            placeholders.put("actor", actor == null || actor.isEmpty() ? "-" : actor);
            placeholders.put("world", world == null || world.isEmpty() ? "-" : world);
            placeholders.put("plot", plot == null || plot.isEmpty() ? "-" : plot);
            placeholders.put("owner", owner == null || owner.isEmpty() ? "-" : owner);
            placeholders.put("merge", merge == null || merge.isEmpty() ? "-" : merge);
            placeholders.put("note", note == null || note.isEmpty() ? "-" : note);
            return placeholders;
        }
    }
}
