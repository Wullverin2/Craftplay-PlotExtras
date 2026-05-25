package de.craftplay.plotextras.menu;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.backup.PlotBackupMetadata;
import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.extras.ExtrasService;
import de.craftplay.plotextras.future.PlotFutureService;
import de.craftplay.plotextras.hook.FloodgateHook;
import de.craftplay.plotextras.hook.HeadDatabaseHook;
import de.craftplay.plotextras.hook.PlaceholderHook;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.myplots.PlotComment;
import de.craftplay.plotextras.myplots.PlotDataStore;
import de.craftplay.plotextras.myplots.PlotMetadata;
import de.craftplay.plotextras.plotpurchase.PlotPurchaseService;
import de.craftplay.plotextras.plotsquared.OwnedPlot;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotIdentity;
import de.craftplay.plotextras.plotsquared.PlotMemberEntry;
import de.craftplay.plotextras.plotsquared.PlotMemberType;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.plotsquared.PlotSquaredPlotService;
import de.craftplay.plotextras.reports.PlotReport;
import de.craftplay.plotextras.reports.ReportService;
import de.craftplay.plotextras.roles.PlotRole;
import de.craftplay.plotextras.roles.PlotRoleService;
import de.craftplay.plotextras.team.TeamFeatureService;
import de.craftplay.plotextras.util.Text;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.util.PatternUtil;
import com.sk89q.worldedit.function.pattern.Pattern;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlotMenuManager implements Listener {

    private final CraftplayPlotExtrasPlugin plugin;
    private final LanguageManager languageManager;
    private final PlotSquaredFlagService flagService;
    private final PlotSquaredPlotService plotService;
    private final PlotDataStore plotDataStore;
    private final PlotBackupService backupService;
    private final ReportService reportService;
    private final PlotRoleService roleService;
    private final PlotFutureService futureService;
    private final TeamFeatureService teamFeatureService;
    private final ExtrasService extrasService;
    private final PlotPurchaseService plotPurchaseService;
    private final HeadDatabaseHook headDatabaseHook;
    private final PlaceholderHook placeholderHook;
    private final FloodgateHook floodgateHook;
    private final Map<Integer, MenuButton> mainButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> mainDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> bedrockMainButtonsByOrder = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotsButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotsDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotDetailButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> myPlotDetailDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> flagButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> flagDecorationsBySlot = new HashMap<>();
    private final Map<Integer, Map<Integer, FlagMenuEntry>> flagsByPageAndSlot = new HashMap<>();
    private final List<FlagMenuEntry> automaticFlagEntries = new ArrayList<>();
    private final Map<Integer, MenuButton> settingsDecorationsBySlot = new HashMap<>();
    private final Map<String, SettingsTab> settingsTabs = new LinkedHashMap<>();
    private final Map<String, ActionMenu> actionMenus = new LinkedHashMap<>();
    private final Map<UUID, PendingChatInput> pendingChatInputs = new HashMap<>();
    private final Map<Integer, MenuButton> teamButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> teamDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> backupListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> backupListDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> reportListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> reportListDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> commentListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> commentListDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> roleListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> roleListDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> memberListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> memberListDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> roleMemberListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> roleMemberListDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> rolePermissionListButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> rolePermissionListDecorationsBySlot = new HashMap<>();
    private final List<RolePermissionTemplate> rolePermissionTemplates = new ArrayList<>();
    private final Map<Integer, MenuButton> plotPurchaseButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> plotPurchaseDecorationsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> plotPurchaseConfirmButtonsBySlot = new HashMap<>();
    private final Map<Integer, MenuButton> plotPurchaseConfirmDecorationsBySlot = new HashMap<>();

    private String mainTitle;
    private int mainSize;
    private ItemStack mainFiller;
    private boolean mainLoaded;
    private List<String> hiddenMainButtons;
    private MenuSound mainOpenSound;
    private MenuSound mainClickSound;
    private boolean inventoryAnimationEnabled;
    private long inventoryAnimationDelayTicks;
    private boolean inventoryAnimationKeepFillerVisible;
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
    private boolean flagsAnimationEnabled;
    private boolean automaticFlagLayout;
    private List<Integer> automaticFlagSlots = Collections.emptyList();
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
    private boolean backupListItemEnabled;
    private String backupListItemPermission;
    private String reportListTitle;
    private int reportListSize;
    private ItemStack reportListFiller;
    private List<Integer> reportListSlots;
    private Material reportListItemMaterial;
    private String reportListItemHeadDatabaseId;
    private String reportListItemSkullOwner;
    private String reportListItemName;
    private List<String> reportListItemLore;
    private boolean reportListItemEnabled;
    private String reportListItemPermission;
    private String commentListTitle;
    private int commentListSize;
    private ItemStack commentListFiller;
    private List<Integer> commentListSlots;
    private Material commentListItemMaterial;
    private String commentListItemHeadDatabaseId;
    private String commentListItemSkullOwner;
    private String commentListItemName;
    private List<String> commentListItemLore;
    private boolean commentListItemEnabled;
    private String commentListItemPermission;
    private String roleListTitle;
    private int roleListSize;
    private ItemStack roleListFiller;
    private List<Integer> roleListSlots;
    private Material roleListItemMaterial;
    private String roleListItemHeadDatabaseId;
    private String roleListItemSkullOwner;
    private String roleListItemName;
    private List<String> roleListItemLore;
    private boolean roleListItemEnabled;
    private String roleListItemPermission;
    private String memberListTitle;
    private int memberListSize;
    private ItemStack memberListFiller;
    private List<Integer> memberListSlots;
    private Material memberListItemMaterial;
    private String memberListItemHeadDatabaseId;
    private String memberListItemSkullOwner;
    private String memberListItemName;
    private List<String> memberListItemLore;
    private boolean memberListItemEnabled;
    private String memberListItemPermission;
    private String roleMemberListTitle;
    private int roleMemberListSize;
    private ItemStack roleMemberListFiller;
    private List<Integer> roleMemberListSlots;
    private Material roleMemberListItemMaterial;
    private String roleMemberListItemHeadDatabaseId;
    private String roleMemberListItemSkullOwner;
    private String roleMemberListItemName;
    private List<String> roleMemberListItemLore;
    private boolean roleMemberListItemEnabled;
    private String roleMemberListItemPermission;
    private String rolePermissionListTitle;
    private int rolePermissionListSize;
    private ItemStack rolePermissionListFiller;
    private List<Integer> rolePermissionListSlots;
    private String plotPurchaseTitle;
    private int plotPurchaseSize;
    private ItemStack plotPurchaseFiller;
    private boolean plotPurchaseLoaded;
    private String plotPurchaseConfirmTitle;
    private int plotPurchaseConfirmSize;
    private ItemStack plotPurchaseConfirmFiller;
    private boolean plotPurchaseBedrockEnabled;
    private String plotPurchaseBedrockTitle;
    private String plotPurchaseBedrockContent;

    public PlotMenuManager(
            final CraftplayPlotExtrasPlugin plugin,
            final LanguageManager languageManager,
            final PlotSquaredFlagService flagService,
            final PlotSquaredPlotService plotService,
            final PlotDataStore plotDataStore,
            final PlotBackupService backupService,
            final ReportService reportService,
            final PlotRoleService roleService,
            final PlotFutureService futureService,
            final TeamFeatureService teamFeatureService,
            final ExtrasService extrasService,
            final PlotPurchaseService plotPurchaseService
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.flagService = flagService;
        this.plotService = plotService;
        this.plotDataStore = plotDataStore;
        this.backupService = backupService;
        this.reportService = reportService;
        this.roleService = roleService;
        this.futureService = futureService;
        this.teamFeatureService = teamFeatureService;
        this.extrasService = extrasService;
        this.plotPurchaseService = plotPurchaseService;
        this.headDatabaseHook = new HeadDatabaseHook(plugin);
        this.placeholderHook = new PlaceholderHook(plugin);
        this.floodgateHook = new FloodgateHook(plugin);
    }

    public void reload() {
        mainButtonsBySlot.clear();
        mainDecorationsBySlot.clear();
        bedrockMainButtonsByOrder.clear();
        myPlotsButtonsBySlot.clear();
        myPlotsDecorationsBySlot.clear();
        myPlotDetailButtonsBySlot.clear();
        myPlotDetailDecorationsBySlot.clear();
        flagButtonsBySlot.clear();
        flagDecorationsBySlot.clear();
        flagsByPageAndSlot.clear();
        automaticFlagEntries.clear();
        settingsDecorationsBySlot.clear();
        settingsTabs.clear();
        actionMenus.clear();
        teamButtonsBySlot.clear();
        teamDecorationsBySlot.clear();
        backupListButtonsBySlot.clear();
        backupListDecorationsBySlot.clear();
        reportListButtonsBySlot.clear();
        reportListDecorationsBySlot.clear();
        commentListButtonsBySlot.clear();
        commentListDecorationsBySlot.clear();
        roleListButtonsBySlot.clear();
        roleListDecorationsBySlot.clear();
        memberListButtonsBySlot.clear();
        memberListDecorationsBySlot.clear();
        roleMemberListButtonsBySlot.clear();
        roleMemberListDecorationsBySlot.clear();
        rolePermissionListButtonsBySlot.clear();
        rolePermissionListDecorationsBySlot.clear();
        rolePermissionTemplates.clear();
        plotPurchaseButtonsBySlot.clear();
        plotPurchaseDecorationsBySlot.clear();
        plotPurchaseConfirmButtonsBySlot.clear();
        plotPurchaseConfirmDecorationsBySlot.clear();
        mainLoaded = false;
        bedrockMainLoaded = false;
        myPlotsLoaded = false;
        bedrockMyPlotsLoaded = false;
        flagsLoaded = false;
        automaticFlagLayout = false;
        automaticFlagSlots = Collections.emptyList();
        settingsLoaded = false;
        teamLoaded = false;
        backupListLoaded = false;
        plotPurchaseLoaded = false;

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
        inventoryAnimationEnabled = plugin.getConfig().getBoolean("gui.animations.enabled", menuConfig.getBoolean("animation.enabled", true));
        inventoryAnimationDelayTicks = Math.max(1L, plugin.getConfig().getLong("gui.animations.delay-ticks", menuConfig.getLong("animation.delay-ticks", 1L)));
        inventoryAnimationKeepFillerVisible = plugin.getConfig().getBoolean("gui.animations.keep-filler-visible", true);
        loadButtons(menuConfig, mainButtonsBySlot, mainSize);
        loadDecorations(menuConfig, mainDecorationsBySlot, mainSize);
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
        loadPlotPurchaseMenu();

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
        reopenDelayTicks = Math.max(0L, flagsConfig.getLong("reopen-delay-ticks", 2L));
        flagsAnimationEnabled = flagsConfig.getBoolean("animation.enabled", inventoryAnimationEnabled);
        loadButtons(flagsConfig, flagButtonsBySlot, flagsSize);
        loadDecorations(flagsConfig, flagDecorationsBySlot, flagsSize);
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
        loadDecorations(teamConfig, teamDecorationsBySlot, teamSize);
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
        backupListItemEnabled = teamConfig.getBoolean("backup-list.item.enabled", true);
        backupListItemPermission = teamConfig.getString("backup-list.item.permission", "");
        loadButtonsFromSection(teamConfig, "backup-list.buttons", backupListButtonsBySlot, backupListSize);
        loadDecorationsFromSection(teamConfig, "backup-list.decorations", backupListDecorationsBySlot, backupListSize);
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
        for (final MenuButton decoration : mainDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration));
            }
        }

        for (final MenuButton button : visibleMainButtons(player)) {
            inventory.setItem(button.getSlot(), createButtonItem(player, button));
        }

        openAnimatedInventory(player, inventory, mainFiller, mainOpenSound);
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

    private void openAnimatedInventory(final Player player, final Inventory inventory, final ItemStack filler) {
        openAnimatedInventory(player, inventory, filler, null);
    }

    private void openAnimatedInventory(
            final Player player,
            final Inventory inventory,
            final ItemStack filler,
            final MenuSound openSound
    ) {
        openAnimatedInventory(player, inventory, filler, openSound, inventoryAnimationEnabled);
    }

    private void openAnimatedInventory(
            final Player player,
            final Inventory inventory,
            final ItemStack filler,
            final MenuSound openSound,
            final boolean animate
    ) {
        if (!animate) {
            player.openInventory(inventory);
            playSound(player, openSound);
            return;
        }

        final List<AnimatedInventorySlot> animatedSlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (inventoryAnimationKeepFillerVisible && filler != null && item.isSimilar(filler)) {
                continue;
            }
            animatedSlots.add(new AnimatedInventorySlot(slot, item.clone()));
            inventory.clear(slot);
        }

        player.openInventory(inventory);
        playSound(player, openSound);

        if (animatedSlots.isEmpty()) {
            return;
        }
        if (inventoryAnimationDelayTicks <= 0L) {
            for (final AnimatedInventorySlot animatedSlot : animatedSlots) {
                inventory.setItem(animatedSlot.getSlot(), animatedSlot.getItem());
            }
            return;
        }

        final int[] index = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                task[0].cancel();
                return;
            }
            final AnimatedInventorySlot animatedSlot = animatedSlots.get(index[0]);
            inventory.setItem(animatedSlot.getSlot(), animatedSlot.getItem());
            index[0]++;
            if (index[0] >= animatedSlots.size()) {
                task[0].cancel();
            }
        }, 0L, inventoryAnimationDelayTicks);
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
        if (!menu.getNestedTabs().isEmpty()) {
            openJavaNestedActionMenu(player, menu, requestedTabId);
            return;
        }
        final SettingsTab tab = actionMenuTab(menu, requestedTabId);
        final Map<String, String> placeholders = actionMenuPlaceholders(player, menu, tab);
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
            for (final MenuButton decoration : tab.getDecorationsBySlot().values()) {
                if (canSee(player, decoration)) {
                    inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
                }
            }
            for (final MenuButton button : tab.getButtonsBySlot().values()) {
                if (canSee(player, button)) {
                    inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
                }
            }
        }
        openAnimatedInventory(player, inventory, menu.getFiller());
    }

    private void openJavaNestedActionMenu(final Player player, final ActionMenu menu, final String requestedTabId) {
        final ActionMenuSelection selection = actionMenuSelection(player, menu, requestedTabId);
        final ActionMenuTab tab = selection == null ? null : selection.getTab();
        final SettingsTab subtab = selection == null ? null : selection.getSubtab();
        final Map<String, String> placeholders = actionMenuPlaceholders(player, menu, tab, subtab);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("action-" + menu.getId(), selection == null ? "" : selection.getId()),
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
        for (final ActionMenuTab actionTab : menu.getNestedTabs().values()) {
            for (final MenuButton selector : actionTab.getSelectors()) {
                if (canSee(player, selector)) {
                    inventory.setItem(selector.getSlot(), createButtonItem(player, selector, placeholders));
                }
            }
        }
        if (tab != null) {
            for (final MenuButton decoration : tab.getDecorationsBySlot().values()) {
                if (canSee(player, decoration)) {
                    inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
                }
            }
            for (final MenuButton button : tab.getButtonsBySlot().values()) {
                if (canSee(player, button)) {
                    inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
                }
            }
            for (final SettingsTab candidate : tab.getSubtabs().values()) {
                for (final MenuButton selector : candidate.getSelectors()) {
                    if (canSee(player, selector)) {
                        inventory.setItem(selector.getSlot(), createButtonItem(player, selector, placeholders));
                    }
                }
            }
        }
        if (subtab != null) {
            for (final MenuButton decoration : subtab.getDecorationsBySlot().values()) {
                if (canSee(player, decoration)) {
                    inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
                }
            }
            for (final MenuButton button : subtab.getButtonsBySlot().values()) {
                if (canSee(player, button)) {
                    inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
                }
            }
        }
        openAnimatedInventory(player, inventory, menu.getFiller());
    }

    private boolean openBedrockActionMenu(final Player player, final ActionMenu menu, final String requestedTabId) {
        if (!menu.getNestedTabs().isEmpty()) {
            return openBedrockNestedActionMenu(player, menu, requestedTabId);
        }
        final SettingsTab tab = actionMenuTab(menu, requestedTabId);
        final Map<String, String> placeholders = actionMenuPlaceholders(player, menu, tab);
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

    private boolean openBedrockNestedActionMenu(final Player player, final ActionMenu menu, final String requestedTabId) {
        final ActionMenuSelection selection = actionMenuSelection(player, menu, requestedTabId);
        final ActionMenuTab tab = selection == null ? null : selection.getTab();
        final SettingsTab subtab = selection == null ? null : selection.getSubtab();
        final Map<String, String> placeholders = actionMenuPlaceholders(player, menu, tab, subtab);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        for (final ActionMenuTab actionTab : menu.getNestedTabs().values()) {
            for (final MenuButton selector : actionTab.getSelectors()) {
                if (!canSee(player, selector)) {
                    continue;
                }
                labels.add(bedrockLabel(player, selector, placeholders));
                actions.add(() -> openActionMenu(player, menu.getId(), actionTab.getId()));
            }
        }
        if (tab != null) {
            for (final SettingsTab candidate : tab.getSubtabs().values()) {
                for (final MenuButton selector : candidate.getSelectors()) {
                    if (!canSee(player, selector)) {
                        continue;
                    }
                    labels.add(bedrockLabel(player, selector, placeholders));
                    actions.add(() -> openActionMenu(player, menu.getId(), candidate.getId()));
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
        if (subtab != null) {
            for (final MenuButton button : subtab.getButtonsBySlot().values()) {
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
            actions.get(clickedButton).run();
        });
    }

    public void openPlotPurchaseMenu(final Player player) {
        if (!plotPurchaseService.canUse(player)) {
            languageManager.send(player, plotPurchaseService.isEnabled() ? "no-permission" : "plotbuy-disabled");
            return;
        }
        if (!plotPurchaseLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }
        if (floodgateHook.isBedrockPlayer(player) && plotPurchaseBedrockEnabled) {
            if (openBedrockPlotPurchaseMenu(player)) {
                return;
            }
        }
        final Map<String, String> placeholders = plotPurchaseService.placeholders(player);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("plot-purchase"),
                plotPurchaseSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(plotPurchaseTitle, placeholders)))
        );
        if (plotPurchaseFiller != null) {
            for (int slot = 0; slot < plotPurchaseSize; slot++) {
                inventory.setItem(slot, plotPurchaseFiller);
            }
        }
        for (final MenuButton decoration : plotPurchaseDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : plotPurchaseButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }
        openAnimatedInventory(player, inventory, plotPurchaseFiller);
    }

    public void openPlotPurchaseConfirmMenu(final Player player) {
        if (!plotPurchaseService.canUse(player)) {
            languageManager.send(player, plotPurchaseService.isEnabled() ? "no-permission" : "plotbuy-disabled");
            return;
        }
        if (!plotPurchaseLoaded) {
            languageManager.send(player, "menu-missing");
            return;
        }
        if (floodgateHook.isBedrockPlayer(player) && plotPurchaseBedrockEnabled) {
            if (openBedrockPlotPurchaseConfirmMenu(player)) {
                return;
            }
        }
        final Map<String, String> placeholders = plotPurchaseService.placeholders(player);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("plot-purchase-confirm"),
                plotPurchaseConfirmSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(plotPurchaseConfirmTitle, placeholders)))
        );
        if (plotPurchaseConfirmFiller != null) {
            for (int slot = 0; slot < plotPurchaseConfirmSize; slot++) {
                inventory.setItem(slot, plotPurchaseConfirmFiller);
            }
        }
        for (final MenuButton decoration : plotPurchaseConfirmDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : plotPurchaseConfirmButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }
        openAnimatedInventory(player, inventory, plotPurchaseConfirmFiller);
    }

    private boolean openBedrockPlotPurchaseMenu(final Player player) {
        final Map<String, String> placeholders = plotPurchaseService.placeholders(player);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add(Text.color("&a+1"));
        actions.add(() -> {
            plotPurchaseService.adjustSelection(player, 1);
            openPlotPurchaseMenu(player);
        });
        labels.add(Text.color("&a+10"));
        actions.add(() -> {
            plotPurchaseService.adjustSelection(player, 10);
            openPlotPurchaseMenu(player);
        });
        labels.add(Text.color("&c-1"));
        actions.add(() -> {
            plotPurchaseService.adjustSelection(player, -1);
            openPlotPurchaseMenu(player);
        });
        labels.add(Text.color("&c-10"));
        actions.add(() -> {
            plotPurchaseService.adjustSelection(player, -10);
            openPlotPurchaseMenu(player);
        });
        labels.add(Text.color(text("plotbuy-bedrock-confirm", "&aKaufen")));
        actions.add(() -> openPlotPurchaseConfirmMenu(player));
        labels.add(Text.color(text("community-back", "&eZurück")));
        actions.add(() -> openMenu(player));
        final String title = Text.color(placeholderHook.apply(player, applyPlaceholders(plotPurchaseBedrockTitle, placeholders)));
        final String content = Text.color(placeholderHook.apply(player, applyPlaceholders(plotPurchaseBedrockContent, placeholders)));
        return floodgateHook.sendSimpleForm(player, title, content, labels, clickedButton -> {
            if (clickedButton < 0 || clickedButton >= actions.size()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, actions.get(clickedButton));
        });
    }

    private boolean openBedrockPlotPurchaseConfirmMenu(final Player player) {
        final Map<String, String> placeholders = plotPurchaseService.placeholders(player);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add(Text.color(text("plotbuy-bedrock-confirm", "&aKaufen")));
        actions.add(() -> {
            player.closeInventory();
            plotPurchaseService.purchase(player);
        });
        labels.add(Text.color(text("plotbuy-bedrock-cancel", "&cAbbrechen")));
        actions.add(() -> openPlotPurchaseMenu(player));
        final String title = Text.color(placeholderHook.apply(player, applyPlaceholders(plotPurchaseConfirmTitle, placeholders)));
        final String content = Text.color(placeholderHook.apply(player,
                applyPlaceholders(text("plotbuy-confirm-summary",
                        "&7Vorher: &f{allowed_plots}\n&7Kauf: &f{selected_plots}\n&7Danach: &f{after_allowed_plots}\n&7Preis: &f{price}"), placeholders)));
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
        if (configuredItemVisible(player, myPlotsConfig, "plot-item.")) {
            for (int index = start; index < end; index++) {
                final int slot = myPlotsSlots.get(index - start);
                inventory.setItem(slot, createMyPlotItem(player, plots.get(index)));
            }
        }
        openAnimatedInventory(player, inventory, myPlotsFiller);
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
        if (configuredItemVisible(player, bedrockMyPlotsConfig, "plot-button.")) {
            for (int index = start; index < end; index++) {
                final OwnedPlot plot = plots.get(index);
                labels.add(Text.color(placeholderHook.apply(player, applyPlaceholders(plotLabel, plotPlaceholders(player, plot)))));
                actions.add(() -> openBedrockMyPlotDetail(player, plot.getKey(), actualPage, normalizedSort, normalizedFilter));
            }
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
        if (configuredItemVisible(player, bedrockMyPlotsConfig, "detail.tag-button.")) {
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
        if (permission != null && !permission.trim().isEmpty() && !hasConfiguredPermission(player, permission.trim())) {
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
        if (previewSlot >= 0 && previewSlot < myPlotDetailSize && configuredItemVisible(player, myPlotsConfig, "detail.preview.")) {
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
            final String tagPath = active ? "detail.tag-active." : "detail.tag-inactive.";
            if (!configuredItemVisible(player, myPlotsConfig, tagPath)) {
                continue;
            }
            tagPlaceholders.put("tag", tag);
            tagPlaceholders.put("tag_status", active ? text("myplots-tag-active", "Aktiv") : text("myplots-tag-inactive", "Inaktiv"));
            inventory.setItem(slot, createDynamicItem(player, myPlotsConfig, tagPath, active ? Material.NAME_TAG : Material.PAPER, tagPlaceholders));
        }
        openAnimatedInventory(player, inventory, myPlotDetailFiller);
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

        final FlagMenuView view = flagMenuView(player, page);
        final int maxPage = view.getMaxPage();
        final int normalizedPage = view.getPage();
        final Map<String, String> pagePlaceholders = flagMenuPlaceholders(normalizedPage, maxPage);
        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("flags", normalizedPage),
                flagsSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(flagsTitle, pagePlaceholders)))
        );
        if (flagsFiller != null) {
            for (int slot = 0; slot < flagsSize; slot++) {
                inventory.setItem(slot, flagsFiller);
            }
        }
        for (final MenuButton decoration : flagDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, pagePlaceholders));
            }
        }

        for (final MenuButton button : flagButtonsBySlot.values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(player, button, pagePlaceholders));
        }

        final Map<Integer, FlagMenuEntry> flagsBySlot = view.getFlagsBySlot();
        final Map<String, Boolean> flagStates = flagService.flagStates(player, flagNames(flagsBySlot.values()));
        for (final FlagMenuEntry flagEntry : flagsBySlot.values()) {
            inventory.setItem(flagEntry.getSlot(), createFlagItem(player, flagEntry,
                    flagStates.getOrDefault(flagEntry.getFlag().toLowerCase(Locale.ROOT), false)));
        }

        openAnimatedInventory(player, inventory, flagsFiller, null, flagsAnimationEnabled);
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

        for (final MenuButton decoration : tab.getDecorationsBySlot().values()) {
            if (!canSee(player, decoration)) {
                continue;
            }
            inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration));
        }

        for (final MenuButton button : tab.getButtonsBySlot().values()) {
            if (!canSee(player, button)) {
                continue;
            }
            inventory.setItem(button.getSlot(), createButtonItem(player, button));
        }

        openAnimatedInventory(player, inventory, settingsFiller);
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
        for (final MenuButton decoration : teamDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration));
            }
        }
        for (final MenuButton button : teamButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button));
            }
        }
        openAnimatedInventory(player, inventory, teamFiller);
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
        for (final MenuButton decoration : backupListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, pagePlaceholders));
            }
        }
        for (final MenuButton button : backupListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, pagePlaceholders));
            }
        }

        final List<PlotBackupMetadata> backups = backupService.listBackups();
        final int pageSize = Math.max(1, backupListSlots.size());
        final int start = (normalizedPage - 1) * pageSize;
        final int end = Math.min(backups.size(), start + pageSize);
        if (configuredItemVisible(player, backupListItemEnabled, backupListItemPermission)) {
            for (int index = start; index < end; index++) {
                final int slot = backupListSlots.get(index - start);
                final PlotBackupMetadata metadata = backups.get(index);
                inventory.setItem(slot, createBackupListItem(player, metadata));
            }
        }
        openAnimatedInventory(player, inventory, backupListFiller);
    }

    public void openReportListMenu(final Player player, final int page, final String status) {
        if (!player.hasPermission("craftplayplotextras.reports.manage")) {
            languageManager.send(player, "no-permission");
            return;
        }
        final String normalizedStatus = status == null || status.trim().isEmpty() ? "open" : status.toLowerCase(Locale.ROOT);
        final int normalizedPage = Math.max(1, page);
        final Map<String, String> pagePlaceholders = new HashMap<>();
        pagePlaceholders.put("page", String.valueOf(normalizedPage));
        pagePlaceholders.put("next_page", String.valueOf(normalizedPage + 1));
        pagePlaceholders.put("previous_page", String.valueOf(Math.max(1, normalizedPage - 1)));
        pagePlaceholders.put("status", normalizedStatus);

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("reports-list", normalizedPage, normalizedStatus),
                reportListSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(reportListTitle, pagePlaceholders)))
        );
        if (reportListFiller != null) {
            for (int slot = 0; slot < reportListSize; slot++) {
                inventory.setItem(slot, reportListFiller);
            }
        }
        for (final MenuButton decoration : reportListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, pagePlaceholders));
            }
        }
        for (final MenuButton button : reportListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, pagePlaceholders));
            }
        }

        final List<PlotReport> reports = reportService.list(normalizedStatus);
        final int pageSize = Math.max(1, reportListSlots.size());
        final int start = (normalizedPage - 1) * pageSize;
        final int end = Math.min(reports.size(), start + pageSize);
        if (configuredItemVisible(player, reportListItemEnabled, reportListItemPermission)) {
            for (int index = start; index < end; index++) {
                inventory.setItem(reportListSlots.get(index - start), createReportListItem(player, reports.get(index)));
            }
        }
        openAnimatedInventory(player, inventory, reportListFiller);
    }

    public void openCommentListMenu(final Player player, final int page) {
        final String plotKey = currentPlotKey(player);
        if (plotKey.isEmpty()) {
            languageManager.send(player, "no-plot");
            return;
        }
        final List<PlotComment> comments = plotDataStore.comments(plotKey);
        if (floodgateHook.isBedrockPlayer(player) && openBedrockCommentListMenu(player, comments)) {
            return;
        }
        final int pageSize = Math.max(1, commentListSlots == null || commentListSlots.isEmpty() ? 1 : commentListSlots.size());
        final int maxPage = Math.max(1, (int) Math.ceil(comments.size() / (double) pageSize));
        final int actualPage = Math.min(Math.max(1, page), maxPage);
        final Map<String, String> placeholders = commentListPlaceholders(actualPage, maxPage, comments.size());

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("comments-list", actualPage),
                commentListSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(commentListTitle, placeholders)))
        );
        if (commentListFiller != null) {
            for (int slot = 0; slot < commentListSize; slot++) {
                inventory.setItem(slot, commentListFiller);
            }
        }
        for (final MenuButton decoration : commentListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : commentListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }

        final int start = (actualPage - 1) * pageSize;
        final int end = Math.min(comments.size(), start + pageSize);
        if (configuredItemVisible(player, commentListItemEnabled, commentListItemPermission)) {
            for (int index = start; index < end; index++) {
                inventory.setItem(commentListSlots.get(index - start), createCommentListItem(player, comments.get(index)));
            }
        }
        openAnimatedInventory(player, inventory, commentListFiller);
    }

    private boolean openBedrockCommentListMenu(final Player player, final List<PlotComment> comments) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final StringBuilder content = new StringBuilder();
        if (comments.isEmpty()) {
            content.append(text("community-no-comments", "Keine Kommentare vorhanden."));
        } else {
            for (final PlotComment comment : comments) {
                if (content.length() > 0) {
                    content.append("\n\n");
                }
                content.append(comment.getAuthorName())
                        .append(" - ")
                        .append(comment.getCreatedAt() <= 0L ? "-" : formatDate(comment.getCreatedAt()))
                        .append("\n")
                        .append(comment.getMessage());
            }
        }
        labels.add(Text.color(text("community-back", "&eZurück")));
        actions.add(() -> openActionMenu(player, "community", "rating"));
        return floodgateHook.sendSimpleForm(
                player,
                Text.color(placeholderHook.apply(player, commentListTitle.replace("{page}", "1").replace("{max_page}", "1"))),
                Text.color(placeholderHook.apply(player, content.toString())),
                labels,
                clickedButton -> {
                    if (clickedButton < 0 || clickedButton >= actions.size()) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, actions.get(clickedButton));
                }
        );
    }

    public void openRoleListMenu(final Player player, final int page) {
        if (!roleService.canManage(player)) {
            return;
        }
        final int normalizedPage = Math.max(1, page);
        final Map<String, String> pagePlaceholders = new HashMap<>();
        pagePlaceholders.put("page", String.valueOf(normalizedPage));
        pagePlaceholders.put("next_page", String.valueOf(normalizedPage + 1));
        pagePlaceholders.put("previous_page", String.valueOf(Math.max(1, normalizedPage - 1)));

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("roles-list", normalizedPage),
                roleListSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(roleListTitle, pagePlaceholders)))
        );
        if (roleListFiller != null) {
            for (int slot = 0; slot < roleListSize; slot++) {
                inventory.setItem(slot, roleListFiller);
            }
        }
        for (final MenuButton decoration : roleListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, pagePlaceholders));
            }
        }
        for (final MenuButton button : roleListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, pagePlaceholders));
            }
        }

        final List<PlotRole> roles = roleService.roles(player);
        final int pageSize = Math.max(1, roleListSlots.size());
        final int start = (normalizedPage - 1) * pageSize;
        final int end = Math.min(roles.size(), start + pageSize);
        if (configuredItemVisible(player, roleListItemEnabled, roleListItemPermission)) {
            for (int index = start; index < end; index++) {
                inventory.setItem(roleListSlots.get(index - start), createRoleListItem(player, roles.get(index)));
            }
        }
        openAnimatedInventory(player, inventory, roleListFiller);
    }

    public void openMemberListMenu(final Player player, final int page, final String filter) {
        if (!flagService.isOnPlot(player)) {
            languageManager.send(player, "no-plot");
            return;
        }
        final String normalizedFilter = normalizeMemberFilter(filter);
        final List<PlotMemberEntry> members = filteredPlotMembers(player, normalizedFilter);
        final int pageSize = Math.max(1, memberListSlots.size());
        final int maxPage = Math.max(1, (int) Math.ceil(members.size() / (double) pageSize));
        final int actualPage = Math.min(Math.max(1, page), maxPage);
        final Map<String, String> placeholders = memberListPlaceholders(actualPage, maxPage, normalizedFilter, members.size());

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("members-list", actualPage, normalizedFilter),
                memberListSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(memberListTitle, placeholders)))
        );
        if (memberListFiller != null) {
            for (int slot = 0; slot < memberListSize; slot++) {
                inventory.setItem(slot, memberListFiller);
            }
        }
        for (final MenuButton decoration : memberListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : memberListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }

        final String plotKey = currentPlotKey(player);
        final int start = (actualPage - 1) * pageSize;
        final int end = Math.min(members.size(), start + pageSize);
        if (configuredItemVisible(player, memberListItemEnabled, memberListItemPermission)) {
            for (int index = start; index < end; index++) {
                inventory.setItem(memberListSlots.get(index - start), createMemberListItem(player, members.get(index), plotKey, normalizedFilter, actualPage));
            }
        }
        openAnimatedInventory(player, inventory, memberListFiller);
    }

    public void openRoleMemberListMenu(final Player player, final int page, final String roleName) {
        if (!roleService.canManage(player)) {
            return;
        }
        final PlotRole role = roleByName(player, roleName);
        if (role == null) {
            languageManager.send(player, "role-not-found", rolePlaceholder(roleName));
            return;
        }
        final List<PlotMemberEntry> members = filteredPlotMembers(player, "all");
        final int pageSize = Math.max(1, roleMemberListSlots.size());
        final int maxPage = Math.max(1, (int) Math.ceil(members.size() / (double) pageSize));
        final int actualPage = Math.min(Math.max(1, page), maxPage);
        final Map<String, String> placeholders = roleListPlaceholders(actualPage, maxPage, role.getName(), members.size());

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("role-members-list", actualPage, role.getName()),
                roleMemberListSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(roleMemberListTitle, placeholders)))
        );
        if (roleMemberListFiller != null) {
            for (int slot = 0; slot < roleMemberListSize; slot++) {
                inventory.setItem(slot, roleMemberListFiller);
            }
        }
        for (final MenuButton decoration : roleMemberListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : roleMemberListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }

        final String plotKey = currentPlotKey(player);
        final int start = (actualPage - 1) * pageSize;
        final int end = Math.min(members.size(), start + pageSize);
        if (configuredItemVisible(player, roleMemberListItemEnabled, roleMemberListItemPermission)) {
            for (int index = start; index < end; index++) {
                inventory.setItem(roleMemberListSlots.get(index - start), createRoleMemberListItem(player, members.get(index), role.getName(), plotKey, actualPage));
            }
        }
        openAnimatedInventory(player, inventory, roleMemberListFiller);
    }

    public void openRolePermissionListMenu(final Player player, final int page, final String roleName) {
        if (!roleService.canManage(player)) {
            return;
        }
        final PlotRole role = roleByName(player, roleName);
        if (role == null) {
            languageManager.send(player, "role-not-found", rolePlaceholder(roleName));
            return;
        }
        final int pageSize = Math.max(1, rolePermissionListSlots.size());
        final int maxPage = Math.max(1, (int) Math.ceil(rolePermissionTemplates.size() / (double) pageSize));
        final int actualPage = Math.min(Math.max(1, page), maxPage);
        final Map<String, String> placeholders = roleListPlaceholders(actualPage, maxPage, role.getName(), rolePermissionTemplates.size());

        final Inventory inventory = Bukkit.createInventory(
                new PlotMenuHolder("role-permissions-list", actualPage, role.getName()),
                rolePermissionListSize,
                Text.color(placeholderHook.apply(player, applyPlaceholders(rolePermissionListTitle, placeholders)))
        );
        if (rolePermissionListFiller != null) {
            for (int slot = 0; slot < rolePermissionListSize; slot++) {
                inventory.setItem(slot, rolePermissionListFiller);
            }
        }
        for (final MenuButton decoration : rolePermissionListDecorationsBySlot.values()) {
            if (canSee(player, decoration)) {
                inventory.setItem(decoration.getSlot(), createButtonItem(player, decoration, placeholders));
            }
        }
        for (final MenuButton button : rolePermissionListButtonsBySlot.values()) {
            if (canSee(player, button)) {
                inventory.setItem(button.getSlot(), createButtonItem(player, button, placeholders));
            }
        }

        final int start = (actualPage - 1) * pageSize;
        final int end = Math.min(rolePermissionTemplates.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            final RolePermissionTemplate template = rolePermissionTemplates.get(index);
            if (configuredItemVisible(player, true, template.getPermission())) {
                inventory.setItem(rolePermissionListSlots.get(index - start), createRolePermissionItem(player, role, template, actualPage));
            }
        }
        openAnimatedInventory(player, inventory, rolePermissionListFiller);
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
        if ("plot-purchase".equalsIgnoreCase(holder.getMenuId())) {
            handlePlotPurchaseClick(player, event.getSlot(), event.getClick());
            return;
        }
        if ("plot-purchase-confirm".equalsIgnoreCase(holder.getMenuId())) {
            handlePlotPurchaseConfirmClick(player, event.getSlot());
            return;
        }
        if (holder.getMenuId().toLowerCase(Locale.ROOT).startsWith("action-")) {
            handleActionMenuClick(player, holder.getMenuId().substring("action-".length()), holder.getTabId(), event.getSlot());
            return;
        }
        if ("flags".equalsIgnoreCase(holder.getMenuId())) {
            handleFlagsClick(player, holder.getPage(), event.getSlot(), event.getInventory());
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
        if ("reports-list".equalsIgnoreCase(holder.getMenuId())) {
            handleReportListClick(player, holder.getPage(), holder.getTabId(), event.getSlot(), event.getClick());
            return;
        }
        if ("comments-list".equalsIgnoreCase(holder.getMenuId())) {
            handleCommentListClick(player, holder.getPage(), event.getSlot());
            return;
        }
        if ("members-list".equalsIgnoreCase(holder.getMenuId())) {
            handleMemberListClick(player, holder.getPage(), holder.getTabId(), event.getSlot(), event.getClick());
            return;
        }
        if ("roles-list".equalsIgnoreCase(holder.getMenuId())) {
            handleRoleListClick(player, holder.getPage(), event.getSlot(), event.getClick());
            return;
        }
        if ("role-members-list".equalsIgnoreCase(holder.getMenuId())) {
            handleRoleMemberListClick(player, holder.getPage(), holder.getTabId(), event.getSlot(), event.getClick());
            return;
        }
        if ("role-permissions-list".equalsIgnoreCase(holder.getMenuId())) {
            handleRolePermissionListClick(player, holder.getPage(), holder.getTabId(), event.getSlot());
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
        loadButtonsFromSection(menuConfig, "buttons", target, menuSize);
    }

    private void loadBedrockButtons(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("buttons");
        if (section == null) {
            return;
        }

        int fallbackOrder = 0;
        for (final String id : section.getKeys(false)) {
            final String path = "buttons." + id + ".";
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
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
        loadActionMenu("deco", plugin.getConfig().getString("gui.deco-menu", "deco.yml"));
        loadActionMenu("reports", plugin.getConfig().getString("gui.reports-menu", "reports.yml"));
        loadActionMenu("history", plugin.getConfig().getString("gui.history-menu", "history.yml"));
        loadActionMenu("danger", plugin.getConfig().getString("gui.danger-menu", "danger.yml"));
        loadActionMenu("help", plugin.getConfig().getString("gui.help-menu", "help.yml"));
        loadActionMenu("future", plugin.getConfig().getString("gui.future-menu", "future.yml"));
        loadActionMenu("extras", plugin.getConfig().getString("gui.extras-menu", "extras.yml"));
        loadReportListMenu();
        loadCommunityCommentListMenu();
        loadRoleListMenu();
    }

    private void loadPlotPurchaseMenu() {
        final YamlConfiguration purchaseConfig = loadMenuConfig(plugin.getConfig().getString("gui.plot-purchase-menu", "plot-purchase.yml"));
        if (purchaseConfig == null) {
            return;
        }
        plotPurchaseTitle = purchaseConfig.getString("title", "&8Plotlimit kaufen");
        plotPurchaseSize = normalizeSize(purchaseConfig.getInt("size", 54));
        plotPurchaseFiller = createFiller(purchaseConfig);
        plotPurchaseBedrockEnabled = purchaseConfig.getBoolean("bedrock.enabled", true);
        plotPurchaseBedrockTitle = purchaseConfig.getString("bedrock.title", plotPurchaseTitle);
        plotPurchaseBedrockContent = purchaseConfig.getString("bedrock.content", "");
        loadButtonsFromSection(purchaseConfig, "buttons", plotPurchaseButtonsBySlot, plotPurchaseSize);
        loadDecorationsFromSection(purchaseConfig, "decorations", plotPurchaseDecorationsBySlot, plotPurchaseSize);

        plotPurchaseConfirmTitle = purchaseConfig.getString("confirm.title", "&8Kauf bestätigen");
        plotPurchaseConfirmSize = normalizeSize(purchaseConfig.getInt("confirm.size", 27));
        plotPurchaseConfirmFiller = createFiller(purchaseConfig, "confirm.filler");
        loadButtonsFromSection(purchaseConfig, "confirm.buttons", plotPurchaseConfirmButtonsBySlot, plotPurchaseConfirmSize);
        loadDecorationsFromSection(purchaseConfig, "confirm.decorations", plotPurchaseConfirmDecorationsBySlot, plotPurchaseConfirmSize);
        plotPurchaseLoaded = true;
    }

    private void loadReportListMenu() {
        final YamlConfiguration reportsConfig = loadMenuConfig(plugin.getConfig().getString("gui.reports-menu", "reports.yml"));
        if (reportsConfig == null) {
            return;
        }
        reportListTitle = reportsConfig.getString("admin-list.title", "&8Reports &7- {status} - Seite {page}");
        reportListSize = normalizeSize(reportsConfig.getInt("admin-list.size", 54));
        reportListFiller = createFiller(reportsConfig, "admin-list.filler");
        reportListSlots = reportsConfig.getIntegerList("admin-list.slots");
        if (reportListSlots.isEmpty()) {
            reportListSlots = defaultListSlots(reportListSize);
        }
        final MaterialDefinition itemDefinition = materialDefinition(reportsConfig, "admin-list.item.", Material.WRITABLE_BOOK);
        reportListItemMaterial = itemDefinition.getMaterial();
        reportListItemHeadDatabaseId = headDatabaseId(reportsConfig, "admin-list.item.");
        reportListItemSkullOwner = skullOwner(reportsConfig, "admin-list.item.", itemDefinition.getSkullOwner());
        reportListItemName = reportsConfig.getString("admin-list.item.name", "&c{id} &7- &f{category}");
        reportListItemLore = reportsConfig.getStringList("admin-list.item.lore");
        reportListItemEnabled = reportsConfig.getBoolean("admin-list.item.enabled", true);
        reportListItemPermission = reportsConfig.getString("admin-list.item.permission", "");
        loadButtonsFromSection(reportsConfig, "admin-list.buttons", reportListButtonsBySlot, reportListSize);
        loadDecorationsFromSection(reportsConfig, "admin-list.decorations", reportListDecorationsBySlot, reportListSize);
    }

    private void loadCommunityCommentListMenu() {
        final YamlConfiguration communityConfig = loadMenuConfig(plugin.getConfig().getString("gui.community-menu", "community.yml"));
        if (communityConfig == null) {
            return;
        }
        commentListTitle = communityConfig.getString("comments-list.title", "&8Plot-Kommentare &7- Seite {page}");
        commentListSize = normalizeSize(communityConfig.getInt("comments-list.size", 54));
        commentListFiller = createFiller(communityConfig, "comments-list.filler");
        commentListSlots = communityConfig.getIntegerList("comments-list.slots");
        if (commentListSlots.isEmpty()) {
            commentListSlots = defaultListSlots(commentListSize);
        }
        final MaterialDefinition itemDefinition = materialDefinition(communityConfig, "comments-list.item.", Material.WRITABLE_BOOK);
        commentListItemMaterial = itemDefinition.getMaterial();
        commentListItemHeadDatabaseId = headDatabaseId(communityConfig, "comments-list.item.");
        commentListItemSkullOwner = skullOwner(communityConfig, "comments-list.item.", itemDefinition.getSkullOwner());
        commentListItemName = communityConfig.getString("comments-list.item.name", "&a{author}");
        commentListItemLore = communityConfig.getStringList("comments-list.item.lore");
        commentListItemEnabled = communityConfig.getBoolean("comments-list.item.enabled", true);
        commentListItemPermission = communityConfig.getString("comments-list.item.permission", "");
        loadButtonsFromSection(communityConfig, "comments-list.buttons", commentListButtonsBySlot, commentListSize);
        loadDecorationsFromSection(communityConfig, "comments-list.decorations", commentListDecorationsBySlot, commentListSize);
    }

    private void loadRoleListMenu() {
        final YamlConfiguration membersConfig = loadMenuConfig(plugin.getConfig().getString("gui.members-menu", "members.yml"));
        if (membersConfig == null) {
            return;
        }
        roleListTitle = membersConfig.getString("role-list.title", "&8Plotrollen &7- Seite {page}");
        roleListSize = normalizeSize(membersConfig.getInt("role-list.size", 54));
        roleListFiller = createFiller(membersConfig, "role-list.filler");
        roleListSlots = membersConfig.getIntegerList("role-list.slots");
        if (roleListSlots.isEmpty()) {
            roleListSlots = defaultListSlots(roleListSize);
        }
        final MaterialDefinition itemDefinition = materialDefinition(membersConfig, "role-list.item.", Material.WRITABLE_BOOK);
        roleListItemMaterial = itemDefinition.getMaterial();
        roleListItemHeadDatabaseId = headDatabaseId(membersConfig, "role-list.item.");
        roleListItemSkullOwner = skullOwner(membersConfig, "role-list.item.", itemDefinition.getSkullOwner());
        roleListItemName = membersConfig.getString("role-list.item.name", "&a{role}");
        roleListItemLore = membersConfig.getStringList("role-list.item.lore");
        roleListItemEnabled = membersConfig.getBoolean("role-list.item.enabled", true);
        roleListItemPermission = membersConfig.getString("role-list.item.permission", "");
        loadButtonsFromSection(membersConfig, "role-list.buttons", roleListButtonsBySlot, roleListSize);
        loadDecorationsFromSection(membersConfig, "role-list.decorations", roleListDecorationsBySlot, roleListSize);

        memberListTitle = membersConfig.getString("member-list.title", "&8Mitglieder &7- {filter} - Seite {page}");
        memberListSize = normalizeSize(membersConfig.getInt("member-list.size", 54));
        memberListFiller = createFiller(membersConfig, "member-list.filler");
        memberListSlots = membersConfig.getIntegerList("member-list.slots");
        if (memberListSlots.isEmpty()) {
            memberListSlots = defaultListSlots(memberListSize);
        }
        final MaterialDefinition memberItemDefinition = materialDefinition(membersConfig, "member-list.item.", Material.PLAYER_HEAD);
        memberListItemMaterial = memberItemDefinition.getMaterial();
        memberListItemHeadDatabaseId = headDatabaseId(membersConfig, "member-list.item.");
        memberListItemSkullOwner = skullOwner(membersConfig, "member-list.item.", memberItemDefinition.getSkullOwner());
        memberListItemName = membersConfig.getString("member-list.item.name", "&a{member}");
        memberListItemLore = membersConfig.getStringList("member-list.item.lore");
        memberListItemEnabled = membersConfig.getBoolean("member-list.item.enabled", true);
        memberListItemPermission = membersConfig.getString("member-list.item.permission", "");
        loadButtonsFromSection(membersConfig, "member-list.buttons", memberListButtonsBySlot, memberListSize);
        loadDecorationsFromSection(membersConfig, "member-list.decorations", memberListDecorationsBySlot, memberListSize);

        roleMemberListTitle = membersConfig.getString("role-member-list.title", "&8Rolle {role} &7- Seite {page}");
        roleMemberListSize = normalizeSize(membersConfig.getInt("role-member-list.size", 54));
        roleMemberListFiller = createFiller(membersConfig, "role-member-list.filler");
        roleMemberListSlots = membersConfig.getIntegerList("role-member-list.slots");
        if (roleMemberListSlots.isEmpty()) {
            roleMemberListSlots = defaultListSlots(roleMemberListSize);
        }
        final MaterialDefinition roleMemberItemDefinition = materialDefinition(membersConfig, "role-member-list.item.", Material.PLAYER_HEAD);
        roleMemberListItemMaterial = roleMemberItemDefinition.getMaterial();
        roleMemberListItemHeadDatabaseId = headDatabaseId(membersConfig, "role-member-list.item.");
        roleMemberListItemSkullOwner = skullOwner(membersConfig, "role-member-list.item.", roleMemberItemDefinition.getSkullOwner());
        roleMemberListItemName = membersConfig.getString("role-member-list.item.name", "&a{member}");
        roleMemberListItemLore = membersConfig.getStringList("role-member-list.item.lore");
        roleMemberListItemEnabled = membersConfig.getBoolean("role-member-list.item.enabled", true);
        roleMemberListItemPermission = membersConfig.getString("role-member-list.item.permission", "");
        loadButtonsFromSection(membersConfig, "role-member-list.buttons", roleMemberListButtonsBySlot, roleMemberListSize);
        loadDecorationsFromSection(membersConfig, "role-member-list.decorations", roleMemberListDecorationsBySlot, roleMemberListSize);

        rolePermissionListTitle = membersConfig.getString("role-permission-list.title", "&8Rechte: {role} &7- Seite {page}");
        rolePermissionListSize = normalizeSize(membersConfig.getInt("role-permission-list.size", 54));
        rolePermissionListFiller = createFiller(membersConfig, "role-permission-list.filler");
        rolePermissionListSlots = membersConfig.getIntegerList("role-permission-list.slots");
        if (rolePermissionListSlots.isEmpty()) {
            rolePermissionListSlots = defaultListSlots(rolePermissionListSize);
        }
        loadButtonsFromSection(membersConfig, "role-permission-list.buttons", rolePermissionListButtonsBySlot, rolePermissionListSize);
        loadDecorationsFromSection(membersConfig, "role-permission-list.decorations", rolePermissionListDecorationsBySlot, rolePermissionListSize);
        loadRolePermissionTemplates(membersConfig);
    }

    private void loadRolePermissionTemplates(final YamlConfiguration membersConfig) {
        final ConfigurationSection section = membersConfig.getConfigurationSection("role-permission-list.permissions");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final String path = "role-permission-list.permissions." + id + ".";
            if (!membersConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
            final String value = membersConfig.getString(path + "value", id);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            final MaterialDefinition materialDefinition = materialDefinition(membersConfig, path, Material.PAPER);
            final Material activeMaterial = material(membersConfig.getString(path + "active-material", "LIME_DYE"), Material.LIME_DYE);
            final Material inactiveMaterial = material(membersConfig.getString(path + "inactive-material", materialDefinition.getMaterial().name()), materialDefinition.getMaterial());
            rolePermissionTemplates.add(new RolePermissionTemplate(
                    id,
                    value.trim(),
                    activeMaterial,
                    inactiveMaterial,
                    headDatabaseId(membersConfig, path),
                    skullOwner(membersConfig, path, materialDefinition.getSkullOwner()),
                    membersConfig.getString(path + "name", "&a" + id),
                    membersConfig.getStringList(path + "lore"),
                    membersConfig.getString(path + "permission", "")
            ));
        }
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
        final Map<String, ActionMenuTab> nestedTabs = new LinkedHashMap<>();
        loadButtonsFromSection(menuConfig, "buttons", buttons, menuSize);
        loadDecorationsFromSection(menuConfig, "decorations", decorations, menuSize);
        loadActionTabs(id, menuConfig, menuSize, tabs, nestedTabs);
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
                nestedTabs,
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
            final Map<String, SettingsTab> target,
            final Map<String, ActionMenuTab> nestedTarget
    ) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("tabs");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final String path = "tabs." + id + ".";
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
            final List<MenuButton> selectors = loadActionSelectors(menuId, menuConfig, path, id, id, menuSize, Material.BOOK);
            if (selectors.isEmpty()) {
                continue;
            }
            final Map<Integer, MenuButton> tabButtons = new HashMap<>();
            final Map<Integer, MenuButton> tabDecorations = new HashMap<>();
            loadButtonsFromSection(menuConfig, path + "buttons", tabButtons, menuSize);
            loadDecorationsFromSection(menuConfig, path + "decorations", tabDecorations, menuSize);
            final ConfigurationSection subtabSection = menuConfig.getConfigurationSection(path + "subtabs");
            if (subtabSection != null) {
                final Map<String, SettingsTab> subtabs = new LinkedHashMap<>();
                for (final String subtabId : subtabSection.getKeys(false)) {
                    final String subtabPath = path + "subtabs." + subtabId + ".";
                    if (!menuConfig.getBoolean(subtabPath + "enabled", true)) {
                        continue;
                    }
                    final String fullId = id.toLowerCase(Locale.ROOT) + ":" + subtabId.toLowerCase(Locale.ROOT);
                    final List<MenuButton> subtabSelectors = loadActionSelectors(menuId, menuConfig, subtabPath,
                            subtabId, fullId, menuSize, Material.PAPER);
                    if (subtabSelectors.isEmpty()) {
                        continue;
                    }
                    final Map<Integer, MenuButton> subtabButtons = new HashMap<>();
                    final Map<Integer, MenuButton> subtabDecorations = new HashMap<>();
                    loadButtonsFromSection(menuConfig, subtabPath + "buttons", subtabButtons, menuSize);
                    loadDecorationsFromSection(menuConfig, subtabPath + "decorations", subtabDecorations, menuSize);
                    subtabs.put(subtabId.toLowerCase(Locale.ROOT), new SettingsTab(fullId, subtabSelectors, subtabButtons, subtabDecorations));
                }
                nestedTarget.put(id.toLowerCase(Locale.ROOT), new ActionMenuTab(
                        id.toLowerCase(Locale.ROOT),
                        selectors,
                        tabButtons,
                        tabDecorations,
                        subtabs,
                        menuConfig.getString(path + "default-subtab", "")
                ));
                continue;
            }
            target.put(id.toLowerCase(Locale.ROOT), new SettingsTab(id.toLowerCase(Locale.ROOT), selectors, tabButtons, tabDecorations));
        }
    }

    private List<MenuButton> loadActionSelectors(
            final String menuId,
            final YamlConfiguration menuConfig,
            final String path,
            final String id,
            final String navigationId,
            final int menuSize,
            final Material fallbackMaterial
    ) {
        final MaterialDefinition materialDefinition = materialDefinition(menuConfig, path, fallbackMaterial);
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
        for (final int slot : configuredSelectorSlots(menuConfig, path, menuSize, "Menue-Tab", id)) {
            selectors.add(new MenuButton(
                    id,
                    slot,
                    material,
                    headDatabaseId,
                    skullOwner,
                    name,
                    lore,
                    commands.isEmpty() ? Collections.singletonList("open-menu:" + menuId + ":" + navigationId) : commands,
                    false,
                    permission,
                    clickSound,
                    bedrockLabel
            ));
        }
        return selectors;
    }

    private void loadDecorations(final YamlConfiguration menuConfig, final Map<Integer, MenuButton> target, final int menuSize) {
        loadDecorationsFromSection(menuConfig, "decorations", target, menuSize);
    }

    private void loadFlags(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("flags");
        if (section == null) {
            return;
        }

        automaticFlagSlots = flagLayoutSlots(menuConfig);
        automaticFlagLayout = menuConfig.getBoolean("flag-layout.enabled", false) && !automaticFlagSlots.isEmpty();
        if (menuConfig.getBoolean("flag-layout.enabled", false) && automaticFlagSlots.isEmpty()) {
            plugin.getLogger().warning("Flag-Layout ist aktiviert, aber flag-layout.slots enthaelt keine gueltigen Slots.");
        }

        for (final String flag : section.getKeys(false)) {
            final String path = "flags." + flag + ".";
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
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
            if (automaticFlagLayout) {
                automaticFlagEntries.add(new FlagMenuEntry(flag, 1, -1, enabledMaterial, disabledMaterial, name, lore, permission));
                continue;
            }

            final int page = Math.max(1, menuConfig.getInt(path + "page", 1));
            for (final int slot : configuredSlots(menuConfig, path, flagsSize, "Flag", flag)) {
                flagsByPageAndSlot
                        .computeIfAbsent(page, ignored -> new HashMap<>())
                        .put(slot, new FlagMenuEntry(flag, page, slot, enabledMaterial, disabledMaterial, name, lore, permission));
            }
        }
    }

    private List<Integer> flagLayoutSlots(final YamlConfiguration menuConfig) {
        final List<Integer> slots = new ArrayList<>();
        slots.addAll(configuredSlotValues(menuConfig, "flag-layout.slots", "Flag-Layout", "slots"));
        if (slots.isEmpty()) {
            slots.addAll(configuredSlotValues(menuConfig, "flag-layout.areas", "Flag-Layout", "areas"));
        }

        final List<Integer> valid = new ArrayList<>();
        for (final int slot : slots) {
            if (slot < 0 || slot >= flagsSize) {
                plugin.getLogger().warning("Flag-Layout hat einen ungueltigen Slot: " + slot);
                continue;
            }
            if (!valid.contains(slot)) {
                valid.add(slot);
            }
        }
        return valid;
    }

    private void loadSettingsTabs(final YamlConfiguration menuConfig) {
        final ConfigurationSection section = menuConfig.getConfigurationSection("tabs");
        if (section == null) {
            return;
        }

        for (final String id : section.getKeys(false)) {
            final String path = "tabs." + id + ".";
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
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
            final Map<Integer, MenuButton> tabDecorations = new HashMap<>();
            loadButtonsFromSection(menuConfig, path + "buttons", tabButtons, settingsSize);
            loadDecorationsFromSection(menuConfig, path + "decorations", tabDecorations, settingsSize);
            settingsTabs.put(id.toLowerCase(Locale.ROOT), new SettingsTab(id.toLowerCase(Locale.ROOT), selectors, tabButtons, tabDecorations));
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

        final List<Integer> automaticSlots = configuredSectionSlots(menuConfig, sectionPath, menuSize, "Button-Slotbereich", sectionPath);
        final Map<String, List<Integer>> explicitSlotsById = new HashMap<>();
        final Set<Integer> reservedSlots = new LinkedHashSet<>(target.keySet());
        for (final String id : section.getKeys(false)) {
            final String path = sectionPath + "." + id + ".";
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
            if (!hasConfiguredSlots(menuConfig, path)) {
                continue;
            }
            final List<Integer> explicitSlots = configuredSlots(menuConfig, path, menuSize, "Menuebutton", id);
            explicitSlotsById.put(id, explicitSlots);
            reservedSlots.addAll(explicitSlots);
        }

        int automaticSlotIndex = 0;
        for (final String id : section.getKeys(false)) {
            final String path = sectionPath + "." + id + ".";
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
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

            List<Integer> slots = explicitSlotsById.get(id);
            if (slots == null) {
                if (automaticSlots.isEmpty()) {
                    slots = configuredSlots(menuConfig, path, menuSize, "Menuebutton", id);
                } else {
                    int automaticSlot = -1;
                    while (automaticSlotIndex < automaticSlots.size()) {
                        final int candidate = automaticSlots.get(automaticSlotIndex++);
                        if (reservedSlots.contains(candidate)) {
                            continue;
                        }
                        automaticSlot = candidate;
                        reservedSlots.add(candidate);
                        break;
                    }
                    if (automaticSlot < 0) {
                        plugin.getLogger().warning("Menuebutton '" + id + "' hat keinen freien automatischen Slot in '" + sectionPath + "'.");
                        slots = Collections.emptyList();
                    } else {
                        slots = Collections.singletonList(automaticSlot);
                    }
                }
            }

            for (final int slot : slots) {
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
            if (!menuConfig.getBoolean(path + "enabled", true)) {
                continue;
            }
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
        final Map<String, String> allReplacements = globalButtonPlaceholders();
        allReplacements.putAll(replacements);
        ItemStack item = headDatabaseHook.getHead(button.getHeadDatabaseId());
        if (item == null) {
            item = new ItemStack(button.getMaterial());
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applySkullOwner(player, meta, button.getSkullOwner(), allReplacements);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(button.getName(), allReplacements))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(button.getLore(), allReplacements))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Map<String, String> globalButtonPlaceholders() {
        final Map<String, String> placeholders = new HashMap<>();
        if (plugin.getPassiveWitherService() != null) {
            placeholders.put("passive_wither_price", plugin.getPassiveWitherService().getPurchasePriceText());
        }
        return placeholders;
    }

    private ItemStack createFlagItem(final Player player, final FlagMenuEntry flagEntry) {
        final boolean enabled = flagService.isFlagEnabled(player, flagEntry.getFlag());
        return createFlagItem(player, flagEntry, enabled);
    }

    private ItemStack createFlagItem(final Player player, final FlagMenuEntry flagEntry, final boolean enabled) {
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

    private ItemStack createReportListItem(final Player player, final PlotReport report) {
        ItemStack item = headDatabaseHook.getHead(reportListItemHeadDatabaseId);
        if (item == null) {
            item = new ItemStack(reportListItemMaterial);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = reportService.placeholders(report);
            applySkullOwner(player, meta, reportListItemSkullOwner, placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(reportListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(reportListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCommentListItem(final Player player, final PlotComment comment) {
        ItemStack item = headDatabaseHook.getHead(commentListItemHeadDatabaseId);
        if (item == null) {
            item = new ItemStack(commentListItemMaterial);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = commentPlaceholders(comment);
            applySkullOwner(player, meta, commentListItemSkullOwner, placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(commentListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(commentListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRoleListItem(final Player player, final PlotRole role) {
        ItemStack item = headDatabaseHook.getHead(roleListItemHeadDatabaseId);
        if (item == null) {
            item = new ItemStack(roleListItemMaterial);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = roleService.placeholders(role);
            applySkullOwner(player, meta, roleListItemSkullOwner, placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(roleListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(roleListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMemberListItem(
            final Player player,
            final PlotMemberEntry member,
            final String plotKey,
            final String filter,
            final int page
    ) {
        ItemStack item = headDatabaseHook.getHead(memberListItemHeadDatabaseId);
        if (item == null) {
            item = new ItemStack(memberListItemMaterial);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = memberPlaceholders(player, member, plotKey, filter, page);
            applySkullOwner(player, meta, memberListItemSkullOwner, placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(memberListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(memberListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRoleMemberListItem(
            final Player player,
            final PlotMemberEntry member,
            final String roleName,
            final String plotKey,
            final int page
    ) {
        ItemStack item = headDatabaseHook.getHead(roleMemberListItemHeadDatabaseId);
        if (item == null) {
            item = new ItemStack(roleMemberListItemMaterial);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = memberPlaceholders(player, member, plotKey, "all", page);
            final String currentRole = roleService.memberRole(plotKey, member.getUuid());
            placeholders.put("role", roleName == null ? "" : roleName);
            placeholders.put("current_role", currentRole == null || currentRole.trim().isEmpty() ? "-" : currentRole);
            placeholders.put("assigned", currentRole != null && currentRole.equalsIgnoreCase(roleName)
                    ? text("role-assigned-yes", "Ja")
                    : text("role-assigned-no", "Nein"));
            applySkullOwner(player, meta, roleMemberListItemSkullOwner, placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(roleMemberListItemName, placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(roleMemberListItemLore, placeholders))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRolePermissionItem(
            final Player player,
            final PlotRole role,
            final RolePermissionTemplate template,
            final int page
    ) {
        final boolean active = hasRolePermission(role, template.getValue());
        ItemStack item = headDatabaseHook.getHead(template.getHeadDatabaseId());
        if (item == null) {
            item = new ItemStack(active ? template.getActiveMaterial() : template.getInactiveMaterial());
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = roleListPlaceholders(page, page, role.getName(), rolePermissionTemplates.size());
            placeholders.put("permission", template.getValue());
            placeholders.put("status", active ? text("role-permission-active", "Aktiv") : text("role-permission-inactive", "Inaktiv"));
            applySkullOwner(player, meta, template.getSkullOwner(), placeholders);
            meta.setDisplayName(Text.color(placeholderHook.apply(player, applyPlaceholders(template.getName(), placeholders))));
            meta.setLore(Text.color(placeholderHook.apply(player, applyPlaceholders(template.getLore(), placeholders))));
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
        if (isFeatureDisabled(button.getId())) {
            return false;
        }
        if (button.getPermission() == null || button.getPermission().trim().isEmpty()) {
            return true;
        }
        final String permission = button.getPermission().trim();
        return hasConfiguredPermission(player, permission);
    }

    private boolean configuredItemVisible(
            final Player player,
            final YamlConfiguration configuration,
            final String path
    ) {
        if (configuration == null) {
            return false;
        }
        if (!configuration.getBoolean(path + "enabled", true)) {
            return false;
        }
        return configuredItemVisible(player, true, configuration.getString(path + "permission", ""));
    }

    private boolean configuredItemVisible(final Player player, final boolean enabled, final String permission) {
        if (!enabled) {
            return false;
        }
        if (permission == null || permission.trim().isEmpty()) {
            return true;
        }
        return hasConfiguredPermission(player, permission.trim());
    }

    private boolean isFeatureDisabled(final String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        final String key = "features." + id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return plugin.getConfig().contains(key) && !plugin.getConfig().getBoolean(key, true);
    }

    private boolean hasConfiguredPermission(final Player player, final String permission) {
        if (permission.toLowerCase(Locale.ROOT).startsWith("role:")) {
            return roleService.hasRolePermission(player, permission.substring("role:".length()));
        }
        if (permission.toLowerCase(Locale.ROOT).startsWith("plots.")) {
            return flagService.hasPermission(player, permission);
        }
        return player.hasPermission(permission);
    }

    private boolean canSeeFlag(final Player player, final FlagMenuEntry flagEntry) {
        return canSeeFlag(player, flagEntry, new FlagPermissionLookup(player));
    }

    private boolean canSeeFlag(
            final Player player,
            final FlagMenuEntry flagEntry,
            final FlagPermissionLookup permissions
    ) {
        if (flagEntry.getPermission() != null && !flagEntry.getPermission().trim().isEmpty()) {
            return permissions.hasConfiguredPermission(flagEntry.getPermission().trim())
                    || permissions.hasAnyFlagPermission(flagEntry.getFlag());
        }
        return permissions.hasAnyFlagPermission(flagEntry.getFlag());
    }

    private boolean hasFlagTogglePermission(
            final Player player,
            final FlagMenuEntry flagEntry,
            final boolean target
    ) {
        return hasFlagTogglePermission(player, flagEntry, target, new FlagPermissionLookup(player));
    }

    private boolean hasFlagTogglePermission(
            final Player player,
            final FlagMenuEntry flagEntry,
            final boolean target,
            final FlagPermissionLookup permissions
    ) {
        if (flagEntry.getPermission() != null
                && !flagEntry.getPermission().trim().isEmpty()
                && permissions.hasConfiguredPermission(flagEntry.getPermission().trim())) {
            return true;
        }
        return permissions.hasTogglePermission(flagEntry.getFlag(), target);
    }

    private int maxFlagPage(final Player player) {
        return flagMenuView(player, 1).getMaxPage();
    }

    private Map<String, String> flagMenuPlaceholders(final int page, final int maxPage) {
        final int normalizedMaxPage = Math.max(1, maxPage);
        final int normalizedPage = Math.min(Math.max(1, page), normalizedMaxPage);
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(normalizedPage));
        placeholders.put("max_page", String.valueOf(normalizedMaxPage));
        placeholders.put("next_page", String.valueOf(Math.min(normalizedMaxPage, normalizedPage + 1)));
        placeholders.put("previous_page", String.valueOf(Math.max(1, normalizedPage - 1)));
        return placeholders;
    }

    private Map<Integer, FlagMenuEntry> flagsForPage(final Player player, final int page) {
        return flagMenuView(player, page).getFlagsBySlot();
    }

    private FlagMenuView flagMenuView(final Player player, final int page) {
        final int requestedPage = Math.max(1, page);
        final FlagPermissionLookup permissions = new FlagPermissionLookup(player);
        if (!automaticFlagLayout) {
            int maxPage = 1;
            for (final Map.Entry<Integer, Map<Integer, FlagMenuEntry>> pageEntry : flagsByPageAndSlot.entrySet()) {
                for (final FlagMenuEntry entry : pageEntry.getValue().values()) {
                    if (canSeeFlag(player, entry, permissions)) {
                        maxPage = Math.max(maxPage, pageEntry.getKey());
                        break;
                    }
                }
            }
            final int actualPage = Math.min(requestedPage, maxPage);
            final Map<Integer, FlagMenuEntry> configured = flagsByPageAndSlot.getOrDefault(actualPage, Collections.emptyMap());
            final Map<Integer, FlagMenuEntry> visible = new LinkedHashMap<>();
            for (final Map.Entry<Integer, FlagMenuEntry> entry : configured.entrySet()) {
                if (canSeeFlag(player, entry.getValue(), permissions)) {
                    visible.put(entry.getKey(), entry.getValue());
                }
            }
            return new FlagMenuView(actualPage, maxPage, visible, permissions);
        }
        if (automaticFlagSlots == null || automaticFlagSlots.isEmpty() || automaticFlagEntries.isEmpty()) {
            return new FlagMenuView(1, 1, Collections.emptyMap(), permissions);
        }

        final List<FlagMenuEntry> visibleTemplates = new ArrayList<>();
        for (final FlagMenuEntry template : automaticFlagEntries) {
            if (canSeeFlag(player, template, permissions)) {
                visibleTemplates.add(template);
            }
        }
        final int maxPage = Math.max(1, (int) Math.ceil(visibleTemplates.size() / (double) automaticFlagSlots.size()));
        final int actualPage = Math.min(requestedPage, maxPage);
        final Map<Integer, FlagMenuEntry> visible = new LinkedHashMap<>();
        int visibleIndex = 0;
        for (final FlagMenuEntry template : visibleTemplates) {
            final int entryPage = (visibleIndex / automaticFlagSlots.size()) + 1;
            final int slot = automaticFlagSlots.get(visibleIndex % automaticFlagSlots.size());
            if (entryPage == actualPage) {
                visible.put(slot, positionedFlag(template, entryPage, slot));
            } else if (entryPage > actualPage) {
                break;
            }
            visibleIndex++;
        }
        return new FlagMenuView(actualPage, maxPage, visible, permissions);
    }

    private FlagMenuEntry positionedFlag(final FlagMenuEntry template, final int page, final int slot) {
        return new FlagMenuEntry(
                template.getFlag(),
                page,
                slot,
                template.getEnabledMaterial(),
                template.getDisabledMaterial(),
                template.getName(),
                template.getLore(),
                template.getPermission()
        );
    }

    private Set<String> flagNames(final Iterable<FlagMenuEntry> entries) {
        final Set<String> names = new LinkedHashSet<>();
        if (entries == null) {
            return names;
        }
        for (final FlagMenuEntry entry : entries) {
            if (entry != null && entry.getFlag() != null && !entry.getFlag().trim().isEmpty()) {
                names.add(entry.getFlag().trim().toLowerCase(Locale.ROOT));
            }
        }
        return names;
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
        if (!configuredItemVisible(player, myPlotsConfig, "plot-item.")) {
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
            final String tag = availableTags().get(tagIndex);
            final boolean active = plotDataStore.metadata(plotKey).getTags().contains(tag);
            final String tagPath = active ? "detail.tag-active." : "detail.tag-inactive.";
            if (!configuredItemVisible(player, myPlotsConfig, tagPath)) {
                return;
            }
            toggleTag(player, plot, tag);
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

    private void handlePlotPurchaseClick(final Player player, final int slot, final ClickType clickType) {
        final MenuButton button = plotPurchaseButtonsBySlot.get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        if ("amount".equalsIgnoreCase(button.getId()) || "selection".equalsIgnoreCase(button.getId())) {
            final int delta;
            if (clickType == ClickType.SHIFT_LEFT) {
                delta = 10;
            } else if (clickType == ClickType.SHIFT_RIGHT) {
                delta = -10;
            } else if (clickType.isRightClick()) {
                delta = -1;
            } else {
                delta = 1;
            }
            plotPurchaseService.adjustSelection(player, delta);
            openPlotPurchaseMenu(player);
            return;
        }
        executeCommands(player, button.isCloseInventory(), button.getCommands());
    }

    private void handlePlotPurchaseConfirmClick(final Player player, final int slot) {
        final MenuButton button = plotPurchaseConfirmButtonsBySlot.get(slot);
        if (button == null || !canSee(player, button)) {
            return;
        }
        executeCommands(player, button.isCloseInventory(), button.getCommands());
    }

    private void handleActionMenuClick(final Player player, final String menuId, final String tabId, final int slot) {
        final ActionMenu menu = actionMenus.get(normalizeActionMenuId(menuId));
        if (menu == null) {
            return;
        }
        if (!menu.getNestedTabs().isEmpty()) {
            handleNestedActionMenuClick(player, menu, tabId, slot);
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

    private void handleNestedActionMenuClick(final Player player, final ActionMenu menu, final String tabId, final int slot) {
        final ActionMenuSelection selection = actionMenuSelection(player, menu, tabId);
        final ActionMenuTab tab = selection == null ? null : selection.getTab();
        final SettingsTab subtab = selection == null ? null : selection.getSubtab();
        for (final ActionMenuTab actionTab : menu.getNestedTabs().values()) {
            for (final MenuButton selector : actionTab.getSelectors()) {
                if (selector.getSlot() == slot && canSee(player, selector)) {
                    executeButtonCommands(player, selector);
                    return;
                }
            }
        }
        if (tab != null) {
            for (final SettingsTab candidate : tab.getSubtabs().values()) {
                for (final MenuButton selector : candidate.getSelectors()) {
                    if (selector.getSlot() == slot && canSee(player, selector)) {
                        executeButtonCommands(player, selector);
                        return;
                    }
                }
            }
        }

        final MenuButton rootButton = menu.getButtonsBySlot().get(slot);
        if (rootButton != null && canSee(player, rootButton)) {
            executeButtonCommands(player, rootButton);
            return;
        }
        if (tab != null) {
            final MenuButton tabButton = tab.getButtonsBySlot().get(slot);
            if (tabButton != null && canSee(player, tabButton)) {
                executeButtonCommands(player, tabButton);
                return;
            }
        }
        if (subtab != null) {
            final MenuButton subtabButton = subtab.getButtonsBySlot().get(slot);
            if (subtabButton != null && canSee(player, subtabButton)) {
                executeButtonCommands(player, subtabButton);
            }
        }
    }

    private void handleFlagsClick(final Player player, final int page, final int slot, final Inventory inventory) {
        final FlagMenuView view = flagMenuView(player, page);
        final MenuButton button = flagButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            playSound(player, button.getClickSound());
            executeCommands(player, button.isCloseInventory(),
                    applyCommandState(button.getCommands(), flagMenuPlaceholders(view.getPage(), view.getMaxPage())),
                    button);
            return;
        }

        final FlagMenuEntry flagEntry = view.getFlagsBySlot().get(slot);
        if (flagEntry == null) {
            return;
        }
        final boolean current = flagService.isFlagEnabled(player, flagEntry.getFlag());
        final boolean target = !current;
        if (!canModifyCurrentPlotFlag(player, flagEntry.getFlag())) {
            return;
        }
        if (!hasFlagTogglePermission(player, flagEntry, target, view.getPermissions())) {
            languageManager.send(player, "flag-no-permission");
            return;
        }

        flagService.setBooleanFlagWithFallback(player, flagEntry.getFlag(), target);
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("flag", Text.color(flagEntry.getName()));
        placeholders.put("status", Text.color(target ? statusEnabled : statusDisabled));
        languageManager.send(player, "flag-toggled", placeholders);
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, createFlagItem(player, flagEntry, target));
        }
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
        if (!configuredItemVisible(player, backupListItemEnabled, backupListItemPermission)) {
            return;
        }
        player.closeInventory();
        backupService.requestRestore(player, backupId);
    }

    private void handleReportListClick(final Player player, final int page, final String status, final int slot, final ClickType clickType) {
        final MenuButton button = reportListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            final List<String> commands = new ArrayList<>();
            for (final String command : button.getCommands()) {
                commands.add(command
                        .replace("{page}", String.valueOf(page))
                        .replace("{next_page}", String.valueOf(page + 1))
                        .replace("{previous_page}", String.valueOf(Math.max(1, page - 1)))
                        .replace("{status}", status == null ? "open" : status));
            }
            executeCommands(player, button.isCloseInventory(), commands);
            return;
        }

        final PlotReport report = reportAt(page, status, slot);
        if (report == null) {
            return;
        }
        if (!configuredItemVisible(player, reportListItemEnabled, reportListItemPermission)) {
            return;
        }
        if (clickType == ClickType.RIGHT) {
            reportService.close(player, report.getId());
            openReportListMenu(player, page, status);
            return;
        }
        if (clickType == ClickType.SHIFT_LEFT) {
            reportService.reopen(player, report.getId());
            openReportListMenu(player, page, status);
            return;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            reportService.setPriority(player, report.getId(), "high");
            openReportListMenu(player, page, status);
            return;
        }
        player.closeInventory();
        player.performCommand("plot visit " + report.getWorld() + ";" + report.getPlot());
    }

    private void handleCommentListClick(final Player player, final int page, final int slot) {
        final MenuButton button = commentListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            final List<PlotComment> comments = plotDataStore.comments(currentPlotKey(player));
            final int pageSize = Math.max(1, commentListSlots == null || commentListSlots.isEmpty() ? 1 : commentListSlots.size());
            final int maxPage = Math.max(1, (int) Math.ceil(comments.size() / (double) pageSize));
            executeCommands(player, button.isCloseInventory(), applyCommandState(button.getCommands(), commentListPlaceholders(page, maxPage, comments.size())));
            return;
        }

        final PlotComment comment = commentAt(player, page, slot);
        if (comment == null || !configuredItemVisible(player, commentListItemEnabled, commentListItemPermission)) {
            return;
        }
        openCommentListMenu(player, page);
    }

    private void handleMemberListClick(final Player player, final int page, final String filter, final int slot, final ClickType clickType) {
        final MenuButton button = memberListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            executeCommands(player, button.isCloseInventory(), applyCommandState(button.getCommands(), memberListPlaceholders(page, page, normalizeMemberFilter(filter), filteredPlotMembers(player, normalizeMemberFilter(filter)).size())));
            return;
        }

        final PlotMemberEntry member = memberAt(player, page, filter, slot);
        if (member == null || !configuredItemVisible(player, memberListItemEnabled, memberListItemPermission)) {
            return;
        }
        if (member.getType() == PlotMemberType.OWNER) {
            languageManager.send(player, "member-owner-protected", memberPlaceholders(player, member, currentPlotKey(player), normalizeMemberFilter(filter), page));
            return;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            removePlotMember(player, member);
            reopenMemberList(player, page, filter);
            return;
        }
        if (member.getType() == PlotMemberType.TRUSTED) {
            demotePlotMember(player, member);
        } else if (member.getType() == PlotMemberType.ADDED) {
            promotePlotMember(player, member);
        } else if (member.getType() == PlotMemberType.DENIED) {
            allowDeniedMember(player, member);
        }
        reopenMemberList(player, page, filter);
    }

    private void handleRoleListClick(final Player player, final int page, final int slot, final ClickType clickType) {
        final MenuButton button = roleListButtonsBySlot.get(slot);
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

        final PlotRole role = roleAt(player, page, slot);
        if (role == null) {
            return;
        }
        if (!configuredItemVisible(player, roleListItemEnabled, roleListItemPermission)) {
            return;
        }
        if (clickType == ClickType.RIGHT) {
            openRolePermissionListMenu(player, 1, role.getName());
            return;
        }
        if (clickType == ClickType.SHIFT_LEFT) {
            runCommand(player, "chat-input:chat-role-name:role:rename:" + role.getName() + ":{input}");
            return;
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            roleService.deleteRole(player, role.getName());
            openRoleListMenu(player, page);
            return;
        }
        openRoleMemberListMenu(player, 1, role.getName());
    }

    private void handleRoleMemberListClick(final Player player, final int page, final String roleName, final int slot, final ClickType clickType) {
        final MenuButton button = roleMemberListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            executeCommands(player, button.isCloseInventory(), applyCommandState(button.getCommands(), roleListPlaceholders(page, page, roleName, filteredPlotMembers(player, "all").size())));
            return;
        }

        final PlotRole role = roleByName(player, roleName);
        if (role == null) {
            languageManager.send(player, "role-not-found", rolePlaceholder(roleName));
            return;
        }
        final PlotMemberEntry member = roleMemberAt(player, page, slot);
        if (member == null || !configuredItemVisible(player, roleMemberListItemEnabled, roleMemberListItemPermission)) {
            return;
        }
        if (member.getType() == PlotMemberType.OWNER) {
            languageManager.send(player, "member-owner-protected", memberPlaceholders(player, member, currentPlotKey(player), "all", page));
            return;
        }
        final String plotKey = currentPlotKey(player);
        final String currentRole = roleService.memberRole(plotKey, member.getUuid());
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT || currentRole.equalsIgnoreCase(role.getName())) {
            roleService.unassign(player, member.getUuid(), member.getName());
        } else {
            roleService.assign(player, role.getName(), member.getUuid(), member.getName());
        }
        openRoleMemberListMenu(player, page, role.getName());
    }

    private void handleRolePermissionListClick(final Player player, final int page, final String roleName, final int slot) {
        final MenuButton button = rolePermissionListButtonsBySlot.get(slot);
        if (button != null) {
            if (!canSee(player, button)) {
                return;
            }
            executeCommands(player, button.isCloseInventory(), applyCommandState(button.getCommands(), roleListPlaceholders(page, page, roleName, rolePermissionTemplates.size())));
            return;
        }

        final PlotRole role = roleByName(player, roleName);
        if (role == null) {
            languageManager.send(player, "role-not-found", rolePlaceholder(roleName));
            return;
        }
        final RolePermissionTemplate template = rolePermissionAt(page, slot);
        if (template == null || !configuredItemVisible(player, true, template.getPermission())) {
            return;
        }
        if (hasRolePermission(role, template.getValue())) {
            roleService.removePermission(player, role.getName(), template.getValue());
        } else {
            roleService.addPermission(player, role.getName(), template.getValue());
        }
        openRolePermissionListMenu(player, page, role.getName());
    }

    private PlotReport reportAt(final int page, final String status, final int slot) {
        if (reportListSlots == null || reportListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = reportListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final List<PlotReport> reports = reportService.list(status);
        final int index = (Math.max(1, page) - 1) * reportListSlots.size() + slotIndex;
        if (index < 0 || index >= reports.size()) {
            return null;
        }
        return reports.get(index);
    }

    private PlotComment commentAt(final Player player, final int page, final int slot) {
        if (commentListSlots == null || commentListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = commentListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final String plotKey = currentPlotKey(player);
        if (plotKey.isEmpty()) {
            return null;
        }
        final List<PlotComment> comments = plotDataStore.comments(plotKey);
        final int index = (Math.max(1, page) - 1) * commentListSlots.size() + slotIndex;
        if (index < 0 || index >= comments.size()) {
            return null;
        }
        return comments.get(index);
    }

    private PlotRole roleAt(final Player player, final int page, final int slot) {
        if (roleListSlots == null || roleListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = roleListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final List<PlotRole> roles = roleService.roles(player);
        final int index = (Math.max(1, page) - 1) * roleListSlots.size() + slotIndex;
        if (index < 0 || index >= roles.size()) {
            return null;
        }
        return roles.get(index);
    }

    private PlotMemberEntry memberAt(final Player player, final int page, final String filter, final int slot) {
        if (memberListSlots == null || memberListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = memberListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final List<PlotMemberEntry> members = filteredPlotMembers(player, normalizeMemberFilter(filter));
        final int index = (Math.max(1, page) - 1) * memberListSlots.size() + slotIndex;
        if (index < 0 || index >= members.size()) {
            return null;
        }
        return members.get(index);
    }

    private PlotMemberEntry roleMemberAt(final Player player, final int page, final int slot) {
        if (roleMemberListSlots == null || roleMemberListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = roleMemberListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final List<PlotMemberEntry> members = filteredPlotMembers(player, "all");
        final int index = (Math.max(1, page) - 1) * roleMemberListSlots.size() + slotIndex;
        if (index < 0 || index >= members.size()) {
            return null;
        }
        return members.get(index);
    }

    private RolePermissionTemplate rolePermissionAt(final int page, final int slot) {
        if (rolePermissionListSlots == null || rolePermissionListSlots.isEmpty()) {
            return null;
        }
        final int slotIndex = rolePermissionListSlots.indexOf(slot);
        if (slotIndex < 0) {
            return null;
        }
        final int index = (Math.max(1, page) - 1) * rolePermissionListSlots.size() + slotIndex;
        if (index < 0 || index >= rolePermissionTemplates.size()) {
            return null;
        }
        return rolePermissionTemplates.get(index);
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

    private List<PlotMemberEntry> filteredPlotMembers(final Player player, final String filter) {
        final String normalizedFilter = normalizeMemberFilter(filter);
        final List<PlotMemberEntry> members = new ArrayList<>();
        for (final PlotMemberEntry member : flagService.currentPlotMembers(player)) {
            if (!"all".equals(normalizedFilter) && !normalizedFilter.equals(member.getType().name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            members.add(member);
        }
        members.sort((first, second) -> {
            final int typeCompare = Integer.compare(memberTypeOrder(first.getType()), memberTypeOrder(second.getType()));
            if (typeCompare != 0) {
                return typeCompare;
            }
            return first.getName().compareToIgnoreCase(second.getName());
        });
        return members;
    }

    private int memberTypeOrder(final PlotMemberType type) {
        if (type == PlotMemberType.OWNER) {
            return 0;
        }
        if (type == PlotMemberType.TRUSTED) {
            return 1;
        }
        if (type == PlotMemberType.ADDED) {
            return 2;
        }
        return 3;
    }

    private String normalizeMemberFilter(final String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return "all";
        }
        final String normalized = filter.trim().toLowerCase(Locale.ROOT);
        if ("trusted".equals(normalized) || "added".equals(normalized) || "denied".equals(normalized)) {
            return normalized;
        }
        return "all";
    }

    private Map<String, String> memberListPlaceholders(final int page, final int maxPage, final String filter, final int amount) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page));
        placeholders.put("max_page", String.valueOf(Math.max(1, maxPage)));
        placeholders.put("next_page", String.valueOf(page + 1));
        placeholders.put("previous_page", String.valueOf(Math.max(1, page - 1)));
        placeholders.put("filter", normalizeMemberFilter(filter));
        placeholders.put("filter_name", memberFilterName(filter));
        placeholders.put("amount", String.valueOf(amount));
        return placeholders;
    }

    private Map<String, String> roleListPlaceholders(final int page, final int maxPage, final String roleName, final int amount) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page));
        placeholders.put("max_page", String.valueOf(Math.max(1, maxPage)));
        placeholders.put("next_page", String.valueOf(page + 1));
        placeholders.put("previous_page", String.valueOf(Math.max(1, page - 1)));
        placeholders.put("role", roleName == null ? "" : roleName);
        placeholders.put("amount", String.valueOf(amount));
        return placeholders;
    }

    private Map<String, String> commentListPlaceholders(final int page, final int maxPage, final int amount) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page));
        placeholders.put("max_page", String.valueOf(Math.max(1, maxPage)));
        placeholders.put("next_page", String.valueOf(Math.min(Math.max(1, maxPage), page + 1)));
        placeholders.put("previous_page", String.valueOf(Math.max(1, page - 1)));
        placeholders.put("comments", String.valueOf(amount));
        placeholders.put("amount", String.valueOf(amount));
        return placeholders;
    }

    private Map<String, String> communityPlaceholders(final Player player) {
        final Map<String, String> placeholders = new HashMap<>();
        final String plotKey = currentPlotKey(player);
        placeholders.put("plot_key", plotKey.isEmpty() ? "-" : plotKey);
        if (plotKey.isEmpty()) {
            placeholders.put("likes", "0");
            placeholders.put("rating_average", "0.0");
            placeholders.put("rating_count", "0");
            placeholders.put("comments", "0");
            placeholders.put("liked", text("community-liked-no", "Nein"));
            return placeholders;
        }
        placeholders.put("likes", String.valueOf(plotDataStore.likeCount(plotKey)));
        placeholders.put("rating_average", String.format(Locale.US, "%.1f", plotDataStore.averageRating(plotKey)));
        placeholders.put("rating_count", String.valueOf(plotDataStore.ratingCount(plotKey)));
        placeholders.put("comments", String.valueOf(plotDataStore.commentCount(plotKey)));
        placeholders.put("liked", plotDataStore.hasLike(player.getUniqueId(), plotKey)
                ? text("community-liked-yes", "Ja")
                : text("community-liked-no", "Nein"));
        return placeholders;
    }

    private Map<String, String> commentPlaceholders(final PlotComment comment) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", comment.getId());
        placeholders.put("author", comment.getAuthorName());
        placeholders.put("author_uuid", comment.getAuthorUuid() == null ? "" : comment.getAuthorUuid().toString());
        placeholders.put("message", comment.getMessage());
        placeholders.put("created", comment.getCreatedAt() <= 0L ? "-" : formatDate(comment.getCreatedAt()));
        return placeholders;
    }

    private Map<String, String> memberPlaceholders(
            final Player player,
            final PlotMemberEntry member,
            final String plotKey,
            final String filter,
            final int page
    ) {
        final Map<String, String> placeholders = memberListPlaceholders(page, page, filter, 0);
        placeholders.put("member", member.getName());
        placeholders.put("member_name", member.getName());
        placeholders.put("uuid", member.getUuid().toString());
        placeholders.put("type", memberTypeName(member.getType()));
        placeholders.put("type_id", member.getType().name().toLowerCase(Locale.ROOT));
        final String roleName = member.getType() == PlotMemberType.OWNER
                ? text("member-role-owner", "Owner")
                : roleService.memberRole(plotKey, member.getUuid());
        placeholders.put("role", roleName == null || roleName.trim().isEmpty() ? "-" : roleName);
        placeholders.put("player", player.getName());
        return placeholders;
    }

    private String memberTypeName(final PlotMemberType type) {
        if (type == PlotMemberType.OWNER) {
            return text("member-type-owner", "Besitzer");
        }
        if (type == PlotMemberType.TRUSTED) {
            return text("member-type-trusted", "Trusted");
        }
        if (type == PlotMemberType.ADDED) {
            return text("member-type-added", "Added");
        }
        return text("member-type-denied", "Denied");
    }

    private String memberFilterName(final String filter) {
        final String normalized = normalizeMemberFilter(filter);
        if ("trusted".equals(normalized)) {
            return text("member-filter-trusted", "Trusted");
        }
        if ("added".equals(normalized)) {
            return text("member-filter-added", "Added");
        }
        if ("denied".equals(normalized)) {
            return text("member-filter-denied", "Denied");
        }
        return text("member-filter-all", "Alle");
    }

    private String currentPlotKey(final Player player) {
        final Optional<PlotContext> context = flagService.currentPlotContext(player);
        if (!context.isPresent() || !context.get().isComplete()) {
            return "";
        }
        return context.get().getWorldName() + ";" + context.get().getPlotId();
    }

    private boolean canModifyCurrentPlotFlag(final Player player, final String flag) {
        final Optional<PlotIdentity> context = flagService.currentPlotIdentity(player);
        if (!context.isPresent() || !context.get().isComplete()) {
            languageManager.send(player, "no-plot");
            return false;
        }
        final PlotIdentity plot = context.get();
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId())) {
            return true;
        }
        if (player.hasPermission("craftplayplotextras.flags.bypass")) {
            return true;
        }
        final String normalizedFlag = flag == null ? "" : flag.trim().toLowerCase(Locale.ROOT);
        if (normalizedFlag.isEmpty()) {
            languageManager.send(player, "flag-no-plot-right");
            return false;
        }
        if (roleService.hasRolePermission(plot.getPlotKey(), player.getUniqueId(), plot.getOwnerUuid(), "flags")
                || roleService.hasRolePermission(plot.getPlotKey(), player.getUniqueId(), plot.getOwnerUuid(), "flags." + normalizedFlag)
                || roleService.hasRolePermission(plot.getPlotKey(), player.getUniqueId(), plot.getOwnerUuid(), "flag." + normalizedFlag)) {
            return true;
        }
        languageManager.send(player, "flag-no-plot-right");
        return false;
    }

    private PlotRole roleByName(final Player player, final String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return null;
        }
        for (final PlotRole role : roleService.roles(player)) {
            if (role.getName().equalsIgnoreCase(roleName.trim())) {
                return role;
            }
        }
        return null;
    }

    private Map<String, String> rolePlaceholder(final String roleName) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("role", roleName == null ? "" : roleName);
        return placeholders;
    }

    private boolean hasRolePermission(final PlotRole role, final String permission) {
        for (final String current : role.getPermissions()) {
            if (current.equalsIgnoreCase(permission)) {
                return true;
            }
        }
        return false;
    }

    private void promotePlotMember(final Player player, final PlotMemberEntry member) {
        if (!hasConfiguredPermission(player, "plots.trust")) {
            languageManager.send(player, "no-permission");
            return;
        }
        player.performCommand("plot trust " + member.getName());
        languageManager.send(player, "member-promoted", memberPlaceholders(player, member, currentPlotKey(player), "all", 1));
    }

    private void demotePlotMember(final Player player, final PlotMemberEntry member) {
        if (!hasConfiguredPermission(player, "plots.trust") || !hasConfiguredPermission(player, "plots.add")) {
            languageManager.send(player, "no-permission");
            return;
        }
        player.performCommand("plot untrust " + member.getName());
        player.performCommand("plot add " + member.getName());
        languageManager.send(player, "member-demoted", memberPlaceholders(player, member, currentPlotKey(player), "all", 1));
    }

    private void allowDeniedMember(final Player player, final PlotMemberEntry member) {
        if (!hasConfiguredPermission(player, "plots.deny")) {
            languageManager.send(player, "no-permission");
            return;
        }
        player.performCommand("plot undeny " + member.getName());
        languageManager.send(player, "member-allowed", memberPlaceholders(player, member, currentPlotKey(player), "all", 1));
    }

    private void removePlotMember(final Player player, final PlotMemberEntry member) {
        if (member.getType() == PlotMemberType.TRUSTED) {
            if (!hasConfiguredPermission(player, "plots.trust")) {
                languageManager.send(player, "no-permission");
                return;
            }
            player.performCommand("plot untrust " + member.getName());
        } else if (member.getType() == PlotMemberType.ADDED) {
            if (!hasConfiguredPermission(player, "plots.remove")) {
                languageManager.send(player, "no-permission");
                return;
            }
            player.performCommand("plot remove " + member.getName());
        } else if (member.getType() == PlotMemberType.DENIED) {
            if (!hasConfiguredPermission(player, "plots.deny")) {
                languageManager.send(player, "no-permission");
                return;
            }
            player.performCommand("plot undeny " + member.getName());
        }
        languageManager.send(player, "member-removed", memberPlaceholders(player, member, currentPlotKey(player), "all", 1));
    }

    private void reopenMemberList(final Player player, final int page, final String filter) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                openMemberListMenu(player, page, filter);
            }
        }, reopenDelayTicks);
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
        placeholders.put("rating_count", String.valueOf(metadata.getRatingCount()));
        placeholders.put("likes", String.valueOf(metadata.getLikes()));
        placeholders.put("comments", String.valueOf(metadata.getComments()));
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

    private ActionMenuSelection actionMenuSelection(final Player player, final ActionMenu menu, final String requestedTabId) {
        if (menu.getNestedTabs().isEmpty()) {
            return null;
        }
        final String raw = requestedTabId == null ? "" : requestedTabId.trim().toLowerCase(Locale.ROOT);
        String tabId = raw;
        String subtabId = "";
        final int separator = raw.indexOf(':');
        if (separator >= 0) {
            tabId = raw.substring(0, separator);
            subtabId = raw.substring(separator + 1);
        } else if (raw.indexOf('-') > 0) {
            final int legacySeparator = raw.indexOf('-');
            final String possibleTab = raw.substring(0, legacySeparator);
            if (menu.getNestedTabs().containsKey(possibleTab)) {
                tabId = possibleTab;
                subtabId = raw.substring(legacySeparator + 1);
            }
        }

        ActionMenuTab tab = visibleActionTab(player, menu.getNestedTabs().get(tabId));
        if (tab == null) {
            tab = visibleActionTab(player, menu.getNestedTabs().get(menu.getDefaultTab().toLowerCase(Locale.ROOT)));
        }
        if (tab == null) {
            for (final ActionMenuTab candidate : menu.getNestedTabs().values()) {
                tab = visibleActionTab(player, candidate);
                if (tab != null) {
                    break;
                }
            }
        }
        if (tab == null) {
            return null;
        }

        SettingsTab subtab = visibleSubtab(player, tab.getSubtabs().get(subtabId));
        if (subtab == null) {
            subtab = visibleSubtab(player, tab.getSubtabs().get(tab.getDefaultSubtab().toLowerCase(Locale.ROOT)));
        }
        if (subtab == null) {
            for (final SettingsTab candidate : tab.getSubtabs().values()) {
                subtab = visibleSubtab(player, candidate);
                if (subtab != null) {
                    break;
                }
            }
        }
        return new ActionMenuSelection(tab, subtab);
    }

    private ActionMenuTab visibleActionTab(final Player player, final ActionMenuTab tab) {
        if (tab == null) {
            return null;
        }
        for (final MenuButton selector : tab.getSelectors()) {
            if (canSee(player, selector)) {
                return tab;
            }
        }
        return null;
    }

    private SettingsTab visibleSubtab(final Player player, final SettingsTab tab) {
        if (tab == null) {
            return null;
        }
        for (final MenuButton selector : tab.getSelectors()) {
            if (canSee(player, selector)) {
                return tab;
            }
        }
        return null;
    }

    private Map<String, String> actionMenuPlaceholders(final Player player, final ActionMenu menu, final SettingsTab tab) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("menu", menu.getId());
        placeholders.put("tab", tab == null ? "" : tabName(tab));
        placeholders.put("tab_id", tab == null ? "" : tab.getId());
        if ("community".equalsIgnoreCase(menu.getId())) {
            placeholders.putAll(communityPlaceholders(player));
        }
        return placeholders;
    }

    private Map<String, String> actionMenuPlaceholders(
            final Player player,
            final ActionMenu menu,
            final ActionMenuTab tab,
            final SettingsTab subtab
    ) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("menu", menu.getId());
        placeholders.put("tab", tab == null ? "" : actionTabName(tab));
        placeholders.put("tab_id", tab == null ? "" : tab.getId());
        placeholders.put("subtab", subtab == null ? "" : tabName(subtab));
        placeholders.put("subtab_id", subtab == null ? "" : subtab.getId());
        placeholders.put("category", subtab == null ? "" : tabName(subtab));
        placeholders.put("category_id", subtab == null ? "" : subtab.getId());
        if ("community".equalsIgnoreCase(menu.getId())) {
            placeholders.putAll(communityPlaceholders(player));
        }
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
        executeCommands(player, button.isCloseInventory(), button.getCommands(), button);
    }

    private void playSound(final Player player, final MenuSound sound) {
        if (sound != null) {
            sound.play(plugin, player);
        }
    }

    private void executeCommands(final Player player, final boolean closeInventory, final List<String> commands) {
        executeCommands(player, closeInventory, commands, null);
    }

    private void executeCommands(
            final Player player,
            final boolean closeInventory,
            final List<String> commands,
            final MenuButton sourceButton
    ) {
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
                runCommand(player, command, sourceButton);
            }
        };
        if (closeInventory) {
            Bukkit.getScheduler().runTask(plugin, action);
        } else {
            action.run();
        }
    }

    private void runCommand(final Player player, final String configuredCommand) {
        runCommand(player, configuredCommand, null);
    }

    private void runCommand(final Player player, final String configuredCommand, final MenuButton sourceButton) {
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
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("plot-purchase-confirm")) {
                openPlotPurchaseConfirmMenu(player);
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("plot-purchase")
                    || menuId.toLowerCase(Locale.ROOT).startsWith("plotbuy")) {
                openPlotPurchaseMenu(player);
            } else if (actionMenus.containsKey(menuRoot(menuId))) {
                openActionMenu(player, menuRoot(menuId), menuArgument(menuId, ""));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("settings")) {
                openSettingsMenu(player, menuArgument(menuId, defaultSettingsTab));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("team-backups")) {
                openBackupListMenu(player, menuPage(menuId));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("reports-list")) {
                openReportListMenu(player, menuPage(menuId), menuArgument(menuId, 2, "open"));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("comments-list")) {
                openCommentListMenu(player, menuPage(menuId));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("members-list")) {
                openMemberListMenu(player, menuPage(menuId), menuArgument(menuId, 2, "all"));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("roles-list")) {
                openRoleListMenu(player, menuPage(menuId));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("role-members-list")) {
                openRoleMemberListMenu(player, menuPage(menuId), menuArgument(menuId, 2, ""));
            } else if (menuId.toLowerCase(Locale.ROOT).startsWith("role-permissions-list")) {
                openRolePermissionListMenu(player, menuPage(menuId), menuArgument(menuId, 2, ""));
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

        if (command.toLowerCase(Locale.ROOT).startsWith("report:")) {
            runReportCommand(player, command.substring("report:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("community:")) {
            runCommunityCommand(player, command.substring("community:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("role:")) {
            runRoleCommand(player, command.substring("role:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("future:")) {
            futureService.runCommand(player, command.substring("future:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("team:")) {
            teamFeatureService.runCommand(player, command.substring("team:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("extras:")) {
            extrasService.runCommand(player, command.substring("extras:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("passive-wither:")) {
            plugin.getPassiveWitherService().runMenuCommand(player, command.substring("passive-wither:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-deco:")) {
            runPlotDecoCommand(player, command.substring("plot-deco:".length()).trim(), sourceButton);
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-deco-reset:")) {
            runPlotDecoResetCommand(player, command.substring("plot-deco-reset:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-purchase:")) {
            runPlotPurchaseCommand(player, command.substring("plot-purchase:".length()).trim());
            return;
        }

        if (command.toLowerCase(Locale.ROOT).startsWith("plot-danger:")) {
            runPlotDangerCommand(player, command.substring("plot-danger:".length()).trim());
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

        performPlotDecoCommand(player, command);
    }

    private void runPlotPurchaseCommand(final Player player, final String payload) {
        final String action = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        if ("open".equals(action) || "menu".equals(action)) {
            openPlotPurchaseMenu(player);
            return;
        }
        if ("confirm".equals(action) || "open-confirm".equals(action)) {
            openPlotPurchaseConfirmMenu(player);
            return;
        }
        if ("buy".equals(action) || "purchase".equals(action)) {
            player.closeInventory();
            plotPurchaseService.purchase(player);
            return;
        }
        if ("cancel".equals(action)) {
            openPlotPurchaseMenu(player);
            return;
        }
        if (action.startsWith("add:")) {
            plotPurchaseService.adjustSelection(player, parseInteger(action.substring("add:".length()), 1));
            openPlotPurchaseMenu(player);
            return;
        }
        if (action.startsWith("remove:")) {
            plotPurchaseService.adjustSelection(player, -parseInteger(action.substring("remove:".length()), 1));
            openPlotPurchaseMenu(player);
            return;
        }
        languageManager.send(player, "chat-input-invalid");
    }

    private void runPlotDecoCommand(final Player player, final String payload, final MenuButton sourceButton) {
        final String command = stripCommandPrefix(payload);
        if (command.isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        if (!canModifyCurrentPlotDeco(player)) {
            return;
        }
        performPlotDecoCommand(player, command, selectedDecoValue(player, sourceButton, plotSetValue(command)));
    }

    private void runPlotDecoResetCommand(final Player player, final String payload) {
        final String component = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        if (!"wall".equals(component) && !"border".equals(component)) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        if (!canModifyCurrentPlotDeco(player)) {
            return;
        }
        final Optional<String> defaultComponent = flagService.defaultPlotComponent(player, component);
        if (!defaultComponent.isPresent()) {
            languageManager.send(player, "plot-deco-reset-failed");
            return;
        }
        performPlotDecoCommand(player, "plot set " + component + " " + defaultComponent.get(), readableDecoValue(defaultComponent.get()));
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("component", languageManager.getMessage("plot-deco-component-" + component));
        placeholders.put("value", defaultComponent.get());
        languageManager.send(player, "plot-deco-reset-done", placeholders);
    }

    private boolean canModifyCurrentPlotDeco(final Player player) {
        final Optional<PlotContext> context = flagService.currentPlotContext(player);
        if (!context.isPresent() || !context.get().isComplete()) {
            languageManager.send(player, "no-plot");
            return false;
        }
        final UUID ownerUuid = context.get().getOwnerUuid();
        if (ownerUuid != null && ownerUuid.equals(player.getUniqueId())
                || player.hasPermission("craftplayplotextras.admin")
                || player.hasPermission("craftplayplotextras.deco.admin")
                || roleService.hasRolePermission(player, "deco")
                || roleService.hasRolePermission(player, "plot-deco")
                || roleService.hasRolePermission(player, "change-deco")) {
            return true;
        }
        languageManager.send(player, "no-permission");
        return false;
    }

    private void performPlotDecoCommand(final Player player, final String command) {
        performPlotDecoCommand(player, command, readableDecoValue(plotSetValue(command)));
    }

    private void performPlotDecoCommand(final Player player, final String command, final String selectedValue) {
        final String component = plotSetComponent(command);
        final String value = plotSetValue(command);
        if (component.isEmpty()) {
            player.performCommand(command);
            return;
        }
        if (value.isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        if (!setPlotDecoComponent(player, component, value, selectedValue)) {
            return;
        }
    }

    private String plotSetComponent(final String command) {
        if (command == null) {
            return "";
        }
        final String[] parts = command.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length < 3) {
            return "";
        }
        if (!("plot".equals(parts[0]) || "plots".equals(parts[0]) || "p".equals(parts[0]))) {
            return "";
        }
        if (!"set".equals(parts[1])) {
            return "";
        }
        if ("wall".equals(parts[2]) || "border".equals(parts[2])) {
            return parts[2];
        }
        return "";
    }

    private String plotSetValue(final String command) {
        if (command == null) {
            return "";
        }
        final String[] parts = command.trim().split("\\s+", 4);
        if (parts.length < 4) {
            return "";
        }
        if (!("plot".equalsIgnoreCase(parts[0]) || "plots".equalsIgnoreCase(parts[0]) || "p".equalsIgnoreCase(parts[0]))) {
            return "";
        }
        if (!"set".equalsIgnoreCase(parts[1])) {
            return "";
        }
        if (!"wall".equalsIgnoreCase(parts[2]) && !"border".equalsIgnoreCase(parts[2])) {
            return "";
        }
        return parts[3].trim();
    }

    private boolean setPlotDecoComponent(
            final Player player,
            final String component,
            final String value,
            final String selectedValue
    ) {
        final Optional<PlotPlayer<?>> plotPlayerOptional = plotPlayer(player);
        if (!plotPlayerOptional.isPresent()) {
            languageManager.send(player, "no-plot");
            return false;
        }

        final PlotPlayer<?> plotPlayer = plotPlayerOptional.get();
        final Plot plot = plotPlayer.getCurrentPlot();
        if (plot == null || plot.getArea() == null) {
            languageManager.send(player, "no-plot");
            return false;
        }

        if (!isSupportedPlotComponent(plot, component)) {
            languageManager.send(player, "chat-input-invalid");
            return false;
        }
        if (plot.getRunning() > 0) {
            languageManager.send(player, "plot-deco-busy");
            return false;
        }

        final Pattern pattern;
        try {
            pattern = PatternUtil.parse(plotPlayer, value, false);
        } catch (final RuntimeException exception) {
            languageManager.send(player, "chat-input-invalid");
            plugin.getLogger().warning("Deko-Material konnte nicht gelesen werden: " + value);
            return false;
        }

        final PlotArea plotArea = plot.getArea();
        final QueueCoordinator queue = plotArea.getQueue();
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("value", selectedValue == null || selectedValue.trim().isEmpty() ? readableDecoValue(value) : selectedValue);
        final Runnable complete = () -> {
            plot.removeRunning();
            languageManager.send(player, "plot-deco-changed-" + component, placeholders);
        };

        plot.addRunning();
        try {
            boolean changed = false;
            queue.setCompleteTask(complete);
            for (final Plot current : plot.getConnectedPlots()) {
                changed = current.getPlotModificationManager().setComponent(component, pattern, plotPlayer, queue) || changed;
            }
            if (!changed) {
                plot.removeRunning();
                languageManager.send(player, "chat-input-invalid");
                return false;
            }
            if (queue.size() > 0) {
                queue.enqueue();
            } else {
                complete.run();
            }
            return true;
        } catch (final RuntimeException exception) {
            plot.removeRunning();
            plugin.getLogger().warning("Plot-Deko konnte nicht gesetzt werden: " + component + " -> " + value);
            languageManager.send(player, "chat-input-invalid");
            return false;
        }
    }

    private boolean isSupportedPlotComponent(final Plot plot, final String component) {
        for (final String supported : plot.getManager().getPlotComponents(plot.getId())) {
            if (component.equalsIgnoreCase(supported)) {
                return true;
            }
        }
        return false;
    }

    private Optional<PlotPlayer<?>> plotPlayer(final Player player) {
        final Object modernPlayer = invokeStatic(
                "com.plotsquared.bukkit.util.BukkitUtil",
                "adapt",
                Player.class,
                player
        );
        if (modernPlayer instanceof PlotPlayer) {
            return Optional.of((PlotPlayer<?>) modernPlayer);
        }

        final Object legacyPlayer = invokeStatic(
                "com.github.intellectualsites.plotsquared.bukkit.util.BukkitUtil",
                "getPlayer",
                Player.class,
                player
        );
        if (legacyPlayer instanceof PlotPlayer) {
            return Optional.of((PlotPlayer<?>) legacyPlayer);
        }
        return Optional.empty();
    }

    private Object invokeStatic(
            final String className,
            final String methodName,
            final Class<?> parameterType,
            final Object argument
    ) {
        try {
            final Class<?> clazz = Class.forName(className);
            final Method method = clazz.getMethod(methodName, parameterType);
            return method.invoke(null, argument);
        } catch (final ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    private String selectedDecoValue(final Player player, final MenuButton sourceButton, final String fallback) {
        if (sourceButton != null && sourceButton.getName() != null) {
            final String label = ChatColor.stripColor(Text.color(placeholderHook.apply(player, sourceButton.getName()))).trim();
            if (!label.isEmpty()) {
                return label;
            }
        }
        return readableDecoValue(fallback);
    }

    private String readableDecoValue(final String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return "";
        }
        final String[] words = rawValue.trim()
                .replace("minecraft:", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .toLowerCase(Locale.ROOT)
                .split("\\s+");
        final StringBuilder builder = new StringBuilder();
        for (final String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.length() == 0 ? rawValue.trim() : builder.toString();
    }

    private void runPlotDangerCommand(final Player player, final String payload) {
        final int separator = payload.indexOf(':');
        if (separator < 1 || separator >= payload.length() - 1) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }

        final String action = payload.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        final String originalCommand = stripCommandPrefix(payload.substring(separator + 1).trim());
        if (action.isEmpty() || originalCommand.isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }

        if (!backupService.requestProtectedAction(player, action, originalCommand)) {
            return;
        }
        openActionMenu(player, "danger", "confirm");
    }

    private void runReportCommand(final Player player, final String payload) {
        final String[] parts = payload.split(":", 3);
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        final String action = parts[0].toLowerCase(Locale.ROOT);
        if ("create".equals(action) && parts.length >= 3) {
            reportService.create(player, parts[1], parts[2], "normal");
            return;
        }
        if ("create-high".equals(action) && parts.length >= 3) {
            reportService.create(player, parts[1], parts[2], "high");
            return;
        }
        if ("close".equals(action) && parts.length >= 2) {
            reportService.close(player, parts[1]);
            return;
        }
        if ("reopen".equals(action) && parts.length >= 2) {
            reportService.reopen(player, parts[1]);
            return;
        }
        if ("priority".equals(action) && parts.length >= 3) {
            reportService.setPriority(player, parts[1], parts[2]);
            return;
        }
        if ("note".equals(action) && parts.length >= 3) {
            reportService.setNote(player, parts[1], parts[2]);
            return;
        }
        languageManager.send(player, "chat-input-invalid");
    }

    private void runCommunityCommand(final Player player, final String payload) {
        final String[] parts = payload.split(":", 2);
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        final String plotKey = currentPlotKey(player);
        if (plotKey.isEmpty()) {
            languageManager.send(player, "no-plot");
            return;
        }

        final String action = parts[0].trim().toLowerCase(Locale.ROOT);
        if ("like".equals(action)) {
            final boolean liked = plotDataStore.toggleLike(player.getUniqueId(), player.getName(), plotKey);
            languageManager.send(player, liked ? "community-like-added" : "community-like-removed", communityPlaceholders(player));
            openActionMenu(player, "community", "rating");
            return;
        }
        if ("rate".equals(action) && parts.length >= 2) {
            try {
                final int rating = Math.max(1, Math.min(5, Integer.parseInt(parts[1].trim())));
                plotDataStore.setRating(player.getUniqueId(), player.getName(), plotKey, rating);
                final Map<String, String> placeholders = communityPlaceholders(player);
                placeholders.put("rating", String.valueOf(rating));
                languageManager.send(player, "community-rated", placeholders);
                openActionMenu(player, "community", "rating");
                return;
            } catch (final NumberFormatException exception) {
                languageManager.send(player, "chat-input-invalid");
                return;
            }
        }
        if ("comment".equals(action) && parts.length >= 2) {
            final String message = parts[1].trim();
            if (message.isEmpty()) {
                languageManager.send(player, "chat-input-invalid");
                return;
            }
            plotDataStore.addComment(player.getUniqueId(), player.getName(), plotKey, message);
            languageManager.send(player, "community-comment-added", communityPlaceholders(player));
            openCommentListMenu(player, 1);
            return;
        }
        if ("comments".equals(action)) {
            openCommentListMenu(player, 1);
            return;
        }
        languageManager.send(player, "chat-input-invalid");
    }

    private void runRoleCommand(final Player player, final String payload) {
        final String[] parts = payload.split(":", 3);
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            languageManager.send(player, "chat-input-invalid");
            return;
        }
        final String action = parts[0].toLowerCase(Locale.ROOT);
        if ("create".equals(action) && parts.length >= 2) {
            roleService.createRole(player, parts[1]);
            return;
        }
        if ("delete".equals(action) && parts.length >= 2) {
            roleService.deleteRole(player, parts[1]);
            return;
        }
        if ("assign".equals(action) && parts.length >= 3) {
            roleService.assign(player, parts[1], parts[2]);
            return;
        }
        if ("unassign".equals(action) && parts.length >= 2) {
            roleService.unassign(player, parts[1]);
            return;
        }
        if ("add-permission".equals(action) && parts.length >= 3) {
            roleService.addPermission(player, parts[1], parts[2]);
            return;
        }
        if ("remove-permission".equals(action) && parts.length >= 3) {
            roleService.removePermission(player, parts[1], parts[2]);
            return;
        }
        if ("rename".equals(action) && parts.length >= 3) {
            roleService.renameRole(player, parts[1], parts[2]);
            return;
        }
        languageManager.send(player, "chat-input-invalid");
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

    private String actionTabName(final ActionMenuTab tab) {
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

    private int parseInteger(final String value, final int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
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

    private List<Integer> configuredSelectorSlots(
            final YamlConfiguration menuConfig,
            final String path,
            final int menuSize,
            final String type,
            final String id
    ) {
        final List<Integer> configured = new ArrayList<>();
        configured.addAll(configuredSlotValues(menuConfig, path + "slot", type, id));
        if (configured.isEmpty()) {
            configured.addAll(configuredSlotValues(menuConfig, path + "selector-slots", type, id));
        }
        if (configured.isEmpty()) {
            configured.addAll(configuredSlotValues(menuConfig, path + "slots", type, id));
        }
        if (configured.isEmpty()) {
            plugin.getLogger().warning(type + " '" + id + "' hat keinen Slot gesetzt. Nutze slot: <zahl>, selector-slots: [<zahl>, ...] oder slots: [<zahl>, ...].");
            return Collections.emptyList();
        }

        final List<Integer> valid = new ArrayList<>();
        for (final int slot : configured) {
            if (slot < 0 || slot >= menuSize) {
                plugin.getLogger().warning(type + " '" + id + "' hat einen ungueltigen Slot: " + slot);
                continue;
            }
            if (!valid.contains(slot)) {
                valid.add(slot);
            }
        }
        return valid;
    }

    private boolean hasConfiguredSlots(final YamlConfiguration menuConfig, final String path) {
        return menuConfig.contains(path + "slots") || menuConfig.contains(path + "slot");
    }

    private List<Integer> configuredSectionSlots(
            final YamlConfiguration menuConfig,
            final String sectionPath,
            final int menuSize,
            final String type,
            final String id
    ) {
        final String parentPath = parentPathForSectionSlots(sectionPath);
        final List<Integer> configured = new ArrayList<>();
        if (parentPath.startsWith("tabs.")) {
            configured.addAll(configuredSlotValues(menuConfig, parentPath + "slots", type, id));
        }
        configured.addAll(configuredSlotValues(menuConfig, parentPath + "button-slots", type, id));
        configured.addAll(configuredSlotValues(menuConfig, parentPath + "block-slots", type, id));
        configured.addAll(configuredSlotValues(menuConfig, parentPath + "content-slots", type, id));

        final List<Integer> valid = new ArrayList<>();
        for (final int slot : configured) {
            if (slot < 0 || slot >= menuSize) {
                plugin.getLogger().warning(type + " '" + id + "' hat einen ungueltigen Slot: " + slot);
                continue;
            }
            if (!valid.contains(slot)) {
                valid.add(slot);
            }
        }
        return valid;
    }

    private String parentPathForSectionSlots(final String sectionPath) {
        if (sectionPath == null || sectionPath.trim().isEmpty()) {
            return "";
        }
        if (sectionPath.endsWith(".buttons")) {
            return sectionPath.substring(0, sectionPath.length() - "buttons".length());
        }
        final int dot = sectionPath.lastIndexOf('.');
        return dot < 0 ? "" : sectionPath.substring(0, dot + 1);
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
            if (addSlotRange(part.trim(), slots, type, id)) {
                continue;
            }
            try {
                slots.add(Integer.parseInt(part.trim()));
            } catch (final NumberFormatException exception) {
                plugin.getLogger().warning(type + " '" + id + "' hat einen ungültigen Slotwert: " + part);
            }
        }
    }

    private boolean addSlotRange(
            final String text,
            final List<Integer> slots,
            final String type,
            final String id
    ) {
        final String separator;
        if (text.contains("..")) {
            separator = "\\.\\.";
        } else if (text.contains("-")) {
            separator = "-";
        } else {
            return false;
        }

        final String[] parts = text.split(separator, 2);
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            return false;
        }

        try {
            final int start = Integer.parseInt(parts[0].trim());
            final int end = Integer.parseInt(parts[1].trim());
            final int step = start <= end ? 1 : -1;
            int slot = start;
            while (true) {
                slots.add(slot);
                if (slot == end) {
                    break;
                }
                slot += step;
            }
            return true;
        } catch (final NumberFormatException exception) {
            plugin.getLogger().warning(type + " '" + id + "' hat einen ungueltigen Slotbereich: " + text);
            return true;
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
        private final Map<String, ActionMenuTab> nestedTabs;
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
                final Map<String, ActionMenuTab> nestedTabs,
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
            this.nestedTabs = nestedTabs;
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

        private Map<String, ActionMenuTab> getNestedTabs() {
            return nestedTabs;
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

    private static final class ActionMenuTab {

        private final String id;
        private final List<MenuButton> selectors;
        private final Map<Integer, MenuButton> buttonsBySlot;
        private final Map<Integer, MenuButton> decorationsBySlot;
        private final Map<String, SettingsTab> subtabs;
        private final String defaultSubtab;

        private ActionMenuTab(
                final String id,
                final List<MenuButton> selectors,
                final Map<Integer, MenuButton> buttonsBySlot,
                final Map<Integer, MenuButton> decorationsBySlot,
                final Map<String, SettingsTab> subtabs,
                final String defaultSubtab
        ) {
            this.id = id;
            this.selectors = selectors == null ? Collections.emptyList() : Collections.unmodifiableList(selectors);
            this.buttonsBySlot = buttonsBySlot == null ? Collections.emptyMap() : buttonsBySlot;
            this.decorationsBySlot = decorationsBySlot == null ? Collections.emptyMap() : decorationsBySlot;
            this.subtabs = subtabs == null ? Collections.emptyMap() : subtabs;
            this.defaultSubtab = defaultSubtab == null ? "" : defaultSubtab.trim();
        }

        private String getId() {
            return id;
        }

        private MenuButton getSelector() {
            return selectors.isEmpty() ? null : selectors.get(0);
        }

        private List<MenuButton> getSelectors() {
            return selectors;
        }

        private Map<Integer, MenuButton> getButtonsBySlot() {
            return buttonsBySlot;
        }

        private Map<Integer, MenuButton> getDecorationsBySlot() {
            return decorationsBySlot;
        }

        private Map<String, SettingsTab> getSubtabs() {
            return subtabs;
        }

        private String getDefaultSubtab() {
            return defaultSubtab;
        }
    }

    private static final class ActionMenuSelection {

        private final ActionMenuTab tab;
        private final SettingsTab subtab;

        private ActionMenuSelection(final ActionMenuTab tab, final SettingsTab subtab) {
            this.tab = tab;
            this.subtab = subtab;
        }

        private ActionMenuTab getTab() {
            return tab;
        }

        private SettingsTab getSubtab() {
            return subtab;
        }

        private String getId() {
            if (tab == null) {
                return "";
            }
            if (subtab == null) {
                return tab.getId();
            }
            final String subtabId = subtab.getId();
            final int separator = subtabId.indexOf(':');
            return tab.getId() + ":" + (separator >= 0 ? subtabId.substring(separator + 1) : subtabId);
        }
    }

    private static final class RolePermissionTemplate {

        private final String id;
        private final String value;
        private final Material activeMaterial;
        private final Material inactiveMaterial;
        private final String headDatabaseId;
        private final String skullOwner;
        private final String name;
        private final List<String> lore;
        private final String permission;

        private RolePermissionTemplate(
                final String id,
                final String value,
                final Material activeMaterial,
                final Material inactiveMaterial,
                final String headDatabaseId,
                final String skullOwner,
                final String name,
                final List<String> lore,
                final String permission
        ) {
            this.id = id;
            this.value = value;
            this.activeMaterial = activeMaterial;
            this.inactiveMaterial = inactiveMaterial;
            this.headDatabaseId = headDatabaseId == null ? "" : headDatabaseId;
            this.skullOwner = skullOwner == null ? "" : skullOwner;
            this.name = name;
            this.lore = lore == null ? Collections.emptyList() : lore;
            this.permission = permission == null ? "" : permission.trim();
        }

        private String getId() {
            return id;
        }

        private String getValue() {
            return value;
        }

        private Material getActiveMaterial() {
            return activeMaterial;
        }

        private Material getInactiveMaterial() {
            return inactiveMaterial;
        }

        private String getHeadDatabaseId() {
            return headDatabaseId;
        }

        private String getSkullOwner() {
            return skullOwner;
        }

        private String getName() {
            return name;
        }

        private List<String> getLore() {
            return lore;
        }

        private String getPermission() {
            return permission;
        }
    }

    private final class FlagMenuView {

        private final int page;
        private final int maxPage;
        private final Map<Integer, FlagMenuEntry> flagsBySlot;
        private final FlagPermissionLookup permissions;

        private FlagMenuView(
                final int page,
                final int maxPage,
                final Map<Integer, FlagMenuEntry> flagsBySlot,
                final FlagPermissionLookup permissions
        ) {
            this.page = page;
            this.maxPage = maxPage;
            this.flagsBySlot = flagsBySlot == null ? Collections.emptyMap() : flagsBySlot;
            this.permissions = permissions;
        }

        private int getPage() {
            return page;
        }

        private int getMaxPage() {
            return maxPage;
        }

        private Map<Integer, FlagMenuEntry> getFlagsBySlot() {
            return flagsBySlot;
        }

        private FlagPermissionLookup getPermissions() {
            return permissions;
        }
    }

    private final class FlagPermissionLookup {

        private final Player player;
        private final Map<String, Boolean> cache = new HashMap<>();
        private Boolean globalFlagPermission;

        private FlagPermissionLookup(final Player player) {
            this.player = player;
        }

        private boolean hasConfiguredPermission(final String permission) {
            final String normalized = permission == null ? "" : permission.trim();
            if (normalized.isEmpty()) {
                return true;
            }
            final Boolean cached = cache.get(normalized.toLowerCase(Locale.ROOT));
            if (cached != null) {
                return cached;
            }
            final boolean result = PlotMenuManager.this.hasConfiguredPermission(player, normalized);
            cache.put(normalized.toLowerCase(Locale.ROOT), result);
            return result;
        }

        private boolean hasAnyFlagPermission(final String flagName) {
            if (hasGlobalFlagPermission()) {
                return true;
            }
            final String normalizedFlag = normalizeFlagPermissionName(flagName);
            if (normalizedFlag.isEmpty()) {
                return false;
            }
            return hasConfiguredPermission("plots.set.flag." + normalizedFlag)
                    || hasConfiguredPermission("plots.set.flag." + normalizedFlag + ".*")
                    || hasConfiguredPermission("plots.set.flag." + normalizedFlag + ".true")
                    || hasConfiguredPermission("plots.set.flag." + normalizedFlag + ".false");
        }

        private boolean hasTogglePermission(final String flagName, final boolean targetValue) {
            if (hasGlobalFlagPermission()) {
                return true;
            }
            final String normalizedFlag = normalizeFlagPermissionName(flagName);
            if (normalizedFlag.isEmpty()) {
                return false;
            }
            return hasConfiguredPermission("plots.set.flag." + normalizedFlag)
                    || hasConfiguredPermission("plots.set.flag." + normalizedFlag + ".*")
                    || hasConfiguredPermission("plots.set.flag." + normalizedFlag + "." + targetValue);
        }

        private boolean hasGlobalFlagPermission() {
            if (globalFlagPermission != null) {
                return globalFlagPermission;
            }
            globalFlagPermission = hasConfiguredPermission("plots.admin")
                    || hasConfiguredPermission("plots.admin.command.set.flag")
                    || hasConfiguredPermission("plots.set.flag")
                    || hasConfiguredPermission("plots.flag")
                    || hasConfiguredPermission("plots.flag.add")
                    || hasConfiguredPermission("plots.flag.remove");
            return globalFlagPermission;
        }

        private String normalizeFlagPermissionName(final String flagName) {
            return flagName == null ? "" : flagName.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static final class AnimatedInventorySlot {

        private final int slot;
        private final ItemStack item;

        private AnimatedInventorySlot(final int slot, final ItemStack item) {
            this.slot = slot;
            this.item = item;
        }

        private int getSlot() {
            return slot;
        }

        private ItemStack getItem() {
            return item;
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
