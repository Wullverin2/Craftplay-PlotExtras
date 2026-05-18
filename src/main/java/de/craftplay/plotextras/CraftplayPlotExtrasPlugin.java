package de.craftplay.plotextras;

import de.craftplay.plotextras.command.PlotExtrasCommand;
import de.craftplay.plotextras.menu.PlotMenuManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin {

    private PlotMenuManager plotMenuManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        plotMenuManager = new PlotMenuManager(this);
        plotMenuManager.reload();

        getServer().getPluginManager().registerEvents(plotMenuManager, this);
        registerCommands();

        if (getServer().getPluginManager().getPlugin("PlotSquared") == null) {
            getLogger().warning("PlotSquared wurde nicht gefunden. Das Menü öffnet trotzdem, aber Befehle können fehlschlagen.");
        }
        getLogger().info("CraftplayPlotExtras Menü wurde geladen.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CraftplayPlotExtras Menü wurde beendet.");
    }

    public void reloadPlugin() {
        reloadConfig();
        plotMenuManager.reload();
    }

    public PlotMenuManager getPlotMenuManager() {
        return plotMenuManager;
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
