package de.craftplay.plotextras.menu;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.backup.PlotBackupMetadata;
import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.hook.FloodgateHook;
import de.craftplay.plotextras.hook.HeadDatabaseHook;
import de.craftplay.plotextras.hook.PlaceholderHook;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.myplots.PlotDataStore;
import de.craftplay.plotextras.myplots.PlotMetadata;
import de.craftplay.plotextras.plotsquared.OwnedPlot;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.plotsquared.PlotSquaredPlotService;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlotMenuManager implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;
    private final LanguageManager languageManager;
    private final PlotSquaredFlagService flagService;
    private final PlotSquaredPlotService plotService;
    private final PlotDataStore plotDataStore;
    private final PlotBackupService backupService;
    private final HeadDatabaseHook headDatabaseHook;
    private final PlaceholderHook placeholderHook;
    private final FloodgateHook floodgateHook;
    private final Map<Integer, MenuButton> mainButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> bedrockMainButtonsByOrder = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotsButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotsDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotDetailButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotDetailDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> flagButtonsBySlot = new HashMap<>();
    private final Map<Integer, Map<Integer, FlagMenuEntry>> flagsByPageAndSlot = new HashMap<>();
    private final Map<Integer, MenuButton> settingsDecorationsBySlot = new HashMap<>();
    private final Map<String, SettingsTab> settingsTabs = new LinkedHashMap<>();
    private final Map<String, ActionMenu> actionMenus = new LinkedHashMap<>();
    private final Map<UUID, PendingChatInput> pendingChatInputs = new HashMap<>();
    private final Map<Integer, MenuButton> teamButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> backupListButtonsBySlot = new HashMap<>();

    private String mainTitle;
    private int mainSize;
    private ItemStack mainFiller;
    private boolean mainLoaded;
    private List<String> hiddenMainButtons;
    private MenuSound mainOpenSound;
    private MenuSound mainClickSound;
    private boolean mainAnimationEnabled;
    private long mainAnimationDelayTicks;
    private boolean bedrockFormsEnabled;
    private boolean bedrockMainLoaded;
    private String bedrockMainTitle;
    private String bedrockMainContent;
    private MenuSound bedrockClickSound;
    private List<String> hiddenBedrockMainButtons;
    private YamlConfiguration myPlotsConfig;
    private YamlConfiguration bedrockMyPlotsConfig;
    private String myPlotsTitle;
    private int myPlotsSize;
    private ItemStack myPlotsFiller;
    private List<Integer> myPlotsSlots;
    private boolean myPlotsLoaded;
    private String myPlotDetailTitle;
    private int myPlotDetailSize;
    private ItemStack myPlotDetailFiller;
    private List<Integer> myPlotDetailTagSlots;
    private boolean bedrockMyPlotsLoaded;
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
    private String backupListItemSkullOwner;
    private String backupListItemName;
    private List<String> backupListItemLore;

    public PlotMenuManager(
            final CraftplayPlotExtrasPlugin plugin,
            final LanguageManager languageManager,
            final PlotSquaredFlagService flagService,
            final PlotSquaredPlotService plotService,
            final PlotDataStore plotDataStore,
            final PlotBackupService backupService
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.flagService = flagService;
        this.plotService = plotService;
        this.plotDataStore = plotDataStore;
        this.backupService = backupService;
        this.headDatabaseHook = new HeadDatabaseHook(plugin);
        this.placeholderHook = new PlaceholderHook(plugin);
        this.floodgateHook = new FloodgateHook(plugin);
    }

    public void reload() {
        mainButtonsBySlot.clear();
        bedrockMainButtonsByOrder.clear();
        myPlotsButtonsBySlot.clear();
        myPlotsDecorationsBySlot.clear();
        myPlotDetailButtonsBySlot.clear();
        myPlotDetailDecorationsBySlot.clear();
        flagButtonsBySlot.clear();
        flagsByPageAndSlot.clear();
        settingsDecorationsBySlot.clear();
        settingsTabs.clear();
        actionMenus.clear();
        teamButtonsBySlot.clear();
        backupListButtonsBySlot.clear();
        mainLoaded = false;
        bedrockMainLoaded = false;
        myPlotsLoaded = false;
        bedrockMyPlotsLoaded = false;
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
        mainOpenSound = loadSound(menuConfig, "open-sound");
        mainClickSound = loadSound(menuConfig, "click-sound");
        mainAnimationEnabled = menuConfig.getBoolean("animation.enabled", false);
        mainAnimationDelayTicks = Math.max(1L, menuConfig.getLong("animation.delay-ticks", 1L));
        loadButtons(menuConfig, mainButtonsBySlot, mainSize);
        loadDecorations(menuConfig, mainButtonsBySlot, mainSize);
        mainLoaded = true;

        bedrockFormsEnabled = plugin.getConfig().getBoolean("bedrock.enabled", true);
        final YamlConfiguration bedrockConfig = loadMenuConfig(plugin.getConfig().getString("gui.bedrock-main-menu", "bedrock.yml"));
        if (bedrockConfig != null) {
            bedrockFormsEnabled = bedrockFormsEnabled && bedrockConfig.getBoolean("enabled", true);
            bedrockMainTitle = bedrockConfig.getString("title", mainTitle);
            bedrockMainContent = bedrockConfig.getString("content", "");
            bedrockClickSound = loadSound(bedrockConfig, "click-sound");
            hiddenBedrockMainButtons = bedrockConfig.getStringList("hidden-buttons");
            loadBedrockButtons(bedrockConfig);
            bedrockMainLoaded = true;
        }

        loadMyPlotsMenus();
        loadActionMenus();

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
        final MaterialDefinition backupListItemDefinition = materialDefinition(teamConfig, "backup-list.item.", Material.FILLED_MAP);
        backupListItemMaterial = backupListItemDefinition.getMaterial();
        backupListItemHeadDatabaseId = headDatabaseId(teamConfig, "backup-list.item.");
        backupListItemSkullOwner = skullOwner(teamConfig, "backup-list.item.", backupListItemDefinition.getSkullOwner());
        backupListItemName = teamConfig.getString("backup-list.item.name", "&e{owner} &7- &f{plot}");
        backupListItemLore = teamConfig.getStringList("backup-list.item.lore");
        loadButtonsFromSection(teamConfig, "backup-list.buttons", backupListButtonsBySlot, backupListSize);
        loadDecorationsFromSection(teamConfig, "backup-list.decorations", backupListButtonsBySlot, backupListSize);
        backupListLoaded = true;
    }

    public void openMenu(final Player player) {
        if (bedrockFormsEnabled && floodgateHook.isBedrockPlayer(player)) {
            if (openBedrockMainMenu(player)) {
                return;
            }
            if (!plugin.getConfig().getBoolean("bedrock.fallback-to-java-gui", true)) {
                return;
            }
        }
        openMainMenu(player);
    }

    public boolean openBedrockMainMenu(final Player player) {
        if (!bedrockMainLoaded) {
            languageManager.send(player, "menu-missing");
            return false;
        }
        if (!floodgateHook.isBedrockPlayer(player)) {
            languageManager.send(player, "bedrock-only");
            return false;
        }

        final List<MenuButton> buttons = visibleBedrockMainActionButtons(player);
        final List<String> labels = new ArrayList<>();
        for (final MenuButton button : buttons) {
            labels.add(bedrockLabel(player, button));
        }
        final String title = Text.color(placeholderHook.apply(player, bedrockMainTitle));
        final String content = Text.color(placeholderHook.apply(player, bedrockMainContent));
        final boolean sent = floodgateHook.sendSimpleForm(player, title, content, labels, clickedButton -> {
            if (clickedButton < 0 || clickedButton >= buttons.size()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    executeButtonCommands(player, buttons.get(clickedButton), bedrockClickSound);
                }
            });
        });
        if (!sent) {
            languageManager.send(player, "bedrock-form-failed");
        }
        return sent;
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

        final List<MenuButton> buttons = visibleMainButtons(player);
        if (mainAnimationEnabled) {
            player.openInventory(inventory);
            playSound(player, mainOpenSound);
            animateButtons(player, inventory, buttons);
            return;
        }

        for (final MenuButton button : buttons) {
            inventory.setItem(button.getSlot(), createButtonItem(player, button));
        }

        player.openInventory(inventory);
        playSound(player, mainOpenSound);
    }

    private List<MenuButton> visibleMainButtons(final Player player) {
        final List<MenuButton> buttons = new ArrayList<>(mainButtonsBySlot.values());
        buttons.sort((first, second) -> Integer.compare(first.getSlot(), second.getSlot()));
        final List<MenuButton> visible = new ArrayList<>();
        for (final MenuButton button : buttons) {
            if (!canSee(player, button)) {
                continue;
            }
            if (isHiddenMainButton(button)) {
                continue;
            }
            visible.add(button);
        }
        return visible;
    }

    private List<MenuButton> visibleMainActionButtons(final Player player) {
        final Set<String> seen = new LinkedHashSet<>();
        final List<MenuButton> buttons = new ArrayList<>();
        for (final MenuButton button : visibleMainButtons(player)) {
            if (button.getCommands().isEmpty() && !button.isCloseInventory()) {
                continue;
            }
            if (seen.add(button.getId().toLowerCase(Locale.ROOT))) {
                buttons.add(button);
            }
        }
        return buttons;
    }

    private List<MenuButton> visibleBedrockMainActionButtons(final Player player) {
        final List<MenuButton> buttons = new ArrayList<>(bedrockMainButtonsByOrder.values());
        buttons.sort((first, second) -> Integer.compare(first.getSlot(), second.getSlot()));
        final List<MenuButton> visible = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final MenuButton button : buttons) {
            if (button.getCommands().isEmpty() && !button.isCloseInventory()) {
                continue;
            }
            if (!canSee(player, button)) {
                continue;
            }
            if (isHiddenBedrockMainButton(button)) {
                continue;
            }
            if (seen.add(button.getId().toLowerCase(Locale.ROOT))) {
                visible.add(button);
            }
        }
        return visible;
    }

    private String bedrockLabel(final Player player, final MenuButton button) {
        final String configured = button.getBedrockLabel();
        final String label = configured == null || configured.trim().isEmpty() ? button.getName() : configured;
        return Text.color(placeholderHook.apply(player, label));
    }

    private void animateButtons(final Player player, final Inventory inventory, final List<MenuButton> buttons) {
        long delay = 0L;
        for (final MenuButton button : buttons) {
            final long scheduledDelay = delay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (player.getOpenInventory().getTopInventory() != inventory) {
                    return;
                }
                inventory.setItem(button.getSlot(), createButtonItem(player, button));
            }, scheduledDelay);
            delay += mainAnimationDelayTicks;
        }
    }

    public void openMyPlotsMenu(final Player player) {
        openMyPlotsMenu(player, 1, "name", "all");
    }

    public void openActionMenu(final Player player, final String menuId) {
        openActionMenu(player, menuId, "");
    }

    public void openActionMenu(final Player player, final String menuId, final String requestedTabId) {
        final ActionMenu menu = actionMenus.get(normalizeActionMenuId(menuId));
        if (menu == null || !menu.isLoaded()) {
            languageManager.send(player, "menu-missing");
            return;
        }
        if (!menu.getPermission().isEmpty() && !hasConfiguredPermission(player, menu.getPermission())) {
            languageManager.send(player, "no-permission");
            return;
        }
        if (menu.isRequirePlot() && !flagService.isOnPlot(player)) {
            languageManager.send(player, "no-plot");
            return;
        }
        if (floodgateHook.isBedrockPlayer(player) && menu.isBedrockEnabled()) {
            if (openBedrockActionMenu(player, menu, requestedTabId)) {
                return;
            }
        }
        openJavaActionMenu(player, menu, requestedTabId);
    }

    private void openJavaActionMenu(final Player player, final ActionMenu menu, final String requestedTabId) {
        final SettingsTab tab = actionMenuTab(menu, requestedTabId);
        final Map<String, String> placeholders = actionMenuPlaceholders(menu, tab);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("action-" + menu.getId(), tab == null ? "" : tab.getId()),
                menu.getSize(),
                Text.color(placeholderHook.apply(player, applyPlaceholders(menu.getTitle(), placeholders)))
        );
        if (menu.getFiller() != null) {
            for (int slot = 0; slot < menu.getSize(); slot++) {
                inventory.setItem(slot, menu.getFiller());
            }
        }
        for (final MenuButton decoration : menu.getDecorationsBySlot().values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : menu.getButtonsBySlot().values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }
        if (tab != null) {
            for (final SettingsTab actionTab : menu.getTabs().values()) {
                for (final MenuButton selector : actionTab.getSelectors()) {
                    if (canSee(player, selector)) {
                        inventory.setItem(selector.getSlot(), createButtonItem(player, selector, placeholders));
                    }
                }
            }
            for (final MenuButton button : tab.getButtonsBySlot().values()) {
                if (canSee(player, button)) {
                    inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
                }
            }
        }
        player.openInventory(inventory);
    }

    private boolean openBedrockActionMenu(final Player player, final ActionMenu menu, final String requestedTabId) {
        final SettingsTab tab = actionMenuTab(menu, requestedTabId);
        final Map<String, String> placeholders = actionMenuPlaceholders(menu, tab);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        if (tab != null) {
            for (final SettingsTab actionTab : menu.getTabs().values()) {
                for (final MenuButton selector : actionTab.getSelectors()) {
                    if (!canSee(player, selector)) {
                        continue;
                    }
                    labels.add(bedrockLabel(player, selector, placeholders));
                    actions.add(() -> openActionMenu(player, menu.getId(), actionTab.getId()));
                }
            }
        }
        for (final MenuButton button : menu.getButtonsBySlot().values()) {
            if (canSee(player, button)) {
                labels.add(bedrockLabel(player, button, placeholders));
                actions.add(() -> executeButtonCommands(player, button));
            }
        }
        if (tab != null) {
            for (final MenuButton button : tab.getButtonsBySlot().values()) {
                if (canSee(player, button)) {
                    labels.add(bedrockLabel(player, button, placeholders));
                    actions.add(() -> executeButtonCommands(player, button));
                }
            }
        }
        final String title = Text.color(placeholderHook.apply(player, applyPlaceholders(menu.getBedrockTitle(), placeholders)));
        final String content = Text.color(placeholderHook.apply(player, applyPlaceholders(menu.getBedrockContent(), placeholders)));
        return floodgateHook.sendSimpleForm(player, title, content, labels, clickedButton -> {
            if (clickedButton < 0 || clickedButton >= actions.size()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, actions.get(clickedButton));
        });
    }

    public void openMyPlotsMenu(final Player player, final int page, final String sort, final String filter) {
        if (floodgateHook.isBedrockPlayer(player) && bedrockMyPlotsLoaded) {
            if (openBedrockMyPlotsMenu(player, page, sort, filter)) {
                return;
            }
        }
        openJavaMyPlotsMenu(player, page, sort, filter);
    }

    public void openJavaMyPlotsMenu(final Player player, final int page, final String sort, final String filter) {
        if (!player.hasPermission("craftplayplotextras.myplots")) {
            languageManager.send(player, "no-permission");
            return;
        }
        if (!myPlotsLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }

        final int normalizedPage = Math.max(1, page);
        final String normalizedSort = normalizeSort(sort);
        final String normalizedFilter = normalizeFilter(filter);
        final List<OwnedPlot> plots = filteredAndSortedPlots(player, normalizedSort, normalizedFilter);
        final int pageSize = Math.max(1, myPlotsSlots.size());
        final int maxPage = Math.max(1, (int) Math.ceil(plots.size() / (double) pageSize));
        final int actualPage = Math.min(normalizedPage, maxPage);
        final Map<String, String> menuPlaceholders = myPlotsMenuPlaceholders(actualPage, maxPage, normalizedSort, normalizedFilter, plots.size());
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("myplots", actualPage, normalizedSort + "|" + normalizedFilter),
                myPlotsSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(myPlotsTitle, menuPlaceholders)))
        );

        if (myPlotsFiller != null) {
            for (int slot = 0; slot < myPlotsSize; slot++) {
                inventory.setItem(slot, myPlotsFiller);
            }
        }
        for (final MenuButton decoration : myPlotsDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, menuPlaceholders));
            }
        }
        for (final MenuButton button : myPlotsButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, menuPlaceholders));
            }
        }

        final int start = (actualPage - 1) * pageSize;
        final int end = Math.min(plots.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            final int slot = myPlotsSlots.get(index - start);
            inventory.setItem(slot, createMyPlotItem(player, plots.get(index)));
        }
        player.openInventory(inventory);
    }

    private boolean openBedrockMyPlotsMenu(final Player player, final int page, final String sort, final String filter) {
        if (!bedrockMyPlotsLoaded) {
            return false;
        }
        final int normalizedPage = Math.max(1, page);
        final String normalizedSort = normalizeSort(sort);
        final String normalizedFilter = normalizeFilter(filter);
        final List<OwnedPlot> plots = filteredAndSortedPlots(player, normalizedSort, normalizedFilter);
        final int pageSize = Math.max(1, bedrockMyPlotsConfig.getInt("page-size", 8));
        final int maxPage = Math.max(1, (int) Math.ceil(plots.size() / (double) pageSize));
        final int actualPage = Math.min(normalizedPage, maxPage);
        final Map<String, String> placeholders = myPlotsMenuPlaceholders(actualPage, maxPage, normalizedSort, normalizedFilter, plots.size());
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        addBedrockAction(player, labels, actions, "buttons.sort-name", placeholders, () -> openMyPlotsMenu(player, 1, "name", normalizedFilter));
        addBedrockAction(player, labels, actions, "buttons.sort-size", placeholders, () -> openMyPlotsMenu(player, 1, "size", normalizedFilter));
        addBedrockAction(player, labels, actions, "buttons.sort-activity", placeholders, () -> openMyPlotsMenu(player, 1, "activity", normalizedFilter));
        addBedrockAction(player, labels, actions, "buttons.sort-rating", placeholders, () -> openMyPlotsMenu(player, 1, "rating", normalizedFilter));
        addBedrockAction(player, labels, actions, "buttons.sort-category", placeholders, () -> openMyPlotsMenu(player, 1, "category", normalizedFilter));
        addBedrockAction(player, labels, actions, "buttons.filter-all", placeholders, () -> openMyPlotsMenu(player, 1, normalizedSort, "all"));
        addBedrockAction(player, labels, actions, "buttons.filter-favorites", placeholders, () -> openMyPlotsMenu(player, 1, normalizedSort, "favorites"));
        addBedrockAction(player, labels, actions, "buttons.filter-public", placeholders, () -> openMyPlotsMenu(player, 1, normalizedSort, "public"));
        addBedrockAction(player, labels, actions, "buttons.filter-private", placeholders, () -> openMyPlotsMenu(player, 1, normalizedSort, "private"));
        if (actualPage > 1) {
            addBedrockAction(player, labels, actions, "buttons.previous", placeholders, () -> openMyPlotsMenu(player, actualPage - 1, normalizedSort, normalizedFilter));
        }
        if (actualPage < maxPage) {
            addBedrockAction(player, labels, actions, "buttons.next", placeholders, () -> openMyPlotsMenu(player, actualPage + 1, normalizedSort, normalizedFilter));
        }

        final int start = (actualPage - 1) * pageSize;
        final int end = Math.min(plots.size(), start + pageSize);
        final String plotLabel = bedrockMyPlotsConfig.getString("plot-label", "{favorite} {name} - {visibility}");
        for (int index = start; index < end; index++) {
            final OwnedPlot plot = plots.get(index);
            labels.add(Text.color(placeholderHook.apply(player, applyPlaceholders(plotLabel, plotPlaceholders(player, plot)))));
            actions.add(() -> openBedrockMyPlotDetail(player, plot.getKey(), actualPage, normalizedSort, normalizedFilter));
        }
        addBedrockAction(player, labels, actions, "buttons.back", placeholders, () -> openMenu(player));

        final String title = Text.color(placeholderHook.apply(player, applyPlaceholders(bedrockMyPlotsConfig.getString("title", "Meine Plots"), placeholders)));
        final String content = Text.color(placeholderHook.apply(player, applyPlaceholders(bedrockMyPlotsConfig.getString("content", ""), placeholders)));
        return floodgateHook.sendSimpleForm(player, title, content, labels, clickedButton -> {
            if (clickedButton < 0 || clickedButton >= actions.size()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, actions.get(clickedButton));
        });
    }

    private void openBedrockMyPlotDetail(
            final Player player,
            final String plotKey,
            final int page,
            final String sort,
            final String filter
    ) {
        final java.util.Optional<OwnedPlot> optionalPlot = plotService.ownedPlot(player, plotKey);
        if (!optionalPlot.isPresent()) {
            languageManager.send(player, "myplots-plot-not-found");
            return;
        }
        final OwnedPlot plot = optionalPlot.get();
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        addBedrockAction(player, labels, actions, "detail.buttons.teleport", placeholders, () -> teleportToPlot(player, plot));
        addBedrockAction(player, labels, actions, "detail.buttons.favorite", placeholders, () -> {
            toggleFavorite(player, plot);
            openBedrockMyPlotDetail(player, plotKey, page, sort, filter);
        });
        addBedrockAction(player, labels, actions, "detail.buttons.visibility", placeholders, () -> {
            toggleVisibility(player, plot);
            openBedrockMyPlotDetail(player, plotKey, page, sort, filter);
        });
        addBedrockAction(player, labels, actions, "detail.buttons.category", placeholders, () -> {
            cycleCategory(player, plot);
            openBedrockMyPlotDetail(player, plotKey, page, sort, filter);
        });
        addBedrockAction(player, labels, actions, "detail.buttons.note", placeholders, () -> {
            runCommand(player, "chat-input:chat-plot-note:plot-data-note:" + plotKey + ":{input}");
        });
        final List<String> availableTags = availableTags();
        for (final String tag : availableTags) {
            final Map<String, String> tagPlaceholders = new HashMap<>(plotPlaceholders(player, plot));
            tagPlaceholders.put("tag", tag);
            tagPlaceholders.put("tag_status", plotDataStore.metadata(plotKey).getTags().contains(tag)
                    ? text("myplots-tag-active", "Aktiv")
                    : text("myplots-tag-inactive", "Inaktiv"));
            final String label = bedrockMyPlotsConfig.getString("detail.tag-label", "{tag}: {tag_status}");
            labels.add(Text.color(placeholderHook.apply(player, applyPlaceholders(label, tagPlaceholders))));
            actions.add(() -> {
                toggleTag(player, plot, tag);
                openBedrockMyPlotDetail(player, plotKey, page, sort, filter);
            });
        }
        addBedrockAction(player, labels, actions, "detail.buttons.back", placeholders, () -> openMyPlotsMenu(player, page, sort, filter));

        final String title = Text.color(placeholderHook.apply(player, applyPlaceholders(bedrockMyPlotsConfig.getString("detail.title", "{name}"), placeholders)));
        final String content = Text.color(placeholderHook.apply(player, applyPlaceholders(bedrockMyPlotsConfig.getString("detail.content", ""), placeholders)));
        floodgateHook.sendSimpleForm(player, title, content, labels, clickedButton -> {
            if (clickedButton < 0 || clickedButton >= actions.size()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, actions.get(clickedButton));
        });
    }

    private void addBedrockAction(
            final Player player,
            final List<String> labels,
            final List<Runnable> actions,
            final String path,
            final Map<String, String> placeholders,
            final Runnable action
    ) {
        if (!bedrockMyPlotsConfig.getBoolean(path + ".enabled", true)) {
            return;
        }
        final String permission = bedrockMyPlotsConfig.getString(path + ".permission", "");
        if (permission != null && !permission.trim().isEmpty() && !player.hasPermission(permission)) {
            return;
        }
        labels.add(Text.color(placeholderHook.apply(player, applyPlaceholders(bedrockMyPlotsConfig.getString(path + ".label", path), placeholders))));
        actions.add(action);
    }

    private void openMyPlotDetailMenu(
            final Player player,
            final String plotKey,
            final int page,
            final String sort,
            final String filter
    ) {
        final java.util.Optional<OwnedPlot> optionalPlot = plotService.ownedPlot(player, plotKey);
        if (!optionalPlot.isPresent()) {
            languageManager.send(player, "myplots-plot-not-found");
            return;
        }
        final OwnedPlot plot = optionalPlot.get();
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        placeholders.put("page", String.valueOf(page));
        placeholders.put("sort", sort);
        placeholders.put("filter", filter);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("myplot-detail", page, plotKey + "|" + sort + "|" + filter),
                myPlotDetailSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(myPlotDetailTitle, placeholders)))
        );
        if (myPlotDetailFiller != null) {
            for (int slot = 0; slot < myPlotDetailSize; slot++) {
                inventory.setItem(slot, myPlotDetailFiller);
            }
        }
        for (final MenuButton decoration : myPlotDetailDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        final int previewSlot = myPlotsConfig.getInt("detail.preview.slot", 13);
        if (previewSlot >= 0 && previewSlot < myPlotDetailSize) {
            inventory.setItem(previewSlot, createDynamicItem(player, myPlotsConfig, "detail.preview.", Material.FILLED_MAP, placeholders));
        }
        for (final MenuButton button : myPlotDetailButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }
        final List<String> tags = availableTags();
        for (int index = 0; index < tags.size() && index < myPlotDetailTagSlots.size(); index++) {
            final String tag = tags.get(index);
            final int slot = myPlotDetailTagSlots.get(index);
            final Map<String, String> tagPlaceholders = new HashMap<>(placeholders);
            final boolean active = plotDataStore.metadata(plotKey).getTags().contains(tag);
            tagPlaceholders.put("tag", tag);
            tagPlaceholders.put("tag_status", active ? text("myplots-tag-active", "Aktiv") : text("myplots-tag-inactive", "Inaktiv"));
            inventory.setItem(slot, createDynamicItem(player, myPlotsConfig, active ? "detail.tag-active." : "detail.tag-inactive.", active ? Material.NAME_TAG : Material.PAPER, tagPlaceholders));
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

        if (floodgateHook.isBedrockPlayer(player)) {
            if (openBedrockTeamMenu(player)) {
                return;
            }
            languageManager.send(player, "bedrock-form-failed");
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

    private boolean openBedrockTeamMenu(final Player player) {
        final List<MenuButton> buttons = new ArrayList<>(teamButtonsBySlot.values());
        buttons.sort(Comparator.comparingInt(MenuButton::getSlot));
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final Map<String, String> placeholders = new HashMap<>();
        for (final MenuButton button : buttons) {
            if (!canSee(player, button)) {
                continue;
            }
            if (button.getCommands().isEmpty() && !button.isCloseInventory()) {
                continue;
            }
            final String label = button.getBedrockLabel().isEmpty() ? button.getName() : button.getBedrockLabel();
            labels.add(Text.color(placeholderHook.apply(player, applyPlaceholders(label, placeholders))));
            actions.add(() -> executeCommands(player, true, button.getCommands()));
        }
        return floodgateHook.sendSimpleForm(
                player,
                Text.color(placeholderHook.apply(player, teamTitle)),
                "",
                labels,
                clickedButton -> {
                    if (clickedButton < 0 || clickedButton >= actions.size()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, actions.get(clickedButton));
                }
        );
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
        if ("myplots".equalsIgnoreCase(holder.getMenuId())) {
            handleMyPlotsClick(player, holder.getPage(), holder.getTabId(), event.getSlot(), event.getClick());
            return;
        }
        if ("myplot-detail".equalsIgnoreCase(holder.getMenuId())) {
            handleMyPlotDetailClick(player, holder.getPage(), holder.getTabId(), event.getSlot());
            return;
        }
        if (holder.getMenuId().toLowerCase(Locale.ROOT).startsWith("action-")) {
            handleActionMenuClick(player, holder.getMenuId().substring("action-".length()), holder.getTabId(), event.getSlot());
            return;
        }
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

        executeButtonCommands(player, button, mainClickSound);
    }

    @EventHandler
    public void onChatInput(final AsyncPlayerChatEvent event) {
        final PendingChatInput pending = pendingChatInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        final String input = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> completeChatInput(event.getPlayer(), pending, input));
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        pendingChatInputs.remove(event.getPlayer().getUniqueId());
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
            final MaterialDefinition materialDefinition = materialDefinition(menuConfig, path, Material.STONE_BUTTON);
            final Material material = materialDefinition.getMaterial();
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String skullOwner = skullOwner(menuConfig, path, materialDefinition.getSkullOwner());
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = configuredCommands(menuConfig, path);
            final boolean close = menuConfig.getBoolean(path + "close", true);
            final String permission = menuConfig.getString(path + "permission", "");
            final MenuSound clickSound = loadButtonSound(menuConfig, path);
            final String bedrockLabel = menuConfig.getString(path + "bedrock-label", "");

            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Menübutton", id)) {
                target.put(slot, new MenuButton(id, slot, material, headDatabaseId, skullOwner, name, lore, commands, close, permission, clickSound, bedrockLabel));
            }
        }
    }

    private void loadBedrockButtons(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("buttons");
        if (section == null) {
            return;
        }

        int fallbackOrder = 0;
        for (final String id : section.getKeys(false)) {
            final String path = "buttons." + id + ".";
            final int order = menuConfig.contains(path + "order")
                    ? menuConfig.getInt(path + "order")
                    : menuConfig.getInt(path + "slot", fallbackOrder);
            fallbackOrder++;
            final String label = menuConfig.getString(path + "label",
                    menuConfig.getString(path + "name", "&a" + id));
            final List<String> commands = configuredCommands(menuConfig, path);
            final boolean close = menuConfig.getBoolean(path + "close", true);
            final String permission = menuConfig.getString(path + "permission", "");
            final MenuSound clickSound = loadButtonSound(menuConfig, path);
            bedrockMainButtonsByOrder.put(order, new MenuButton(
                    id,
                    order,
                    Material.STONE_BUTTON,
                    "",
                    "",
                    label,
                    Collections.emptyList(),
                    commands,
                    close,
                    permission,
                    clickSound,
                    label
            ));
        }
    }

    private void loadMyPlotsMenus() {
        myPlotsConfig = loadMenuConfig(plugin.getConfig().getString("gui.my-plots-menu", "myplots.yml"));
        if (myPlotsConfig != null) {
            myPlotsTitle = myPlotsConfig.getString("title", "&8Meine Plots &7- Seite {page}");
            myPlotsSize = normalizeSize(myPlotsConfig.getInt("size", 54));
            myPlotsFiller = createFiller(myPlotsConfig);
            myPlotsSlots = myPlotsConfig.getIntegerList("plot-slots");
            if (myPlotsSlots.isEmpty()) {
                myPlotsSlots = defaultListSlots(myPlotsSize);
            }
            loadButtonsFromSection(myPlotsConfig, "buttons", myPlotsButtonsBySlot, myPlotsSize);
            loadDecorationsFromSection(myPlotsConfig, "decorations", myPlotsDecorationsBySlot, myPlotsSize);

            myPlotDetailTitle = myPlotsConfig.getString("detail.title", "&8Plot: {name}");
            myPlotDetailSize = normalizeSize(myPlotsConfig.getInt("detail.size", 54));
            myPlotDetailFiller = createFiller(myPlotsConfig, "detail.filler");
            myPlotDetailTagSlots = myPlotsConfig.getIntegerList("detail.tag-slots");
            loadButtonsFromSection(myPlotsConfig, "detail.buttons", myPlotDetailButtonsBySlot, myPlotDetailSize);
            loadDecorationsFromSection(myPlotsConfig, "detail.decorations", myPlotDetailDecorationsBySlot, myPlotDetailSize);
            myPlotsLoaded = true;
        }

        bedrockMyPlotsConfig = loadMenuConfig(plugin.getConfig().getString("gui.bedrock-my-plots-menu", "bedrock-myplots.yml"));
        bedrockMyPlotsLoaded = bedrockMyPlotsConfig != null && bedrockMyPlotsConfig.getBoolean("enabled", true);
    }

    private void loadActionMenus() {
        loadActionMenu("create", plugin.getConfig().getString("gui.create-menu", "create.yml"));
        loadActionMenu("members", plugin.getConfig().getString("gui.members-menu", "members.yml"));
        loadActionMenu("search", plugin.getConfig().getString("gui.search-menu", "search.yml"));
        loadActionMenu("community", plugin.getConfig().getString("gui.community-menu", "community.yml"));
        loadActionMenu("reports", plugin.getConfig().getString("gui.reports-menu", "reports.yml"));
        loadActionMenu("history", plugin.getConfig().getString("gui.history-menu", "history.yml"));
        loadActionMenu("danger", plugin.getConfig().getString("gui.danger-menu", "danger.yml"));
        loadActionMenu("help", plugin.getConfig().getString("gui.help-menu", "help.yml"));
    }

    private void loadActionMenu(final String id, final String fileName) {
        final YamlConfiguration menuConfig = loadMenuConfig(fileName);
        if (menuConfig == null) {
            return;
        }

        final int menuSize = normalizeSize(menuConfig.getInt("size", 54));
        final Map<Integer, MenuButton> buttons = new HashMap<>();
        final Map<Integer, MenuButton> decorations = new HashMap<>();
        final Map<String, SettingsTab> tabs = new LinkedHashMap<>();
        loadButtonsFromSection(menuConfig, "buttons", buttons, menuSize);
        loadDecorationsFromSection(menuConfig, "decorations", decorations, menuSize);
        loadActionTabs(id, menuConfig, menuSize, tabs);
        actionMenus.put(id, new ActionMenu(
                id,
                true,
                menuConfig.getString("title", "&8" + id),
                menuConfig.getString("bedrock.title", menuConfig.getString("title", id)),
                menuConfig.getString("bedrock.content", menuConfig.getString("content", "")),
                menuSize,
                createFiller(menuConfig),
                buttons,
                decorations,
                tabs,
                menuConfig.getString("default-tab", ""),
                menuConfig.getBoolean("require-plot", false),
                menuConfig.getBoolean("bedrock.enabled", true),
                menuConfig.getString("permission", "")
        ));
    }

    private void loadActionTabs(
            final String menuId,
            final YamlConfiguration menuConfig,
            final int menuSize,
            final Map<String, SettingsTab> target
    ) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("tabs");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final String path = "tabs." + id + ".";
            final MaterialDefinition materialDefinition = materialDefinition(menuConfig, path, Material.BOOK);
            final Material material = materialDefinition.getMaterial();
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String skullOwner = skullOwner(menuConfig, path, materialDefinition.getSkullOwner());
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = configuredCommands(menuConfig, path);
            final String permission = menuConfig.getString(path + "permission", "");
            final MenuSound clickSound = loadButtonSound(menuConfig, path);
            final String bedrockLabel = menuConfig.getString(path + "bedrock-label", "");
            final List<MenuButton> selectors = new ArrayList<>();
            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Menü-Tab", id)) {
                selectors.add(new MenuButton(
                        id,
                        slot,
                        material,
                        headDatabaseId,
                        skullOwner,
                        name,
                        lore,
                        commands.isEmpty() ? Collections.singletonList("open-menu:" + menuId + ":" + id) : commands,
                        false,
                        permission,
                        clickSound,
                        bedrockLabel
                ));
            }
            if (selectors.isEmpty()) {
                continue;
            }
            final Map<Integer, MenuButton> tabButtons = new HashMap<>();
            loadButtonsFromSection(menuConfig, path + "buttons", tabButtons, menuSize);
            loadDecorationsFromSection(menuConfig, path + "decorations", tabButtons, menuSize);
            target.put(id.toLowerCase(Locale.ROOT), new SettingsTab(id.toLowerCase(Locale.ROOT), selectors, tabButtons));
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
            final MaterialDefinition materialDefinition = materialDefinition(menuConfig, path, Material.BOOK);
            final Material material = materialDefinition.getMaterial();
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String skullOwner = skullOwner(menuConfig, path, materialDefinition.getSkullOwner());
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = configuredCommands(menuConfig, path);
            final String permission = menuConfig.getString(path + "permission", "");
            final MenuSound clickSound = loadButtonSound(menuConfig, path);
            final String bedrockLabel = menuConfig.getString(path + "bedrock-label", "");
            final List<MenuButton> selectors = new ArrayList<>();
            for (final int slot : configuredSlots(menuConfig, path, settingsSize, "Einstellungstab", id)) {
                selectors.add(new MenuButton(
                        id,
                        slot,
                        material,
                        headDatabaseId,
                        skullOwner,
                        name,
                        lore,
                        commands.isEmpty() ? Collections.singletonList("open-menu:settings:" + id) : commands,
                        false,
                        permission,
                        clickSound,
                        bedrockLabel
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
            final MaterialDefinition materialDefinition = materialDefinition(menuConfig, path, Material.STONE_BUTTON);
            final Material material = materialDefinition.getMaterial();
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String skullOwner = skullOwner(menuConfig, path, materialDefinition.getSkullOwner());
            final String name = menuConfig.getString(path + "name", "&a" + id);
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final List<String> commands = configuredCommands(menuConfig, path);
            final boolean close = menuConfig.getBoolean(path + "close", true);
            final String permission = menuConfig.getString(path + "permission", "");
            final MenuSound clickSound = loadButtonSound(menuConfig, path);
            final String bedrockLabel = menuConfig.getString(path + "bedrock-label", "");

            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Menübutton", id)) {
                target.put(slot, new MenuButton(id, slot, material, headDatabaseId, skullOwner, name, lore, commands, close, permission, clickSound, bedrockLabel));
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
            final MaterialDefinition materialDefinition = materialDefinition(menuConfig, path, Material.GRAY_STAINED_GLASS_PANE);
            final Material material = materialDefinition.getMaterial();
            final String headDatabaseId = headDatabaseId(menuConfig, path);
            final String skullOwner = skullOwner(menuConfig, path, materialDefinition.getSkullOwner());
            final String name = menuConfig.getString(path + "name", "&r");
            final List<String> lore = menuConfig.getStringList(path + "lore");
            final String permission = menuConfig.getString(path + "permission", "");

            for (final int slot : configuredSlots(menuConfig, path, menuSize, "Deko-Item", id)) {
                target.put(slot, new MenuButton(id, slot, material, headDatabaseId, skullOwner, name, lore, Collections.emptyList(), false, permission));
            }
        }
    }

    private MenuSound loadButtonSound(final YamlConfiguration menuConfig, final String path) {
        if (menuConfig.contains(path + "click-sound")) {
            return loadSound(menuConfig, path + "click-sound");
        }
        if (menuConfig.contains(path + "sound")) {
            return loadSound(menuConfig, path + "sound");
        }
        return null;
    }

    private MenuSound loadSound(final YamlConfiguration menuConfig, final String path) {
        if (!menuConfig.contains(path)) {
            return null;
        }
        if (menuConfig.isString(path)) {
            return new MenuSound(true, menuConfig.getString(path, ""), 1.0F, 1.0F);
        }
        final boolean enabled = menuConfig.getBoolean(path + ".enabled", true);
        final String soundName = menuConfig.getString(path + ".sound", menuConfig.getString(path + ".name", ""));
        final float volume = (float) menuConfig.getDouble(path + ".volume", 1.0D);
        final float pitch = (float) menuConfig.getDouble(path + ".pitch", 1.0D);
        return new MenuSound(enabled, soundName, volume, pitch);
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
            applySkullOwner(player, meta, button.getSkullOwner(), replacements);
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
            applySkullOwner(player, meta, backupListItemSkullOwner, placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(backupListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(backupListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applySkullOwner(
            final Player player,
            final ItemMeta meta,
            final String configuredOwner,
            final Map<String, String> replacements
    ) {
        if (!(meta instanceof SkullMeta) || configuredOwner == null || configuredOwner.trim().isEmpty()) {
            return;
        }
        String owner = applyPlaceholders(configuredOwner, replacements)
                .replace("{player}", player.getName())
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .trim();
        owner = ChatColor.stripColor(placeholderHook.apply(player, owner))
                .replace("\"", "")
                .replace("'", "")
                .trim();
        if (owner.isEmpty()) {
            return;
        }
        final SkullMeta skullMeta = (SkullMeta) meta;
        if (owner.equalsIgnoreCase(player.getName())) {
            skullMeta.setOwningPlayer(player);
            return;
        }
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
    }

    private boolean canSee(final Player player, final MenuButton button) {
        if (button.getPermission() == null || button.getPermission().trim().isEmpty()) {
            return true;
        }
        final String permission = button.getPermission().trim();
        return hasConfiguredPermission(player, permission);
    }

    private boolean hasConfiguredPermission(final Player player, final String permission) {
        if (permission.toLowerCase(Locale.ROOT).startsWith("plots.")) {
            return flagService.hasPermission(player, permission);
        }
        return player.hasPermission(permission);
    }

    private boolean canSeeFlag(final Player player, final FlagMenuEntry flagEntry) {
        if (flagEntry.getPermission() != null && !flagEntry.getPermission().trim().isEmpty()) {
            return hasConfiguredPermission(player, flagEntry.getPermission().trim());
        }
        return flagService.hasAnyFlagPermission(player, flagEntry.getFlag());
    }

    private void handleMyPlotsClick(
            final Player player,
            final int page,
            final String state,
            final int slot,
            final ClickType clickType
    ) {
        final String sort = statePart(state, 0, "name");
        final String filter = statePart(state, 1, "all");
        final MenuButton button = myPlotsButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            final List<OwnedPlot> plots = filteredAndSortedPlots(player, sort, filter);
            final int pageSize = myPlotsSlots == null || myPlotsSlots.isEmpty() ? 1 : myPlotsSlots.size();
            final int maxPage = Math.max(1, (int) Math.ceil(plots.size() / (double) pageSize));
            executeCommands(player, button.isCloseInventory(), myPlotsButtonCommands(button, page, maxPage, sort, filter, plots.size()));
            return;
        }

        final OwnedPlot plot = plotAt(player, page, sort, filter, slot);
        if (plot == null) {
            return;
        }
        if (clickType == ClickType.SHIFT_LEFT) {
            toggleFavorite(player, plot);
            openJavaMyPlotsMenu(player, page, sort, filter);
            return;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            toggleVisibility(player, plot);
            openJavaMyPlotsMenu(player, page, sort, filter);
            return;
        }
        if (clickType.isRightClick()) {
            openMyPlotDetailMenu(player, plot.getKey(), page, sort, filter);
            return;
        }
        teleportToPlot(player, plot);
    }

    private void handleMyPlotDetailClick(final Player player, final int page, final String state, final int slot) {
        final String plotKey = statePart(state, 0, "");
        final String sort = statePart(state, 1, "name");
        final String filter = statePart(state, 2, "all");
        final java.util.Optional<OwnedPlot> optionalPlot = plotService.ownedPlot(player, plotKey);
        if (!optionalPlot.isPresent()) {
            languageManager.send(player, "myplots-plot-not-found");
            return;
        }
        final OwnedPlot plot = optionalPlot.get();

        final int tagIndex = myPlotDetailTagSlots == null ? -1 : myPlotDetailTagSlots.indexOf(slot);
        if (tagIndex >= 0 && tagIndex < availableTags().size()) {
            toggleTag(player, plot, availableTags().get(tagIndex));
            openMyPlotDetailMenu(player, plotKey, page, sort, filter);
            return;
        }

        final MenuButton button = myPlotDetailButtonsBySlot.get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        final String id = button.getId().toLowerCase(Locale.ROOT);
        if ("teleport".equals(id)) {
            teleportToPlot(player, plot);
            return;
        }
        if ("favorite".equals(id)) {
            toggleFavorite(player, plot);
            openMyPlotDetailMenu(player, plotKey, page, sort, filter);
            return;
        }
        if ("visibility".equals(id)) {
            toggleVisibility(player, plot);
            openMyPlotDetailMenu(player, plotKey, page, sort, filter);
            return;
        }
        if ("category".equals(id)) {
            cycleCategory(player, plot);
            openMyPlotDetailMenu(player, plotKey, page, sort, filter);
            return;
        }
        executeCommands(player, button.isCloseInventory(), detailButtonCommands(player, button, plot, page, sort, filter));
    }

    private void handleActionMenuClick(final Player player, final String menuId, final String tabId, final int slot) {
        final ActionMenu menu = actionMenus.get(normalizeActionMenuId(menuId));
        if (menu == null) {
            return;
        }
        final SettingsTab tab = actionMenuTab(menu, tabId);
        for (final SettingsTab actionTab : menu.getTabs().values()) {
            for (final MenuButton selector : actionTab.getSelectors()) {
                if (selector.getSlot() == slot && canSee(player, selector)) {
                    executeButtonCommands(player, selector);
                    return;
                }
            }
        }

        final MenuButton rootButton = menu.getButtonsBySlot().get(slot);
        if (rootButton != null && canSee(player, rootButton)) {
            executeButtonCommands(player, rootButton);
            return;
        }

        if (tab == null) {
            return;
        }
        final MenuButton tabButton = tab.getButtonsBySlot().get(slot);
        if (tabButton != null && canSee(player, tabButton)) {
            executeButtonCommands(player, tabButton);
        }
    }

    private void handleFlagsClick(final Player player, final int page, final int slot) {
        final MenuButton button = flagButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            executeButtonCommands(player, button);
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
                executeButtonCommands(player, selector);
                return;
            }
        }

        final SettingsTab tab = settingsTab(tabId);
        final MenuButton button = tab.getButtonsBySlot().get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        executeButtonCommands(player, button);
    }

    private void handleTeamClick(final Player player, final int slot) {
        final MenuButton button = teamButtonsBySlot.get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        executeButtonCommands(player, button);
    }

    private void handleBackupListClick(final Player player, final int page, final int slot) {
        final MenuButton button = backupListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            final List<String> commands = new ArrayList<>();
            for (final String command : button.getCommands()) {
                commands.add(command
                        .replace("{page}", String.valueOf(page))
                        .replace("{next_page}", String.valueOf(page + 1))
                        .replace("{previous_page}", String.valueOf(Math.max(1, page - 1))));
            }
            executeCommands(player, button.isCloseInventory(), commands);
            return;
        }

        final String backupId = backupIdAt(page, slot);
        if (backupId == null) {
            return;
        }
        player.closeInventory();
        backupService.requestRestore(player, backupId);
    }

    private OwnedPlot plotAt(final Player player, final int page, final String sort, final String filter, final int slot) {
        if (myPlotsSlots == null || myPlotsSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = myPlotsSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final List<OwnedPlot> plots = filteredAndSortedPlots(player, sort, filter);
        final int plotIndex = (Math.max(1, page) - 1) * myPlotsSlots.size() + slotIndex;
        if (plotIndex < 0 || plotIndex >= plots.size()) {
            return null;
        }
        return plots.get(plotIndex);
    }

    private List<OwnedPlot> filteredAndSortedPlots(final Player player, final String sort, final String filter) {
        final String normalizedSort = normalizeSort(sort);
        final String normalizedFilter = normalizeFilter(filter);
        final List<OwnedPlot> plots = new ArrayList<>();
        for (final OwnedPlot plot : plotService.ownedPlots(player)) {
            final PlotMetadata metadata = plotDataStore.metadata(plot.getKey());
            if ("favorites".equals(normalizedFilter) && !plotDataStore.isFavorite(player.getUniqueId(), plot.getKey())) {
                continue;
            }
            if ("public".equals(normalizedFilter) && !isPublic(plot, metadata)) {
                continue;
            }
            if ("private".equals(normalizedFilter) && isPublic(plot, metadata)) {
                continue;
            }
            if (normalizedFilter.startsWith("category:") && !metadata.getCategory().equalsIgnoreCase(normalizedFilter.substring("category:".length()))) {
                continue;
            }
            plots.add(plot);
        }
        plots.sort(plotComparator(player, normalizedSort));
        return plots;
    }

    private Comparator<OwnedPlot> plotComparator(final Player player, final String sort) {
        if ("size".equals(sort)) {
            return Comparator.comparingInt(OwnedPlot::getSize).reversed().thenComparing(OwnedPlot::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        }
        if ("activity".equals(sort)) {
            return Comparator.comparingLong((OwnedPlot plot) -> plotDataStore.metadata(plot.getKey()).getLastVisit())
                    .reversed()
                    .thenComparing(OwnedPlot::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        }
        if ("rating".equals(sort)) {
            return Comparator.comparingDouble((OwnedPlot plot) -> plotDataStore.metadata(plot.getKey()).getRating())
                    .reversed()
                    .thenComparing(OwnedPlot::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        }
        if ("category".equals(sort)) {
            return Comparator.comparing((OwnedPlot plot) -> displayCategory(plotDataStore.metadata(plot.getKey()).getCategory()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(OwnedPlot::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparing(OwnedPlot::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OwnedPlot::getWorldName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OwnedPlot::getPlotId, String.CASE_INSENSITIVE_ORDER);
    }

    private ItemStack createMyPlotItem(final Player player, final OwnedPlot plot) {
        return createDynamicItem(player, myPlotsConfig, "plot-item.", Material.FILLED_MAP, plotPlaceholders(player, plot));
    }

    private ItemStack createDynamicItem(
            final Player player,
            final YamlConfiguration configuration,
            final String path,
            final Material fallback,
            final Map<String, String> placeholders
    ) {
        final MaterialDefinition materialDefinition = materialDefinition(configuration, path, fallback);
        ItemStack item = headDatabaseHook.getHead(headDatabaseId(configuration, path));
        if (item == null) {
            item = new ItemStack(materialDefinition.getMaterial());
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applySkullOwner(player, meta, skullOwner(configuration, path, materialDefinition.getSkullOwner()), placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(configuration.getString(path + "name", "&a{name}"), placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(configuration.getStringList(path + "lore"), placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Map<String, String> plotPlaceholders(final Player player, final OwnedPlot plot) {
        final PlotMetadata metadata = plotDataStore.metadata(plot.getKey());
        final boolean favorite = plotDataStore.isFavorite(player.getUniqueId(), plot.getKey());
        final boolean visiblePublic = isPublic(plot, metadata);
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("plot_key", plot.getKey());
        placeholders.put("plot", plot.getPlotId());
        placeholders.put("plot_id", plot.getPlotId());
        placeholders.put("command_id", plot.getCommandId());
        placeholders.put("world", plot.getWorldName());
        placeholders.put("name", plot.getDisplayName());
        placeholders.put("alias", plot.getAlias().isEmpty() ? "-" : plot.getAlias());
        placeholders.put("owner", plot.getOwnerName());
        placeholders.put("merge", plot.getMergeType());
        placeholders.put("size", String.valueOf(plot.getSize()));
        placeholders.put("plots", plot.getPlotIds().isEmpty() ? plot.getPlotId() : String.join(", ", plot.getPlotIds()));
        placeholders.put("category", displayCategory(metadata.getCategory()));
        placeholders.put("tags", metadata.getTags().isEmpty() ? text("myplots-no-tags", "Keine") : String.join(", ", metadata.getTags()));
        placeholders.put("note", metadata.getNote().isEmpty() ? text("myplots-no-note", "Keine Notiz") : metadata.getNote());
        placeholders.put("visibility", visiblePublic ? text("myplots-public", "Öffentlich") : text("myplots-private", "Privat"));
        placeholders.put("visibility_mode", visibilityModeName(metadata.getVisibility()));
        placeholders.put("favorite", favorite ? text("myplots-favorite-yes", "★") : text("myplots-favorite-no", "☆"));
        placeholders.put("favorite_status", favorite ? text("myplots-favorite-active", "Favorit") : text("myplots-favorite-inactive", "Kein Favorit"));
        placeholders.put("visits", String.valueOf(metadata.getVisits()));
        placeholders.put("last_visit", metadata.getLastVisit() <= 0L ? text("myplots-never", "Nie") : formatDate(metadata.getLastVisit()));
        placeholders.put("created", plot.getCreatedAt() <= 0L ? "-" : formatDate(plot.getCreatedAt()));
        placeholders.put("rating", String.format(Locale.US, "%.1f", metadata.getRating()));
        return placeholders;
    }

    private Map<String, String> myPlotsMenuPlaceholders(
            final int page,
            final int maxPage,
            final String sort,
            final String filter,
            final int amount
    ) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page));
        placeholders.put("max_page", String.valueOf(maxPage));
        placeholders.put("next_page", String.valueOf(Math.min(maxPage, page + 1)));
        placeholders.put("previous_page", String.valueOf(Math.max(1, page - 1)));
        placeholders.put("sort", sort);
        placeholders.put("sort_name", sortName(sort));
        placeholders.put("filter", filter);
        placeholders.put("filter_name", filterName(filter));
        placeholders.put("amount", String.valueOf(amount));
        return placeholders;
    }

    private List<String> myPlotsButtonCommands(
            final MenuButton button,
            final int page,
            final int maxPage,
            final String sort,
            final String filter,
            final int amount
    ) {
        return applyCommandState(button.getCommands(), myPlotsMenuPlaceholders(page, maxPage, sort, filter, amount));
    }

    private List<String> detailButtonCommands(
            final Player player,
            final MenuButton button,
            final OwnedPlot plot,
            final int page,
            final String sort,
            final String filter
    ) {
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        placeholders.put("page", String.valueOf(page));
        placeholders.put("sort", sort);
        placeholders.put("filter", filter);
        return applyCommandState(button.getCommands(), placeholders);
    }

    private List<String> applyCommandState(final List<String> commands, final Map<String, String> placeholders) {
        final List<String> replaced = new ArrayList<>();
        for (final String command : commands) {
            replaced.add(applyPlaceholders(command, placeholders));
        }
        return replaced;
    }

    private void teleportToPlot(final Player player, final OwnedPlot plot) {
        plotDataStore.recordVisit(plot.getKey());
        languageManager.send(player, "myplots-teleport", plotPlaceholders(player, plot));
        player.closeInventory();
        plotService.teleportTo(player, plot);
    }

    private void toggleFavorite(final Player player, final OwnedPlot plot) {
        final boolean favorite = plotDataStore.toggleFavorite(player.getUniqueId(), plot.getKey());
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        placeholders.put("status", favorite ? text("myplots-favorite-active", "Favorit") : text("myplots-favorite-inactive", "Kein Favorit"));
        languageManager.send(player, favorite ? "myplots-favorite-added" : "myplots-favorite-removed", placeholders);
    }

    private void toggleVisibility(final Player player, final OwnedPlot plot) {
        final String visibility = plotDataStore.cycleVisibility(plot.getKey());
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        placeholders.put("status", visibilityModeName(visibility));
        languageManager.send(player, "myplots-visibility-changed", placeholders);
    }

    private void cycleCategory(final Player player, final OwnedPlot plot) {
        final String category = plotDataStore.setNextCategory(plot.getKey(), availableCategories(), defaultCategory());
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        placeholders.put("category", displayCategory(category));
        languageManager.send(player, "myplots-category-changed", placeholders);
    }

    private void toggleTag(final Player player, final OwnedPlot plot, final String tag) {
        final boolean active = plotDataStore.toggleTag(plot.getKey(), tag);
        final Map<String, String> placeholders = plotPlaceholders(player, plot);
        placeholders.put("tag", tag);
        placeholders.put("status", active ? text("myplots-tag-active", "Aktiv") : text("myplots-tag-inactive", "Inaktiv"));
        languageManager.send(player, active ? "myplots-tag-added" : "myplots-tag-removed", placeholders);
    }

    private boolean isPublic(final OwnedPlot plot, final PlotMetadata metadata) {
        if ("public".equals(metadata.getVisibility())) {
            return true;
        }
        if ("private".equals(metadata.getVisibility())) {
            return false;
        }
        return plot.isPublicByFlag();
    }

    private List<String> availableCategories() {
        final List<String> categories = myPlotsConfig == null ? Collections.emptyList() : myPlotsConfig.getStringList("categories.available");
        if (categories.isEmpty()) {
            return Collections.singletonList(defaultCategory());
        }
        return categories;
    }

    private List<String> availableTags() {
        if (myPlotsConfig == null) {
            return Collections.emptyList();
        }
        return myPlotsConfig.getStringList("tags.available");
    }

    private String defaultCategory() {
        return myPlotsConfig == null ? "Allgemein" : myPlotsConfig.getString("categories.default", "Allgemein");
    }

    private String displayCategory(final String category) {
        return category == null || category.trim().isEmpty() ? defaultCategory() : category;
    }

    private String normalizeSort(final String sort) {
        final String normalized = sort == null ? "name" : sort.toLowerCase(Locale.ROOT);
        return ("size".equals(normalized) || "activity".equals(normalized) || "rating".equals(normalized) || "category".equals(normalized))
                ? normalized
                : "name";
    }

    private String normalizeFilter(final String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return "all";
        }
        final String normalized = filter.toLowerCase(Locale.ROOT);
        if ("favorites".equals(normalized) || "public".equals(normalized) || "private".equals(normalized) || normalized.startsWith("category:")) {
            return normalized;
        }
        return "all";
    }

    private String sortName(final String sort) {
        return text("myplots-sort-" + normalizeSort(sort), normalizeSort(sort));
    }

    private String filterName(final String filter) {
        final String normalized = normalizeFilter(filter);
        if (normalized.startsWith("category:")) {
            return normalized.substring("category:".length());
        }
        return text("myplots-filter-" + normalized, normalized);
    }

    private String visibilityModeName(final String visibilityMode) {
        final String normalized = visibilityMode == null || visibilityMode.trim().isEmpty()
                ? "auto"
                : visibilityMode.trim().toLowerCase(Locale.ROOT);
        if ("public".equals(normalized) || "private".equals(normalized)) {
            return text("myplots-visibility-" + normalized, normalized);
        }
        return text("myplots-visibility-auto", "Automatisch");
    }

    private String statePart(final String state, final int index, final String fallback) {
        if (state == null) {
            return fallback;
        }
        final String[] parts = state.split("\\|", -1);
        if (index < 0 || index >= parts.length || parts[index].trim().isEmpty()) {
            return fallback;
        }
        return parts[index].trim();
    }

    private String formatDate(final long timestamp) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }

    private String text(final String key, final String fallback) {
        final String message = languageManager.getMessage(key);
        return key.equals(message) ? fallback : message;
    }

    private SettingsTab actionMenuTab(final ActionMenu menu, final String requestedTabId) {
        if (menu.getTabs().isEmpty()) {
            return null;
        }
        final String normalized = requestedTabId == null ? "" : requestedTabId.toLowerCase(Locale.ROOT);
        final SettingsTab requested = menu.getTabs().get(normalized);
        if (requested != null) {
            return requested;
        }
        final SettingsTab fallback = menu.getTabs().get(menu.getDefaultTab().toLowerCase(Locale.ROOT));
        if (fallback != null) {
            return fallback;
        }
        return menu.getTabs().values().iterator().next();
    }

    private Map<String, String> actionMenuPlaceholders(final ActionMenu menu, final SettingsTab tab) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("menu", menu.getId());
        placeholders.put("tab", tab == null ? "" : tabName(tab));
        placeholders.put("tab_id", tab == null ? "" : tab.getId());
        return placeholders;
    }

    private String normalizeActionMenuId(final String menuId) {
        return menuId == null ? "" : menuId.trim().toLowerCase(Locale.ROOT);
    }

    private String menuRoot(final String menuId) {
        if (menuId == null) {
            return "";
        }
        final int separator = menuId.indexOf(':');
        return (separator < 0 ? menuId : menuId.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
    }

    private String bedrockLabel(final Player player, final MenuButton button, final Map<String, String> placeholders) {
        final String label = button.getBedrockLabel() == null || button.getBedrockLabel().trim().isEmpty()
                ? button.getName()
                : button.getBedrockLabel();
        return Text.color(placeholderHook.apply(player, applyPlaceholders(label, placeholders)));
    }

    private void executeButtonCommands(final Player player, final MenuButton button) {
        executeButtonCommands(player, button, null);
    }

    private void executeButtonCommands(final Player player, final MenuButton button, final MenuSound defaultSound) {
        playSound(player, button.getClickSound() == null ? defaultSound : button.getClickSound());
        executeCommands(player, button.isCloseInventory(), button.getCommands());
    }

    private void playSound(final Player player, final MenuSound sound) {
        if (sound != null) {
            sound.play(plugin, player);
        }
    }

    private void executeCommands(final Player player, final boolean closeInventory, final List<String> commands) {
        if (closeInventory) {
            player.closeInventory();
        }
        if (commands == null || commands.isEmpty()) {
            return;
        }

        final Runnable action = () -> {
            if (!player.isOnline()) {
                return;
            }
            for (final String command : commands) {
                runCommand(player, command);
            }
        };
        if (closeInventory) {
            Bukkit.getScheduler().runTask(plugin, action);
        } else {
            action.run();
        }
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
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("myplots")) {
                openMyPlotsMenu(player, menuPage(menuId), menuArgument(menuId, 2, "name"), menuArgument(menuId, 3, "all"));
            } else if (actionMenus.containsKey(menuRoot(menuId))) {
                openActionMenu(player, menuRoot(menuId), menuArgument(menuId, 1, ""));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("settings")) {
                openSettingsMenu(player, menuArgument(menuId, defaultSettingsTab));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("team-backups")) {
                openBackupListMenu(player, menuPage(menuId));
            } else if ("team".equalsIgnoreCase(menuId)) {
                openTeamMenu(player);
            } else if ("main".equalsIgnoreCase(menuId)) {
                openMenu(player);
            }
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("chat-input:")) {
            startChatInput(player, command.substring("chat-input:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-data-note:")) {
            final String payload = command.substring("plot-data-note:".length());
            final int separator = payload.indexOf(':');
            if (separator < 1) {
                languageManager.send(player, "chat-input-invalid");
                return;
            }
            final String plotKey = payload.substring(0, separator);
            final String note = payload.substring(separator + 1).trim();
            plotDataStore.setNote(plotKey, note);
            languageManager.send(player, "myplots-note-saved");
            return;
        }

        if ("plot-data-clear-visits".equalsIgnoreCase(command)) {
            for (final OwnedPlot plot : plotService.ownedPlots(player)) {
                plotDataStore.clearActivity(plot.getKey());
            }
            languageManager.send(player, "myplots-history-cleared");
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-backup:")) {
            final String backupAction = command.substring("plot-backup:".length()).trim();
            if ("create".equalsIgnoreCase(backupAction)) {
                player.closeInventory();
                backupService.requestManualBackup(player);
            } else if ("confirm".equalsIgnoreCase(backupAction)) {
                player.closeInventory();
                backupService.confirm(player);
            } else if ("cancel".equalsIgnoreCase(backupAction)) {
                player.closeInventory();
                backupService.cancel(player);
            } else if (backupAction.toLowerCase(Locale.ROOT).startsWith("restore:")) {
                player.closeInventory();
                backupService.requestRestore(player, backupAction.substring("restore:".length()).trim());
            }
            return;
        }

        player.performCommand(command);
    }

    private void startChatInput(final Player player, final String payload) {
        final int separator = payload.indexOf(':');
        if (separator < 0 || separator >= payload.length() - 1) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        final String messageKey = payload.substring(0, separator).trim();
        final String commandTemplate = payload.substring(separator + 1).trim();
        if (commandTemplate.isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new PendingChatInput(messageKey, commandTemplate));
        player.closeInventory();
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("cancel", "cancel");
        languageManager.send(player, messageKey, placeholders);
    }

    private void completeChatInput(final Player player, final PendingChatInput pending, final String input) {
        if (input.isEmpty() || "cancel".equalsIgnoreCase(input) || "abbrechen".equalsIgnoreCase(input)) {
            languageManager.send(player, "chat-input-cancelled");
            return;
        }
        final String command = pending.getCommandTemplate()
                .replace("{input}", input)
                .replace("{input_underscore}", input.replace(' ', '_'))
                .replace("{player}", player.getName())
                .replace("%player%", player.getName());
        languageManager.send(player, "chat-input-accepted");
        runCommand(player, command);
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

    private boolean isHiddenBedrockMainButton(final MenuButton button) {
        if (hiddenBedrockMainButtons == null || hiddenBedrockMainButtons.isEmpty()) {
            return false;
        }
        for (final String hiddenButton : hiddenBedrockMainButtons) {
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

    private List<String> configuredCommands(final YamlConfiguration menuConfig, final String path) {
        final Object configured = menuConfig.get(path + "commands");
        if (configured == null) {
            return Collections.emptyList();
        }
        final List<String> commands = new ArrayList<>();
        if (configured instanceof Iterable) {
            for (final Object entry : (Iterable<?>) configured) {
                if (entry != null && !entry.toString().trim().isEmpty()) {
                    commands.add(entry.toString().trim());
                }
            }
            return commands;
        }

        final String command = configured.toString().trim();
        if (!command.isEmpty()) {
            commands.add(command);
        }
        return commands;
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

    private String menuArgument(final String menuId, final int index, final String fallback) {
        final String[] parts = menuId.split(":", -1);
        if (index < 0 || index >= parts.length || parts[index].trim().isEmpty()) {
            return fallback;
        }
        return parts[index].trim();
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
        configured.addAll(configuredSlotValues(menuConfig, path + "slots", type, id));
        if (configured.isEmpty() && menuConfig.contains(path + "slot")) {
            configured.addAll(configuredSlotValues(menuConfig, path + "slot", type, id));
        }
        if (configured.isEmpty()) {
            plugin.getLogger().warning(type + " '" + id + "' hat keinen Slot gesetzt. Nutze slot: <zahl>, slot: [<zahl>, ...] oder slots: [<zahl>, ...].");
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

    private List<Integer> configuredSlotValues(
            final YamlConfiguration menuConfig,
            final String path,
            final String type,
            final String id
    ) {
        final List<Integer> slots = new ArrayList<>();
        final Object value = menuConfig.get(path);
        if (value instanceof Iterable) {
            for (final Object entry : (Iterable<?>) value) {
                addSlotValue(entry, slots, type, id);
            }
            return slots;
        }
        addSlotValue(value, slots, type, id);
        return slots;
    }

    private void addSlotValue(
            final Object value,
            final List<Integer> slots,
            final String type,
            final String id
    ) {
        if (value == null) {
            return;
        }
        if (value instanceof Number) {
            slots.add(((Number) value).intValue());
            return;
        }
        final String text = value.toString()
                .replace("[", "")
                .replace("]", "")
                .trim();
        if (text.isEmpty()) {
            return;
        }
        for (final String part : text.split("[,;\\s]+")) {
            if (part.trim().isEmpty()) {
                continue;
            }
            try {
                slots.add(Integer.parseInt(part.trim()));
            } catch (final NumberFormatException exception) {
                plugin.getLogger().warning(type + " '" + id + "' hat einen ungültigen Slotwert: " + part);
            }
        }
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
        final String materialName = configuredMaterial.trim().split("\\s+", 2)[0];
        final Material material = Material.matchMaterial(normalizeMaterialName(materialName));
        return material == null ? fallback : material;
    }

    private MaterialDefinition materialDefinition(
            final YamlConfiguration menuConfig,
            final String path,
            final Material fallback
    ) {
        final String configuredMaterial = menuConfig.getString(path + "material", fallback.name());
        if (configuredMaterial == null || configuredMaterial.trim().isEmpty()) {
            return new MaterialDefinition(fallback, "");
        }

        final String[] parts = configuredMaterial.trim().split("\\s+", 2);
        final Material material = material(parts[0], fallback);
        final String skullOwner = material == Material.PLAYER_HEAD && parts.length > 1 ? parts[1].trim() : "";
        return new MaterialDefinition(material, skullOwner);
    }

    private String normalizeMaterialName(final String materialName) {
        if (materialName == null) {
            return "";
        }
        final String normalized = materialName.trim().toUpperCase(Locale.ROOT);
        if ("PLAYERHEAD".equals(normalized) || "PLAYER-HEAD".equals(normalized)) {
            return "PLAYER_HEAD";
        }
        return normalized;
    }

    private String skullOwner(final YamlConfiguration menuConfig, final String path, final String fallback) {
        String owner = menuConfig.getString(path + "skull-owner", "");
        if (owner == null || owner.trim().isEmpty()) {
            owner = menuConfig.getString(path + "head-owner", "");
        }
        if (owner == null || owner.trim().isEmpty()) {
            owner = fallback;
        }
        return owner == null ? "" : owner.trim();
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

    private static final class MaterialDefinition {

        private final Material material;
        private final String skullOwner;

        private MaterialDefinition(final Material material, final String skullOwner) {
            this.material = material;
            this.skullOwner = skullOwner == null ? "" : skullOwner;
        }

        private Material getMaterial() {
            return material;
        }

        private String getSkullOwner() {
            return skullOwner;
        }
    }

    private static final class ActionMenu {

        private final String id;
        private final boolean loaded;
        private final String title;
        private final String bedrockTitle;
        private final String bedrockContent;
        private final int size;
        private final ItemStack filler;
        private final Map<Integer, MenuButton> buttonsBySlot;
        private final Map<Integer, MenuButton> decorationsBySlot;
        private final Map<String, SettingsTab> tabs;
        private final String defaultTab;
        private final boolean requirePlot;
        private final boolean bedrockEnabled;
        private final String permission;

        private ActionMenu(
                final String id,
                final boolean loaded,
                final String title,
                final String bedrockTitle,
                final String bedrockContent,
                final int size,
                final ItemStack filler,
                final Map<Integer, MenuButton> buttonsBySlot,
                final Map<Integer, MenuButton> decorationsBySlot,
                final Map<String, SettingsTab> tabs,
                final String defaultTab,
                final boolean requirePlot,
                final boolean bedrockEnabled,
                final String permission
        ) {
            this.id = id;
            this.loaded = loaded;
            this.title = title;
            this.bedrockTitle = bedrockTitle;
            this.bedrockContent = bedrockContent;
            this.size = size;
            this.filler = filler;
            this.buttonsBySlot = buttonsBySlot;
            this.decorationsBySlot = decorationsBySlot;
            this.tabs = tabs;
            this.defaultTab = defaultTab == null ? "" : defaultTab;
            this.requirePlot = requirePlot;
            this.bedrockEnabled = bedrockEnabled;
            this.permission = permission == null ? "" : permission.trim();
        }

        private String getId() {
            return id;
        }

        private boolean isLoaded() {
            return loaded;
        }

        private String getTitle() {
            return title;
        }

        private String getBedrockTitle() {
            return bedrockTitle;
        }

        private String getBedrockContent() {
            return bedrockContent;
        }

        private int getSize() {
            return size;
        }

        private ItemStack getFiller() {
            return filler;
        }

        private Map<Integer, MenuButton> getButtonsBySlot() {
            return buttonsBySlot;
        }

        private Map<Integer, MenuButton> getDecorationsBySlot() {
            return decorationsBySlot;
        }

        private Map<String, SettingsTab> getTabs() {
            return tabs;
        }

        private String getDefaultTab() {
            return defaultTab;
        }

        private boolean isRequirePlot() {
            return requirePlot;
        }

        private boolean isBedrockEnabled() {
            return bedrockEnabled;
        }

        private String getPermission() {
            return permission;
        }
    }

    private static final class PendingChatInput {

        private final String messageKey;
        private final String commandTemplate;

        private PendingChatInput(final String messageKey, final String commandTemplate) {
            this.messageKey = messageKey;
            this.commandTemplate = commandTemplate;
        }

        private String getMessageKey() {
            return messageKey;
        }

        private String getCommandTemplate() {
            return commandTemplate;
        }
    }
}
