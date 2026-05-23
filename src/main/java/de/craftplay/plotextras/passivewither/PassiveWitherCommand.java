package de.craftplay.plotextras.passivewither;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public final class PassiveWitherCommand implements CommandExecutor, TabCompleter {

    private final CraftplayPlotExtrasPlugin plugin;

    public PassiveWitherCommand(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            final CommandSender sender,
            final Command command,
            final String label,
            final String[] args
    ) {
        return plugin.getPassiveWitherService().handleCommand(sender, label, args);
    }

    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        if (plugin.getPassiveWitherService() == null) {
            return Collections.emptyList();
        }
        return plugin.getPassiveWitherService().tabComplete(sender, args);
    }
}
