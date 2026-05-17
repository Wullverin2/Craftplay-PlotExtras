package de.craftplay.plotextras.command;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.audit.AuditLogEntry;
import de.craftplay.plotextras.backup.PlotBackupEntry;
import de.craftplay.plotextras.language.LanguageDefinition;
import de.craftplay.plotextras.plot.PlotRole;
import de.craftplay.plotextras.plot.PlotRolePermission;
import de.craftplay.plotextras.plot.PlotRoleService;
import de.craftplay.plotextras.util.TextUtil;
import com.plotsquared.core.plot.Plot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class PlotExtrasCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CraftplayPlotExtrasPlugin plugin;

    public PlotExtrasCommand(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length > 0 && isRedstoneCommand(args[0])) {
            return handleRedstone(player, args);
        }

        if (!player.hasPermission("craftplayplotextras.use")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiManager().open(player, "main", 0);
            return true;
        }

        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("language") || subCommand.equals("sprache")) {
            if (args.length >= 2) {
                final String language = args[1].toLowerCase(Locale.ROOT);
                if (!plugin.getLanguageManager().setPlayerLanguage(player, language)) {
                    plugin.getLanguageManager().send(player, "unknown-language", Map.of("language", language));
                    return true;
                }
                plugin.getLanguageManager().send(player, "language-set", Map.of("language", language));
                return true;
            }
            plugin.getGuiManager().open(player, "language", 0);
            return true;
        }

        if (subCommand.equals("role") || subCommand.equals("roles") || subCommand.equals("rolle") || subCommand.equals("rollen")) {
            return handleRoles(player, args);
        }

        if (subCommand.equals("backup") || subCommand.equals("backups") || subCommand.equals("sicherung") || subCommand.equals("sicherungen")) {
            return handleBackups(player, args);
        }

        if (subCommand.equals("audit") || subCommand.equals("log") || subCommand.equals("logs")) {
            return handleAudit(player, args);
        }

        if (subCommand.equals("inspect") || subCommand.equals("inspector") || subCommand.equals("team")) {
            if (!plugin.getAuditLogService().canView(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getGuiManager().open(player, "team-inspector", 0);
            return true;
        }

        if (subCommand.equals("open") || subCommand.equals("gui")) {
            final String gui = args.length >= 2 ? args[1] : "main";
            plugin.getGuiManager().open(player, gui, 0);
            return true;
        }

        player.sendMessage(TextUtil.component(plugin.getLanguageManager().getRawMessage(player, "help")));
        return true;
    }

    private boolean handleAudit(final Player player, final String[] args) {
        if (!plugin.getAuditLogService().canView(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
            plugin.getGuiManager().open(player, "audit-log", 0);
            return true;
        }

        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        final boolean currentPlotOnly = args.length >= 2 && (args[1].equalsIgnoreCase("plot") || args[1].equalsIgnoreCase("hier"));
        final List<AuditLogEntry> entries = currentPlotOnly && plot != null
                ? plugin.getAuditLogService().listForPlot(plot, 10)
                : plugin.getAuditLogService().listRecent(10);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aAuditlog &8(" + entries.size() + ")"));
        if (entries.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine Einträge gefunden."));
        }
        for (final AuditLogEntry entry : entries) {
            player.sendMessage(TextUtil.component("&e" + BACKUP_TIME_FORMAT.format(entry.createdAt())
                    + " &7| &f" + entry.actor()
                    + " &7| &a" + entry.action()
                    + " &7| &f" + entry.world() + " " + entry.plotId()
                    + " &7| &8" + entry.details()));
        }
        player.sendMessage(TextUtil.component("&8/pe audit gui &7- Auditlog-GUI öffnen"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleRedstone(final Player player, final String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("help") || args[1].equalsIgnoreCase("hilfe")) {
            sendRedstoneHelp(player);
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("tp") || action.equals("teleport")) {
            if (!plugin.getRedstoneLagProtectionService().canReceiveAlerts(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            if (args.length < 3 || !plugin.getRedstoneLagProtectionService().teleportToAlert(player, args[2])) {
                player.sendMessage(TextUtil.component("&cRedstone-Alarm wurde nicht gefunden."));
                return true;
            }
            player.sendMessage(TextUtil.component("&aDu wurdest zur Redstone-Lagmaschine teleportiert."));
            return true;
        }

        if (action.equals("enable") || action.equals("aktivieren") || action.equals("reactivate")) {
            if (!plugin.getRedstoneLagProtectionService().canAdmin(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            final boolean enabled = args.length >= 3
                    ? plugin.getRedstoneLagProtectionService().enableRedstoneAtAlert(player, args[2])
                    : plugin.getRedstoneLagProtectionService().enableRedstoneAtCurrentPlot(player);
            if (!enabled) {
                player.sendMessage(TextUtil.component("&cRedstone konnte auf diesem Plot nicht aktiviert werden."));
                return true;
            }
            player.sendMessage(TextUtil.component("&aRedstone wurde auf dem Plot wieder aktiviert."));
            return true;
        }

        sendRedstoneHelp(player);
        return true;
    }

    private boolean handleRoles(final Player player, final String[] args) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        if (!plugin.getPlotService().canManageRoles(player, plot)) {
            plugin.getLanguageManager().send(player, "not-owner");
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("liste")) {
            sendRoleList(player, plot);
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "create", "add", "neu", "erstellen" -> createRole(player, plot, args);
            case "rename", "umbenennen" -> renameRole(player, plot, args);
            case "delete", "remove", "del", "löschen", "loeschen" -> deleteRole(player, plot, args);
            case "permission", "perm", "right", "recht" -> setRolePermission(player, plot, args);
            case "assign", "set", "zuweisen" -> assignRole(player, plot, args);
            case "unassign", "clear", "entfernen" -> unassignRole(player, plot, args);
            case "info" -> roleInfo(player, plot, args);
            default -> {
                plugin.getLanguageManager().send(player, "role-help");
                yield true;
            }
        };
    }

    private boolean createRole(final Player player, final Plot plot, final String[] args) {
        if (args.length < 3) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final String roleId = PlotRoleService.normalizeRoleId(args[2]);
        final String displayName = args.length >= 4 ? join(args, 3) : roleId;
        final PlotRoleService.RoleResult result = plugin.getPlotRoleService().createRole(plot, roleId, displayName);
        sendRoleResult(player, result, roleId, displayName);
        return true;
    }

    private boolean renameRole(final Player player, final Plot plot, final String[] args) {
        if (args.length < 4) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final String roleId = PlotRoleService.normalizeRoleId(args[2]);
        final String displayName = join(args, 3);
        final PlotRoleService.RoleResult result = plugin.getPlotRoleService().renameRole(plot, roleId, displayName);
        sendRoleResult(player, result, roleId, displayName);
        return true;
    }

    private boolean deleteRole(final Player player, final Plot plot, final String[] args) {
        if (args.length < 3) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final String roleId = PlotRoleService.normalizeRoleId(args[2]);
        final PlotRoleService.RoleResult result = plugin.getPlotRoleService().deleteRole(plot, roleId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            plugin.getLanguageManager().send(player, "role-deleted", Map.of("role", roleId));
            return true;
        }
        sendRoleResult(player, result, roleId, roleId);
        return true;
    }

    private boolean setRolePermission(final Player player, final Plot plot, final String[] args) {
        if (args.length < 5) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final String roleId = PlotRoleService.normalizeRoleId(args[2]);
        final Optional<PlotRolePermission> permission = PlotRolePermission.fromKey(args[3]);
        if (permission.isEmpty()) {
            plugin.getLanguageManager().send(player, "role-permission-unknown", Map.of(
                    "permission", args[3],
                    "permissions", String.join(", ", PlotRolePermission.keys())
            ));
            return true;
        }

        final boolean enabled = parseEnabled(args[4]);
        final PlotRoleService.RoleResult result = plugin.getPlotRoleService().setPermission(plot, roleId, permission.get(), enabled);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            plugin.getLanguageManager().send(player, "role-permission-set", Map.of(
                    "role", roleId,
                    "permission", permission.get().displayName(),
                    "state", enabled ? "aktiv" : "inaktiv"
            ));
            return true;
        }
        sendRoleResult(player, result, roleId, roleId);
        return true;
    }

    private boolean assignRole(final Player player, final Plot plot, final String[] args) {
        if (args.length < 4) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final OfflinePlayer target = plugin.getPlotRoleService().getOfflinePlayer(args[2]);
        final UUID targetId = target.getUniqueId();
        final String roleId = PlotRoleService.normalizeRoleId(args[3]);
        final PlotRoleService.RoleResult result = plugin.getPlotRoleService().assignRole(plot, targetId, roleId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            plugin.getLanguageManager().send(player, "role-assigned", Map.of(
                    "player", target.getName() == null ? args[2] : target.getName(),
                    "role", roleId
            ));
            return true;
        }
        sendRoleResult(player, result, roleId, roleId);
        return true;
    }

    private boolean unassignRole(final Player player, final Plot plot, final String[] args) {
        if (args.length < 3) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final OfflinePlayer target = plugin.getPlotRoleService().getOfflinePlayer(args[2]);
        plugin.getPlotRoleService().unassignRole(plot, target.getUniqueId());
        plugin.getLanguageManager().send(player, "role-unassigned", Map.of(
                "player", target.getName() == null ? args[2] : target.getName()
        ));
        return true;
    }

    private boolean roleInfo(final Player player, final Plot plot, final String[] args) {
        if (args.length < 3) {
            plugin.getLanguageManager().send(player, "role-help");
            return true;
        }
        final String roleId = PlotRoleService.normalizeRoleId(args[2]);
        final Optional<PlotRole> role = plugin.getPlotRoleService().getRole(plot, roleId);
        if (role.isEmpty()) {
            plugin.getLanguageManager().send(player, "role-unknown", Map.of("role", roleId));
            return true;
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aRolle: &f" + role.get().displayName() + " &8(" + role.get().id() + ")"));
        player.sendMessage(TextUtil.component("&7Rechte: &f" + plugin.getPlotRoleService().permissionSummary(role.get())));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private void sendRoleList(final Player player, final Plot plot) {
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Rollen"));
        for (final PlotRole role : plugin.getPlotRoleService().getRoles(plot)) {
            player.sendMessage(TextUtil.component("&e" + role.id() + " &7- &f" + role.displayName()
                    + " &8| &7" + plugin.getPlotRoleService().permissionSummary(role)));
        }
        player.sendMessage(TextUtil.component("&8/pe role create <id> <Name>"));
        player.sendMessage(TextUtil.component("&8/pe role permission <id> <Recht> <on|off>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private boolean handleBackups(final Player player, final String[] args) {
        if (!plugin.getPlotBackupService().canManage(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("help") || args[1].equalsIgnoreCase("hilfe")) {
            sendBackupHelp(player);
            return true;
        }

        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list", "liste" -> listBackups(player, args);
            case "gui" -> openBackupGui(player, args);
            case "info" -> backupInfo(player, args);
            case "restore", "wiederherstellen" -> restoreBackup(player, args);
            default -> {
                sendBackupHelp(player);
                yield true;
            }
        };
    }

    private boolean listBackups(final Player player, final String[] args) {
        final List<PlotBackupEntry> backups;
        if (args.length >= 3) {
            final OfflinePlayer target = plugin.getPlotRoleService().getOfflinePlayer(args[2]);
            backups = plugin.getPlotBackupService().listBackups(target.getUniqueId());
        } else {
            backups = plugin.getPlotBackupService().listAllBackups();
        }

        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Backups &8(" + backups.size() + ")"));
        if (backups.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine Backups gefunden."));
        }
        for (final PlotBackupEntry backup : backups.stream().limit(10).toList()) {
            player.sendMessage(TextUtil.component("&e" + backup.id()
                    + " &7| &f" + backup.ownerName()
                    + " &7| &f" + backup.reason()
                    + " &7| &f" + backup.mergeSize()
                    + " &7| &f" + BACKUP_TIME_FORMAT.format(backup.createdAt())));
        }
        player.sendMessage(TextUtil.component("&8/pe backup info <id>"));
        player.sendMessage(TextUtil.component("&8/pe backup restore <id> &7- auf aktuellen Plot wiederherstellen"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean openBackupGui(final Player player, final String[] args) {
        final UUID ownerUuid;
        if (args.length >= 3) {
            ownerUuid = plugin.getPlotRoleService().getOfflinePlayer(args[2]).getUniqueId();
        } else {
            ownerUuid = null;
        }
        plugin.getGuiManager().openBackups(player, ownerUuid);
        return true;
    }

    private boolean backupInfo(final Player player, final String[] args) {
        if (args.length < 3) {
            sendBackupHelp(player);
            return true;
        }
        final Optional<PlotBackupEntry> backup = plugin.getPlotBackupService().getBackup(args[2]);
        if (backup.isEmpty()) {
            player.sendMessage(TextUtil.component("&cBackup &e" + args[2] + " &cwurde nicht gefunden."));
            return true;
        }
        final PlotBackupEntry entry = backup.get();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aBackup: &e" + entry.id()));
        player.sendMessage(TextUtil.component("&7Spieler: &f" + entry.ownerName() + " &8(" + entry.ownerUuid() + ")"));
        player.sendMessage(TextUtil.component("&7Löschdatum: &f" + BACKUP_TIME_FORMAT.format(entry.createdAt())));
        player.sendMessage(TextUtil.component("&7Grund: &f" + entry.reason()));
        player.sendMessage(TextUtil.component("&7Quelle: &f" + entry.sourceWorld() + " " + entry.sourcePlot()));
        player.sendMessage(TextUtil.component("&7Merge: &f" + entry.mergeSize() + " &8(" + entry.plotCount() + " Plots)"));
        player.sendMessage(TextUtil.component("&7Plot-IDs: &f" + String.join(", ", entry.sourcePlots())));
        player.sendMessage(TextUtil.component("&7Datei: &f" + entry.schematicFile().getName()));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean restoreBackup(final Player player, final String[] args) {
        if (args.length < 3) {
            sendBackupHelp(player);
            return true;
        }
        if (!plugin.getPlotBackupService().canRestore(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        if (!plugin.getPlotBackupService().restoreBackup(player, args[2])) {
            player.sendMessage(TextUtil.component("&cBackup &e" + args[2] + " &ckonnte nicht gestartet werden."));
        } else {
            player.sendMessage(TextUtil.component("&aWiederherstellung von &e" + args[2] + " &awurde gestartet."));
        }
        return true;
    }

    private void sendBackupHelp(final Player player) {
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Backup Befehle"));
        player.sendMessage(TextUtil.component("&e/pe backup list <Spieler> &7- Backups ansehen"));
        player.sendMessage(TextUtil.component("&e/pe backup gui <Spieler> &7- Backup-GUI öffnen"));
        player.sendMessage(TextUtil.component("&e/pe backup info <id> &7- Details anzeigen"));
        player.sendMessage(TextUtil.component("&e/pe backup restore <id> &7- auf aktuellen Plot wiederherstellen"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void sendRedstoneHelp(final Player player) {
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aRedstone-Schutz Befehle"));
        player.sendMessage(TextUtil.component("&e/pe redstone tp <Alarm-ID> &7- zum erkannten Block teleportieren"));
        player.sendMessage(TextUtil.component("&e/pe redstone enable <Alarm-ID> &7- Redstone fuer den Alarm-Plot aktivieren"));
        player.sendMessage(TextUtil.component("&e/pe redstone enable &7- Redstone auf deinem aktuellen Plot aktivieren"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void sendRoleResult(
            final Player player,
            final PlotRoleService.RoleResult result,
            final String roleId,
            final String displayName
    ) {
        switch (result) {
            case SUCCESS -> plugin.getLanguageManager().send(player, "role-updated", Map.of("role", roleId, "name", displayName));
            case INVALID_ID -> plugin.getLanguageManager().send(player, "role-invalid-id");
            case ALREADY_EXISTS -> plugin.getLanguageManager().send(player, "role-exists", Map.of("role", roleId));
            case NOT_FOUND -> plugin.getLanguageManager().send(player, "role-unknown", Map.of("role", roleId));
            case PROTECTED -> plugin.getLanguageManager().send(player, "role-delete-protected", Map.of("role", roleId));
            case PROTECTED_PERMISSION -> plugin.getLanguageManager().send(player, "role-owner-permissions-fixed");
            case TARGET_OWNER -> plugin.getLanguageManager().send(player, "role-target-owner");
            case ALREADY_AT_LIMIT -> plugin.getLanguageManager().send(player, "role-no-next-rank");
            case NO_PERMISSION -> plugin.getLanguageManager().send(player, "no-permission");
        }
    }

    private boolean parseEnabled(final String input) {
        final String normalized = input.toLowerCase(Locale.ROOT);
        return normalized.equals("on")
                || normalized.equals("true")
                || normalized.equals("ja")
                || normalized.equals("an")
                || normalized.equals("1");
    }

    private String join(final String[] args, final int start) {
        final StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private boolean reload(final CommandSender sender) {
        if (!sender.hasPermission("craftplayplotextras.admin")) {
            if (sender instanceof Player player) {
                plugin.getLanguageManager().send(player, "no-permission");
            } else {
                sender.sendMessage("No permission.");
            }
            return true;
        }
        plugin.reloadPlugin();
        if (sender instanceof Player player) {
            plugin.getLanguageManager().send(player, "reloaded");
        } else {
            sender.sendMessage("CraftplayPlotExtras reloaded.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "open", "language", "role", "roles", "rollen", "backup", "backups", "redstone", "rs", "audit", "inspect", "team"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return filter(plugin.getGuiManager().getGuiIds(), args[1]);
        }
        if (args.length >= 2 && isRoleCommand(args[0])) {
            return completeRoleCommand(sender, args);
        }
        if (args.length >= 2 && isBackupCommand(args[0])) {
            return completeBackupCommand(args);
        }
        if (args.length >= 2 && isRedstoneCommand(args[0])) {
            return completeRedstoneCommand(args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("audit")) {
            return filter(List.of("gui", "plot"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            final List<String> languages = new ArrayList<>();
            for (final LanguageDefinition language : plugin.getLanguageManager().getLanguages()) {
                languages.add(language.code());
            }
            return filter(languages, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeRoleCommand(final CommandSender sender, final String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "create", "rename", "delete", "permission", "assign", "unassign", "info"), args[1]);
        }
        if (args.length == 3 && List.of("rename", "delete", "permission", "info").contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(roleIds(sender), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("assign")) {
            return filter(roleIds(sender), args[3]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("permission")) {
            return filter(PlotRolePermission.keys(), args[3]);
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("permission")) {
            return filter(List.of("on", "off"), args[4]);
        }
        return Collections.emptyList();
    }

    private List<String> roleIds(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            return Collections.emptyList();
        }
        return plugin.getPlotRoleService().getRoles(plot).stream().map(PlotRole::id).toList();
    }

    private boolean isRoleCommand(final String command) {
        return command.equalsIgnoreCase("role")
                || command.equalsIgnoreCase("roles")
                || command.equalsIgnoreCase("rolle")
                || command.equalsIgnoreCase("rollen");
    }

    private boolean isBackupCommand(final String command) {
        return command.equalsIgnoreCase("backup")
                || command.equalsIgnoreCase("backups")
                || command.equalsIgnoreCase("sicherung")
                || command.equalsIgnoreCase("sicherungen");
    }

    private boolean isRedstoneCommand(final String command) {
        return command.equalsIgnoreCase("redstone")
                || command.equalsIgnoreCase("rs");
    }

    private List<String> completeBackupCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "gui", "info", "restore"), args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("restore"))) {
            return filter(plugin.getPlotBackupService().listAllBackups().stream().map(PlotBackupEntry::id).toList(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeRedstoneCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("tp", "enable"), args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("enable"))) {
            return filter(plugin.getRedstoneLagProtectionService().getAlertIds(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(final Iterable<String> values, final String input) {
        final String normalizedInput = input.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalizedInput)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
