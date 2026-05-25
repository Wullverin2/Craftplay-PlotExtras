package de.craftplay.plotextras.listener;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CommandListener implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;

    public CommandListener(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        final String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        final String commandLine = message.substring(1).trim();
        if (commandLine.isEmpty()) {
            return;
        }

        final String[] parts = commandLine.split("\\s+");
        final String label = parts[0].toLowerCase(Locale.ROOT);
        final String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        if (isPluginPrefix(label)) {
            event.setCancelled(true);
            plugin.getCommandExecutor().execute(event.getPlayer(), args);
            return;
        }

        if (!plugin.getPlotBackupService().isEnabled() || !isPlotCommand(label)) {
            return;
        }
        if (plugin.getPlotBackupService().consumeBypass(event.getPlayer())) {
            return;
        }
        if (args.length == 0) {
            return;
        }
        if (isPlotDecoSetCommand(args)) {
            return;
        }

        final String action = plotAction(args[0]);
        if (action == null) {
            return;
        }
        if (!interceptsAction(action)) {
            return;
        }

        event.setCancelled(true);
        plugin.getPlotBackupService().requestProtectedAction(event.getPlayer(), action, commandLine);
    }

    private boolean isPluginPrefix(final String label) {
        for (final String prefix : pluginCommandPrefixes()) {
            if (label.equalsIgnoreCase(prefix)) {
                return true;
            }
        }
        return false;
    }

    private List<String> pluginCommandPrefixes() {
        final List<String> labels = new ArrayList<>();
        final String primary = plugin.getConfig().getString("command.prefix", "plotextras");
        if (primary != null && !primary.trim().isEmpty()) {
            labels.add(primary.trim().toLowerCase(Locale.ROOT));
        }
        for (final String alias : plugin.getConfig().getStringList("command.aliases")) {
            if (alias != null && !alias.trim().isEmpty()) {
                labels.add(alias.trim().toLowerCase(Locale.ROOT));
            }
        }
        labels.add("plotextras");
        labels.add("cpe");
        return labels;
    }

    private boolean isPlotCommand(final String label) {
        for (final String plotCommand : plugin.getConfig().getStringList("plot-backups.intercept.plot-command-labels")) {
            if (label.equalsIgnoreCase(plotCommand)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlotDecoSetCommand(final String[] args) {
        if (args.length < 2 || !"set".equalsIgnoreCase(args[0])) {
            return false;
        }
        final String component = args[1].toLowerCase(Locale.ROOT);
        return "wall".equals(component) || "border".equals(component);
    }

    private String plotAction(final String subCommand) {
        final String normalized = subCommand.toLowerCase(Locale.ROOT);
        if (contains("plot-backups.intercept.delete-aliases", normalized)) {
            return "delete";
        }
        if (contains("plot-backups.intercept.unmerge-aliases", normalized)) {
            return "unmerge";
        }
        if (contains("plot-backups.intercept.reset-aliases", normalized)) {
            return "reset";
        }
        return null;
    }

    private boolean interceptsAction(final String action) {
        return plugin.getConfig().getBoolean("plot-backups.intercept." + action, true);
    }

    private boolean contains(final String path, final String value) {
        for (final String entry : plugin.getConfig().getStringList(path)) {
            if (value.equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }
}
