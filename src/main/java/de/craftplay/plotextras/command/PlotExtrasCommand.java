package de.craftplay.plotextras.command;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.audit.AuditLogEntry;
import de.craftplay.plotextras.backup.PlotBackupEntry;
import de.craftplay.plotextras.competition.CompetitionEntry;
import de.craftplay.plotextras.language.LanguageDefinition;
import de.craftplay.plotextras.performance.PlotPerformanceSnapshot;
import de.craftplay.plotextras.plot.PlotRole;
import de.craftplay.plotextras.plot.PlotRolePermission;
import de.craftplay.plotextras.plot.PlotRoleService;
import de.craftplay.plotextras.report.PlotReportEntry;
import de.craftplay.plotextras.util.TextUtil;
import de.craftplay.plotextras.utility.PlotUtilityService;
import de.craftplay.plotextras.warp.PlotWarpEntry;
import com.plotsquared.core.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

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
            if (!feature(player, "redstone")) {
                return true;
            }
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
            if (!feature(player, "player.language")) {
                return true;
            }
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
            if (!feature(player, "player.roles")) {
                return true;
            }
            return handleRoles(player, args);
        }

        if (subCommand.equals("backup") || subCommand.equals("backups") || subCommand.equals("sicherung") || subCommand.equals("sicherungen")) {
            if (!feature(player, "team.backups")) {
                return true;
            }
            return handleBackups(player, args);
        }

        if (subCommand.equals("audit") || subCommand.equals("log") || subCommand.equals("logs")) {
            if (!feature(player, "team.audit-log")) {
                return true;
            }
            return handleAudit(player, args);
        }

        if (subCommand.equals("warp") || subCommand.equals("warps")) {
            if (!feature(player, "player.plot-warps")) {
                return true;
            }
            return handleWarps(player, args);
        }

        if (subCommand.equals("report") || subCommand.equals("melden") || subCommand.equals("meldung")) {
            if (!feature(player, "player.reports")) {
                return true;
            }
            return createReport(player, args);
        }

        if (subCommand.equals("reports") || subCommand.equals("meldungen")) {
            if (!feature(player, "team.reports")) {
                return true;
            }
            return handleReports(player, args);
        }

        if (subCommand.equals("moderation") || subCommand.equals("moderate") || subCommand.equals("mod")) {
            if (!feature(player, "team.moderation")) {
                return true;
            }
            return handleModeration(player, args);
        }

        if (subCommand.equals("performance") || subCommand.equals("perf") || subCommand.equals("lag")) {
            if (!feature(player, "team.performance")) {
                return true;
            }
            return handlePerformance(player);
        }

        if (subCommand.equals("contest") || subCommand.equals("competition") || subCommand.equals("wettbewerb")) {
            if (!feature(player, "player.competitions")) {
                return true;
            }
            return handleCompetition(player, args);
        }

        if (subCommand.equals("validate") || subCommand.equals("configcheck")) {
            if (!feature(player, "team.config-validator")) {
                return true;
            }
            return handleValidate(player);
        }

        if (subCommand.equals("inspect") || subCommand.equals("inspector") || subCommand.equals("team")) {
            if (!feature(player, "team.inspector")) {
                return true;
            }
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

        if (subCommand.equals("dashboard") || subCommand.equals("info")) {
            if (!feature(player, "player.dashboard")) {
                return true;
            }
            plugin.getGuiManager().open(player, "plot-dashboard", 0);
            return true;
        }

        if (subCommand.equals("tools") || subCommand.equals("werkzeuge")) {
            if (!feature(player, "player.tools")) {
                return true;
            }
            plugin.getGuiManager().open(player, "plot-tools", 0);
            return true;
        }

        if (subCommand.equals("teamtools") || subCommand.equals("teamwerkzeuge")) {
            if (!feature(player, "team.tools")) {
                return true;
            }
            if (!player.hasPermission("craftplayplotextras.teamtools") && !player.hasPermission("craftplayplotextras.admin")) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getGuiManager().open(player, "team-tools", 0);
            return true;
        }

        if (subCommand.equals("assistant") || subCommand.equals("assistent")) {
            if (!feature(player, "player.assistant")) {
                return true;
            }
            return handleAssistant(player);
        }

        if (subCommand.equals("profile") || subCommand.equals("profil")) {
            if (!feature(player, "player.plot-profile")) {
                return true;
            }
            return handleProfile(player, args);
        }

        if (subCommand.equals("guestbook") || subCommand.equals("gaestebuch") || subCommand.equals("gästebuch")) {
            if (!feature(player, "player.guestbook")) {
                return true;
            }
            return handleGuestbook(player, args);
        }

        if (subCommand.equals("request") || subCommand.equals("anfrage")) {
            if (!feature(player, "player.requests")) {
                return true;
            }
            return handlePlayerRequest(player, args);
        }

        if (subCommand.equals("requests") || subCommand.equals("anfragen")) {
            if (!feature(player, "team.requests")) {
                return true;
            }
            return handleTeamRequests(player, args);
        }

        if (subCommand.equals("search") || subCommand.equals("suche")) {
            if (!feature(player, "player.plot-search")) {
                return true;
            }
            return handleSearch(player, args);
        }

        if (subCommand.equals("favorite") || subCommand.equals("favorit")) {
            if (!feature(player, "player.plot-favorites")) {
                return true;
            }
            return handleFavorite(player, args);
        }

        if (subCommand.equals("cleanup") || subCommand.equals("aufräumen") || subCommand.equals("aufraeumen")) {
            if (!feature(player, "player.cleanup")) {
                return true;
            }
            return handlePlayerCleanup(player, args);
        }

        if (subCommand.equals("selfcheck") || subCommand.equals("check")) {
            if (!feature(player, "player.assistant")) {
                return true;
            }
            return handleSelfCheck(player);
        }

        if (subCommand.equals("stats") || subCommand.equals("statistik")) {
            if (!feature(player, "team.statistics")) {
                return true;
            }
            return handleStats(player);
        }

        if (subCommand.equals("permcheck") || subCommand.equals("rechtecheck")) {
            if (!feature(player, "team.permission-checker")) {
                return true;
            }
            return handlePermCheck(player, args);
        }

        if (subCommand.equals("buildtask") || subCommand.equals("bauaufgabe")) {
            if (!feature(player, "team.builder.tasks")) {
                return true;
            }
            return handleBuildTask(player, args);
        }

        if (subCommand.equals("buildermode") || subCommand.equals("baumodus")) {
            if (!feature(player, "team.builder.mode")) {
                return true;
            }
            return handleBuilderMode(player, args);
        }

        player.sendMessage(TextUtil.component(plugin.getLanguageManager().getRawMessage(player, "help")));
        return true;
    }

    private boolean feature(final Player player, final String feature) {
        if (plugin.getFeatureToggleService().isEnabled(feature)) {
            return true;
        }
        plugin.getLanguageManager().send(player, "feature-disabled", Map.of("feature", feature));
        return false;
    }

    private boolean handleWarps(final Player player, final String[] args) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("gui")) {
            plugin.getGuiManager().open(player, "plot-warps", 0);
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        if ((action.equals("set") || action.equals("setzen")) && args.length >= 3) {
            if (!feature(player, "player.plot-warps.set")) {
                return true;
            }
            if (!plugin.getPlotService().canModifySetting(player, plot, "home")) {
                plugin.getLanguageManager().send(player, "not-owner");
                return true;
            }
            final String warp = args[2];
            if (plugin.getPlotWarpService().setWarp(plot, warp, player.getLocation())) {
                plugin.getAuditLogService().log(player, plot, "Plot-Warp gesetzt", warp);
                player.sendMessage(TextUtil.component("&aWarp &e" + warp + " &awurde gesetzt."));
            } else {
                player.sendMessage(TextUtil.component("&cWarp &e" + warp + " &ckonnte nicht gesetzt werden."));
            }
            return true;
        }
        if ((action.equals("delete") || action.equals("del") || action.equals("löschen") || action.equals("loeschen")) && args.length >= 3) {
            if (!feature(player, "player.plot-warps.delete")) {
                return true;
            }
            if (!plugin.getPlotService().canModifySetting(player, plot, "home")) {
                plugin.getLanguageManager().send(player, "not-owner");
                return true;
            }
            final String warp = args[2];
            if (plugin.getPlotWarpService().deleteWarp(plot, warp)) {
                plugin.getAuditLogService().log(player, plot, "Plot-Warp gelöscht", warp);
                player.sendMessage(TextUtil.component("&aWarp &e" + warp + " &awurde gelöscht."));
            } else {
                player.sendMessage(TextUtil.component("&cWarp &e" + warp + " &cwurde nicht gefunden."));
            }
            return true;
        }
        if ((action.equals("tp") || action.equals("teleport")) && args.length >= 3) {
            if (!feature(player, "player.plot-warps.teleport")) {
                return true;
            }
            final String warp = args[2];
            if (plugin.getPlotWarpService().teleport(player, plot, warp)) {
                player.sendMessage(TextUtil.component("&aTeleportiere zu Warp &e" + warp + "&a."));
            } else {
                player.sendMessage(TextUtil.component("&cWarp &e" + warp + " &cwurde nicht gefunden."));
            }
            return true;
        }

        final List<PlotWarpEntry> warps = plugin.getPlotWarpService().listWarps(plot);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Warps &8(" + warps.size() + ")"));
        if (warps.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine Warps vorhanden."));
        }
        for (final PlotWarpEntry warp : warps) {
            player.sendMessage(TextUtil.component("&e" + warp.id() + " &7- &f" + warp.displayName()));
        }
        player.sendMessage(TextUtil.component("&8/pe warp set <Name>"));
        player.sendMessage(TextUtil.component("&8/pe warp tp <Name>"));
        player.sendMessage(TextUtil.component("&8/pe warp delete <Name>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean createReport(final Player player, final String[] args) {
        if (!plugin.getPlotReportService().canCreate(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        final String reason = args.length >= 2 ? join(args, 1) : "Keine Begründung angegeben.";
        final PlotReportEntry report = plugin.getPlotReportService().create(player, plot, reason);
        if (report == null) {
            player.sendMessage(TextUtil.component("&cMeldung konnte nicht erstellt werden."));
            return true;
        }
        plugin.getAuditLogService().log(player, plot, "Plot gemeldet", report.id() + ": " + reason);
        player.sendMessage(TextUtil.component("&aMeldung &e" + report.id() + " &awurde an das Team gesendet."));
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlotReportService().canView(online)) {
                online.sendMessage(TextUtil.component("&cNeue Plot-Meldung &e" + report.id()
                        + " &7von &f" + player.getName()
                        + " &7auf &f" + report.plotKey()
                        + " &8- &f/pe reports"));
            }
        }
        return true;
    }

    private boolean handleReports(final Player player, final String[] args) {
        if (!plugin.getPlotReportService().canView(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("close") || action.equals("done") || action.equals("erledigt")) {
            if (args.length < 3 || !plugin.getPlotReportService().canClose(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            final String note = args.length >= 4 ? join(args, 3) : "Erledigt.";
            if (plugin.getPlotReportService().close(player, args[2], note)) {
                player.sendMessage(TextUtil.component("&aMeldung &e" + args[2] + " &awurde geschlossen."));
            } else {
                player.sendMessage(TextUtil.component("&cMeldung &e" + args[2] + " &cwurde nicht gefunden."));
            }
            return true;
        }

        final List<PlotReportEntry> reports = action.equals("all") ? plugin.getPlotReportService().listAll() : plugin.getPlotReportService().listOpen();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Meldungen &8(" + reports.size() + ")"));
        if (reports.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine Meldungen gefunden."));
        }
        for (final PlotReportEntry report : reports.stream().limit(10).toList()) {
            player.sendMessage(TextUtil.component("&e" + report.id()
                    + " &7| &f" + report.plotKey()
                    + " &7| &f" + report.reporterName()
                    + " &7| &c" + report.status()
                    + " &7| &8" + report.reason()));
        }
        player.sendMessage(TextUtil.component("&8/pe reports close <id> <Notiz>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleModeration(final Player player, final String[] args) {
        if (!plugin.getPlotModerationService().canModerate(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        if (action.equals("list") || action.equals("liste")) {
            player.sendMessage(TextUtil.component("&8&m----------------"));
            player.sendMessage(TextUtil.component("&aGesperrte Plots"));
            for (final String line : plugin.getPlotModerationService().listFrozen()) {
                player.sendMessage(TextUtil.component("&e" + line));
            }
            player.sendMessage(TextUtil.component("&8&m----------------"));
            return true;
        }
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        if (action.equals("freeze") || action.equals("sperren")) {
            final String reason = args.length >= 3 ? join(args, 2) : "Teamprüfung";
            if (plugin.getPlotModerationService().freeze(player, plot, reason)) {
                plugin.getAuditLogService().log(player, plot, "Plot eingefroren", reason);
                player.sendMessage(TextUtil.component("&aDer aktuelle Plot wurde eingefroren."));
            } else {
                player.sendMessage(TextUtil.component("&cDer Plot konnte nicht eingefroren werden."));
            }
            return true;
        }
        if (action.equals("unfreeze") || action.equals("freigeben")) {
            if (plugin.getPlotModerationService().unfreeze(player, plot)) {
                plugin.getAuditLogService().log(player, plot, "Plot-Freeze aufgehoben", "-");
                player.sendMessage(TextUtil.component("&aDer aktuelle Plot wurde freigegeben."));
            } else {
                player.sendMessage(TextUtil.component("&cDer Plot war nicht eingefroren."));
            }
            return true;
        }
        if (action.equals("cleanup") || action.equals("clean")) {
            final String mode = args.length >= 3 ? args[2] : "drops";
            final int removed = plugin.getPlotModerationService().cleanup(player, plot, mode);
            if (removed >= 0) {
                plugin.getAuditLogService().log(player, plot, "Plot-Cleanup", mode + ": " + removed);
                player.sendMessage(TextUtil.component("&aEntfernt: &e" + removed + " &7(" + mode + ")"));
            } else {
                player.sendMessage(TextUtil.component("&cCleanup konnte nicht ausgeführt werden."));
            }
            return true;
        }
        player.sendMessage(TextUtil.component("&e/pe mod freeze <Grund>"));
        player.sendMessage(TextUtil.component("&e/pe mod unfreeze"));
        player.sendMessage(TextUtil.component("&e/pe mod cleanup <drops|projectiles|monsters|animals|vehicles|all>"));
        player.sendMessage(TextUtil.component("&e/pe mod list"));
        return true;
    }

    private boolean handlePerformance(final Player player) {
        if (!plugin.getPlotPerformanceService().canView(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        final PlotPerformanceSnapshot snapshot = plugin.getPlotPerformanceService().snapshot(plot);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPerformance: &f" + snapshot.plotKey()));
        player.sendMessage(TextUtil.component("&7Entities gesamt: &f" + snapshot.totalEntities()));
        snapshot.entityCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> player.sendMessage(TextUtil.component("&e" + entry.getKey() + " &7- &f" + entry.getValue())));
        for (final String warning : snapshot.warnings()) {
            player.sendMessage(TextUtil.component("&6" + warning));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleCompetition(final Player player, final String[] args) {
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("join") || action.equals("teilnehmen")) {
            if (!plugin.getCompetitionService().canJoin(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            final Plot plot = plugin.getPlotService().getCurrentPlot(player);
            if (plot == null) {
                plugin.getLanguageManager().send(player, "no-plot");
                return true;
            }
            final String competition = args.length >= 3 ? args[2] : "default";
            final String note = args.length >= 4 ? join(args, 3) : "-";
            final CompetitionEntry entry = plugin.getCompetitionService().join(player, plot, competition, note);
            if (entry == null) {
                player.sendMessage(TextUtil.component("&cTeilnahme konnte nicht gespeichert werden."));
            } else {
                plugin.getAuditLogService().log(player, plot, "Wettbewerb angemeldet", entry.competition());
                player.sendMessage(TextUtil.component("&aPlot wurde für Wettbewerb &e" + entry.competition() + " &aangemeldet."));
            }
            return true;
        }
        if (action.equals("score") || action.equals("bewerten")) {
            if (args.length < 4 || !plugin.getCompetitionService().canJudge(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            final int score;
            try {
                score = Integer.parseInt(args[3]);
            } catch (final NumberFormatException exception) {
                player.sendMessage(TextUtil.component("&cBitte gib eine Zahl zwischen 0 und 100 an."));
                return true;
            }
            final String note = args.length >= 5 ? join(args, 4) : "-";
            if (plugin.getCompetitionService().score(player, args[2], score, note)) {
                player.sendMessage(TextUtil.component("&aBewertung gespeichert."));
            } else {
                player.sendMessage(TextUtil.component("&cEintrag wurde nicht gefunden."));
            }
            return true;
        }
        final String competition = args.length >= 3 ? args[2] : "";
        final List<CompetitionEntry> entries = plugin.getCompetitionService().list(competition);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aWettbewerbe &8(" + entries.size() + ")"));
        for (final CompetitionEntry entry : entries.stream().limit(10).toList()) {
            player.sendMessage(TextUtil.component("&e" + entry.id()
                    + " &7| &f" + entry.ownerName()
                    + " &7| &f" + entry.plotKey()
                    + " &7| &a" + entry.score()));
        }
        player.sendMessage(TextUtil.component("&8/pe contest join <Name> <Notiz>"));
        player.sendMessage(TextUtil.component("&8/pe contest score <id> <0-100> <Notiz>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleValidate(final Player player) {
        if (!plugin.getConfigValidationService().canValidate(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final List<String> issues = plugin.getConfigValidationService().validate();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aConfig-Check &8(" + issues.size() + " Hinweise)"));
        if (issues.isEmpty()) {
            player.sendMessage(TextUtil.component("&aKeine Fehler gefunden."));
        }
        for (final String issue : issues.stream().limit(20).toList()) {
            player.sendMessage(TextUtil.component("&c" + issue));
        }
        if (issues.size() > 20) {
            player.sendMessage(TextUtil.component("&7Weitere Hinweise: &f" + (issues.size() - 20)));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleAssistant(final Player player) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Assistent"));
        player.sendMessage(TextUtil.component("&7Plots: &f" + plugin.getPlotService().getPlotPlaceholders(player).get("plot_count")
                + "&7/&f" + plugin.getPlotService().getPlotLimit(player)));
        if (plot == null) {
            player.sendMessage(TextUtil.component("&cDu stehst auf keinem Plot."));
            player.sendMessage(TextUtil.component("&8Tipp: &f/plot auto &8oder das PlotSquared-Claim-Menue nutzen."));
        } else {
            final Map<String, String> meta = plugin.getPlotUtilityService().placeholders(plot);
            player.sendMessage(TextUtil.component("&7Plot: &f" + meta.getOrDefault("plot_access_mode", "normal")
                    + " &8| &7Kategorie: &f" + meta.getOrDefault("plot_category", "-")));
            player.sendMessage(TextUtil.component("&7Beschreibung: &f" + meta.getOrDefault("plot_description", "-")));
            player.sendMessage(TextUtil.component("&7Tags: &f" + meta.getOrDefault("plot_tags", "-")));
            player.sendMessage(TextUtil.component("&8/pe profile description <Text> &7- Beschreibung setzen"));
            player.sendMessage(TextUtil.component("&8/pe profile tags shop,farm,deko &7- Tags setzen"));
            player.sendMessage(TextUtil.component("&8/pe selfcheck &7- Entities und Warnungen anzeigen"));
        }
        player.sendMessage(TextUtil.component("&8/pe tools &7- Spieler-Werkzeuge öffnen"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleProfile(final Player player, final String[] args) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        final PlotUtilityService utility = plugin.getPlotUtilityService();
        if (args.length < 2 || args[1].equalsIgnoreCase("show")) {
            final Map<String, String> meta = utility.placeholders(plot);
            player.sendMessage(TextUtil.component("&8&m----------------"));
            player.sendMessage(TextUtil.component("&aPlotprofil"));
            player.sendMessage(TextUtil.component("&7Beschreibung: &f" + meta.getOrDefault("plot_description", "-")));
            player.sendMessage(TextUtil.component("&7Kategorie: &f" + meta.getOrDefault("plot_category", "-")));
            player.sendMessage(TextUtil.component("&7Tags: &f" + meta.getOrDefault("plot_tags", "-")));
            player.sendMessage(TextUtil.component("&7Besuchsmodus: &f" + meta.getOrDefault("plot_access_mode", "normal")));
            player.sendMessage(TextUtil.component("&8/pe profile description <Text>"));
            player.sendMessage(TextUtil.component("&8/pe profile category <Kategorie>"));
            player.sendMessage(TextUtil.component("&8/pe profile tags tag1,tag2"));
            player.sendMessage(TextUtil.component("&8/pe profile access <normal|public|private|members|locked>"));
            player.sendMessage(TextUtil.component("&8&m----------------"));
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        final boolean changed = switch (action) {
            case "description", "beschreibung" -> args.length >= 3 && utility.setDescription(player, plot, join(args, 2));
            case "category", "kategorie" -> args.length >= 3 && utility.setCategory(player, plot, join(args, 2));
            case "tags" -> args.length >= 3 && utility.setTags(player, plot, join(args, 2));
            case "access", "visitmode", "besuchsmodus" -> args.length >= 3 && utility.setAccessMode(player, plot, args[2]);
            case "lockmessage", "sperrnachricht" -> args.length >= 3 && utility.setLockedMessage(player, plot, join(args, 2));
            default -> false;
        };
        if (!changed) {
            player.sendMessage(TextUtil.component("&cProfil konnte nicht geändert werden. Prüfe Rechte und Eingabe."));
            return true;
        }
        plugin.getAuditLogService().log(player, plot, "Plotprofil geändert", action);
        player.sendMessage(TextUtil.component("&aPlotprofil wurde gespeichert."));
        return true;
    }

    private boolean handleGuestbook(final Player player, final String[] args) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("sign") || args[1].equalsIgnoreCase("write") || args[1].equalsIgnoreCase("schreiben"))) {
            if (args.length < 3) {
                player.sendMessage(TextUtil.component("&cNutze: &e/pe guestbook sign <Nachricht>"));
                return true;
            }
            final PlotUtilityService.GuestbookEntry entry = plugin.getPlotUtilityService().signGuestbook(player, plot, join(args, 2));
            player.sendMessage(TextUtil.component(entry == null ? "&cEintrag konnte nicht gespeichert werden." : "&aGästebuch-Eintrag gespeichert."));
            return true;
        }
        final List<PlotUtilityService.GuestbookEntry> entries = plugin.getPlotUtilityService().guestbook(plot, 8);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aGästebuch &8(" + entries.size() + ")"));
        if (entries.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Noch keine Einträge."));
        }
        for (final PlotUtilityService.GuestbookEntry entry : entries) {
            player.sendMessage(TextUtil.component("&e" + entry.playerName() + " &7- &f" + entry.message()));
        }
        player.sendMessage(TextUtil.component("&8/pe guestbook sign <Nachricht>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handlePlayerRequest(final Player player, final String[] args) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            return listOwnRequests(player);
        }
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(TextUtil.component("&e/pe request <trust|move|backup|restore|design|support> <Notiz>"));
            player.sendMessage(TextUtil.component("&e/pe request list"));
            return true;
        }
        final String note = args.length >= 3 ? join(args, 2) : "-";
        final PlotUtilityService.UtilityRequestEntry entry = plugin.getPlotUtilityService().createRequest(player, plot, args[1], note);
        if (entry == null) {
            player.sendMessage(TextUtil.component("&cAnfrage konnte nicht erstellt werden."));
            return true;
        }
        player.sendMessage(TextUtil.component("&aAnfrage &e" + entry.id() + " &awurde erstellt."));
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getPlotUtilityService().canHandleRequests(online)) {
                online.sendMessage(TextUtil.component("&eNeue Plot-Anfrage &f" + entry.id()
                        + " &7von &f" + player.getName()
                        + " &7auf &f" + entry.plotKey()
                        + " &8- &f/pe requests"));
            }
        }
        return true;
    }

    private boolean listOwnRequests(final Player player) {
        final List<PlotUtilityService.UtilityRequestEntry> entries = plugin.getPlotUtilityService().listOwnRequests(player);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aDeine Anfragen &8(" + entries.size() + ")"));
        for (final PlotUtilityService.UtilityRequestEntry entry : entries.stream().limit(10).toList()) {
            player.sendMessage(TextUtil.component("&e" + entry.id() + " &7| &f" + entry.type()
                    + " &7| &f" + entry.status() + " &7| &8" + entry.note()));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleTeamRequests(final Player player, final String[] args) {
        if (!plugin.getPlotUtilityService().canHandleRequests(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("close") || action.equals("done")) {
            if (args.length < 3) {
                player.sendMessage(TextUtil.component("&cNutze: &e/pe requests close <id> <Antwort>"));
                return true;
            }
            final String response = args.length >= 4 ? join(args, 3) : "Erledigt.";
            player.sendMessage(TextUtil.component(plugin.getPlotUtilityService().closeRequest(player, args[2], response)
                    ? "&aAnfrage geschlossen."
                    : "&cAnfrage konnte nicht geschlossen werden."));
            return true;
        }
        if (action.equals("accepttrust") || action.equals("trust")) {
            if (args.length < 3) {
                player.sendMessage(TextUtil.component("&cNutze: &e/pe requests accepttrust <id>"));
                return true;
            }
            player.sendMessage(TextUtil.component(plugin.getPlotUtilityService().acceptTrustRequest(player, args[2])
                    ? "&aTrust-Anfrage angenommen."
                    : "&cTrust-Anfrage konnte nicht angenommen werden. Stehe auf dem passenden Plot."));
            return true;
        }
        final List<PlotUtilityService.UtilityRequestEntry> entries = action.equals("all")
                ? plugin.getPlotUtilityService().listRequests()
                : plugin.getPlotUtilityService().listOpenRequests();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Anfragen &8(" + entries.size() + ")"));
        for (final PlotUtilityService.UtilityRequestEntry entry : entries.stream().limit(12).toList()) {
            player.sendMessage(TextUtil.component("&e" + entry.id()
                    + " &7| &f" + entry.type()
                    + " &7| &f" + entry.requesterName()
                    + " &7| &f" + entry.plotKey()
                    + " &7| &8" + entry.note()));
        }
        player.sendMessage(TextUtil.component("&8/pe requests accepttrust <id>"));
        player.sendMessage(TextUtil.component("&8/pe requests close <id> <Antwort>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleSearch(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(TextUtil.component("&cNutze: &e/pe search <Tag|Kategorie|Text>"));
            return true;
        }
        final List<PlotUtilityService.PlotProfileEntry> results = plugin.getPlotUtilityService().searchProfiles(join(args, 1));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlotsuche &8(" + results.size() + ")"));
        if (results.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine passenden Plotprofile gefunden."));
        }
        for (final PlotUtilityService.PlotProfileEntry entry : results.stream().limit(10).toList()) {
            player.sendMessage(TextUtil.component("&e" + entry.plotKey()
                    + " &7| &f" + entry.ownerName()
                    + " &7| &a" + entry.category()
                    + " &7| &8" + String.join(", ", entry.tags())));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleFavorite(final Player player, final String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            final List<String> favorites = plugin.getPlotUtilityService().favorites(player);
            player.sendMessage(TextUtil.component("&8&m----------------"));
            player.sendMessage(TextUtil.component("&aFavoriten &8(" + favorites.size() + ")"));
            favorites.stream().limit(15).forEach(key -> player.sendMessage(TextUtil.component("&e" + key)));
            player.sendMessage(TextUtil.component("&8&m----------------"));
            return true;
        }
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        final boolean added = plugin.getPlotUtilityService().toggleFavorite(player, plot);
        player.sendMessage(TextUtil.component(added ? "&aPlot wurde zu deinen Favoriten hinzugefügt." : "&7Plot wurde aus deinen Favoriten entfernt."));
        return true;
    }

    private boolean handlePlayerCleanup(final Player player, final String[] args) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        final String mode = args.length >= 2 ? args[1] : "drops";
        final int removed = plugin.getPlotUtilityService().cleanupOwnedPlot(player, plot, mode);
        if (removed < 0) {
            player.sendMessage(TextUtil.component("&cCleanup konnte nicht ausgeführt werden. Nur Plotbesitzer können das nutzen."));
            return true;
        }
        plugin.getAuditLogService().log(player, plot, "Spieler-Cleanup", mode + ": " + removed);
        player.sendMessage(TextUtil.component("&aEntfernt: &e" + removed + " &7(" + mode + ")"));
        return true;
    }

    private boolean handleSelfCheck(final Player player) {
        final Plot plot = plugin.getPlotService().getCurrentPlot(player);
        if (plot == null) {
            plugin.getLanguageManager().send(player, "no-plot");
            return true;
        }
        final PlotPerformanceSnapshot snapshot = plugin.getPlotPerformanceService().snapshot(plot);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Selbstcheck"));
        player.sendMessage(TextUtil.component("&7Entities gesamt: &f" + snapshot.totalEntities()));
        snapshot.entityCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .forEach(entry -> player.sendMessage(TextUtil.component("&e" + entry.getKey() + " &7- &f" + entry.getValue())));
        if (snapshot.warnings().isEmpty()) {
            player.sendMessage(TextUtil.component("&aKeine Performance-Warnungen gefunden."));
        }
        for (final String warning : snapshot.warnings()) {
            player.sendMessage(TextUtil.component("&6" + warning));
        }
        player.sendMessage(TextUtil.component("&8/pe cleanup drops &7- Drops aufräumen"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleStats(final Player player) {
        if (!player.hasPermission("craftplayplotextras.statistics") && !player.hasPermission("craftplayplotextras.admin")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final Map<String, Integer> stats = plugin.getPlotUtilityService().statistics();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlotExtras Statistik"));
        stats.forEach((key, value) -> player.sendMessage(TextUtil.component("&e" + key + " &7- &f" + value)));
        player.sendMessage(TextUtil.component("&7Reports offen: &f" + plugin.getPlotReportService().listOpen().size()));
        player.sendMessage(TextUtil.component("&7Backups gesamt: &f" + plugin.getPlotBackupService().listAllBackups().size()));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handlePermCheck(final Player player, final String[] args) {
        if (!player.hasPermission("craftplayplotextras.permissioncheck") && !player.hasPermission("craftplayplotextras.admin")) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(TextUtil.component("&cNutze: &e/pe permcheck <Spieler>"));
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(TextUtil.component("&cDer Spieler muss online sein."));
            return true;
        }
        final List<String> permissions = target.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .filter(permission -> permission.startsWith("craftplayplotextras.") || permission.startsWith("plots.plot."))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aRechtecheck: &f" + target.getName()));
        player.sendMessage(TextUtil.component("&7Plotlimit: &f" + plugin.getPlotService().getPlotLimit(target)));
        permissions.stream().limit(30).forEach(permission -> player.sendMessage(TextUtil.component("&e" + permission)));
        if (permissions.size() > 30) {
            player.sendMessage(TextUtil.component("&7Weitere Rechte: &f" + (permissions.size() - 30)));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleBuildTask(final Player player, final String[] args) {
        if (!plugin.getPlotUtilityService().canManageBuildTasks(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("create") || action.equals("neu")) {
            final Plot plot = plugin.getPlotService().getCurrentPlot(player);
            if (plot == null) {
                plugin.getLanguageManager().send(player, "no-plot");
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(TextUtil.component("&cNutze: &e/pe buildtask create <Titel> | <Notiz>"));
                return true;
            }
            final String raw = join(args, 2);
            final String[] parts = raw.split("\\|", 2);
            final PlotUtilityService.BuildTaskEntry entry = plugin.getPlotUtilityService().createBuildTask(
                    player,
                    plot,
                    parts[0].trim(),
                    parts.length >= 2 ? parts[1].trim() : "-"
            );
            player.sendMessage(TextUtil.component(entry == null ? "&cBauaufgabe konnte nicht erstellt werden." : "&aBauaufgabe &e" + entry.id() + " &aerstellt."));
            return true;
        }
        if (action.equals("done") || action.equals("close")) {
            if (args.length < 3) {
                player.sendMessage(TextUtil.component("&cNutze: &e/pe buildtask done <id>"));
                return true;
            }
            player.sendMessage(TextUtil.component(plugin.getPlotUtilityService().completeBuildTask(player, args[2])
                    ? "&aBauaufgabe abgeschlossen."
                    : "&cBauaufgabe wurde nicht gefunden."));
            return true;
        }
        final boolean all = action.equals("all");
        final List<PlotUtilityService.BuildTaskEntry> entries = plugin.getPlotUtilityService().listBuildTasks(all);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aBauaufgaben &8(" + entries.size() + ")"));
        for (final PlotUtilityService.BuildTaskEntry entry : entries.stream().limit(12).toList()) {
            player.sendMessage(TextUtil.component("&e" + entry.id()
                    + " &7| &f" + entry.status()
                    + " &7| &f" + entry.plotKey()
                    + " &7| &a" + entry.title()));
        }
        player.sendMessage(TextUtil.component("&8/pe buildtask create <Titel> | <Notiz>"));
        player.sendMessage(TextUtil.component("&8/pe buildtask done <id>"));
        player.sendMessage(TextUtil.component("&8&m----------------"));
        return true;
    }

    private boolean handleBuilderMode(final Player player, final String[] args) {
        final boolean enabled = args.length < 2 || parseEnabled(args[1]);
        if (!plugin.getPlotUtilityService().setBuilderMode(player, enabled)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return true;
        }
        player.sendMessage(TextUtil.component(enabled ? "&aBuilder-Modus aktiviert." : "&7Builder-Modus deaktiviert."));
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
        if (action.equals("gui") || action.equals("alarme") || action.equals("alerts")) {
            if (!feature(player, "team.redstone-alerts")) {
                return true;
            }
            if (!plugin.getRedstoneLagProtectionService().canReceiveAlerts(player)) {
                plugin.getLanguageManager().send(player, "no-permission");
                return true;
            }
            plugin.getGuiManager().open(player, "redstone-alerts", 0);
            return true;
        }
        if (action.equals("tp") || action.equals("teleport")) {
            if (!feature(player, "team.redstone-alerts")) {
                return true;
            }
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
            if (!feature(player, "redstone.reactivate")) {
                return true;
            }
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
        player.sendMessage(TextUtil.component("&e/pe redstone gui &7- offene Redstone-Alarme anzeigen"));
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
            return filter(List.of("reload", "open", "language", "role", "roles", "rollen", "backup", "backups", "redstone", "rs", "audit", "inspect", "team", "dashboard", "info", "tools", "teamtools", "assistant", "profile", "guestbook", "request", "requests", "search", "favorite", "cleanup", "selfcheck", "stats", "permcheck", "buildtask", "buildermode", "warp", "warps", "report", "reports", "mod", "performance", "contest", "validate"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
            return filter(plugin.getFeatureToggleService().enabledGuiIds(plugin.getGuiManager().getGuiIds()), args[1]);
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
        if (args.length >= 2 && (args[0].equalsIgnoreCase("warp") || args[0].equalsIgnoreCase("warps"))) {
            return completeWarpCommand(sender, args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("reports") || args[0].equalsIgnoreCase("meldungen"))) {
            return completeReportsCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("mod") || args[0].equalsIgnoreCase("moderation") || args[0].equalsIgnoreCase("moderate"))) {
            return completeModerationCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("contest") || args[0].equalsIgnoreCase("competition") || args[0].equalsIgnoreCase("wettbewerb"))) {
            return completeCompetitionCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("profile") || args[0].equalsIgnoreCase("profil"))) {
            return completeProfileCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("guestbook") || args[0].equalsIgnoreCase("gaestebuch") || args[0].equalsIgnoreCase("gästebuch"))) {
            return completeGuestbookCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("anfrage"))) {
            return completeRequestCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("requests") || args[0].equalsIgnoreCase("anfragen"))) {
            return completeTeamRequestCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("cleanup") || args[0].equalsIgnoreCase("aufraeumen") || args[0].equalsIgnoreCase("aufräumen"))) {
            return completeCleanupCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("favorite") || args[0].equalsIgnoreCase("favorit"))) {
            return filter(List.of("list", "toggle"), args[1]);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("buildtask") || args[0].equalsIgnoreCase("bauaufgabe"))) {
            return completeBuildTaskCommand(args);
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("buildermode") || args[0].equalsIgnoreCase("baumodus"))) {
            return filter(List.of("on", "off"), args[1]);
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

    private List<String> completeReportsCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "all", "close"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("close")) {
            return filter(plugin.getPlotReportService().listOpen().stream().map(PlotReportEntry::id).toList(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeModerationCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("freeze", "unfreeze", "cleanup", "list"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("cleanup")) {
            return filter(List.of("drops", "projectiles", "monsters", "animals", "vehicles", "all"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeCompetitionCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("join", "list", "score"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("score")) {
            return filter(plugin.getCompetitionService().list("").stream().map(CompetitionEntry::id).toList(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeProfileCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("show", "description", "category", "tags", "access", "lockmessage"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("access")) {
            return filter(List.of("normal", "public", "private", "members", "friends", "locked"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeGuestbookCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "sign"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeRequestCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("trust", "move", "backup", "restore", "design", "support", "list"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeTeamRequestCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "all", "accepttrust", "close"), args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("accepttrust") || args[1].equalsIgnoreCase("close"))) {
            return filter(plugin.getPlotUtilityService().listOpenRequests().stream().map(PlotUtilityService.UtilityRequestEntry::id).toList(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeCleanupCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("drops", "projectiles", "monsters", "animals", "vehicles", "all"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeBuildTaskCommand(final String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "all", "create", "done"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("done")) {
            return filter(plugin.getPlotUtilityService().listBuildTasks(false).stream().map(PlotUtilityService.BuildTaskEntry::id).toList(), args[2]);
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
            return filter(List.of("tp", "enable", "gui"), args[1]);
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("enable"))) {
            return filter(plugin.getRedstoneLagProtectionService().getAlertIds(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> completeWarpCommand(final CommandSender sender, final String[] args) {
        if (args.length == 2) {
            return filter(List.of("gui", "list", "set", "tp", "delete"), args[1]);
        }
        if (args.length == 3 && sender instanceof Player player
                && (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("delete"))) {
            final Plot plot = plugin.getPlotService().getCurrentPlot(player);
            if (plot == null) {
                return Collections.emptyList();
            }
            return filter(plugin.getPlotWarpService().listWarps(plot).stream().map(PlotWarpEntry::id).toList(), args[2]);
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
