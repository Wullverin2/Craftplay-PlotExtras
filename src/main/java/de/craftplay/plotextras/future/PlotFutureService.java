package de.craftplay.plotextras.future;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.backup.PlotBackupMetadata;
import de.craftplay.plotextras.myplots.PlotDataStore;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotFutureService {

    private final CraftplayPlotExtrasPlugin plugin;
    private final PlotDataStore plotDataStore;
    private final Map<UUID, ActiveVisit> activeVisits = new HashMap<>();

    private YamlConfiguration configuration;
    private String dataFile;
    private boolean saveQueued;

    public PlotFutureService(final CraftplayPlotExtrasPlugin plugin, final PlotDataStore plotDataStore) {
        this.plugin = plugin;
        this.plotDataStore = plotDataStore;
    }

    public void reload() {
        dataFile = plugin.getConfig().getString("future.data-file", "futurefeatures.yml");
        configuration = plugin.getStorageService().load("futurefeatures", dataFile);
        if (configuration.getInt("file-version", 0) < 1) {
            configuration.set("file-version", 1);
            saveNow();
        }
    }

    public void shutdown() {
        finishAllVisits();
        saveNow();
    }

    public void runCommand(final Player player, final String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            plugin.getLanguageManager().send(player, "chat-input-invalid");
            return;
        }
        final String[] parts = payload.split(":", 3);
        final String action = parts[0].trim().toLowerCase(Locale.ROOT);
        final String first = parts.length >= 2 ? parts[1].trim() : "";
        final String second = parts.length >= 3 ? parts[2].trim() : "";

        if ("heatmap".equals(action)) {
            showHeatmap(player);
            return;
        }
        if ("visitors".equals(action)) {
            showVisitorStatistics(player);
            return;
        }
        if ("ranking".equals(action)) {
            showRanking(player);
            return;
        }
        if ("ai-tag".equals(action)) {
            autoTagCurrentPlot(player);
            return;
        }
        if ("gallery".equals(action)) {
            handleGallery(player, first);
            return;
        }
        if ("template".equals(action)) {
            showTemplates(player);
            return;
        }
        if ("auto-backup".equals(action)) {
            createManualBackup(player);
            return;
        }
        if ("undo".equals(action)) {
            restoreLatestBackup(player);
            return;
        }
        if ("redstone".equals(action)) {
            scanRedstone(player);
            return;
        }
        if ("market".equals(action)) {
            handleMarket(player, first, second);
            return;
        }
        if ("npc".equals(action)) {
            handleNpc(player, first);
            return;
        }
        if ("discord".equals(action)) {
            sendDiscordTest(player);
            return;
        }
        if ("web".equals(action)) {
            exportWebOverview(player);
            return;
        }

        plugin.getLanguageManager().send(player, "chat-input-invalid");
    }

    public void recordPresence(final Player player) {
        if (!isFeatureEnabled("visitor-statistics")) {
            return;
        }
        final Optional<PlotContext> context = plugin.getPlotSquaredFlagService().currentPlotContext(player);
        if (!context.isPresent()) {
            finishVisit(player.getUniqueId());
            return;
        }
        final PlotContext plot = context.get();
        final String key = plotKey(plot);
        final ActiveVisit current = activeVisits.get(player.getUniqueId());
        if (current != null && current.getPlotKey().equals(key)) {
            return;
        }
        finishVisit(player.getUniqueId());
        ensurePlotIdentity(key, plot);
        increment(path(key, "statistics.visits"), 1);
        configuration.set(path(key, "statistics.last-visit"), System.currentTimeMillis());
        activeVisits.put(player.getUniqueId(), new ActiveVisit(key, System.currentTimeMillis()));
        plotDataStore.recordVisit(key);
        saveSoon();
    }

    public void finishVisit(final UUID playerUuid) {
        final ActiveVisit visit = activeVisits.remove(playerUuid);
        if (visit == null) {
            return;
        }
        final long duration = Math.max(0L, (System.currentTimeMillis() - visit.getStartedAt()) / 1000L);
        if (duration > 0L) {
            increment(path(visit.getPlotKey(), "statistics.duration-seconds"), duration);
            saveSoon();
        }
    }

    public void finishAllVisits() {
        final List<UUID> players = new ArrayList<>(activeVisits.keySet());
        for (final UUID player : players) {
            finishVisit(player);
        }
    }

    public void recordBlockActivity(final Player player, final Block block) {
        if (!isFeatureEnabled("heatmaps") && !isFeatureEnabled("redstone-analysis")) {
            return;
        }
        final Optional<PlotContext> context = plugin.getPlotSquaredFlagService().currentPlotContext(player);
        if (!context.isPresent()) {
            return;
        }
        final PlotContext plot = context.get();
        final String key = plotKey(plot);
        ensurePlotIdentity(key, plot);
        increment(path(key, "activity.block-changes"), 1);
        if (block != null) {
            final int cellSize = Math.max(1, plugin.getConfig().getInt("future.heatmaps.cell-size", 16));
            final String cell = Math.floorDiv(block.getX(), cellSize) + ";" + Math.floorDiv(block.getZ(), cellSize);
            increment(path(key, "heatmap.cells." + safeYamlKey(cell)), 1);
            if (isRedstoneMaterial(block.getType())) {
                increment(path(key, "activity.redstone-changes"), 1);
            }
        }
        saveSoon();
    }

    private void showHeatmap(final Player player) {
        if (!checkFeature(player, "heatmaps", "craftplayplotextras.future.heatmaps")) {
            return;
        }
        final Optional<PlotContext> context = requirePlot(player);
        if (!context.isPresent()) {
            return;
        }
        final String key = plotKey(context.get());
        ensurePlotIdentity(key, context.get());
        final List<Map.Entry<String, Integer>> cells = cellEntries(key);
        final Map<String, String> header = plotPlaceholders(key);
        plugin.getLanguageManager().send(player, "future-heatmap-header", header);
        if (cells.isEmpty()) {
            plugin.getLanguageManager().send(player, "future-list-empty");
            return;
        }
        final int limit = Math.max(1, plugin.getConfig().getInt("future.heatmaps.top-cells", 8));
        int rank = 1;
        for (final Map.Entry<String, Integer> cell : cells.subList(0, Math.min(limit, cells.size()))) {
            final Map<String, String> placeholders = new HashMap<>(header);
            placeholders.put("rank", String.valueOf(rank));
            placeholders.put("cell", cell.getKey().replace('_', ';'));
            placeholders.put("changes", String.valueOf(cell.getValue()));
            plugin.getLanguageManager().send(player, "future-heatmap-line", placeholders);
            rank++;
        }
    }

    private void showVisitorStatistics(final Player player) {
        if (!checkFeature(player, "visitor-statistics", "craftplayplotextras.future.visitors")) {
            return;
        }
        final Optional<PlotContext> context = requirePlot(player);
        if (!context.isPresent()) {
            return;
        }
        final String key = plotKey(context.get());
        ensurePlotIdentity(key, context.get());
        final Map<String, String> placeholders = plotPlaceholders(key);
        placeholders.put("visits", String.valueOf(configuration.getInt(path(key, "statistics.visits"), 0)));
        placeholders.put("duration", formatDuration(configuration.getLong(path(key, "statistics.duration-seconds"), 0L)));
        placeholders.put("last_visit", formatTime(configuration.getLong(path(key, "statistics.last-visit"), 0L)));
        plugin.getLanguageManager().send(player, "future-visitors", placeholders);
    }

    private void showRanking(final Player player) {
        if (!checkFeature(player, "ranking", "craftplayplotextras.future.ranking")) {
            return;
        }
        final List<RankedPlot> ranking = rankedPlots();
        plugin.getLanguageManager().send(player, "future-ranking-header");
        if (ranking.isEmpty()) {
            plugin.getLanguageManager().send(player, "future-list-empty");
            return;
        }
        final int limit = Math.max(1, plugin.getConfig().getInt("future.ranking.limit", 10));
        int rank = 1;
        for (final RankedPlot plot : ranking.subList(0, Math.min(limit, ranking.size()))) {
            final Map<String, String> placeholders = plotPlaceholders(plot.getKey());
            placeholders.put("rank", String.valueOf(rank));
            placeholders.put("score", String.valueOf(plot.getScore()));
            placeholders.put("visits", String.valueOf(configuration.getInt(path(plot.getKey(), "statistics.visits"), 0)));
            plugin.getLanguageManager().send(player, "future-ranking-line", placeholders);
            rank++;
        }
    }

    private void autoTagCurrentPlot(final Player player) {
        if (!checkFeature(player, "ai-tagging", "craftplayplotextras.future.ai")) {
            return;
        }
        final Optional<PlotContext> context = requireManagedPlot(player);
        if (!context.isPresent()) {
            return;
        }
        final PlotContext plot = context.get();
        final Set<String> tags = detectTags(plot);
        final String key = plotKey(plot);
        ensurePlotIdentity(key, plot);
        for (final String tag : tags) {
            configuration.set(path(key, "ai-tags." + safeYamlKey(tag)), true);
        }
        plotDataStore.addTags(key, tags);
        saveSoon();
        final Map<String, String> placeholders = plotPlaceholders(key);
        placeholders.put("tags", tags.isEmpty() ? "-" : join(tags));
        plugin.getLanguageManager().send(player, "future-tags-applied", placeholders);
    }

    private void handleGallery(final Player player, final String action) {
        if (!checkFeature(player, "gallery", "craftplayplotextras.future.gallery")) {
            return;
        }
        if ("list".equalsIgnoreCase(action)) {
            listGallery(player);
            return;
        }
        final Optional<PlotContext> context = requireManagedPlot(player);
        if (!context.isPresent()) {
            return;
        }
        final String key = plotKey(context.get());
        ensurePlotIdentity(key, context.get());
        final boolean enabled = !configuration.getBoolean(path(key, "gallery.enabled"), false);
        configuration.set(path(key, "gallery.enabled"), enabled);
        configuration.set(path(key, "gallery.updated-at"), System.currentTimeMillis());
        saveSoon();
        plugin.getLanguageManager().send(player, enabled ? "future-gallery-enabled" : "future-gallery-disabled", plotPlaceholders(key));
    }

    private void listGallery(final Player player) {
        final List<String> plots = featurePlots("gallery.enabled", true);
        plugin.getLanguageManager().send(player, "future-gallery-list-header");
        if (plots.isEmpty()) {
            plugin.getLanguageManager().send(player, "future-list-empty");
            return;
        }
        int rank = 1;
        for (final String key : limit(plots, plugin.getConfig().getInt("future.gallery.list-limit", 10))) {
            final Map<String, String> placeholders = plotPlaceholders(key);
            placeholders.put("rank", String.valueOf(rank));
            plugin.getLanguageManager().send(player, "future-gallery-list-line", placeholders);
            rank++;
        }
    }

    private void showTemplates(final Player player) {
        if (!checkFeature(player, "templates", "craftplayplotextras.future.templates")) {
            return;
        }
        final List<String> templates = plugin.getConfig().getStringList("future.templates.names");
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("templates", templates.isEmpty() ? "-" : String.join(", ", templates));
        plugin.getLanguageManager().send(player, "future-template-list", placeholders);
    }

    private void createManualBackup(final Player player) {
        if (!checkFeature(player, "auto-backups", "craftplayplotextras.future.backups")) {
            return;
        }
        player.closeInventory();
        plugin.getPlotBackupService().requestManualBackup(player);
    }

    private void restoreLatestBackup(final Player player) {
        if (!checkFeature(player, "undo", "craftplayplotextras.future.undo")) {
            return;
        }
        final Optional<PlotContext> context = requirePlot(player);
        if (!context.isPresent()) {
            return;
        }
        final String currentKey = plotKey(context.get());
        PlotBackupMetadata latest = null;
        for (final PlotBackupMetadata backup : plugin.getPlotBackupService().listBackups()) {
            final String backupKey = backup.getWorldName() + ";" + backup.getPlotId();
            if (!currentKey.equalsIgnoreCase(backupKey)) {
                continue;
            }
            if (latest == null || backup.getCreatedAt().compareTo(latest.getCreatedAt()) > 0) {
                latest = backup;
            }
        }
        if (latest == null) {
            plugin.getLanguageManager().send(player, "future-undo-none");
            return;
        }
        player.closeInventory();
        plugin.getPlotBackupService().requestRestore(player, latest.getId());
    }

    private void scanRedstone(final Player player) {
        if (!checkFeature(player, "redstone-analysis", "craftplayplotextras.future.redstone")) {
            return;
        }
        final Optional<PlotContext> context = requirePlot(player);
        if (!context.isPresent()) {
            return;
        }
        final PlotContext plot = context.get();
        final RedstoneScan scan = scanRedstone(plot);
        final String key = plotKey(plot);
        ensurePlotIdentity(key, plot);
        configuration.set(path(key, "analysis.redstone-score"), scan.getScore());
        configuration.set(path(key, "analysis.redstone-components"), scan.getComponents());
        configuration.set(path(key, "analysis.redstone-scanned-at"), System.currentTimeMillis());
        saveSoon();

        final Map<String, String> placeholders = plotPlaceholders(key);
        placeholders.put("score", String.valueOf(scan.getScore()));
        placeholders.put("components", String.valueOf(scan.getComponents()));
        placeholders.put("scanned", String.valueOf(scan.getScannedBlocks()));
        placeholders.put("level", scan.isHigh() ? "hoch" : "normal");
        plugin.getLanguageManager().send(player, "future-redstone-scan", placeholders);
        if (scan.isHigh() && plugin.getConfig().getBoolean("future.discord.notify-redstone-scans", false)) {
            sendDiscord("Redstone-Analyse", "Plot " + key + " hat einen Redstone-Score von " + scan.getScore() + ".");
        }
    }

    private void handleMarket(final Player player, final String type, final String value) {
        final String normalizedType = normalizeMarketType(type);
        final String feature = "auction".equals(normalizedType) ? "auctions" : ("trade".equals(normalizedType) ? "trading" : "market");
        final String permission = "auction".equals(normalizedType)
                ? "craftplayplotextras.future.auctions"
                : ("trade".equals(normalizedType) ? "craftplayplotextras.future.trading" : "craftplayplotextras.future.market");
        if (!checkFeature(player, feature, permission)) {
            return;
        }
        if ("list".equalsIgnoreCase(type)) {
            listMarket(player, value);
            return;
        }
        if ("remove".equalsIgnoreCase(type)) {
            removeMarketListing(player);
            return;
        }
        final Optional<PlotContext> context = requireManagedPlot(player);
        if (!context.isPresent()) {
            return;
        }
        final PlotContext plot = context.get();
        final String key = plotKey(plot);
        ensurePlotIdentity(key, plot);
        configuration.set(path(key, "market.type"), normalizedType);
        configuration.set(path(key, "market.value"), value == null || value.trim().isEmpty()
                ? plugin.getConfig().getString("future.market.default-value", "VB")
                : value.trim());
        configuration.set(path(key, "market.created-at"), System.currentTimeMillis());
        saveSoon();
        final Map<String, String> placeholders = plotPlaceholders(key);
        placeholders.put("type", marketName(normalizedType));
        placeholders.put("value", configuration.getString(path(key, "market.value"), "VB"));
        plugin.getLanguageManager().send(player, "future-market-listed", placeholders);
        if (plugin.getConfig().getBoolean("future.discord.notify-market", false)) {
            sendDiscord("Plotmarkt", player.getName() + " hat " + key + " als " + marketName(normalizedType) + " eingetragen.");
        }
    }

    private void removeMarketListing(final Player player) {
        final Optional<PlotContext> context = requireManagedPlot(player);
        if (!context.isPresent()) {
            return;
        }
        final String key = plotKey(context.get());
        configuration.set(path(key, "market"), null);
        saveSoon();
        plugin.getLanguageManager().send(player, "future-market-removed", plotPlaceholders(key));
    }

    private void listMarket(final Player player, final String requestedType) {
        final String type = normalizeMarketType(requestedType);
        final List<String> plots = marketPlots(type);
        final Map<String, String> header = new HashMap<>();
        header.put("type", marketName(type));
        plugin.getLanguageManager().send(player, "future-market-list-header", header);
        if (plots.isEmpty()) {
            plugin.getLanguageManager().send(player, "future-list-empty");
            return;
        }
        int rank = 1;
        for (final String key : limit(plots, plugin.getConfig().getInt("future.market.list-limit", 10))) {
            final Map<String, String> placeholders = plotPlaceholders(key);
            placeholders.put("rank", String.valueOf(rank));
            placeholders.put("type", marketName(type));
            placeholders.put("value", configuration.getString(path(key, "market.value"), "VB"));
            plugin.getLanguageManager().send(player, "future-market-list-line", placeholders);
            rank++;
        }
    }

    private void handleNpc(final Player player, final String action) {
        if (!checkFeature(player, "npcs", "craftplayplotextras.future.npcs")) {
            return;
        }
        final Optional<PlotContext> context = requireManagedPlot(player);
        if (!context.isPresent()) {
            return;
        }
        final String key = plotKey(context.get());
        ensurePlotIdentity(key, context.get());
        final boolean enabled = !"off".equalsIgnoreCase(action) && !configuration.getBoolean(path(key, "npc.enabled"), false);
        configuration.set(path(key, "npc.enabled"), enabled);
        configuration.set(path(key, "npc.updated-at"), System.currentTimeMillis());
        saveSoon();
        if (enabled) {
            runConfiguredPlayerCommand(player, plugin.getConfig().getString("future.npcs.spawn-command", ""));
        } else {
            runConfiguredPlayerCommand(player, plugin.getConfig().getString("future.npcs.remove-command", ""));
        }
        plugin.getLanguageManager().send(player, enabled ? "future-npc-enabled" : "future-npc-disabled", plotPlaceholders(key));
    }

    private void sendDiscordTest(final Player player) {
        if (!checkFeature(player, "discord", "craftplayplotextras.future.discord")) {
            return;
        }
        if (!plugin.getConfig().getBoolean("future.discord.enabled", false)) {
            plugin.getLanguageManager().send(player, "future-discord-disabled");
            return;
        }
        sendDiscord("CraftplayPlotExtras", "Discord-Integration wurde von " + player.getName() + " getestet.");
        plugin.getLanguageManager().send(player, "future-discord-sent");
    }

    private void exportWebOverview(final Player player) {
        if (!checkFeature(player, "web-overview", "craftplayplotextras.future.web")) {
            return;
        }
        final File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("future.web-overview.folder", "web"));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Webübersicht-Ordner konnte nicht erstellt werden: " + folder.getPath());
            return;
        }
        final File export = new File(folder, "plots.json");
        try {
            java.nio.file.Files.write(export.toPath(), webJson().getBytes(StandardCharsets.UTF_8));
            final Map<String, String> placeholders = new HashMap<>();
            placeholders.put("file", export.getPath());
            plugin.getLanguageManager().send(player, "future-web-exported", placeholders);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Webübersicht konnte nicht exportiert werden.", exception);
        }
    }

    private Optional<PlotContext> requirePlot(final Player player) {
        final Optional<PlotContext> context = plugin.getPlotSquaredFlagService().currentPlotContext(player);
        if (!context.isPresent()) {
            plugin.getLanguageManager().send(player, "no-plot");
            return Optional.empty();
        }
        return context;
    }

    private Optional<PlotContext> requireManagedPlot(final Player player) {
        final Optional<PlotContext> context = requirePlot(player);
        if (!context.isPresent()) {
            return Optional.empty();
        }
        final UUID owner = context.get().getOwnerUuid();
        if (owner != null && owner.equals(player.getUniqueId())) {
            return context;
        }
        if (player.hasPermission("craftplayplotextras.admin")
                || player.hasPermission("craftplayplotextras.future.manage")
                || plugin.getPlotRoleService().hasRolePermission(player, "future")) {
            return context;
        }
        plugin.getLanguageManager().send(player, "no-permission");
        return Optional.empty();
    }

    private boolean checkFeature(final Player player, final String feature, final String permission) {
        if (!isFeatureEnabled(feature)) {
            plugin.getLanguageManager().send(player, "future-disabled");
            return false;
        }
        if (permission != null && !permission.trim().isEmpty()
                && !player.hasPermission(permission)
                && !hasTeamFeaturePermission(player, feature)
                && !player.hasPermission("craftplayplotextras.admin")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return false;
        }
        return true;
    }

    private boolean hasTeamFeaturePermission(final Player player, final String feature) {
        if ("visitor-statistics".equals(feature) || "ranking".equals(feature)) {
            return player.hasPermission("craftplayplotextras.team.activity");
        }
        if ("redstone-analysis".equals(feature)) {
            return player.hasPermission("craftplayplotextras.team.lagscan");
        }
        if ("heatmaps".equals(feature)) {
            return player.hasPermission("craftplayplotextras.future.heatmaps");
        }
        return false;
    }

    private boolean isFeatureEnabled(final String feature) {
        return plugin.getConfig().getBoolean("future.enabled", true)
                && plugin.getConfig().getBoolean("features." + feature, true)
                && plugin.getConfig().getBoolean("future." + feature + ".enabled", true);
    }

    private void ensurePlotIdentity(final String key, final PlotContext context) {
        configuration.set(path(key, "world"), context.getWorldName());
        configuration.set(path(key, "plot"), context.getPlotId());
        configuration.set(path(key, "owner.uuid"), context.getOwnerUuid() == null ? "" : context.getOwnerUuid().toString());
        configuration.set(path(key, "owner.name"), context.getOwnerName());
        configuration.set(path(key, "merge"), context.getMergeType());
        configuration.set(path(key, "plots"), context.getPlotIds());
    }

    private List<Map.Entry<String, Integer>> cellEntries(final String key) {
        final ConfigurationSection section = configuration.getConfigurationSection(path(key, "heatmap.cells"));
        if (section == null) {
            return Collections.emptyList();
        }
        final List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (final String cell : section.getKeys(false)) {
            entries.add(new java.util.AbstractMap.SimpleEntry<>(cell, section.getInt(cell)));
        }
        entries.sort((first, second) -> Integer.compare(second.getValue(), first.getValue()));
        return entries;
    }

    private List<RankedPlot> rankedPlots() {
        final ConfigurationSection plots = configuration.getConfigurationSection("plots");
        if (plots == null) {
            return Collections.emptyList();
        }
        final List<RankedPlot> ranking = new ArrayList<>();
        for (final String safeKey : plots.getKeys(false)) {
            final String key = decodeKey(safeKey);
            final int visits = configuration.getInt(path(key, "statistics.visits"), 0);
            final long duration = configuration.getLong(path(key, "statistics.duration-seconds"), 0L);
            final int redstone = configuration.getInt(path(key, "analysis.redstone-score"), 0);
            final boolean gallery = configuration.getBoolean(path(key, "gallery.enabled"), false);
            final boolean market = configuration.contains(path(key, "market.type"));
            final int score = visits * plugin.getConfig().getInt("future.ranking.weights.visit", 5)
                    + (int) Math.min(500L, duration / 60L) * plugin.getConfig().getInt("future.ranking.weights.visit-minute", 1)
                    + (gallery ? plugin.getConfig().getInt("future.ranking.weights.gallery", 20) : 0)
                    + (market ? plugin.getConfig().getInt("future.ranking.weights.market", 10) : 0)
                    + Math.min(250, redstone);
            if (score > 0) {
                ranking.add(new RankedPlot(key, score));
            }
        }
        ranking.sort((first, second) -> Integer.compare(second.getScore(), first.getScore()));
        return ranking;
    }

    private Set<String> detectTags(final PlotContext context) {
        final Set<String> tags = new LinkedHashSet<>();
        final RedstoneScan redstone = scanRedstone(context);
        if (redstone.getComponents() >= plugin.getConfig().getInt("future.ai-tagging.redstone-threshold", 12)) {
            tags.add("Redstone");
        }
        final MaterialStats stats = scanMaterials(context);
        if (stats.getCropBlocks() >= plugin.getConfig().getInt("future.ai-tagging.farm-threshold", 20)) {
            tags.add("Farm");
        }
        if (stats.getShopLikeBlocks() >= plugin.getConfig().getInt("future.ai-tagging.shop-threshold", 8)) {
            tags.add("Shop");
        }
        if (stats.getColorBlocks() >= plugin.getConfig().getInt("future.ai-tagging.pixelart-threshold", 80)) {
            tags.add("Pixelart");
        }
        if (stats.getDecorativeBlocks() >= plugin.getConfig().getInt("future.ai-tagging.build-threshold", 120)) {
            tags.add("Bauprojekt");
        }
        return tags;
    }

    private RedstoneScan scanRedstone(final PlotContext context) {
        int score = 0;
        int components = 0;
        int scanned = 0;
        final int maxBlocks = Math.max(1000, plugin.getConfig().getInt("future.redstone-analysis.max-scanned-blocks", 250000));
        for (final PlotRegion region : context.getRegions()) {
            final World world = context.getWorld();
            if (world == null) {
                continue;
            }
            for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                        if (scanned >= maxBlocks) {
                            return new RedstoneScan(score, components, scanned, isHighRedstone(score, components));
                        }
                        final Material material = world.getBlockAt(x, y, z).getType();
                        scanned++;
                        if (isRedstoneMaterial(material)) {
                            components++;
                            score += redstoneWeight(material);
                        }
                    }
                }
            }
        }
        return new RedstoneScan(score, components, scanned, isHighRedstone(score, components));
    }

    private MaterialStats scanMaterials(final PlotContext context) {
        final MaterialStats stats = new MaterialStats();
        int scanned = 0;
        final int maxBlocks = Math.max(1000, plugin.getConfig().getInt("future.ai-tagging.max-scanned-blocks", 200000));
        for (final PlotRegion region : context.getRegions()) {
            final World world = context.getWorld();
            if (world == null) {
                continue;
            }
            for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                        if (scanned >= maxBlocks) {
                            return stats;
                        }
                        stats.add(world.getBlockAt(x, y, z).getType());
                        scanned++;
                    }
                }
            }
        }
        return stats;
    }

    private boolean isHighRedstone(final int score, final int components) {
        return score >= plugin.getConfig().getInt("future.redstone-analysis.high-score", 350)
                || components >= plugin.getConfig().getInt("future.redstone-analysis.high-components", 96);
    }

    private boolean isRedstoneMaterial(final Material material) {
        if (material == null) {
            return false;
        }
        final String name = material.name();
        return name.contains("REDSTONE")
                || name.contains("REPEATER")
                || name.contains("COMPARATOR")
                || name.contains("PISTON")
                || name.contains("OBSERVER")
                || name.contains("HOPPER")
                || name.contains("DROPPER")
                || name.contains("DISPENSER")
                || name.contains("TNT")
                || name.contains("TRIPWIRE")
                || name.contains("DAYLIGHT_DETECTOR");
    }

    private int redstoneWeight(final Material material) {
        final String name = material.name();
        if (name.contains("HOPPER")) {
            return 8;
        }
        if (name.contains("PISTON") || name.contains("OBSERVER") || name.contains("TNT")) {
            return 6;
        }
        if (name.contains("DROPPER") || name.contains("DISPENSER")) {
            return 5;
        }
        if (name.contains("REPEATER") || name.contains("COMPARATOR")) {
            return 4;
        }
        return 2;
    }

    private List<String> featurePlots(final String relativePath, final boolean expected) {
        final ConfigurationSection plots = configuration.getConfigurationSection("plots");
        if (plots == null) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        for (final String safeKey : plots.getKeys(false)) {
            final String key = decodeKey(safeKey);
            if (configuration.getBoolean(path(key, relativePath), false) == expected) {
                result.add(key);
            }
        }
        result.sort(Comparator.comparing(key -> configuration.getLong(path(key, "gallery.updated-at"), 0L), Comparator.reverseOrder()));
        return result;
    }

    private List<String> marketPlots(final String type) {
        final ConfigurationSection plots = configuration.getConfigurationSection("plots");
        if (plots == null) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        for (final String safeKey : plots.getKeys(false)) {
            final String key = decodeKey(safeKey);
            if (type.equalsIgnoreCase(configuration.getString(path(key, "market.type"), ""))) {
                result.add(key);
            }
        }
        result.sort(Comparator.comparing(key -> configuration.getLong(path(key, "market.created-at"), 0L), Comparator.reverseOrder()));
        return result;
    }

    private void sendDiscord(final String title, final String message) {
        if (!plugin.getConfig().getBoolean("future.discord.enabled", false)) {
            return;
        }
        final String webhook = plugin.getConfig().getString("future.discord.webhook-url", "");
        if (webhook == null || webhook.trim().isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final URL url = new URL(webhook.trim());
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);
                final String username = plugin.getConfig().getString("future.discord.username", "CraftplayPlotExtras");
                final String json = "{\"username\":\"" + json(username) + "\",\"content\":\"**" + json(title) + "**\\n" + json(message) + "\"}";
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                }
                final int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    if (connection.getErrorStream() == null) {
                        plugin.getLogger().warning("Discord-Webhook antwortete mit " + responseCode + ".");
                    } else {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                            plugin.getLogger().warning("Discord-Webhook antwortete mit " + responseCode + ": " + reader.readLine());
                        }
                    }
                }
            } catch (final Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Discord-Webhook konnte nicht gesendet werden.", exception);
            }
        });
    }

    private String webJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"generatedAt\": ").append(System.currentTimeMillis()).append(",\n  \"plots\": [\n");
        final ConfigurationSection plots = configuration.getConfigurationSection("plots");
        if (plots != null) {
            final List<String> keys = new ArrayList<>(plots.getKeys(false));
            for (int index = 0; index < keys.size(); index++) {
                final String key = decodeKey(keys.get(index));
                builder.append("    {");
                builder.append("\"key\":\"").append(json(key)).append("\",");
                builder.append("\"world\":\"").append(json(configuration.getString(path(key, "world"), ""))).append("\",");
                builder.append("\"plot\":\"").append(json(configuration.getString(path(key, "plot"), ""))).append("\",");
                builder.append("\"owner\":\"").append(json(configuration.getString(path(key, "owner.name"), ""))).append("\",");
                builder.append("\"merge\":\"").append(json(configuration.getString(path(key, "merge"), ""))).append("\",");
                builder.append("\"visits\":").append(configuration.getInt(path(key, "statistics.visits"), 0)).append(",");
                builder.append("\"durationSeconds\":").append(configuration.getLong(path(key, "statistics.duration-seconds"), 0L)).append(",");
                builder.append("\"gallery\":").append(configuration.getBoolean(path(key, "gallery.enabled"), false)).append(",");
                builder.append("\"marketType\":\"").append(json(configuration.getString(path(key, "market.type"), ""))).append("\",");
                builder.append("\"marketValue\":\"").append(json(configuration.getString(path(key, "market.value"), ""))).append("\",");
                builder.append("\"redstoneScore\":").append(configuration.getInt(path(key, "analysis.redstone-score"), 0));
                builder.append("}");
                if (index < keys.size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
        }
        builder.append("  ]\n}\n");
        return builder.toString();
    }

    private Map<String, String> plotPlaceholders(final String key) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("key", key);
        placeholders.put("world", configuration.getString(path(key, "world"), key.contains(";") ? key.substring(0, key.indexOf(';')) : "-"));
        placeholders.put("plot", configuration.getString(path(key, "plot"), key.contains(";") ? key.substring(key.indexOf(';') + 1) : key));
        placeholders.put("owner", configuration.getString(path(key, "owner.name"), "-"));
        placeholders.put("merge", configuration.getString(path(key, "merge"), "-"));
        return placeholders;
    }

    private String plotKey(final PlotContext context) {
        return context.getWorldName() + ";" + context.getPlotId();
    }

    private String path(final String key, final String suffix) {
        return "plots." + safeKey(key) + "." + suffix;
    }

    private String safeKey(final String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeKey(final String safeKey) {
        try {
            return new String(Base64.getUrlDecoder().decode(safeKey), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException exception) {
            return safeKey;
        }
    }

    private String safeYamlKey(final String value) {
        return value == null ? "" : value.replace('.', '_').replace(';', '_').replace(':', '_');
    }

    private void increment(final String path, final long amount) {
        configuration.set(path, configuration.getLong(path, 0L) + amount);
    }

    private String normalizeMarketType(final String type) {
        if ("trade".equalsIgnoreCase(type) || "handel".equalsIgnoreCase(type)) {
            return "trade";
        }
        if ("auction".equalsIgnoreCase(type) || "auktion".equalsIgnoreCase(type) || "auctions".equalsIgnoreCase(type)) {
            return "auction";
        }
        return "sale";
    }

    private String marketName(final String type) {
        if ("trade".equals(type)) {
            return "Handel";
        }
        if ("auction".equals(type)) {
            return "Auktion";
        }
        return "Verkauf";
    }

    private String formatDuration(final long seconds) {
        final long hours = seconds / 3600L;
        final long minutes = (seconds % 3600L) / 60L;
        final long rest = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + rest + "s";
        }
        return rest + "s";
    }

    private String formatTime(final long timestamp) {
        if (timestamp <= 0L) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(new Date(timestamp));
    }

    private List<String> limit(final List<String> values, final int limit) {
        if (values.size() <= limit) {
            return values;
        }
        return values.subList(0, Math.max(1, limit));
    }

    private String join(final Iterable<String> values) {
        final List<String> list = new ArrayList<>();
        for (final String value : values) {
            list.add(value);
        }
        return String.join(", ", list);
    }

    private String json(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private void runConfiguredPlayerCommand(final Player player, final String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        String prepared = command.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString()).trim();
        if (prepared.startsWith("/")) {
            prepared = prepared.substring(1);
        }
        player.performCommand(prepared);
    }

    private void saveSoon() {
        if (saveQueued) {
            return;
        }
        saveQueued = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveQueued = false;
            saveNow();
        }, 40L);
    }

    private void saveNow() {
        if (configuration == null || dataFile == null) {
            return;
        }
        plugin.getStorageService().save("futurefeatures", dataFile, configuration);
    }

    private static final class ActiveVisit {

        private final String plotKey;
        private final long startedAt;

        private ActiveVisit(final String plotKey, final long startedAt) {
            this.plotKey = plotKey;
            this.startedAt = startedAt;
        }

        private String getPlotKey() {
            return plotKey;
        }

        private long getStartedAt() {
            return startedAt;
        }
    }

    private static final class RankedPlot {

        private final String key;
        private final int score;

        private RankedPlot(final String key, final int score) {
            this.key = key;
            this.score = score;
        }

        private String getKey() {
            return key;
        }

        private int getScore() {
            return score;
        }
    }

    private static final class RedstoneScan {

        private final int score;
        private final int components;
        private final int scannedBlocks;
        private final boolean high;

        private RedstoneScan(final int score, final int components, final int scannedBlocks, final boolean high) {
            this.score = score;
            this.components = components;
            this.scannedBlocks = scannedBlocks;
            this.high = high;
        }

        private int getScore() {
            return score;
        }

        private int getComponents() {
            return components;
        }

        private int getScannedBlocks() {
            return scannedBlocks;
        }

        private boolean isHigh() {
            return high;
        }
    }

    private static final class MaterialStats {

        private int cropBlocks;
        private int shopLikeBlocks;
        private int colorBlocks;
        private int decorativeBlocks;

        private void add(final Material material) {
            if (material == null || material == Material.AIR) {
                return;
            }
            final String name = material.name();
            if (name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO")
                    || name.contains("BEETROOT") || name.contains("MELON") || name.contains("PUMPKIN")
                    || name.contains("SUGAR_CANE") || name.contains("CACTUS") || name.contains("COCOA")) {
                cropBlocks++;
            }
            if (name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER_BOX")
                    || name.contains("SIGN") || name.contains("LECTERN") || name.contains("EMERALD")) {
                shopLikeBlocks++;
            }
            if (name.contains("WOOL") || name.contains("TERRACOTTA") || name.contains("CONCRETE")
                    || name.contains("STAINED_GLASS")) {
                colorBlocks++;
            }
            if (material.isSolid()) {
                decorativeBlocks++;
            }
        }

        private int getCropBlocks() {
            return cropBlocks;
        }

        private int getShopLikeBlocks() {
            return shopLikeBlocks;
        }

        private int getColorBlocks() {
            return colorBlocks;
        }

        private int getDecorativeBlocks() {
            return decorativeBlocks;
        }
    }
}
