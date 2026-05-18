package de.craftplay.plotextras.command;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlotExtrasCommand implements CommandExecutor, TabCompleter {

    private final CraftplayPlotExtrasPlugin plugin;

    public PlotExtrasCommand(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            final CommandSender sender,
            final Command command,
            final String label,
            final String[] args
    ) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern genutzt werden.");
            return true;
        }

        final Player player = (Player) sender;
        if (!player.hasPermission("craftplayplotextras.use")) {
            player.sendMessage(Text.color(plugin.getConfig().getString("messages.no-permission", "&cDazu hast du keine Berechtigung.")));
            return true;
        }

        plugin.getPlotMenuManager().openMainMenu(player);
        return true;
    }

    private boolean reload(final CommandSender sender) {
        if (!sender.hasPermission("craftplayplotextras.admin")) {
            sender.sendMessage(Text.color(plugin.getConfig().getString("messages.no-permission", "&cDazu hast du keine Berechtigung.")));
            return true;
        }

        plugin.reloadPlugin();
        sender.sendMessage(Text.color(plugin.getConfig().getString("messages.reloaded", "&aCraftplayPlotExtras wurde neu geladen.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        if (args.length != 1 || !sender.hasPermission("craftplayplotextras.admin")) {
            return Collections.emptyList();
        }

        final String input = args[0].toLowerCase(Locale.ROOT);
        final List<String> completions = new ArrayList<>();
        if ("reload".startsWith(input)) {
            completions.add("reload");
        }
        return completions;
    }
}
