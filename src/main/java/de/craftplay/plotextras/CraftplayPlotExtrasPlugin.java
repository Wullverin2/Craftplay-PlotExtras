package de.craftplay.plotextras;

import de.craftplay.plotextras.command.PlotExtrasCommand;
import de.craftplay.plotextras.config.ConfigurationManager;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.menu.PlotMenuManager;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin {

    private ConfigurationManager configurationManager;
    private LanguageManager languageManager;
    private PlotSquaredFlagService plotSquaredFlagService;
    private PlotMenuManager plotMenuManager;

    @Override
    public void onEnable() {
        configurationManager = new ConfigurationManager(this);
        configurationManager.installOrUpdateDefaults();
        reloadConfig();
        languageManager = new LanguageManager(this);
        languageManager.reload();
        plotSquaredFlagService = new PlotSquaredFlagService(this);
        plotMenuManager = new PlotMenuManager(this, languageManager, plotSquaredFlagService);
        plotMenuManager.reload();

        getServer().getPluginManager().registerEvents(plotMenuManager, this);
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

    private void registerCommands() {
        final PluginCommand command = getCommand("plotextras");
        if (command == null) {
            getLogger().warning("Der Befehl 'plotextras' fehlt in der plugin.yml.");
            return;
        }

        final PlotExtrasCommand executor = new PlotExtrasCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
