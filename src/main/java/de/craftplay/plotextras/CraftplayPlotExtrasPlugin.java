package de.craftplay.plotextras;

import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.command.PlotExtrasCommand;
import de.craftplay.plotextras.config.ConfigurationManager;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.listener.CommandListener;
import de.craftplay.plotextras.listener.PlotBackupProtectionListener;
import de.craftplay.plotextras.menu.PlotMenuManager;
import de.craftplay.plotextras.myplots.PlotDataStore;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.plotsquared.PlotSquaredPlotService;
import de.craftplay.plotextras.reports.ReportService;
import de.craftplay.plotextras.roles.PlotRoleService;
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
    private PlotExtrasCommand commandExecutor;

    @Override
    public void onEnable() {
        configurationManager = new ConfigurationManager(this);
        configurationManager.installOrUpdateDefaults();
        reloadConfig();
        languageManager = new LanguageManager(this);
        languageManager.reload();
        plotSquaredFlagService = new PlotSquaredFlagService(this);
        plotSquaredPlotService = new PlotSquaredPlotService(this);
        plotDataStore = new PlotDataStore(this);
        plotDataStore.reload();
        reportService = new ReportService(this, plotSquaredFlagService);
        reportService.reload();
        plotRoleService = new PlotRoleService(this, plotSquaredFlagService);
        plotRoleService.reload();
        plotBackupService = new PlotBackupService(this);
        plotBackupService.reload();
        plotMenuManager = new PlotMenuManager(this, languageManager, plotSquaredFlagService, plotSquaredPlotService,
                plotDataStore, plotBackupService, reportService, plotRoleService);
        plotMenuManager.reload();

        getServer().getPluginManager().registerEvents(plotMenuManager, this);
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotBackupProtectionListener(plotBackupService), this);
        registerCommands();

        getLogger().info("CraftplayPlotExtras Menü wurde geladen.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CraftplayPlotExtras Menü wurde beendet.");
    }

    public void reloadPlugin() {
        configurationManager.installOrUpdateDefaults();
        reloadConfig();
        languageManager.reload();
        plotDataStore.reload();
        reportService.reload();
        plotRoleService.reload();
        plotBackupService.reload();
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
    }
}
