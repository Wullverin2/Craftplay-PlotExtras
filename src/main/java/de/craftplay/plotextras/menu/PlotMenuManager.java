package de.craftplay.plotextras.menu;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.backup.PlotBackupMetadata;
import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.hook.HeadDatabaseHook;
import de.craftplay.plotextras.hook.PlaceholderHook;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlotMenuManager implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;
    private final LanguageManager languageManager;
    private final PlotSquaredFlagService flagService;
    private final PlotBackupService backupService;
    private final HeadDatabaseHook headDatabaseHook;
    private final PlaceholderHook placeholderHook;
    private final Map<Integer, MenuButton> mainButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> flagButtonsBySlot = new HashMap<>();
    private final Map<Integer, Map<Integer, FlagMenuEntry>> flagsByPageAndSlot = new HashMap<>();
    private final Map<Integer, MenuButton> settingsDecorationsBySlot = new HashMap<>();
    private final Map<String, SettingsTab> settingsTabs = new LinkedHashMap<>();
    private final Map<Integer, MenuButton> teamButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> backupListButtonsBySlot = new HashMap<>();

    private String mainTitle;
    private int mainSize;
    private ItemStack mainFiller;
    private boolean mainLoaded;
    private List<String> hiddenMainButtons;
    private String flagsTitle;
    private int flagsSize;
    private ItemStack flagsFiller;
    private boolean flagsLoaded;
    private String statusEnabled;
    private String statusDisabled;
    private long reopenDelayTicks;
    private String settingsTitlePattern;
    private int settingsSize;
    private ItemStack settingsFiller;
    private boolean settingsLoaded;
    private String defaultSettingsTab;
    private String teamTitle;
    private int teamSize;
    private ItemStack teamFiller;
    private boolean teamLoaded;
    private String backupListTitle;
    private int backupListSize;
    private ItemStack backupListFiller;
    private boolean backupListLoaded;
    private List<Integer> backupListSlots;
    private Material backupListItemMaterial;
    private String backupListItemHeadDatabaseId;
    private String backupListItemName;
    private List<String> backupListItemLore;

    public PlotMenuManager(
            final CraftplayPlotExtrasPlugin plugin,
            final LanguageManager languageManager,
            final PlotSquaredFlagService flagService,
            final PlotBackupService backupService
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.flagService = flagService;
        this.backupService = backupService;
        this.headDatabaseHook = new HeadDatabaseHook(plugin);
        this.placeholderHook = new PlaceholderHook(plugin);
    }

    public void reload() {
        mainButtonsBySlot.clear();
        flagButtonsBySlot.clear();
        flagsByPageAndSlot.clear();
        settingsDecorationsBySlot.clear();
        settingsTabs.clear();
        teamButtonsBySlot.clear();
        backupListButtonsBySlot.clear();
        mainLoaded = false;
        flagsLoaded = false;
        settingsLoaded = false;
        teamLoaded = false;
        backupListLoaded = false;

        final YamlConfiguration menuConfig = loadMenuConfig(plugin.getConfig().getString("gui.main-menu", "main.yml"));
        if (menuConfig == null) {
            mainTitle = Text.color("&8Plot-Menü");
            mainSize = 27;
            mainFiller = null;
            return;
        }

        mainTitle = menuConfig.getString("title", "&8Plot-Menü");
        mainSize = normalizeSize(menuConfig.getInt("size", 27));
        hiddenMainButtons = plugin.getConfig().getStringList("gui.hidden-main-buttons");
        mainFiller = createFiller(menuConfig);
        loadButtons(menuConfig, mainButtonsBySlot, mainSize);
        loadDecorations(menuConfig, mainButtonsBySlot, mainSize);
        mainLoaded = true;

        final YamlConfiguration flagsConfig = loadMenuConfig(plugin.getConfig().getString("gui.flags-menu", "flags.yml"));
        if (flagsConfig == null) {
            flagsTitle = Text.color("&8Plot-Flags");
            flagsSize = 54;
            flagsFiller = null;
            return;
        }

        flagsTitle = flagsConfig.getString("title", "&8Plot-Flags");
        flagsSize = normalizeSize(flagsConfig.getInt("size", 54));
        flagsFiller = createFiller(flagsConfig);
        statusEnabled = flagsConfig.getString("status.enabled", "&aAktiv");
        statusDisabled = flagsConfig.getString("status.disabled", "&cInaktiv");
        reopenDelayTicks = Math.max(1L, flagsConfig.getLong("reopen-delay-ticks", 2L));
        loadButtons(flagsConfig, flagButtonsBySlot, flagsSize);
        loadDecorations(flagsConfig, flagButtonsBySlot, flagsSize);
        loadFlags(flagsConfig);
        flagsLoaded = true;

        final YamlConfiguration settingsConfig = loadMenuConfig(plugin.getConfig().getString("gui.settings-menu", "settings.yml"));
        if (settingsConfig == null) {
            settingsTitlePattern = "&8Plot-Einstellungen: {tab}";
            settingsSize = 54;
            settingsFiller = null;
            return;
        }

        settingsTitlePattern = settingsConfig.getString("title", "&8Plot-Einstellungen: {tab}");
        settingsSize = normalizeSize(settingsConfig.getInt("size", 54));
        settingsFiller = createFiller(settingsConfig);
        defaultSettingsTab = settingsConfig.getString("default-tab", "homes");
        loadDecorations(settingsConfig, settingsDecorationsBySlot, settingsSize);
        loadSettingsTabs(settingsConfig);
        settingsLoaded = true;

        final YamlConfiguration teamConfig = loadMenuConfig(plugin.getConfig().getString("gui.team-menu", "team.yml"));
        if (teamConfig == null) {
            teamTitle = "&8Team-Menü";
            teamSize = 27;
            teamFiller = null;
            return;
        }

        teamTitle = teamConfig.getString("title", "&8Team-Menü");
        teamSize = normalizeSize(teamConfig.getInt("size", 27));
        teamFiller = createFiller(teamConfig);
        loadButtons(teamConfig, teamButtonsBySlot, teamSize);
        loadDecorations(teamConfig, teamButtonsBySlot, teamSize);
        teamLoaded = true;

        backupListTitle = teamConfig.getString("backup-list.title", "&8Plotbackups");
        backupListSize = normalizeSize(teamConfig.getInt("backup-list.size", 54));
        backupListFiller = createFiller(teamConfig, "backup-list.filler");
        backupListSlots = teamConfig.getIntegerList("backup-list.slots");
        if (backupListSlots.isEmpty()) {
            backupListSlots = defaultListSlots(backupListSize);
        }
        backupListItemMaterial = material(teamConfig.getString("backup-list.item.material", "FILLED_MAP"), Material.FILLED_MAP);
        backupListItemHeadDatabaseId = headDatabaseId(teamConfig, "backup-list.item.");
        backupListItemName = teamConfig.getString("backup-list.item.name", "&e{owner} &7- &f{plot}");
        backupListItemLore = teamConfig.getStringList("backup-list.item.lore");
        loadButtonsFromSection(teamConfig, "backup-list.buttons", backupListButtonsBySlot, backupListSize);
        loadDecorationsFromSection(teamConfig, "backup-list.decorations", backupListButtonsBySlot, backupListSize);
        backupListLoaded = true;
    }

    public void openMainMenu(final Player player) {
        if (!mainLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("main"),
                mainSize,
                Text.color(placeholderHook.apply(player, mainTitle))
        );
        if (mainFiller != null) {
            for (int slot = 0; slot < mainSize; slot++) {
                inventory.setItem(slot, mainFiller);
            }
        }

        for (final MenuButton button : mainButtonsBySlot.values()) {
            if (!canSee(player, button)) {
                continue;
            }
            if (isHiddenMainButton(button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(player, button));
        }

        player.openInventory(inventory);
    }

    public void openFlagsMenu(final Player player) {
        openFlagsMenu(player, 1);
    }

    public void openFlagsMenu(final Player player, final int page) {
        if (!flagsLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }
        if (!flagService.isOnPlot(player)) {
            languageManager.send(player, "no-plot");
            return;
        }

        final int normalizedPage = Math.max(1, page);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("flags", normalizedPage),
                flagsSize,
                Text.color(placeholderHook.apply(player, flagsTitle))
        );
        if (flagsFiller != null) {
            for (int slot = 0; slot < flagsSize; slot++) {
                inventory.setItem(slot, flagsFiller);
            }
        }

        for (final MenuButton button : flagButtonsBySlot.values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(player, button));
        }

        final Map<Integer, FlagMenuEntry> flagsBySlot = flagsByPageAndSlot.getOrDefault(normalizedPage, Collections.emptyMap());
        for (final FlagMenuEntry flagEntry : flagsBySlot.values()) {
            if (!canSeeFlag(player, flagEntry)) {
                continue;
            }
            inventory.setItem(flagEntry.getSlot(), createFlagItem(player, flagEntry));
        }

        player.openInventory(inventory);
    }

    public void openSettingsMenu(final Player player) {
        openSettingsMenu(player, defaultSettingsTab);
    }

    public void openSettingsMenu(final Player player, final String requestedTabId) {
        if (!settingsLoaded || settingsTabs.isEmpty()) {
            languageManager.send(player, "menu-missing");
            return;
        }
        if (!flagService.isOnPlot(player)) {
            languageManager.send(player, "no-plot");
            return;
        }

        final SettingsTab tab = settingsTab(requestedTabId);
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tab", tabName(tab));
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("settings", tab.getId()),
                settingsSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(settingsTitlePattern, placeholders)))
        );
        if (settingsFiller != null) {
            for (int slot = 0; slot < settingsSize; slot++) {
                inventory.setItem(slot, settingsFiller);
            }
        }

        for (final MenuButton decoration : settingsDecorationsBySlot.values()) {
            if (!canSee(player, decoration)) {
                continue;
            }
            inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration));
        }

        for (final SettingsTab settingsTab : settingsTabs.values()) {
            for (final MenuButton selector : settingsTab.getSelectors()) {
                if (!canSee(player, selector)) {
                    continue;
                }
                inventory.setItem(selector.getSlot(), createButtonItem(player, selector));
            }
        }

        for (final MenuButton button : tab.getButtonsBySlot().values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(player, button));
        }

        player.openInventory(inventory);
    }

    public void openTeamMenu(final Player player) {
        if (!player.hasPermission("craftplayplotextras.team")) {
            languageManager.send(player, "no-permission");
            return;
        }
        if (!teamLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("team"),
                teamSize,
                Text.color(placeholderHook.apply(player, teamTitle))
        );
        if (teamFiller != null) {
            for (int slot = 0; slot < teamSize; slot++) {
                inventory.setItem(slot, teamFiller);
            }
        }
        for (final MenuButton button : teamButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button));
            }
        }
        player.openInventory(inventory);
    }

    public void openBackupListMenu(final Player player, final int page) {
        if (!player.hasPermission("craftplayplotextras.backup.list")) {
            languageManager.send(player, "no-permission");
            return;
        }
        if (!backupListLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }

        final int normalizedPage = Math.max(1, page);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("team-backups", normalizedPage),
                backupListSize,
                Text.color(placeholderHook.apply(player, backupListTitle.replace("{page}", String.valueOf(normalizedPage))))
        );
        if (backupListFiller != null) {
            for (int slot = 0; slot < backupListSize; slot++) {
                inventory.setItem(slot, backupListFiller);
            }
        }
        final Map<String, String> pagePlaceholders = new HashMap<>();
        pagePlaceholders.put("page", String.valueOf(normalizedPage));
        pagePlaceholders.put("next_page", String.valueOf(normalizedPage + 1));
        pagePlaceholders.put("previous_page", String.valueOf(Math.max(1, normalizedPage - 1)));
        for (final MenuButton button : backupListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, pagePlaceholders));
            }
        }

        final List<PlotBackupMetadata> backups = backupService.listBackups();
        final int pageSize = Math.max(1, backupListSlots.size());
        final int start = (normalizedPage - 1) * pageSize;
        final int end = Math.min(backups.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            final int slot = backupListSlots.get(index - start);
            final PlotBackupMetadata metadata = backups.get(index);
            inventory.setItem(slot, createBackupListItem(player, metadata));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PlotMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        final Player player = (Player) event.getWhoClicked();
        final PlotMenuHolder holder = (PlotMenuHolder) event.getInventory().getHolder();
        if ("flags".equalsIgnoreCase(holder.getMenuId())) {
            handleFlagsClick(player, holder.getPage(), event.getSlot());
            return;
        }
        if ("settings".equalsIgnoreCase(holder.getMenuId())) {
            handleSettingsClick(player, holder.getTabId(), event.getSlot());
            return;
        }
        if ("team".equalsIgnoreCase(holder.getMenuId())) {
            handleTeamClick(player, event.getSlot());
            return;
        }
        if ("team-backups".equalsIgnoreCase(holder.getMenuId())) {
            handleBackupListClick(player, holder.getPage(), event.getSlot());
            return;
        }

        final MenuButton button = mainButtonsBySlot.get(event.getSlot());
        if (button == null || isHiddenMainButton(button) || !canSee(player, button)) {
            return;
        }

        if (button.isCloseInventory()) {
            player.closeInventory();
        }

        for (final String command : button.getCommands()) {
            runCommand(player, command);
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PlotMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void loadButtons(final YamlConfiguration menuConfig, final Map<Integer, MenuButton> target, final int menuSize) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("buttons");
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = "buttons." + id + ".";
            final Material material = material(menuConfig.getString(path + "material", "STONE_BUTTON"), Material.STONE_BUTTON);
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = menuConfig.getStringList(path + "commands");
            final boolean close = menuConfig.getBoolean(path + "close", true);
            final String permission = menuConfig.getString(path + "permission", "");

            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Menübutton", id)) {
                target.put(slot, new MenuButton(id, slot, material, headDatabaseId, name, lore, commands, close, permission));
            }
        }
    }

    private void loadDecorations(final YamlConfiguration menuConfig, final Map<Integer, MenuButton> target, final int menuSize) {
        loadDecorationsFromSection(menuConfig, "decorations", target, menuSize);
    }

    private void loadFlags(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("flags");
        if (section == null) {
            return;
        }

        for (final String flag : section.getKeys(false)) {
            final String path = "flags." + flag + ".";
            final int page = Math.max(1, menuConfig.getInt(path + "page", 1));
            if (!flagService.isBooleanFlag(flag)) {
                plugin.getLogger().warning("Flag '" + flag + "' ist keine bekannte Boolean-Flag und wird übersprungen.");
                continue;
            }

            final Material enabledMaterial = material(menuConfig.getString(path + "enabled-material",
                    menuConfig.getString(path + "material", "LIME_DYE")), Material.LIME_DYE);
            final Material disabledMaterial = material(menuConfig.getString(path + "disabled-material",
                    menuConfig.getString(path + "material", "GRAY_DYE")), Material.GRAY_DYE);
            final String name = menuConfig.getString(path + "name", "&a" + flag);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final String permission = menuConfig.getString(path + "permission", "");
            for (final int slot : configuredSlots(menuConfig, path, flagsSize, "Flag", flag)) {
                flagsByPageAndSlot
                        .computeIfAbsent(page, ignored -> new HashMap<>())
                        .put(slot, new FlagMenuEntry(flag, page, slot, enabledMaterial, disabledMaterial, name, lore, permission));
            }
        }
    }

    private void loadSettingsTabs(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("tabs");
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = "tabs." + id + ".";
            final Material material = material(menuConfig.getString(path + "material", "BOOK"), Material.BOOK);
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = menuConfig.getStringList(path + "commands");
            final String permission = menuConfig.getString(path + "permission", "");
            final List<MenuButton> selectors = new ArrayList<>();
            for (final int slot : configuredSlots(menuConfig, path, settingsSize, "Einstellungstab", id)) {
                selectors.add(new MenuButton(
                        id,
                        slot,
                        material,
                        headDatabaseId,
                        name,
                        lore,
                        commands.isEmpty() ? Collections.singletonList("open-menu:settings:" + id) : commands,
                        false,
                        permission
                ));
            }
            if (selectors.isEmpty()) {
                continue;
            }
            final Map<Integer, MenuButton> tabButtons = new HashMap<>();
            loadButtonsFromSection(menuConfig, path + "buttons", tabButtons, settingsSize);
            loadDecorationsFromSection(menuConfig, path + "decorations", tabButtons, settingsSize);
            settingsTabs.put(id.toLowerCase(Locale.ROOT), new SettingsTab(id.toLowerCase(Locale.ROOT), selectors, tabButtons));
        }
    }

    private void loadButtonsFromSection(
            final YamlConfiguration menuConfig,
            final String sectionPath,
            final Map<Integer, MenuButton> target,
            final int menuSize
    ) {
        final ConfigurationSection section = menuConfig.getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = sectionPath + "." + id + ".";
            final Material material = material(menuConfig.getString(path + "material", "STONE_BUTTON"), Material.STONE_BUTTON);
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = menuConfig.getStringList(path + "commands");
            final boolean close = menuConfig.getBoolean(path + "close", true);
            final String permission = menuConfig.getString(path + "permission", "");

            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Menübutton", id)) {
                target.put(slot, new MenuButton(id, slot, material, headDatabaseId, name, lore, commands, close, permission));
            }
        }
    }

    private void loadDecorationsFromSection(
            final YamlConfiguration menuConfig,
            final String sectionPath,
            final Map<Integer, MenuButton> target,
            final int menuSize
    ) {
        final ConfigurationSection section = menuConfig.getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = sectionPath + "." + id + ".";
            final Material material = material(menuConfig.getString(path + "material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String name = menuConfig.getString(path + "name", "&r");
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final String permission = menuConfig.getString(path + "permission", "");

            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Deko-Item", id)) {
                target.put(slot, new MenuButton(id, slot, material, headDatabaseId, name, lore, Collections.emptyList(), false, permission));
            }
        }
    }

    private ItemStack createFiller(final YamlConfiguration menuConfig) {
        return createFiller(menuConfig, "filler");
    }

    private ItemStack createFiller(final YamlConfiguration menuConfig, final String path) {
        if (!menuConfig.getBoolean(path + ".enabled", true)) {
            return null;
        }

        final Material material = material(menuConfig.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE);
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(menuConfig.getString(path + ".name", "&r")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private YamlConfiguration loadMenuConfig(final String menuFile) {
        final String language = languageManager.getDefaultLanguage();
        final File localizedFile = new File(plugin.getDataFolder(), "gui/" + language + "/" + menuFile);
        if (localizedFile.exists()) {
            return YamlConfiguration.loadConfiguration(localizedFile);
        }

        final File fallbackFile = new File(plugin.getDataFolder(), "gui/de/" + menuFile);
        if (fallbackFile.exists()) {
            plugin.getLogger().warning("GUI-Datei für Sprache '" + language + "' fehlt. Nutze Deutsch als Fallback.");
            return YamlConfiguration.loadConfiguration(fallbackFile);
        }

        plugin.getLogger().warning("GUI-Datei fehlt: " + localizedFile.getPath());
        return null;
    }

    private ItemStack createButtonItem(final Player player, final MenuButton button) {
        return createButtonItem(player, button, Collections.emptyMap());
    }

    private ItemStack createButtonItem(
            final Player player,
            final MenuButton button,
            final Map<String, String> replacements
    ) {
        ItemStack item = headDatabaseHook.getHead(button.getHeadDatabaseId());
        if (item == null) {
            item = new ItemStack(button.getMaterial());
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(button.getName(), replacements))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(button.getLore(), replacements))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFlagItem(final Player player, final FlagMenuEntry flagEntry) {
        final boolean enabled = flagService.isFlagEnabled(player, flagEntry.getFlag());
        final ItemStack item = new ItemStack(enabled ? flagEntry.getEnabledMaterial() : flagEntry.getDisabledMaterial());
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = flagPlaceholders(flagEntry, enabled);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(flagEntry.getName(), placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(flagEntry.getLore(), placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackupListItem(final Player player, final PlotBackupMetadata metadata) {
        ItemStack item = headDatabaseHook.getHead(backupListItemHeadDatabaseId);
        if (item == null) {
            item = new ItemStack(backupListItemMaterial);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = backupPlaceholders(metadata);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(backupListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(backupListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean canSee(final Player player, final MenuButton button) {
        if (button.getPermission() == null || button.getPermission().trim().isEmpty()) {
            return true;
        }
        final String permission = button.getPermission().trim();
        if (permission.toLowerCase(Locale.ROOT).startsWith("plots.")) {
            return flagService.hasPermission(player, permission);
        }
        return player.hasPermission(permission);
    }

    private boolean canSeeFlag(final Player player, final FlagMenuEntry flagEntry) {
        if (flagEntry.getPermission() != null && !flagEntry.getPermission().trim().isEmpty()) {
            return player.hasPermission(flagEntry.getPermission());
        }
        return flagService.hasAnyFlagPermission(player, flagEntry.getFlag());
    }

    private void handleFlagsClick(final Player player, final int page, final int slot) {
        final MenuButton button = flagButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            if (button.isCloseInventory()) {
                player.closeInventory();
            }
            for (final String command : button.getCommands()) {
                runCommand(player, command);
            }
            return;
        }

        final Map<Integer, FlagMenuEntry> flagsBySlot = flagsByPageAndSlot.getOrDefault(page, Collections.emptyMap());
        final FlagMenuEntry flagEntry = flagsBySlot.get(slot);
        if (flagEntry == null || !canSeeFlag(player, flagEntry)) {
            return;
        }
        final boolean current = flagService.isFlagEnabled(player, flagEntry.getFlag());
        final boolean target = !current;
        if (!flagService.hasTogglePermission(player, flagEntry.getFlag(), target)) {
            languageManager.send(player, "flag-no-permission");
            return;
        }

        flagService.toggleBooleanFlag(player, flagEntry.getFlag());
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("flag", Text.color(flagEntry.getName()));
        placeholders.put("status", Text.color(target ? statusEnabled : statusDisabled));
        languageManager.send(player, "flag-toggled", placeholders);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                openFlagsMenu(player, page);
            }
        }, reopenDelayTicks);
    }

    private void handleSettingsClick(final Player player, final String tabId, final int slot) {
        for (final SettingsTab tab : settingsTabs.values()) {
            for (final MenuButton selector : tab.getSelectors()) {
                if (selector.getSlot() != slot) {
                    continue;
                }
                if (!canSee(player, selector)) {
                    return;
                }
                for (final String command : selector.getCommands()) {
                    runCommand(player, command);
                }
                return;
            }
        }

        final SettingsTab tab = settingsTab(tabId);
        final MenuButton button = tab.getButtonsBySlot().get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        if (button.isCloseInventory()) {
            player.closeInventory();
        }
        for (final String command : button.getCommands()) {
            runCommand(player, command);
        }
    }

    private void handleTeamClick(final Player player, final int slot) {
        final MenuButton button = teamButtonsBySlot.get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        if (button.isCloseInventory()) {
            player.closeInventory();
        }
        for (final String command : button.getCommands()) {
            runCommand(player, command);
        }
    }

    private void handleBackupListClick(final Player player, final int page, final int slot) {
        final MenuButton button = backupListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            if (button.isCloseInventory()) {
                player.closeInventory();
            }
            for (final String command : button.getCommands()) {
                runCommand(player, command
                        .replace("{page}", String.valueOf(page))
                        .replace("{next_page}", String.valueOf(page + 1))
                        .replace("{previous_page}", String.valueOf(Math.max(1, page - 1))));
            }
            return;
        }

        final String backupId = backupIdAt(page, slot);
        if (backupId == null) {
            return;
        }
        player.closeInventory();
        backupService.requestRestore(player, backupId);
    }

    private void runCommand(final Player player, final String configuredCommand) {
        if (configuredCommand == null || configuredCommand.trim().isEmpty()) {
            return;
        }

        String command = configuredCommand
                .replace("{player}", player.getName())
                .replace("%player%", player.getName())
                .trim();
        command = placeholderHook.apply(player, command).trim();

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("console:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripCommandPrefix(command.substring("console:".length()).trim()));
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("player:")) {
            player.performCommand(stripCommandPrefix(command.substring("player:".length()).trim()));
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("op:")) {
            final boolean wasOp = player.isOp();
            try {
                player.setOp(true);
                player.performCommand(stripCommandPrefix(command.substring("op:".length()).trim()));
            } finally {
                player.setOp(wasOp);
            }
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("open-menu:")) {
            final String menuId = command.substring("open-menu:".length()).trim();
            if (menuId.toLowerCase(Locale.ROOT).startsWith("flags")) {
                openFlagsMenu(player, menuPage(menuId));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("settings")) {
                openSettingsMenu(player, menuArgument(menuId, defaultSettingsTab));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("team-backups")) {
                openBackupListMenu(player, menuPage(menuId));
            } else if ("team".equalsIgnoreCase(menuId)) {
                openTeamMenu(player);
            } else if ("main".equalsIgnoreCase(menuId)) {
                openMainMenu(player);
            }
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-backup:")) {
            final String backupAction = command.substring("plot-backup:".length()).trim();
            if ("create".equalsIgnoreCase(backupAction)) {
                player.closeInventory();
                backupService.requestManualBackup(player);
            } else if (backupAction.toLowerCase(Locale.ROOT).startsWith("restore:")) {
                player.closeInventory();
                backupService.requestRestore(player, backupAction.substring("restore:".length()).trim());
            }
            return;
        }

        player.performCommand(command);
    }

    private String stripCommandPrefix(final String command) {
        if (command.startsWith("/")) {
            return command.substring(1);
        }
        return command;
    }

    private boolean isHiddenMainButton(final MenuButton button) {
        if (hiddenMainButtons == null || hiddenMainButtons.isEmpty()) {
            return false;
        }
        for (final String hiddenButton : hiddenMainButtons) {
            if (button.getId().equalsIgnoreCase(hiddenButton)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> flagPlaceholders(final FlagMenuEntry flagEntry, final boolean enabled) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("flag", flagEntry.getFlag());
        placeholders.put("status", enabled ? statusEnabled : statusDisabled);
        placeholders.put("next_status", enabled ? statusDisabled : statusEnabled);
        return placeholders;
    }

    private Map<String, String> backupPlaceholders(final PlotBackupMetadata metadata) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", value(metadata.getId()));
        placeholders.put("file", value(metadata.getSchematicFileName()));
        placeholders.put("owner", value(metadata.getOwnerName()));
        placeholders.put("created_by", value(metadata.getCreatedByName()));
        placeholders.put("created_at", value(metadata.getCreatedAt()));
        placeholders.put("action", actionName(metadata.getAction()));
        placeholders.put("world", value(metadata.getWorldName()));
        placeholders.put("plot", value(metadata.getPlotId()));
        placeholders.put("plots", metadata.getPlotIds().isEmpty() ? value(metadata.getPlotId()) : String.join(", ", metadata.getPlotIds()));
        placeholders.put("merge", value(metadata.getMergeType()));
        return placeholders;
    }

    private String value(final String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String actionName(final String action) {
        if (action == null || action.trim().isEmpty()) {
            return "-";
        }
        final String configured = plugin.getConfig().getString("plot-backups.action-names." + action.toLowerCase(Locale.ROOT), "");
        return configured == null || configured.trim().isEmpty() ? action : configured;
    }

    private String applyPlaceholders(final String text, final Map<String, String> placeholders) {
        String replaced = text == null ? "" : text;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return replaced;
    }

    private List<String> applyPlaceholders(final List<String> lines, final Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> replaced = new java.util.ArrayList<>();
        for (final String line : lines) {
            replaced.add(applyPlaceholders(line, placeholders));
        }
        return replaced;
    }

    private SettingsTab settingsTab(final String requestedTabId) {
        final String normalized = requestedTabId == null ? "" : requestedTabId.toLowerCase(Locale.ROOT);
        final SettingsTab requested = settingsTabs.get(normalized);
        if (requested != null) {
            return requested;
        }
        final SettingsTab fallback = settingsTabs.get(defaultSettingsTab == null ? "" : defaultSettingsTab.toLowerCase(Locale.ROOT));
        if (fallback != null) {
            return fallback;
        }
        return settingsTabs.values().iterator().next();
    }

    private String tabName(final SettingsTab tab) {
        final MenuButton selector = tab.getSelector();
        return selector == null ? tab.getId() : selector.getName();
    }

    private int menuPage(final String menuId) {
        final int separator = menuId.indexOf(':');
        if (separator < 0 || separator >= menuId.length() - 1) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(menuId.substring(separator + 1)));
        } catch (final NumberFormatException exception) {
            return 1;
        }
    }

    private String menuArgument(final String menuId, final String fallback) {
        final int separator = menuId.indexOf(':');
        if (separator < 0 || separator >= menuId.length() - 1) {
            return fallback;
        }
        return menuId.substring(separator + 1).trim();
    }

    private String backupIdAt(final int page, final int slot) {
        if (backupListSlots == null || backupListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = backupListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final int backupIndex = (Math.max(1, page) - 1) * backupListSlots.size() + slotIndex;
        final List<PlotBackupMetadata> backups = backupService.listBackups();
        if (backupIndex < 0 || backupIndex >= backups.size()) {
            return null;
        }
        return backups.get(backupIndex).getId();
    }

    private List<Integer> defaultListSlots(final int menuSize) {
        final List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < menuSize; slot++) {
            final int column = slot % 9;
            final int row = slot / 9;
            if (row == 0 || row >= (menuSize / 9) - 1 || column == 0 || column == 8) {
                continue;
            }
            slots.add(slot);
        }
        return slots;
    }

    private List<Integer> configuredSlots(
            final YamlConfiguration menuConfig,
            final String path,
            final int menuSize,
            final String type,
            final String id
    ) {
        final List<Integer> configured = new ArrayList<>();
        configured.addAll(menuConfig.getIntegerList(path + "slots"));
        if (configured.isEmpty() && menuConfig.contains(path + "slot")) {
            configured.add(menuConfig.getInt(path + "slot", -1));
        }
        if (configured.isEmpty()) {
            plugin.getLogger().warning(type + " '" + id + "' hat keinen Slot gesetzt. Nutze slot: <zahl> oder slots: [<zahl>, ...].");
            return Collections.emptyList();
        }

        final List<Integer> valid = new ArrayList<>();
        for (final int slot : configured) {
            if (slot < 0 || slot >= menuSize) {
                plugin.getLogger().warning(type + " '" + id + "' hat einen ungültigen Slot: " + slot);
                continue;
            }
            if (!valid.contains(slot)) {
                valid.add(slot);
            }
        }
        return valid;
    }

    private int normalizeSize(final int configuredSize) {
        int normalized = Math.max(9, Math.min(54, configuredSize));
        if (normalized % 9 != 0) {
            normalized = ((normalized / 9) + 1) * 9;
        }
        return normalized;
    }

    private Material material(final String configuredMaterial, final Material fallback) {
        if (configuredMaterial == null) {
            return fallback;
        }
        final Material material = Material.matchMaterial(configuredMaterial);
        return material == null ? fallback : material;
    }

    private String headDatabaseId(final YamlConfiguration menuConfig, final String path) {
        String id = menuConfig.getString(path + "head-database-id", "");
        if (id == null || id.trim().isEmpty()) {
            id = menuConfig.getString(path + "hdb-id", "");
        }
        if (id == null || id.trim().isEmpty()) {
            id = menuConfig.getString(path + "head-id", "");
        }
        return id == null ? "" : id.trim();
    }
}
