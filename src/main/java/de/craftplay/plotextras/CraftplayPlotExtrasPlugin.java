package de.craftplay.plotextras;

import de.craftplay.plotextras.command.PlotExtrasCommand;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.menu.PlotMenuManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin {

    private LanguageManager languageManager;
    private PlotMenuManager plotMenuManager;

    @Override
    public void onEnable() {
        installDefaults();
        languageManager = new LanguageManager(this);
        languageManager.reload();
        plotMenuManager = new PlotMenuManager(this, languageManager);
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

    private void installDefaults() {
        saveDefaultConfig();
        saveResourceIfMissing("language/de.yml");
        saveResourceIfMissing("language/en.yml");
        saveResourceIfMissing("gui/de/main.yml");
        saveResourceIfMissing("gui/en/main.yml");
    }

    private void saveResourceIfMissing(final String resourcePath) {
        if (getResource(resourcePath) == null) {
            return;
        }
        if (new java.io.File(getDataFolder(), resourcePath).exists()) {
            return;
        }
        saveResource(resourcePath, false);
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
