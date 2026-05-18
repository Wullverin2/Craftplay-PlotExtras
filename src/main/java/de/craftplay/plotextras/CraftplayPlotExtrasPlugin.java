package de.craftplay.plotextras;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftplayPlotExtrasPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("CraftplayPlotExtras wurde frisch gestartet.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CraftplayPlotExtras wurde beendet.");
    }

    @Override
    public boolean onCommand(
            final CommandSender sender,
            final Command command,
            final String label,
            final String[] args
    ) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("craftplayplotextras.admin")) {
                sender.sendMessage("Du hast keine Berechtigung dafür.");
                return true;
            }
            reloadConfig();
            sender.sendMessage("CraftplayPlotExtras wurde neu geladen.");
            return true;
        }

        sender.sendMessage("CraftplayPlotExtras ist frisch zurückgesetzt. Nächster Schritt: Funktionen neu planen.");
        return true;
    }
}
