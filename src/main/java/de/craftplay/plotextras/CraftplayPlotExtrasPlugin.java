package de.craftplay.plotextras;

import com.google.common.eventbus.Subscribe;
import com.plotsquared.core.PlotAPI;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.events.PlayerAutoPlotEvent;
import com.plotsquared.core.events.PlayerClaimPlotEvent;
import com.plotsquared.core.events.PlayerEnterPlotEvent;
import com.plotsquared.core.events.PlayerLeavePlotEvent;
import com.plotsquared.core.events.PlayerPlotLimitEvent;
import com.plotsquared.core.events.Result;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.audit.AuditLogService;
import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.command.PlotExtrasCommand;
import de.craftplay.plotextras.competition.CompetitionService;
import de.craftplay.plotextras.debug.DebugLogService;
import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.furniture.FurnitureProtectionManager;
import de.craftplay.plotextras.gui.GuiManager;
import de.craftplay.plotextras.integration.BedrockService;
import de.craftplay.plotextras.integration.HeadDatabaseService;
import de.craftplay.plotextras.integration.PlaceholderService;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.limit.EntityLimitService;
import de.craftplay.plotextras.moderation.PlotModerationService;
import de.craftplay.plotextras.performance.PlotPerformanceService;
import de.craftplay.plotextras.player.PlayerDataManager;
import de.craftplay.plotextras.plot.PlotRoleService;
import de.craftplay.plotextras.plot.PlotService;
import de.craftplay.plotextras.plotmeta.PlotMetaService;
import de.craftplay.plotextras.redstone.RedstoneLagProtectionService;
import de.craftplay.plotextras.report.PlotReportService;
import de.craftplay.plotextras.resource.ResourceInstaller;
import de.craftplay.plotextras.safety.CooldownService;
import de.craftplay.plotextras.utility.PlotUtilityService;
import de.craftplay.plotextras.validation.ConfigValidationService;
import de.craftplay.plotextras.warp.PlotWarpService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, FlightState> rememberedFlightStates = new HashMap<>();
    private final Map<UUID, Location> lastAllowedLocations = new HashMap<>();
    private final PlotAPI plotApi = new PlotAPI();

    private PlayerDataManager playerDataManager;
    private LanguageManager languageManager;
    private PlaceholderService placeholderService;
    private HeadDatabaseService headDatabaseService;
    private BedrockService bedrockService;
    private CooldownService cooldownService;
    private FurnitureProtectionManager furnitureProtectionManager;
    private EntityLimitService entityLimitService;
    private AuditLogService auditLogService;
    private PlotBackupService plotBackupService;
    private RedstoneLagProtectionService redstoneLagProtectionService;
    private PlotReportService plotReportService;
    private PlotModerationService plotModerationService;
    private PlotPerformanceService plotPerformanceService;
    private CompetitionService competitionService;
    private ConfigValidationService configValidationService;
    private PlotRoleService plotRoleService;
    private PlotMetaService plotMetaService;
    private PlotWarpService plotWarpService;
    private PlotUtilityService plotUtilityService;
    private FeatureToggleService featureToggleService;
    private PlotService plotService;
    private GuiManager guiManager;
    private DebugLogService debugLogService;

    private boolean cmiAvailable;
    private boolean restoreFlightInsidePlotWorlds;
    private boolean disableFlightOutsidePlotWorlds;
    private boolean useCmiCommand;
    private boolean ignoreCreativeAndSpectator;
    private boolean preventNamedEntityDespawn;
    private boolean preventDragonEggTeleportInPlotWorlds;
    private boolean debug;
    private List<Long> restoreDelayTicks;
    private List<Long> disableDelayTicks;

    @Override
    public void onEnable() {
        debugLogService = new DebugLogService(this);
        debugLogService.startFromDiskConfig("Plugin enable started.");
        try {
            enablePlugin();
            debugLogService.lifecycle("Plugin enable finished.");
        } catch (final RuntimeException | Error exception) {
            debugLogService.logThrowable("Plugin enable failed.", exception);
            throw exception;
        }
    }

    private void enablePlugin() {
        ResourceInstaller.installDefaults(this);
        reloadConfig();
        featureToggleService = new FeatureToggleService(this);
        featureToggleService.reload();
        loadSettings();
        debugLogService.reloadFromPluginConfig(false, "Plugin config loaded.");

        final PluginManager pluginManager = getServer().getPluginManager();
        cmiAvailable = pluginManager.isPluginEnabled("CMI");

        playerDataManager = new PlayerDataManager(this);
        languageManager = new LanguageManager(this, playerDataManager);
        placeholderService = new PlaceholderService(this, languageManager, featureToggleService);
        headDatabaseService = new HeadDatabaseService(this, featureToggleService);
        bedrockService = new BedrockService(this, featureToggleService);
        cooldownService = new CooldownService(this, featureToggleService);
        furnitureProtectionManager = new FurnitureProtectionManager(this, featureToggleService);
        entityLimitService = new EntityLimitService(this, languageManager, featureToggleService);
        auditLogService = new AuditLogService(this, featureToggleService);
        plotRoleService = new PlotRoleService(this);
        plotMetaService = new PlotMetaService(this, featureToggleService);
        plotWarpService = new PlotWarpService(this, featureToggleService);
        plotService = new PlotService(this, plotRoleService);
        plotUtilityService = new PlotUtilityService(this, plotService, featureToggleService);
        plotBackupService = new PlotBackupService(this, plotService, featureToggleService);
        redstoneLagProtectionService = new RedstoneLagProtectionService(this, plotService, auditLogService, featureToggleService);
        plotReportService = new PlotReportService(this, featureToggleService);
        plotModerationService = new PlotModerationService(this, featureToggleService);
        plotPerformanceService = new PlotPerformanceService(this, featureToggleService);
        competitionService = new CompetitionService(this, featureToggleService);
        configValidationService = new ConfigValidationService(this, featureToggleService);
        guiManager = new GuiManager(this, languageManager, placeholderService, headDatabaseService, bedrockService, plotService, entityLimitService, plotBackupService, auditLogService, redstoneLagProtectionService, plotMetaService, plotWarpService, plotUtilityService, plotReportService, plotModerationService, plotPerformanceService, competitionService, configValidationService, featureToggleService, playerDataManager);

        furnitureProtectionManager.registerFlags();
        furnitureProtectionManager.reload();
        entityLimitService.reload();
        auditLogService.load();
        plotMetaService.load();
        plotWarpService.load();
        plotUtilityService.load();
        plotUtilityService.revokeExpiredTemporaryTrusts();
        playerDataManager.load();
        plotRoleService.load();
        plotBackupService.load();
        redstoneLagProtectionService.reload();
        plotReportService.load();
        plotModerationService.load();
        competitionService.load();
        languageManager.load();
        placeholderService.reload();
        headDatabaseService.reload();
        bedrockService.reload();
        cooldownService.reload();
        plotService.reload();
        guiManager.reload();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(furnitureProtectionManager, this);
        getServer().getPluginManager().registerEvents(entityLimitService, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(redstoneLagProtectionService, this);
        getServer().getPluginManager().registerEvents(plotModerationService, this);
        plotApi.registerListener(this);
        plotApi.registerListener(plotBackupService);
        registerCommands();
        startFlightStateScanner();
        startTemporaryTrustScanner();

        getLogger().info("Flight stays untouched in PlotSquared worlds and is disabled in non-plot worlds.");
        if (cmiAvailable && useCmiCommand) {
            getLogger().info("CMI detected. CMI /fly will be disabled automatically outside plot worlds.");
        }
        getLogger().info("Loaded " + languageManager.getLanguages().size() + " language(s) and configurable plot GUIs.");
    }

    @Override
    public void onDisable() {
        if (debugLogService != null) {
            debugLogService.lifecycle("Plugin disable started.");
        }
        getServer().getScheduler().cancelTasks(this);
        try {
            PlotSquared.get().getEventDispatcher().unregisterListener(this);
            if (plotBackupService != null) {
                PlotSquared.get().getEventDispatcher().unregisterListener(plotBackupService);
            }
        } catch (final RuntimeException ignored) {
            // PlotSquared may already be shutting down.
        }
        rememberedFlightStates.clear();
        lastAllowedLocations.clear();
        if (debugLogService != null) {
            debugLogService.close("Plugin disable finished.");
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        if (debugLogService != null) {
            debugLogService.reloadFromPluginConfig(true, "Plugin reload started.");
        }
        try {
            featureToggleService.reload();
            loadSettings();
            cmiAvailable = getServer().getPluginManager().isPluginEnabled("CMI");
            furnitureProtectionManager.registerFlags();
            furnitureProtectionManager.reload();
            entityLimitService.reload();
            auditLogService.load();
            plotMetaService.load();
            plotWarpService.load();
            plotUtilityService.load();
            plotUtilityService.revokeExpiredTemporaryTrusts();
            playerDataManager.load();
            plotRoleService.load();
            plotBackupService.load();
            redstoneLagProtectionService.reload();
            plotReportService.load();
            plotModerationService.load();
            competitionService.load();
            languageManager.load();
            placeholderService.reload();
            headDatabaseService.reload();
            bedrockService.reload();
            cooldownService.reload();
            plotService.reload();
            guiManager.reload();
            if (debugLogService != null) {
                debugLogService.lifecycle("Plugin reload finished.");
            }
        } catch (final RuntimeException | Error exception) {
            if (debugLogService != null) {
                debugLogService.logThrowable("Plugin reload failed.", exception);
            }
            throw exception;
        }
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public PlotService getPlotService() {
        return plotService;
    }

    public PlotRoleService getPlotRoleService() {
        return plotRoleService;
    }

    public PlotMetaService getPlotMetaService() {
        return plotMetaService;
    }

    public PlotWarpService getPlotWarpService() {
        return plotWarpService;
    }

    public PlotUtilityService getPlotUtilityService() {
        return plotUtilityService;
    }

    public PlotBackupService getPlotBackupService() {
        return plotBackupService;
    }

    public AuditLogService getAuditLogService() {
        return auditLogService;
    }

    public RedstoneLagProtectionService getRedstoneLagProtectionService() {
        return redstoneLagProtectionService;
    }

    public PlotReportService getPlotReportService() {
        return plotReportService;
    }

    public PlotModerationService getPlotModerationService() {
        return plotModerationService;
    }

    public PlotPerformanceService getPlotPerformanceService() {
        return plotPerformanceService;
    }

    public CompetitionService getCompetitionService() {
        return competitionService;
    }

    public ConfigValidationService getConfigValidationService() {
        return configValidationService;
    }

    public FeatureToggleService getFeatureToggleService() {
        return featureToggleService;
    }

    public CooldownService getCooldownService() {
        return cooldownService;
    }

    private void registerCommands() {
        final PlotExtrasCommand command = new PlotExtrasCommand(this);
        final PluginCommand pluginCommand = getCommand("plotextras");
        if (pluginCommand == null) {
            getLogger().warning("Command 'plotextras' is missing from plugin.yml.");
            return;
        }

        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    @EventHandler
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        if (!isEnabled()) {
            return;
        }
        final Player player = event.getPlayer();
        if (isPlotWorld(player.getWorld())) {
            rememberFlightState(player);
            return;
        }

        rememberedFlightStates.remove(player.getUniqueId());
        scheduleFlightDisable(player);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        if (!isEnabled()) {
            return;
        }
        rememberedFlightStates.remove(event.getPlayer().getUniqueId());
        lastAllowedLocations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        if (!isEnabled() || plotUtilityService == null || !feature("player.visit-mode")) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        final Player player = event.getPlayer();
        if (!isPlotWorld(player.getWorld())) {
            lastAllowedLocations.put(player.getUniqueId(), player.getLocation());
            return;
        }
        final Plot currentPlot = plotService.getCurrentPlot(player);
        if (currentPlot == null || plotUtilityService.canVisit(player, currentPlot)) {
            lastAllowedLocations.put(player.getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(final PlayerToggleFlightEvent event) {
        if (!isEnabled()) {
            return;
        }
        final Player player = event.getPlayer();
        if (!isPlotWorld(player.getWorld())) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> rememberFlightState(player));
    }

    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (!isEnabled()) {
            return;
        }
        final Player player = event.getPlayer();
        if (!isPlotWorld(player.getWorld()) || !isFlyCommand(event.getMessage())) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> rememberFlightState(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!preventNamedEntityDespawn) {
            return;
        }

        final Entity clickedEntity = event.getRightClicked();
        if (!isPersistentNametagTarget(clickedEntity) || !isNameTag(event.getPlayer(), event.getHand())) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> makeEntityPersistentIfNamed(clickedEntity));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractDragonEgg(final PlayerInteractEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!preventDragonEggTeleportInPlotWorlds
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK)) {
            return;
        }

        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.DRAGON_EGG || !isPlotWorld(clickedBlock.getWorld())) {
            return;
        }

        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonEggTeleport(final BlockFromToEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!preventDragonEggTeleportInPlotWorlds
                || event.getBlock().getType() != Material.DRAGON_EGG
                || !isPlotWorld(event.getBlock().getWorld())) {
            return;
        }

        event.setCancelled(true);
    }

    @Subscribe
    public void onPlayerEnterPlot(final PlayerEnterPlotEvent event) {
        if (!isEnabled()) {
            return;
        }
        final Player player = getBukkitPlayer(event.getPlotPlayer());
        if (player != null && !plotUtilityService.canVisit(player, event.getPlot())) {
            final Location fallback = lastAllowedLocations.getOrDefault(player.getUniqueId(), player.getWorld().getSpawnLocation());
            getServer().getScheduler().runTask(this, () -> player.teleport(fallback));
            languageManager.send(player, "visit-denied", Map.of(
                    "mode", plotUtilityService.accessMode(event.getPlot()),
                    "message", plotUtilityService.lockedMessage(event.getPlot())
            ));
            return;
        }
        if (player != null && feature("player.plot-visits")) {
            plotMetaService.recordVisit(event.getPlot(), player);
        }
        protectFlightInPlotWorld(event.getPlotPlayer());
    }

    @Subscribe
    public void onPlayerLeavePlot(final PlayerLeavePlotEvent event) {
        if (!isEnabled()) {
            return;
        }
        protectFlightInPlotWorld(event.getPlotPlayer());
    }

    @Subscribe
    public void onPlayerPlotLimit(final PlayerPlotLimitEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (!feature("player.plot-limits")) {
            return;
        }
        final Player player = getBukkitPlayer(event.player());
        if (player != null) {
            event.limit(plotService.getPlotLimit(player));
        }
    }

    @Subscribe
    public void onPlayerClaimPlot(final PlayerClaimPlotEvent event) {
        if (!isEnabled()) {
            return;
        }
        enforcePlotLimit(event.getPlotPlayer(), 1, event);
    }

    @Subscribe
    public void onPlayerAutoPlot(final PlayerAutoPlotEvent event) {
        if (!isEnabled()) {
            return;
        }
        enforcePlotLimit(event.getPlayer(), Math.max(1, event.getSizeX() * event.getSizeZ()), event);
    }

    private void enforcePlotLimit(final PlotPlayer<?> plotPlayer, final int plotsToAdd, final Object event) {
        if (!feature("player.plot-limits")) {
            return;
        }
        final Player player = getBukkitPlayer(plotPlayer);
        if (player == null || player.hasPermission("craftplayplotextras.admin")) {
            return;
        }
        if (!plotService.isAtPlotLimit(player, plotsToAdd)) {
            return;
        }

        if (event instanceof PlayerClaimPlotEvent claimEvent) {
            claimEvent.setEventResult(Result.DENY);
        } else if (event instanceof PlayerAutoPlotEvent autoPlotEvent) {
            autoPlotEvent.setEventResult(Result.DENY);
        }
        languageManager.send(player, "plot-limit-reached", Map.of(
                "plot_count", String.valueOf(plotPlayer.getPlotCount()),
                "plot_max", String.valueOf(plotService.getPlotLimit(player))
        ));
    }

    private boolean isPlotWorld(final World world) {
        return PlotSquared.get().getPlotAreaManager().hasPlotArea(world.getName());
    }

    private void protectFlightInPlotWorld(final PlotPlayer<?> plotPlayer) {
        if (!restoreFlightInsidePlotWorlds) {
            return;
        }

        final Player player = getBukkitPlayer(plotPlayer);
        if (player == null || !isPlotWorld(player.getWorld())) {
            return;
        }

        rememberFlightState(player);
        scheduleFlightRestore(player);
    }

    private Player getBukkitPlayer(final PlotPlayer<?> plotPlayer) {
        final Object platformPlayer = plotPlayer.getPlatformPlayer();
        if (platformPlayer instanceof Player player && player.isOnline()) {
            return player;
        }

        return null;
    }

    private void rememberFlightState(final Player player) {
        if (!player.getAllowFlight() && !player.isFlying()) {
            return;
        }

        rememberedFlightStates.put(player.getUniqueId(), new FlightState(player.getAllowFlight(), player.isFlying()));
        debug("Remembered flight state for " + player.getName() + ": allowFlight="
                + player.getAllowFlight() + ", flying=" + player.isFlying());
    }

    private void startFlightStateScanner() {
        final long interval = Math.max(1L, getConfig().getLong("flight-state-scan-interval-ticks", 20L));
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!isEnabled()) {
                return;
            }
            for (final Player player : getServer().getOnlinePlayers()) {
                if (isPlotWorld(player.getWorld())) {
                    rememberFlightState(player);
                }
            }
        }, interval, interval);
    }

    private void startTemporaryTrustScanner() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!isEnabled() || plotUtilityService == null || !feature("player.temporary-trusts")) {
                return;
            }
            final int revoked = plotUtilityService.revokeExpiredTemporaryTrusts();
            if (revoked > 0) {
                debug("Removed expired temporary plot trusts: " + revoked);
            }
        }, 20L * 60L, 20L * 60L);
    }

    private void scheduleFlightRestore(final Player player) {
        if (!isEnabled()) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        for (final long delay : restoreDelayTicks) {
            getServer().getScheduler().runTaskLater(this, () -> restoreFlight(playerId), delay);
        }
    }

    private void restoreFlight(final UUID playerId) {
        if (!isEnabled()) {
            return;
        }
        final Player player = getServer().getPlayer(playerId);
        if (player == null || !player.isOnline() || !isPlotWorld(player.getWorld())) {
            return;
        }

        final FlightState flightState = rememberedFlightStates.get(playerId);
        if (flightState == null || !flightState.allowFlight()) {
            return;
        }

        if (useCmiCommand && cmiAvailable) {
            getServer().dispatchCommand(getServer().getConsoleSender(), "cmi fly " + player.getName() + " true -s");
        }

        player.setAllowFlight(true);
        player.setFlying(flightState.flying());
        debug("Restored flight state for " + player.getName());
    }

    private void scheduleFlightDisable(final Player player) {
        if (!isEnabled()) {
            return;
        }
        if (!disableFlightOutsidePlotWorlds) {
            return;
        }

        disableFlight(player);
        final UUID playerId = player.getUniqueId();
        for (final long delay : disableDelayTicks) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!isEnabled()) {
                    return;
                }
                final Player delayedPlayer = getServer().getPlayer(playerId);
                if (delayedPlayer != null && delayedPlayer.isOnline() && !isPlotWorld(delayedPlayer.getWorld())) {
                    disableFlight(delayedPlayer);
                }
            }, delay);
        }
    }

    private void disableFlight(final Player player) {
        if (ignoreCreativeAndSpectator
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }

        disableCmiFlight(player);
        player.setFlying(false);
        player.setAllowFlight(false);
        debug("Disabled flight for " + player.getName() + " in world " + player.getWorld().getName());
    }

    private void disableCmiFlight(final Player player) {
        if (!useCmiCommand || !cmiAvailable) {
            return;
        }

        getServer().dispatchCommand(getServer().getConsoleSender(), "cmi fly " + player.getName() + " false -s");
    }

    private boolean isFlyCommand(final String message) {
        final String command = message.toLowerCase().trim();
        return command.equals("/fly")
                || command.startsWith("/fly ")
                || command.equals("/cmi fly")
                || command.startsWith("/cmi fly ");
    }

    private void loadSettings() {
        restoreFlightInsidePlotWorlds = getConfig().getBoolean("restore-flight-inside-plot-worlds", true)
                && feature("protection.flight")
                && feature("protection.flight.restore-inside-plot-worlds");
        disableFlightOutsidePlotWorlds = getConfig().getBoolean("disable-flight-outside-plot-worlds", true)
                && feature("protection.flight")
                && feature("protection.flight.disable-outside-plot-worlds");
        useCmiCommand = getConfig().getBoolean("use-cmi-command", true) && feature("integrations.cmi");
        ignoreCreativeAndSpectator = getConfig().getBoolean("ignore-creative-and-spectator", true);
        preventNamedEntityDespawn = getConfig().getBoolean("prevent-named-entity-despawn", true)
                && feature("protection.named-entity-despawn");
        preventDragonEggTeleportInPlotWorlds = getConfig().getBoolean("prevent-dragon-egg-teleport-in-plot-worlds", true)
                && feature("protection.dragon-egg-teleport");
        debug = getConfig().getBoolean("debug", false);
        restoreDelayTicks = getTickList("restore-delay-ticks", List.of(1L, 5L));
        disableDelayTicks = getTickList("disable-delay-ticks", List.of(1L, 5L));
    }

    private boolean feature(final String feature) {
        return featureToggleService == null || featureToggleService.isEnabled(feature);
    }

    private List<Long> getTickList(final String path, final List<Long> fallback) {
        final List<Integer> configuredTicks = getConfig().getIntegerList(path);
        if (configuredTicks.isEmpty()) {
            return fallback;
        }

        final List<Long> ticks = new ArrayList<>();
        for (final int tick : configuredTicks) {
            if (tick >= 0) {
                ticks.add((long) tick);
            }
        }

        return ticks.isEmpty() ? fallback : ticks;
    }

    private boolean isNameTag(final Player player, final EquipmentSlot hand) {
        final ItemStack item = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        return item.getType() == Material.NAME_TAG;
    }

    private boolean isPersistentNametagTarget(final Entity entity) {
        return entity instanceof Monster || entity instanceof Animals || entity instanceof Villager;
    }

    private void makeEntityPersistentIfNamed(final Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity) || livingEntity.customName() == null) {
            return;
        }

        livingEntity.setRemoveWhenFarAway(false);
        livingEntity.setPersistent(true);
        debug("Made named entity persistent: " + livingEntity.getType());
    }

    private void debug(final String message) {
        if (debug) {
            getLogger().info("[Debug] " + message);
        }
        if (debugLogService != null) {
            debugLogService.debug(message);
        }
    }

    private record FlightState(boolean allowFlight, boolean flying) {
    }
}
