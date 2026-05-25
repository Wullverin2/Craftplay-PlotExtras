package de.craftplay.plotextras;

import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.command.PlotExtrasCommand;
import de.craftplay.plotextras.config.ConfigurationManager;
import de.craftplay.plotextras.extras.ExtrasService;
import de.craftplay.plotextras.future.PlotFutureService;
import de.craftplay.plotextras.furniture.FurnitureIntegrationService;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.listener.CommandListener;
import de.craftplay.plotextras.listener.PlotBackupProtectionListener;
import de.craftplay.plotextras.listener.PlotFutureListener;
import de.craftplay.plotextras.listener.TeamFeatureProtectionListener;
import de.craftplay.plotextras.limits.EntityLimitService;
import de.craftplay.plotextras.menu.PlotMenuManager;
import de.craftplay.plotextras.myplots.PlotDataStore;
import de.craftplay.plotextras.nofly.PlotNoFlyService;
import de.craftplay.plotextras.passivewither.PassiveWitherCommand;
import de.craftplay.plotextras.passivewither.PassiveWitherService;
import de.craftplay.plotextras.plotpurchase.PlotPurchaseService;
import de.craftplay.plotextras.plotsquared.PlotSquaredCustomFlagRegistry;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.plotsquared.PlotSquaredPlotService;
import de.craftplay.plotextras.reports.ReportService;
import de.craftplay.plotextras.roles.PlotRoleService;
import de.craftplay.plotextras.storage.StorageService;
import de.craftplay.plotextras.team.TeamFeatureService;
import de.craftplay.plotextras.vehicles.VehiclesIntegrationService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin {

    private ConfigurationManager configurationManager;
    private LanguageManager languageManager;
    private PlotSquaredFlagService plotSquaredFlagService;
    private PlotSquaredPlotService plotSquaredPlotService;
    private PlotDataStore plotDataStore;
    private PlotMenuManager plotMenuManager;
    private PlotBackupService plotBackupService;
    private ReportService reportService;
    private PlotRoleService plotRoleService;
    private PlotFutureService plotFutureService;
    private TeamFeatureService teamFeatureService;
    private ExtrasService extrasService;
    private PassiveWitherService passiveWitherService;
    private EntityLimitService entityLimitService;
    private PlotPurchaseService plotPurchaseService;
    private StorageService storageService;
    private VehiclesIntegrationService vehiclesIntegrationService;
    private FurnitureIntegrationService furnitureIntegrationService;
    private PlotNoFlyService plotNoFlyService;
    private PlotExtrasCommand commandExecutor;

    @Override
    public void onLoad() {
        PlotSquaredCustomFlagRegistry.registerAll(this);
    }

    @Override
    public void onEnable() {
        configurationManager = new ConfigurationManager(this);
        configurationManager.installOrUpdateDefaults();
        reloadConfig();
        languageManager = new LanguageManager(this);
        languageManager.reload();
        storageService = new StorageService(this);
        storageService.reload();
        plotSquaredFlagService = new PlotSquaredFlagService(this);
        plotSquaredPlotService = new PlotSquaredPlotService(this);
        plotPurchaseService = new PlotPurchaseService(this, plotSquaredPlotService);
        plotPurchaseService.reload();
        plotDataStore = new PlotDataStore(this);
        plotDataStore.reload();
        reportService = new ReportService(this, plotSquaredFlagService);
        reportService.reload();
        plotRoleService = new PlotRoleService(this, plotSquaredFlagService);
        plotRoleService.reload();
        plotBackupService = new PlotBackupService(this);
        plotBackupService.reload();
        plotFutureService = new PlotFutureService(this, plotDataStore);
        plotFutureService.reload();
        teamFeatureService = new TeamFeatureService(this);
        teamFeatureService.reload();
        passiveWitherService = new PassiveWitherService(this);
        passiveWitherService.reload();
        vehiclesIntegrationService = new VehiclesIntegrationService(this);
        vehiclesIntegrationService.reload();
        furnitureIntegrationService = new FurnitureIntegrationService(this);
        furnitureIntegrationService.reload();
        plotNoFlyService = new PlotNoFlyService(this);
        plotNoFlyService.reload();
        entityLimitService = new EntityLimitService(this, plotSquaredFlagService);
        entityLimitService.reload();
        extrasService = new ExtrasService(this, languageManager, plotSquaredFlagService);
        extrasService.reload();
        plotMenuManager = new PlotMenuManager(this, languageManager, plotSquaredFlagService, plotSquaredPlotService,
                plotDataStore, plotBackupService, reportService, plotRoleService, plotFutureService, teamFeatureService,
                extrasService, plotPurchaseService);
        plotMenuManager.reload();

        getServer().getPluginManager().registerEvents(plotMenuManager, this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotBackupProtectionListener(plotBackupService), this);
        getServer().getPluginManager().registerEvents(new PlotFutureListener(plotFutureService), this);
        getServer().getPluginManager().registerEvents(new TeamFeatureProtectionListener(teamFeatureService), this);
        getServer().getPluginManager().registerEvents(passiveWitherService, this);
        getServer().getPluginManager().registerEvents(furnitureIntegrationService, this);
        getServer().getPluginManager().registerEvents(plotNoFlyService, this);
        getServer().getPluginManager().registerEvents(entityLimitService, this);
        getServer().getPluginManager().registerEvents(extrasService, this);
        registerCommands();

        getLogger().info("CraftplayPlotExtras Menü wurde geladen.");
    }

    @Override
    public void onDisable() {
        if (plotFutureService != null) {
            plotFutureService.shutdown();
        }
        if (plotDataStore != null) {
            plotDataStore.shutdown();
        }
        if (passiveWitherService != null) {
            passiveWitherService.shutdown();
        }
        if (vehiclesIntegrationService != null) {
            vehiclesIntegrationService.shutdown();
        }
        if (furnitureIntegrationService != null) {
            furnitureIntegrationService.shutdown();
        }
        if (plotNoFlyService != null) {
            plotNoFlyService.shutdown();
        }
        if (plotPurchaseService != null) {
            plotPurchaseService.shutdown();
        }
        if (storageService != null) {
            storageService.close();
        }
        getLogger().info("CraftplayPlotExtras Menü wurde beendet.");
    }

    public void reloadPlugin() {
        configurationManager.installOrUpdateDefaults();
        reloadConfig();
        languageManager.reload();
        storageService.reload();
        plotPurchaseService.reload();
        plotDataStore.reload();
        reportService.reload();
        plotRoleService.reload();
        plotBackupService.reload();
        plotFutureService.reload();
        teamFeatureService.reload();
        passiveWitherService.reload();
        vehiclesIntegrationService.reload();
        furnitureIntegrationService.reload();
        plotNoFlyService.reload();
        entityLimitService.reload();
        extrasService.reload();
        plotMenuManager.reload();
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public PlotMenuManager getPlotMenuManager() {
        return plotMenuManager;
    }

    public PlotSquaredFlagService getPlotSquaredFlagService() {
        return plotSquaredFlagService;
    }

    public PlotSquaredPlotService getPlotSquaredPlotService() {
        return plotSquaredPlotService;
    }

    public PlotDataStore getPlotDataStore() {
        return plotDataStore;
    }

    public PlotBackupService getPlotBackupService() {
        return plotBackupService;
    }

    public ReportService getReportService() {
        return reportService;
    }

    public PlotRoleService getPlotRoleService() {
        return plotRoleService;
    }

    public PlotFutureService getPlotFutureService() {
        return plotFutureService;
    }

    public TeamFeatureService getTeamFeatureService() {
        return teamFeatureService;
    }

    public ExtrasService getExtrasService() {
        return extrasService;
    }

    public PassiveWitherService getPassiveWitherService() {
        return passiveWitherService;
    }

    public EntityLimitService getEntityLimitService() {
        return entityLimitService;
    }

    public PlotPurchaseService getPlotPurchaseService() {
        return plotPurchaseService;
    }

    public StorageService getStorageService() {
        return storageService;
    }

    public PlotExtrasCommand getCommandExecutor() {
        return commandExecutor;
    }

    private void registerCommands() {
        final PluginCommand command = getCommand("plotextras");
        if (command == null) {
            getLogger().warning("Der Befehl 'plotextras' fehlt in der plugin.yml.");
            return;
        }

        commandExecutor = new PlotExtrasCommand(this);
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);

        final PluginCommand passiveWitherCommand = getCommand("passivewither");
        if (passiveWitherCommand != null) {
            final PassiveWitherCommand passiveWitherExecutor = new PassiveWitherCommand(this);
            passiveWitherCommand.setExecutor(passiveWitherExecutor);
            passiveWitherCommand.setTabCompleter(passiveWitherExecutor);
        }
    }
}
