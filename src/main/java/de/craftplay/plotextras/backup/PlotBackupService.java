package de.craftplay.plotextras.backup;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.events.PlotUnlinkEvent;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.plot.schematic.Schematic;
import com.plotsquared.core.util.SchematicHandler;
import com.plotsquared.core.util.task.RunnableVal;
import com.sk89q.jnbt.CompoundTag;
import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.plot.PlotService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotBackupService {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final PlotService plotService;
    private final FeatureToggleService featureToggleService;
    private final File backupFolder;
    private final File dataFile;
    private final Map<String, PlotBackupEntry> backups = new LinkedHashMap<>();
    private final Set<String> runningBackups = new HashSet<>();
    private YamlConfiguration data;
    private boolean enabled;

    public PlotBackupService(final JavaPlugin plugin, final PlotService plotService, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.plotService = plotService;
        this.featureToggleService = featureToggleService;
        this.backupFolder = new File(plugin.getDataFolder(), "plot-backups");
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-backups.yml");
    }

    public void load() {
        enabled = plugin.getConfig().getBoolean("plot-backups.enabled", true)
                && featureToggleService.isEnabled("backups");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create plot backup folder.");
        }
        final File dataFolder = dataFile.getParentFile();
        if (dataFolder != null && !dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create plot backup data folder.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        reloadEntries();
    }

    @Subscribe
    public void onPlotUnlink(final PlotUnlinkEvent event) {
        if (!enabled
                || !featureToggleService.isEnabled("backups.automatic")
                || !plugin.getConfig().getBoolean("plot-backups.automatic.enabled", true)) {
            return;
        }
        final PlotUnlinkEvent.REASON reason = event.getReason();
        if (!shouldBackup(reason)) {
            return;
        }
        createAutomaticBackup(event.getPlot(), reasonKey(reason));
    }

    public List<PlotBackupEntry> listBackups(final UUID ownerUuid) {
        return backups.values().stream()
                .filter(entry -> entry.ownerUuid().equals(ownerUuid))
                .sorted(Comparator.comparing(PlotBackupEntry::createdAt).reversed())
                .toList();
    }

    public List<PlotBackupEntry> listAllBackups() {
        return backups.values().stream()
                .sorted(Comparator.comparing(PlotBackupEntry::createdAt).reversed())
                .toList();
    }

    public Optional<PlotBackupEntry> getBackup(final String backupId) {
        if (backupId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(backups.get(backupId.toLowerCase(Locale.ROOT)));
    }

    public boolean restoreBackup(final Player player, final String backupId) {
        if (!canRestore(player)) {
            return false;
        }
        final Plot targetPlot = plotService.getCurrentPlot(player);
        final Optional<PlotBackupEntry> backup = getBackup(backupId);
        if (targetPlot == null || backup.isEmpty() || !backup.get().schematicFile().exists()) {
            return false;
        }
        final SchematicHandler schematicHandler = SchematicHandler.manager;
        if (schematicHandler == null) {
            return false;
        }

        final PlotPlayer<?> plotPlayer = plotService.getPlotPlayer(player);
        if (plotPlayer == null) {
            return false;
        }

        try {
            final Schematic schematic = schematicHandler.getSchematic(backup.get().schematicFile());
            schematicHandler.paste(
                    schematic,
                    targetPlot,
                    0,
                    targetPlot.getArea().getMinBuildHeight(),
                    0,
                    true,
                    plotPlayer,
                    new RunnableVal<>() {
                        @Override
                        public void run(final Boolean success) {
                            if (Boolean.TRUE.equals(success)) {
                                player.sendMessage(de.craftplay.plotextras.util.TextUtil.component("&aBackup &e" + backupId + " &awurde wiederhergestellt."));
                            } else {
                                player.sendMessage(de.craftplay.plotextras.util.TextUtil.component("&cBackup &e" + backupId + " &ckonnte nicht wiederhergestellt werden."));
                            }
                        }
                    }
            );
            return true;
        } catch (final SchematicHandler.UnsupportedFormatException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read plot backup schematic: " + backupId, exception);
            return false;
        }
    }

    public boolean canManage(final org.bukkit.command.CommandSender sender) {
        return featureToggleService.isEnabled("team.backups")
                && (sender.hasPermission("craftplayplotextras.backup.admin") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public boolean canCreateManual(final org.bukkit.command.CommandSender sender) {
        return enabled
                && featureToggleService.isEnabled("team.backups.create")
                && featureToggleService.isEnabled("backups.manual")
                && (sender.hasPermission("craftplayplotextras.backup.create")
                || sender.hasPermission("craftplayplotextras.backup.admin")
                || sender.hasPermission("craftplayplotextras.admin"));
    }

    public boolean canRestore(final Player player) {
        return featureToggleService.isEnabled("team.backups.restore")
                && featureToggleService.isEnabled("backups.restore")
                && (player.hasPermission("craftplayplotextras.backup.restore")
                || player.hasPermission("craftplayplotextras.backup.admin")
                || player.hasPermission("craftplayplotextras.admin"));
    }

    public boolean createManualBackup(final Player actor, final Plot plot, final String reason) {
        if (actor == null || !canCreateManual(actor)) {
            return false;
        }
        return createBackup(plot, "manual-" + sanitize(blank(reason, "admin")), actor.getName());
    }

    private void createAutomaticBackup(final Plot plot, final String reason) {
        createBackup(plot, reason, "System");
    }

    private boolean createBackup(final Plot plot, final String reason, final String actorName) {
        if (plot == null || !plot.hasOwner()) {
            return false;
        }
        final SchematicHandler schematicHandler = SchematicHandler.manager;
        if (schematicHandler == null) {
            plugin.getLogger().warning("Could not create plot backup because PlotSquared schematic handler is unavailable.");
            return false;
        }

        final Plot basePlot = plot.getBasePlot(false);
        final UUID ownerUuid = basePlot.getOwnerAbs();
        if (ownerUuid == null) {
            return false;
        }

        final Set<Plot> connectedPlots = basePlot.getConnectedPlots();
        final String runningKey = basePlot.getWorldName() + ":" + basePlot.getId() + ":" + reason;
        if (!runningBackups.add(runningKey)) {
            return false;
        }

        final Instant now = Instant.now();
        final String backupId = sanitize(basePlot.getWorldName()) + "-" + basePlot.getId().toDashSeparatedString()
                + "-" + reason + "-" + FILE_TIME_FORMAT.format(now);
        final File ownerFolder = new File(backupFolder, ownerUuid.toString());
        if (!ownerFolder.exists() && !ownerFolder.mkdirs()) {
            runningBackups.remove(runningKey);
            plugin.getLogger().warning("Could not create backup folder for " + ownerUuid + ".");
            return false;
        }

        final String ownerName = ownerName(ownerUuid);
        final String mergeSize = mergeSize(connectedPlots);
        final List<String> plotIds = connectedPlots.stream()
                .map(connectedPlot -> connectedPlot.getId().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        final File schematicFile = new File(ownerFolder, backupId + ".schem");
        schematicHandler.getCompoundTag(basePlot).whenComplete((compoundTag, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (throwable != null || compoundTag == null) {
                    plugin.getLogger().log(Level.WARNING, "Could not create schematic data for plot backup " + backupId + ".", throwable);
                    return;
                }
                if (!saveSchematic(schematicHandler, compoundTag, schematicFile)) {
                    plugin.getLogger().warning("Plot backup failed because the schematic file could not be written: " + schematicFile.getAbsolutePath());
                    return;
                }
                saveEntry(new PlotBackupEntry(
                        backupId.toLowerCase(Locale.ROOT),
                        ownerUuid,
                        ownerName,
                        now,
                        reason,
                        basePlot.getWorldName(),
                        basePlot.getId().toString(),
                        mergeSize,
                        connectedPlots.size(),
                        plotIds,
                        schematicFile
                ));
                plugin.getLogger().info("Created plot schematic backup " + schematicFile.getName() + " for " + ownerName + " (" + mergeSize + ") by " + actorName + ".");
            } finally {
                runningBackups.remove(runningKey);
            }
        }));
        return true;
    }

    private void reloadEntries() {
        backups.clear();
        final ConfigurationSection section = data.getConfigurationSection("backups");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                final UUID ownerUuid = UUID.fromString(entrySection.getString("owner-uuid", ""));
                final String filePath = entrySection.getString("schematic-file", "");
                final PlotBackupEntry entry = new PlotBackupEntry(
                        id.toLowerCase(Locale.ROOT),
                        ownerUuid,
                        entrySection.getString("owner-name", ownerUuid.toString()),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        entrySection.getString("reason", "unknown"),
                        entrySection.getString("source-world", "-"),
                        entrySection.getString("source-plot", "-"),
                        entrySection.getString("merge-size", "1x1"),
                        entrySection.getInt("plot-count", 1),
                        entrySection.getStringList("source-plots"),
                        new File(filePath)
                );
                backups.put(entry.id(), entry);
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid plot backup entry ignored: " + id);
            }
        }
    }

    private void saveEntry(final PlotBackupEntry entry) {
        final String path = "backups." + entry.id();
        data.set(path + ".owner-uuid", entry.ownerUuid().toString());
        data.set(path + ".owner-name", entry.ownerName());
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".reason", entry.reason());
        data.set(path + ".source-world", entry.sourceWorld());
        data.set(path + ".source-plot", entry.sourcePlot());
        data.set(path + ".merge-size", entry.mergeSize());
        data.set(path + ".plot-count", entry.plotCount());
        data.set(path + ".source-plots", entry.sourcePlots());
        data.set(path + ".schematic-file", entry.schematicFile().getAbsolutePath());
        try {
            data.save(dataFile);
            backups.put(entry.id(), entry);
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save plot backup metadata.", exception);
        }
    }

    private boolean shouldBackup(final PlotUnlinkEvent.REASON reason) {
        return switch (reason) {
            case DELETE, EXPIRE_DELETE -> featureToggleService.isEnabled("backups.automatic.on-delete")
                    && plugin.getConfig().getBoolean("plot-backups.automatic.on-delete", true);
            case PLAYER_COMMAND -> featureToggleService.isEnabled("backups.automatic.on-unmerge")
                    && plugin.getConfig().getBoolean("plot-backups.automatic.on-unmerge", true);
            case CLEAR -> featureToggleService.isEnabled("backups.automatic.on-clear")
                    && plugin.getConfig().getBoolean("plot-backups.automatic.on-clear", true);
            case NEW_OWNER -> false;
        };
    }

    private String reasonKey(final PlotUnlinkEvent.REASON reason) {
        return switch (reason) {
            case DELETE, EXPIRE_DELETE -> "delete";
            case PLAYER_COMMAND -> "unmerge";
            case CLEAR -> "clear";
            case NEW_OWNER -> "owner-change";
        };
    }

    private String mergeSize(final Set<Plot> plots) {
        if (plots.isEmpty()) {
            return "1x1";
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (final Plot connectedPlot : plots) {
            final PlotId id = connectedPlot.getId();
            minX = Math.min(minX, id.getX());
            maxX = Math.max(maxX, id.getX());
            minY = Math.min(minY, id.getY());
            maxY = Math.max(maxY, id.getY());
        }
        return Math.max(1, maxX - minX + 1) + "x" + Math.max(1, maxY - minY + 1);
    }

    private File findSchematic(final File folder, final String backupId) {
        final File exactSchem = new File(folder, backupId + ".schem");
        if (exactSchem.exists()) {
            return exactSchem;
        }
        final File exactSchematic = new File(folder, backupId + ".schematic");
        if (exactSchematic.exists()) {
            return exactSchematic;
        }
        final File[] files = folder.listFiles((dir, name) -> name.startsWith(backupId + "."));
        return files == null || files.length == 0 ? null : files[0];
    }

    private boolean saveSchematic(final SchematicHandler schematicHandler, final CompoundTag compoundTag, final File schematicFile) {
        final File parent = schematicFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        return schematicHandler.save(compoundTag, schematicFile.getAbsolutePath()) && schematicFile.exists();
    }

    private String ownerName(final UUID ownerUuid) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        return offlinePlayer.getName() == null ? ownerUuid.toString() : offlinePlayer.getName();
    }

    private String blank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sanitize(final String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
