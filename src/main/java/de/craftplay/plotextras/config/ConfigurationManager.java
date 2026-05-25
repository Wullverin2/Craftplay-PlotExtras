package de.craftplay.plotextras.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public final class ConfigurationManager {

    private static final List<String> MANAGED_FILES = Arrays.asList(
            "config.yml",
            "limits.yml",
            "language/de.yml",
            "language/en.yml",
            "gui/de/main.yml",
            "gui/en/main.yml",
            "gui/de/bedrock.yml",
            "gui/en/bedrock.yml",
            "gui/de/myplots.yml",
            "gui/en/myplots.yml",
            "gui/de/bedrock-myplots.yml",
            "gui/en/bedrock-myplots.yml",
            "gui/de/create.yml",
            "gui/en/create.yml",
            "gui/de/members.yml",
            "gui/en/members.yml",
            "gui/de/search.yml",
            "gui/en/search.yml",
            "gui/de/community.yml",
            "gui/en/community.yml",
            "gui/de/plot-purchase.yml",
            "gui/en/plot-purchase.yml",
            "gui/de/deco.yml",
            "gui/en/deco.yml",
            "gui/de/extras.yml",
            "gui/en/extras.yml",
            "gui/de/reports.yml",
            "gui/en/reports.yml",
            "gui/de/history.yml",
            "gui/en/history.yml",
            "gui/de/danger.yml",
            "gui/en/danger.yml",
            "gui/de/help.yml",
            "gui/en/help.yml",
            "gui/de/flags.yml",
            "gui/en/flags.yml",
            "gui/de/settings.yml",
            "gui/en/settings.yml",
            "gui/de/team.yml",
            "gui/en/team.yml",
            "gui/de/future.yml",
            "gui/en/future.yml"
    );
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final JavaPlugin plugin;

    public ConfigurationManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void installOrUpdateDefaults() {
        for (final String resourcePath : MANAGED_FILES) {
            installOrUpdate(resourcePath);
        }
    }

    private void installOrUpdate(final String resourcePath) {
        if (plugin.getResource(resourcePath) == null) {
            plugin.getLogger().warning("Standarddatei fehlt im Plugin: " + resourcePath);
            return;
        }

        final File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.exists()) {
            plugin.saveResource(resourcePath, false);
            return;
        }

        final int defaultVersionHint = readResourceVersion(resourcePath, -1);
        final int existingVersionHint = readFileVersion(target, -1);
        final boolean needsGuiAnimationDefaults = isJavaInventoryGuiFile(resourcePath) && hasMissingGuiAnimation(target);
        if (defaultVersionHint > 0 && existingVersionHint >= defaultVersionHint && !needsGuiAnimationDefaults) {
            return;
        }

        final YamlConfiguration defaults = loadDefaults(resourcePath);
        final YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
        boolean changed = false;

        for (final String key : defaults.getKeys(true)) {
            if ("file-version".equals(key)) {
                continue;
            }
            if (existing.contains(key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                existing.createSection(key);
            } else {
                existing.set(key, defaults.get(key));
            }
            changed = true;
        }
        if (applyGuiAnimationDefaults(existing, resourcePath)) {
            changed = true;
        }

        final int defaultVersion = defaults.getInt("file-version", 1);
        final int existingVersion = existing.getInt("file-version", 0);
        if ("config.yml".equals(resourcePath) && existingVersion < 6) {
            final List<String> hiddenButtons = existing.getStringList("gui.hidden-main-buttons");
            if (hiddenButtons.removeIf(hiddenButton -> "help".equalsIgnoreCase(hiddenButton))) {
                existing.set("gui.hidden-main-buttons", hiddenButtons);
                changed = true;
            }
        }
        if ("config.yml".equals(resourcePath) && existingVersion < 19) {
            final String passiveWitherItem = existing.getString("passive-wither.egg.material", "");
            if ("WITHER_SKELETON_SKULL".equalsIgnoreCase(passiveWitherItem)) {
                existing.set("passive-wither.egg.material", "WITHER_SKELETON_SPAWN_EGG");
                changed = true;
            }
        }
        if ("config.yml".equals(resourcePath) && existingVersion < 23) {
            if (existing.contains("passive-wither.drops.nearest-inventory-radius")) {
                existing.set("passive-wither.drops.nearest-inventory-radius", null);
                changed = true;
            }
            final List<String> eggLore = existing.getStringList("passive-wither.egg.lore");
            final int nearestChestLine = eggLore.indexOf("&7Drops gehen in die nächste Truhe.");
            if (nearestChestLine >= 0) {
                eggLore.set(nearestChestLine, "&7Drops gehen in die verlinkte Zieltruhe.");
                existing.set("passive-wither.egg.lore", eggLore);
                changed = true;
            }
            final List<String> unlinkLore = existing.getStringList("passive-wither.menu.buttons.unlink.lore");
            if (unlinkLore.equals(Arrays.asList(
                    "&7Aktuell: &f{status}",
                    "&7Danach nutzt der Wither wieder",
                    "&7die globale oder nächste Truhe."))) {
                existing.set("passive-wither.menu.buttons.unlink.lore", Arrays.asList(
                        "&7Aktuell: &f{status}",
                        "&7Danach droppen Items wieder",
                        "&7normal am Abbauort."));
                changed = true;
            }
        }
        if ("config.yml".equals(resourcePath) && existingVersion < 24) {
            if (existing.contains("passive-wither.chest-permission")) {
                existing.set("passive-wither.chest-permission", null);
                changed = true;
            }
            final List<String> unlinkLore = existing.getStringList("passive-wither.menu.buttons.unlink.lore");
            if (unlinkLore.equals(Arrays.asList(
                    "&7Aktuell: &f{status}",
                    "&7Danach nutzt der Wither nur",
                    "&7noch die globale Zieltruhe."))
                    || unlinkLore.equals(Arrays.asList(
                    "&7Aktuell: &f{status}",
                    "&7Danach nutzt der Wither wieder",
                    "&7die globale oder nächste Truhe."))) {
                existing.set("passive-wither.menu.buttons.unlink.lore", Arrays.asList(
                        "&7Aktuell: &f{status}",
                        "&7Danach droppen Items wieder",
                        "&7normal am Abbauort."));
                changed = true;
            }
        }
        if (resourcePath.replace('\\', '/').equals("language/de.yml") && existingVersion < 22) {
            final String oldUsage = "&cBenutzung: /{label} egg [spieler] [anzahl], /{label} reload, /{label} chest <set|clear|info> oder /{label} sound <on|off|toggle>";
            if (oldUsage.equals(existing.getString("passive-wither-command-usage", ""))) {
                existing.set("passive-wither-command-usage",
                        "&cBenutzung: /{label} egg [spieler] [anzahl], /{label} reload oder /{label} sound <on|off|toggle>");
                changed = true;
            }
            if (removeGlobalPassiveWitherChestMessages(existing)) {
                changed = true;
            }
        }
        if (resourcePath.replace('\\', '/').equals("language/en.yml") && existingVersion < 22) {
            final String oldUsage = "&cUsage: /{label} egg [player] [amount], /{label} reload, /{label} chest <set|clear|info> or /{label} sound <on|off|toggle>";
            if (oldUsage.equals(existing.getString("passive-wither-command-usage", ""))) {
                existing.set("passive-wither-command-usage",
                        "&cUsage: /{label} egg [player] [amount], /{label} reload or /{label} sound <on|off|toggle>");
                changed = true;
            }
            if (removeGlobalPassiveWitherChestMessages(existing)) {
                changed = true;
            }
        }
        if (resourcePath.replace('\\', '/').endsWith("/extras.yml") && existingVersion < 4) {
            final String passiveWitherIcon = existing.getString("buttons.passive-wither.material", "");
            if ("WITHER_SKELETON_SKULL".equalsIgnoreCase(passiveWitherIcon)) {
                existing.set("buttons.passive-wither.material", "WITHER_SKELETON_SPAWN_EGG");
                changed = true;
            }
        }
        if (resourcePath.replace('\\', '/').endsWith("/extras.yml") && existingVersion < 6) {
            final List<String> lore = existing.getStringList("buttons.passive-wither.lore");
            final int germanLine = lore.indexOf("&7Drops gehen in eine Truhe.");
            final int englishLine = lore.indexOf("&7Drops go into a chest.");
            if (germanLine >= 0) {
                lore.set(germanLine, "&7Drops gehen in die verlinkte Truhe.");
                existing.set("buttons.passive-wither.lore", lore);
                changed = true;
            } else if (englishLine >= 0) {
                lore.set(englishLine, "&7Drops go into the linked chest.");
                existing.set("buttons.passive-wither.lore", lore);
                changed = true;
            }
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 10) {
            if (existing.contains("bedrock-form")) {
                existing.set("bedrock-form", null);
                changed = true;
            }
            final org.bukkit.configuration.ConfigurationSection buttons = existing.getConfigurationSection("buttons");
            if (buttons != null) {
                for (final String buttonId : buttons.getKeys(false)) {
                    final String bedrockLabelPath = "buttons." + buttonId + ".bedrock-label";
                    if (existing.contains(bedrockLabelPath)) {
                        existing.set(bedrockLabelPath, null);
                        changed = true;
                    }
                }
            }
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 11) {
            existing.set("buttons.my-plots.commands", Arrays.asList("open-menu:myplots"));
            existing.set("buttons.my-plots.close", false);
            existing.set("buttons.favorites.commands", Arrays.asList("open-menu:myplots:1:name:favorites"));
            existing.set("buttons.favorites.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/bedrock.yml") && existingVersion < 2) {
            existing.set("buttons.my-plots.commands", Arrays.asList("open-menu:myplots"));
            existing.set("buttons.my-plots.close", false);
            existing.set("buttons.favorites.commands", Arrays.asList("open-menu:myplots:1:name:favorites"));
            existing.set("buttons.favorites.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 12) {
            existing.set("buttons.search.commands", Arrays.asList("open-menu:search"));
            existing.set("buttons.search.close", false);
            existing.set("buttons.create.commands", Arrays.asList("open-menu:create:types"));
            existing.set("buttons.create.close", false);
            existing.set("buttons.community.commands", Arrays.asList("open-menu:community"));
            existing.set("buttons.community.close", false);
            existing.set("buttons.members.commands", Arrays.asList("open-menu:members:overview"));
            existing.set("buttons.members.close", false);
            existing.set("buttons.roles.commands", Arrays.asList("open-menu:members:roles"));
            existing.set("buttons.roles.close", false);
            existing.set("buttons.reports.commands", Arrays.asList("open-menu:reports"));
            existing.set("buttons.reports.close", false);
            existing.set("buttons.history.commands", Arrays.asList("open-menu:history"));
            existing.set("buttons.history.close", false);
            existing.set("buttons.help.commands", Arrays.asList("open-menu:help"));
            existing.set("buttons.help.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/bedrock.yml") && existingVersion < 3) {
            existing.set("buttons.search.commands", Arrays.asList("open-menu:search"));
            existing.set("buttons.search.close", false);
            existing.set("buttons.create.commands", Arrays.asList("open-menu:create:types"));
            existing.set("buttons.create.close", false);
            existing.set("buttons.community.commands", Arrays.asList("open-menu:community"));
            existing.set("buttons.community.close", false);
            existing.set("buttons.members.commands", Arrays.asList("open-menu:members:overview"));
            existing.set("buttons.members.close", false);
            existing.set("buttons.roles.commands", Arrays.asList("open-menu:members:roles"));
            existing.set("buttons.roles.close", false);
            existing.set("buttons.reports.commands", Arrays.asList("open-menu:reports"));
            existing.set("buttons.reports.close", false);
            existing.set("buttons.history.commands", Arrays.asList("open-menu:history"));
            existing.set("buttons.history.close", false);
            existing.set("buttons.help.commands", Arrays.asList("open-menu:help"));
            existing.set("buttons.help.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 13) {
            existing.set("buttons.future.commands", Arrays.asList("open-menu:future"));
            existing.set("buttons.future.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/bedrock.yml") && existingVersion < 4) {
            existing.set("buttons.future.commands", Arrays.asList("open-menu:future"));
            existing.set("buttons.future.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/future.yml") && existingVersion < 2) {
            existing.set("tabs.analytics.buttons.heatmaps.commands", Arrays.asList("future:heatmap"));
            existing.set("tabs.analytics.buttons.heatmaps.close", true);
            existing.set("tabs.analytics.buttons.visitor-statistics.commands", Arrays.asList("future:visitors"));
            existing.set("tabs.analytics.buttons.visitor-statistics.close", true);
            existing.set("tabs.analytics.buttons.ranking.commands", Arrays.asList("future:ranking"));
            existing.set("tabs.analytics.buttons.ranking.close", true);
            existing.set("tabs.analytics.buttons.ranking.permission", "craftplayplotextras.future.ranking");
            existing.set("tabs.analytics.buttons.ai-tagging.commands", Arrays.asList("future:ai-tag"));
            existing.set("tabs.analytics.buttons.ai-tagging.close", true);
            existing.set("tabs.analytics.buttons.redstone-analysis.commands", Arrays.asList("future:redstone"));
            existing.set("tabs.analytics.buttons.redstone-analysis.close", true);
            existing.set("tabs.analytics.buttons.redstone-analysis.permission", "craftplayplotextras.future.redstone");
            existing.set("tabs.content.buttons.gallery.commands", Arrays.asList("future:gallery:toggle"));
            existing.set("tabs.content.buttons.gallery.close", true);
            existing.set("tabs.content.buttons.gallery.permission", "craftplayplotextras.future.gallery");
            existing.set("tabs.content.buttons.templates.commands", Arrays.asList("future:template:list", "open-menu:create:templates"));
            existing.set("tabs.content.buttons.templates.close", false);
            existing.set("tabs.content.buttons.templates.permission", "craftplayplotextras.future.templates");
            existing.set("tabs.content.buttons.auto-backups.commands", Arrays.asList("future:auto-backup"));
            existing.set("tabs.content.buttons.auto-backups.close", true);
            existing.set("tabs.content.buttons.auto-backups.permission", "craftplayplotextras.future.backups");
            existing.set("tabs.content.buttons.undo.commands", Arrays.asList("future:undo"));
            existing.set("tabs.content.buttons.undo.close", true);
            existing.set("tabs.content.buttons.undo.permission", "craftplayplotextras.future.undo");
            existing.set("tabs.economy.buttons.market.commands", Arrays.asList("chat-input:chat-future-market-value:future:market:sale:{input}"));
            existing.set("tabs.economy.buttons.market.close", true);
            existing.set("tabs.economy.buttons.trading.commands", Arrays.asList("chat-input:chat-future-market-value:future:market:trade:{input}"));
            existing.set("tabs.economy.buttons.trading.close", true);
            existing.set("tabs.economy.buttons.auctions.commands", Arrays.asList("chat-input:chat-future-market-value:future:market:auction:{input}"));
            existing.set("tabs.economy.buttons.auctions.close", true);
            existing.set("tabs.economy.buttons.npcs.commands", Arrays.asList("future:npc:toggle"));
            existing.set("tabs.economy.buttons.npcs.close", true);
            existing.set("tabs.integrations.buttons.discord.commands", Arrays.asList("future:discord:test"));
            existing.set("tabs.integrations.buttons.discord.close", true);
            existing.set("tabs.integrations.buttons.web-overview.commands", Arrays.asList("future:web:export"));
            existing.set("tabs.integrations.buttons.web-overview.close", true);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/team.yml") && existingVersion < 5) {
            existing.set("buttons.lock-plot.commands", Arrays.asList("team:lock"));
            existing.set("buttons.lock-plot.close", true);
            existing.set("buttons.lock-plot.permission", "craftplayplotextras.team.lock");
            existing.set("buttons.freeze-plot.commands", Arrays.asList("team:freeze"));
            existing.set("buttons.freeze-plot.close", true);
            existing.set("buttons.freeze-plot.permission", "craftplayplotextras.team.freeze");
            existing.set("buttons.audit-logs.commands", Arrays.asList("team:audit"));
            existing.set("buttons.audit-logs.close", true);
            existing.set("buttons.audit-logs.permission", "craftplayplotextras.team.audit");
            existing.set("buttons.lagscanner.commands", Arrays.asList("team:lagscan"));
            existing.set("buttons.lagscanner.close", true);
            existing.set("buttons.lagscanner.permission", "craftplayplotextras.team.lagscan");
            existing.set("buttons.plot-analysis.commands", Arrays.asList("team:analysis"));
            existing.set("buttons.plot-analysis.close", true);
            existing.set("buttons.plot-analysis.permission", "craftplayplotextras.team.analysis");
            existing.set("buttons.heatmaps.commands", Arrays.asList("team:heatmap"));
            existing.set("buttons.heatmaps.close", true);
            existing.set("buttons.heatmaps.permission", "craftplayplotextras.future.heatmaps");
            existing.set("buttons.activity-check.commands", Arrays.asList("team:activity"));
            existing.set("buttons.activity-check.close", true);
            existing.set("buttons.activity-check.permission", "craftplayplotextras.team.activity");
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/danger.yml") && existingVersion < 3) {
            final boolean english = resourcePath.replace('\\', '/').contains("/en/");
            existing.set("tabs.actions.lore", english
                    ? Arrays.asList("&7Delete, reset", "&7and unmerge.")
                    : Arrays.asList("&7Löschen, resetten", "&7und unmergen."));
            existing.set("tabs.actions.buttons.backup.enabled", false);
            existing.set("tabs.actions.buttons.backup.commands", Arrays.asList());
            existing.set("tabs.actions.buttons.backup.close", false);
            existing.set("tabs.actions.buttons.delete.commands", Arrays.asList("plot-danger:delete:plot delete"));
            existing.set("tabs.actions.buttons.delete.close", false);
            existing.set("tabs.actions.buttons.reset.commands", Arrays.asList("plot-danger:reset:plot clear"));
            existing.set("tabs.actions.buttons.reset.close", false);
            existing.set("tabs.actions.buttons.unmerge.commands", Arrays.asList("plot-danger:unmerge:plot unmerge"));
            existing.set("tabs.actions.buttons.unmerge.close", false);
            existing.set("tabs.restore.buttons.manual.enabled", false);
            existing.set("tabs.restore.buttons.manual.commands", Arrays.asList());
            existing.set("tabs.restore.buttons.manual.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/members.yml") && existingVersion < 5) {
            final boolean english = resourcePath.replace('\\', '/').contains("/en/");
            existing.set("tabs.overview.lore", english
                    ? Arrays.asList("&7Opens GUI lists", "&7for members and roles.")
                    : Arrays.asList("&7Öffnet die GUI-Listen", "&7für Mitglieder und Rollen."));
            existing.set("tabs.overview.buttons.members.commands", Arrays.asList("open-menu:members-list:1:all"));
            existing.set("tabs.overview.buttons.members.close", false);
            existing.set("tabs.overview.buttons.trusted.commands", Arrays.asList("open-menu:members-list:1:trusted"));
            existing.set("tabs.overview.buttons.trusted.close", false);
            existing.set("tabs.overview.buttons.added.commands", Arrays.asList("open-menu:members-list:1:added"));
            existing.set("tabs.overview.buttons.added.close", false);
            existing.set("tabs.overview.buttons.denied.commands", Arrays.asList("open-menu:members-list:1:denied"));
            existing.set("tabs.overview.buttons.denied.close", false);
            existing.set("tabs.overview.buttons.online.commands", Arrays.asList("open-menu:members-list:1:all"));
            existing.set("tabs.overview.buttons.online.close", false);
            existing.set("tabs.remove.lore", english
                    ? Arrays.asList("&7Removing and allowing", "&7runs through the GUI list.")
                    : Arrays.asList("&7Entfernen und Freigeben", "&7läuft über die GUI-Liste."));
            existing.set("tabs.remove.buttons.remove.commands", Arrays.asList("open-menu:members-list:1:all"));
            existing.set("tabs.remove.buttons.remove.close", false);
            existing.set("tabs.remove.buttons.untrust.commands", Arrays.asList("open-menu:members-list:1:trusted"));
            existing.set("tabs.remove.buttons.untrust.close", false);
            existing.set("tabs.remove.buttons.undeny.commands", Arrays.asList("open-menu:members-list:1:denied"));
            existing.set("tabs.remove.buttons.undeny.close", false);
            existing.set("tabs.roles.buttons.promote.commands", Arrays.asList("open-menu:members-list:1:added"));
            existing.set("tabs.roles.buttons.promote.close", false);
            existing.set("tabs.roles.buttons.demote.commands", Arrays.asList("open-menu:members-list:1:trusted"));
            existing.set("tabs.roles.buttons.demote.close", false);
            existing.set("tabs.roles.buttons.copy-role.commands", Arrays.asList("open-menu:roles-list:1"));
            existing.set("tabs.roles.buttons.copy-role.close", false);
            existing.set("tabs.roles.buttons.export-role.commands", Arrays.asList("open-menu:roles-list:1"));
            existing.set("tabs.roles.buttons.export-role.close", false);
            existing.set("tabs.roles.buttons.owner-info.commands", Arrays.asList());
            existing.set("tabs.roles.buttons.owner-info.close", false);
            existing.set("role-list.item.lore", english
                    ? Arrays.asList("&7Rights: &f{permissions}", "&7Protected: &f{protected}", "", "&aLeft click: assign players", "&eRight click: manage rights", "&bShift-left: manage rights", "&cShift-right: delete role")
                    : Arrays.asList("&7Rechte: &f{permissions}", "&7Geschützt: &f{protected}", "", "&aLinksklick: Spieler zuweisen", "&eRechtsklick: Rechte verwalten", "&bShift-Linksklick: Rechte verwalten", "&cShift-Rechtsklick: Rolle löschen"));
            existing.set("role-list.buttons.create.enabled", false);
            existing.set("role-list.buttons.create.commands", Arrays.asList());
            existing.set("role-list.buttons.create.close", false);
            existing.set("role-list.buttons.unassign.enabled", false);
            existing.set("role-list.buttons.unassign.commands", Arrays.asList());
            existing.set("role-list.buttons.unassign.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/members.yml") && existingVersion < 6) {
            final boolean english = resourcePath.replace('\\', '/').contains("/en/");
            existing.set("role-list.item.lore", english
                    ? Arrays.asList("&7Rights: &f{permissions}", "&7Protected: &f{protected}", "", "&aLeft click: assign players", "&eRight click: manage rights", "&bShift-left: rename role", "&cShift-right: delete role")
                    : Arrays.asList("&7Rechte: &f{permissions}", "&7Geschützt: &f{protected}", "", "&aLinksklick: Spieler zuweisen", "&eRechtsklick: Rechte verwalten", "&bShift-Linksklick: Rolle umbenennen", "&cShift-Rechtsklick: Rolle löschen"));
            existing.set("role-list.buttons.create-custom.enabled", true);
            existing.set("role-list.buttons.create-custom.slot", 52);
            existing.set("role-list.buttons.create-custom.material", "NAME_TAG");
            existing.set("role-list.buttons.create-custom.name", english ? "&aCreate Custom Role" : "&aEigene Rolle erstellen");
            existing.set("role-list.buttons.create-custom.lore", english
                    ? Arrays.asList("&7Asks for the role name", "&7privately in chat.")
                    : Arrays.asList("&7Fragt den Rollennamen", "&7privat im Chat ab."));
            existing.set("role-list.buttons.create-custom.commands", Arrays.asList("chat-input:chat-role-name:role:create:{input}"));
            existing.set("role-list.buttons.create-custom.close", true);
            existing.set("role-list.buttons.create-custom.permission", "");
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/community.yml") && existingVersion < 4) {
            existing.set("tabs.rating.buttons.rate.enabled", false);
            existing.set("tabs.rating.buttons.rate.commands", Arrays.asList());
            existing.set("tabs.rating.buttons.rate.close", false);
            existing.set("tabs.rating.buttons.comments.enabled", false);
            existing.set("tabs.rating.buttons.comments.commands", Arrays.asList());
            existing.set("tabs.rating.buttons.comments.close", false);
            existing.set("tabs.rating.buttons.stars.enabled", false);
            existing.set("tabs.rating.buttons.stars.commands", Arrays.asList());
            existing.set("tabs.rating.buttons.stars.close", false);
            existing.set("tabs.rating.buttons.like.commands", Arrays.asList("community:like"));
            existing.set("tabs.rating.buttons.like.close", false);
            existing.set("tabs.rating.buttons.like.permission", "plots.rate");
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/deco.yml") && existingVersion < 2
                && !existing.contains("tabs.wall.subtabs")) {
            replaceSection(existing, defaults, "tabs");
            existing.set("title", defaults.getString("title", existing.getString("title")));
            existing.set("default-tab", defaults.getString("default-tab", existing.getString("default-tab")));
            existing.set("bedrock.content", defaults.getString("bedrock.content", existing.getString("bedrock.content")));
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/deco.yml") && existingVersion < 7
                && (existing.contains("tabs.wall-basic") || existing.contains("tabs.border-basic")
                || !existing.contains("tabs.wall.subtabs") || !existing.contains("tabs.border.subtabs"))) {
            replaceSection(existing, defaults, "tabs");
            existing.set("title", defaults.getString("title", existing.getString("title")));
            existing.set("default-tab", defaults.getString("default-tab", existing.getString("default-tab")));
            existing.set("bedrock.content", defaults.getString("bedrock.content", existing.getString("bedrock.content")));
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/flags.yml") && existingVersion < 11) {
            existing.set("flags.passive-wither-spawn.permission", "craftplayplotextras.passivewither");
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/flags.yml") && existingVersion < 13) {
            final boolean english = resourcePath.replace('\\', '/').contains("/en/");
            final String oldTitle = existing.getString("title", "");
            if ("&8Plot-Flags".equals(oldTitle) || "&8Plot Flags".equals(oldTitle)) {
                existing.set("title", english ? "&8Plot Flags &7{page}/{max_page}" : "&8Plot-Flags &7{page}/{max_page}");
            }
            existing.set("buttons.previous-page.name", english ? "&eBack to Page {previous_page}" : "&eZurück zu Seite {previous_page}");
            existing.set("buttons.previous-page.lore", english
                    ? Arrays.asList("&7Current page: &f{page}/{max_page}", "&7Open the previous flag page.")
                    : Arrays.asList("&7Aktuelle Seite: &f{page}/{max_page}", "&7Öffnet die vorherige Flag-Seite."));
            existing.set("buttons.previous-page.commands", Arrays.asList("open-menu:flags:{previous_page}"));
            existing.set("buttons.previous-page.close", false);
            existing.set("buttons.next-page.name", english ? "&eNext to Page {next_page}" : "&eWeiter zu Seite {next_page}");
            existing.set("buttons.next-page.lore", english
                    ? Arrays.asList("&7Current page: &f{page}/{max_page}", "&7Open the next flag page.")
                    : Arrays.asList("&7Aktuelle Seite: &f{page}/{max_page}", "&7Öffnet die nächste Flag-Seite."));
            existing.set("buttons.next-page.commands", Arrays.asList("open-menu:flags:{next_page}"));
            existing.set("buttons.next-page.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/flags.yml") && existingVersion < 15) {
            existing.set("reopen-delay-ticks", 0);
            existing.set("animation.enabled", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 18) {
            existing.set("buttons.deco.commands", Arrays.asList("open-menu:deco:wall:basic"));
            existing.set("buttons.deco.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/bedrock.yml") && existingVersion < 9) {
            existing.set("buttons.deco.commands", Arrays.asList("open-menu:deco:wall:basic"));
            existing.set("buttons.deco.close", false);
            changed = true;
        }
        if (existingVersion < defaultVersion) {
            existing.set("file-version", defaultVersion);
            changed = true;
        }

        if (!changed) {
            return;
        }

        if (!backup(target, resourcePath)) {
            plugin.getLogger().warning("Konfiguration wird nicht verändert, weil kein Backup erstellt werden konnte: " + resourcePath);
            return;
        }
        try {
            existing.save(target);
            plugin.getLogger().info("Konfiguration ergänzt: " + resourcePath);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Konfiguration konnte nicht gespeichert werden: " + resourcePath, exception);
        }
    }

    private YamlConfiguration loadDefaults(final String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Standarddatei konnte nicht gelesen werden: " + resourcePath, exception);
            return new YamlConfiguration();
        }
    }

    private boolean removeGlobalPassiveWitherChestMessages(final YamlConfiguration existing) {
        boolean changed = false;
        final List<String> keys = Arrays.asList(
                "passive-wither-chest-usage",
                "passive-wither-chest-not-found",
                "passive-wither-chest-set",
                "passive-wither-chest-cleared",
                "passive-wither-chest-info-empty",
                "passive-wither-chest-info"
        );
        for (final String key : keys) {
            if (existing.contains(key)) {
                existing.set(key, null);
                changed = true;
            }
        }
        return changed;
    }

    private boolean isJavaInventoryGuiFile(final String resourcePath) {
        final String normalized = resourcePath == null ? "" : resourcePath.replace('\\', '/');
        return normalized.startsWith("gui/")
                && normalized.endsWith(".yml")
                && !normalized.endsWith("/bedrock.yml")
                && !normalized.endsWith("/bedrock-myplots.yml");
    }

    private boolean hasMissingGuiAnimation(final File target) {
        boolean inAnimationSection = false;
        boolean enabled = false;
        boolean delayTicks = false;
        boolean keepFillerVisible = false;
        try (BufferedReader reader = Files.newBufferedReader(target.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    if (inAnimationSection) {
                        break;
                    }
                    inAnimationSection = trimmed.equals("animation:");
                    continue;
                }
                if (!inAnimationSection) {
                    continue;
                }
                if (trimmed.startsWith("enabled:")) {
                    enabled = true;
                } else if (trimmed.startsWith("delay-ticks:")) {
                    delayTicks = true;
                } else if (trimmed.startsWith("keep-filler-visible:")) {
                    keepFillerVisible = true;
                }
            }
            return !enabled || !delayTicks || !keepFillerVisible;
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.FINE, "GUI-Animationsschluessel konnten nicht schnell gelesen werden: " + target.getPath(), exception);
            return true;
        }
    }

    private boolean applyGuiAnimationDefaults(final YamlConfiguration existing, final String resourcePath) {
        if (!isJavaInventoryGuiFile(resourcePath)) {
            return false;
        }
        boolean changed = false;
        if (!existing.contains("animation.enabled")) {
            existing.set("animation.enabled", true);
            changed = true;
        }
        if (!existing.contains("animation.delay-ticks")) {
            existing.set("animation.delay-ticks", 1);
            changed = true;
        }
        if (!existing.contains("animation.keep-filler-visible")) {
            existing.set("animation.keep-filler-visible", true);
            changed = true;
        }
        return changed;
    }

    private void replaceSection(
            final YamlConfiguration existing,
            final YamlConfiguration defaults,
            final String path
    ) {
        existing.set(path, null);
        final ConfigurationSection section = defaults.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        existing.createSection(path);
        for (final String key : section.getKeys(true)) {
            final String fullPath = path + "." + key;
            if (defaults.isConfigurationSection(fullPath)) {
                if (!existing.contains(fullPath)) {
                    existing.createSection(fullPath);
                }
                continue;
            }
            existing.set(fullPath, defaults.get(fullPath));
        }
    }

    private int readResourceVersion(final String resourcePath, final int fallback) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return fallback;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return readVersion(reader, fallback);
            }
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.FINE, "Dateiversion konnte nicht aus Standarddatei gelesen werden: " + resourcePath, exception);
            return fallback;
        }
    }

    private int readFileVersion(final File file, final int fallback) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return readVersion(reader, fallback);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.FINE, "Dateiversion konnte nicht gelesen werden: " + file.getPath(), exception);
            return fallback;
        }
    }

    private int readVersion(final BufferedReader reader, final int fallback) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            final String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("file-version:")) {
                continue;
            }
            final String raw = trimmed.substring("file-version:".length())
                    .split("#", 2)[0]
                    .trim()
                    .replace("\"", "")
                    .replace("'", "");
            try {
                return Integer.parseInt(raw);
            } catch (final NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean backup(final File source, final String resourcePath) {
        final File backupFolder = new File(plugin.getDataFolder(), "backup");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("Backup-Ordner konnte nicht erstellt werden: " + backupFolder.getPath());
            return false;
        }

        final String timestamp = BACKUP_TIME_FORMAT.format(LocalDateTime.now());
        final String safeName = resourcePath.replace('/', '_').replace('\\', '_').replace(".yml", "");
        File backupFile = new File(backupFolder, safeName + "-" + timestamp + ".yml");
        int counter = 2;
        while (backupFile.exists()) {
            backupFile = new File(backupFolder, safeName + "-" + timestamp + "-" + counter + ".yml");
            counter++;
        }
        try {
            Files.copy(source.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Backup konnte nicht erstellt werden: " + backupFile.getPath(), exception);
            return false;
        }
    }
}
