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
        if (args.length >= 1 && args[0].equalsIgnoreCase("storage")) {
            return storage(sender, args);
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("reports")) {
            if (!player.hasPermission("craftplayplotextras.reports.manage")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getPlotMenuManager().openReportListMenu(player, 1, args.length >= 2 ? args[1] : "open");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("roles")) {
            if (!plugin.getPlotRoleService().canManage(player)) {
                return true;
            }
            plugin.getPlotMenuManager().openRoleListMenu(player, 1);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("future")) {
            if (!player.hasPermission("craftplayplotextras.future")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            if (args.length == 1) {
                plugin.getPlotMenuManager().openActionMenu(player, "future");
                return true;
            }
            final StringBuilder payload = new StringBuilder();
            for (int index = 1; index < args.length; index++) {
                if (payload.length() > 0) {
                    payload.append(':');
                }
                payload.append(args[index]);
            }
            plugin.getPlotFutureService().runCommand(player, payload.toString());
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("passivewither")) {
            final String[] passiveWitherArgs = new String[Math.max(0, args.length - 1)];
            if (passiveWitherArgs.length > 0) {
                System.arraycopy(args, 1, passiveWitherArgs, 0, passiveWitherArgs.length);
            }
            return plugin.getPassiveWitherService().handleCommand(
                    player,
                    plugin.getConfig().getString("command.prefix", "plotextras"),
                    passiveWitherArgs
            );
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
        if (args.length == 1 && args[0].equalsIgnoreCase("myplots")) {
            if (!player.hasPermission("craftplayplotextras.myplots")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getPlotMenuManager().openMyPlotsMenu(player);
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

    private boolean storage(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("craftplayplotextras.admin")) {
            plugin.getLanguageManager().send(sender, "no-permission");
            return true;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("migrate")) {
            return plugin.getStorageService().migrate(sender, args[2], args[3]);
        }
        sender.sendMessage("§e/" + plugin.getConfig().getString("command.prefix", "plotextras")
                + " storage migrate <quelle> <ziel>");
        sender.sendMessage("§7Typen: yaml, sqlite, mysql, mariadb, postgresql, redis, mongodb");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("storage") && sender.hasPermission("craftplayplotextras.admin")) {
            return storageCompletions(args);
        }
        if (args.length != 1) {
            return Collections.emptyList();
        }

        final String input = args[0].toLowerCase(Locale.ROOT);
        final List<String> completions = new ArrayList<>();
        if (sender.hasPermission("craftplayplotextras.admin") && "reload".startsWith(input)) {
            completions.add("reload");
        }
        if (sender.hasPermission("craftplayplotextras.admin") && "storage".startsWith(input)) {
            completions.add("storage");
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
        if (sender.hasPermission("craftplayplotextras.reports.manage") && "reports".startsWith(input)) {
            completions.add("reports");
        }
        if (sender.hasPermission("craftplayplotextras.roles.manage") && "roles".startsWith(input)) {
            completions.add("roles");
        }
        if (sender.hasPermission("craftplayplotextras.future") && "future".startsWith(input)) {
            completions.add("future");
        }
        if ((sender.hasPermission("craftplayplotextras.passivewither.command")
                || sender.hasPermission("craftplayplotextras.passivewither.sound"))
                && "passivewither".startsWith(input)) {
            completions.add("passivewither");
        }
        if (sender.hasPermission("craftplayplotextras.use") && "gui".startsWith(input)) {
            completions.add("gui");
        }
        if (sender.hasPermission("craftplayplotextras.use") && "bgui".startsWith(input)) {
            completions.add("bgui");
        }
        if (sender.hasPermission("craftplayplotextras.myplots") && "myplots".startsWith(input)) {
            completions.add("myplots");
        }
        return completions;
    }

    private List<String> storageCompletions(final String[] args) {
        final String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        final List<String> values = new ArrayList<>();
        if (args.length == 2) {
            if ("migrate".startsWith(input)) {
                values.add("migrate");
            }
            return values;
        }
        if (args.length == 3 || args.length == 4) {
            for (final String type : new String[]{"yaml", "sqlite", "mysql", "mariadb", "postgresql", "redis", "mongodb"}) {
                if (type.startsWith(input)) {
                    values.add(type);
                }
            }
        }
        return values;
    }
}
