package de.craftplay.plotextras.command;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
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
        return execute(sender, args);
    }

    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }

        if (!(sender instanceof Player)) {
            plugin.getLanguageManager().send(sender, "only-players");
            return true;
        }

        final Player player = (Player) sender;
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            return plugin.getPlotBackupService().confirm(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            return plugin.getPlotBackupService().cancel(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("team")) {
            if (!player.hasPermission("craftplayplotextras.team")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getPlotMenuManager().openTeamMenu(player);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("backup")) {
            return backup(player, args);
        }

        if (!player.hasPermission("craftplayplotextras.use")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
            plugin.getPlotMenuManager().openMainMenu(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("bgui")) {
            plugin.getPlotMenuManager().openBedrockMainMenu(player);
            return true;
        }

        plugin.getPlotMenuManager().openMenu(player);
        return true;
    }

    private boolean reload(final CommandSender sender) {
        if (!sender.hasPermission("craftplayplotextras.admin")) {
            plugin.getLanguageManager().send(sender, "no-permission");
            return true;
        }

        plugin.reloadPlugin();
        plugin.getLanguageManager().send(sender, "reloaded");
        return true;
    }

    private boolean backup(final Player player, final String[] args) {
        if (args[1].equalsIgnoreCase("create")) {
            plugin.getPlotBackupService().requestManualBackup(player);
            return true;
        }
        if (args[1].equalsIgnoreCase("restore") && args.length >= 3) {
            plugin.getPlotBackupService().requestRestore(player, args[2]);
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            if (!player.hasPermission("craftplayplotextras.backup.list")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getPlotMenuManager().openBackupListMenu(player, 1);
            return true;
        }
        final java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("prefix", plugin.getConfig().getString("command.prefix", "plotextras"));
        plugin.getLanguageManager().send(player, "backup-command-usage", placeholders);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        final String input = args[0].toLowerCase(Locale.ROOT);
        final List<String> completions = new ArrayList<>();
        if (sender.hasPermission("craftplayplotextras.admin") && "reload".startsWith(input)) {
            completions.add("reload");
        }
        if (sender.hasPermission("craftplayplotextras.team") && "team".startsWith(input)) {
            completions.add("team");
        }
        if ("confirm".startsWith(input)) {
            completions.add("confirm");
        }
        if ("cancel".startsWith(input)) {
            completions.add("cancel");
        }
        if ((sender.hasPermission("craftplayplotextras.backup.create")
                || sender.hasPermission("craftplayplotextras.backup.list")
                || sender.hasPermission("craftplayplotextras.backup.restore"))
                && "backup".startsWith(input)) {
            completions.add("backup");
        }
        if (sender.hasPermission("craftplayplotextras.use") && "gui".startsWith(input)) {
            completions.add("gui");
        }
        if (sender.hasPermission("craftplayplotextras.use") && "bgui".startsWith(input)) {
            completions.add("bgui");
        }
        return completions;
    }
}
