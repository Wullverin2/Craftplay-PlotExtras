package de.craftplay.plotextras.gui;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.audit.AuditLogEntry;
import de.craftplay.plotextras.audit.AuditLogService;
import de.craftplay.plotextras.backup.PlotBackupEntry;
import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.integration.HeadDatabaseService;
import de.craftplay.plotextras.integration.PlaceholderService;
import de.craftplay.plotextras.language.LanguageDefinition;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.limit.EntityLimitService;
import de.craftplay.plotextras.player.PlayerDataManager;
import de.craftplay.plotextras.plot.FlagEntry;
import de.craftplay.plotextras.plot.MemberEntry;
import de.craftplay.plotextras.plot.PlotRole;
import de.craftplay.plotextras.plot.PlotRolePermission;
import de.craftplay.plotextras.plot.PlotRoleService;
import de.craftplay.plotextras.plot.PlotService;
import de.craftplay.plotextras.plotmeta.PlotMetaService;
import de.craftplay.plotextras.redstone.RedstoneLagProtectionService;
import de.craftplay.plotextras.util.SlotParser;
import de.craftplay.plotextras.util.TextUtil;
import de.craftplay.plotextras.warp.PlotWarpEntry;
import de.craftplay.plotextras.warp.PlotWarpService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager implements Listener {

    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final PlaceholderService placeholderService;
    private final HeadDatabaseService headDatabaseService;
    private final PlotService plotService;
    private final EntityLimitService entityLimitService;
    private final PlotBackupService plotBackupService;
    private final AuditLogService auditLogService;
    private final RedstoneLagProtectionService redstoneLagProtectionService;
    private final PlotMetaService plotMetaService;
    private final PlotWarpService plotWarpService;
    private final FeatureToggleService featureToggleService;
    @SuppressWarnings("unused")
    private final PlayerDataManager playerDataManager;
    private final Map<String, Map<String, YamlConfiguration>> guiConfigs = new HashMap<>();
    private final Map<UUID, String> selectedRoles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> selectedMembers = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedBackups = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> backupViewOwners = new ConcurrentHashMap<>();
    private final Map<UUID, ChatInput> pendingChatInputs = new ConcurrentHashMap<>();

    public GuiManager(
            final JavaPlugin plugin,
            final LanguageManager languageManager,
            final PlaceholderService placeholderService,
            final HeadDatabaseService headDatabaseService,
            final PlotService plotService,
            final EntityLimitService entityLimitService,
            final PlotBackupService plotBackupService,
            final AuditLogService auditLogService,
            final RedstoneLagProtectionService redstoneLagProtectionService,
            final PlotMetaService plotMetaService,
            final PlotWarpService plotWarpService,
            final FeatureToggleService featureToggleService,
            final PlayerDataManager playerDataManager
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.placeholderService = placeholderService;
        this.headDatabaseService = headDatabaseService;
        this.plotService = plotService;
        this.entityLimitService = entityLimitService;
        this.plotBackupService = plotBackupService;
        this.auditLogService = auditLogService;
        this.redstoneLagProtectionService = redstoneLagProtectionService;
        this.plotMetaService = plotMetaService;
        this.plotWarpService = plotWarpService;
        this.featureToggleService = featureToggleService;
        this.playerDataManager = playerDataManager;
    }

    public void openBackups(final Player player, final UUID ownerUuid) {
        if (ownerUuid == null) {
            backupViewOwners.remove(player.getUniqueId());
        } else {
            backupViewOwners.put(player.getUniqueId(), ownerUuid);
        }
        open(player, "backups", 0);
    }

    public void reload() {
        guiConfigs.clear();
        final File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists() && !guiFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create GUI folder.");
        }

        final File[] languageFolders = guiFolder.listFiles(File::isDirectory);
        if (languageFolders == null) {
            return;
        }
        for (final File languageFolder : languageFolders) {
            final String language = languageFolder.getName().toLowerCase(Locale.ROOT);
            final Map<String, YamlConfiguration> languageGuis = new LinkedHashMap<>();
            final File[] files = languageFolder.listFiles((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files == null) {
                continue;
            }
            for (final File file : files) {
                final String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                languageGuis.put(id, YamlConfiguration.loadConfiguration(file));
            }
            guiConfigs.put(language, languageGuis);
        }
    }

    public Collection<String> getGuiIds() {
        final Set<String> ids = new HashSet<>();
        for (final Map<String, YamlConfiguration> configs : guiConfigs.values()) {
            ids.addAll(configs.keySet());
        }
        return ids;
    }

    public void open(final Player player, final String guiId, final int page) {
        final String normalizedGuiId = guiId.toLowerCase(Locale.ROOT);
        if (!featureToggleService.guiEnabled(normalizedGuiId)) {
            sendMessage(player, "feature-disabled", Map.of("feature", normalizedGuiId));
            return;
        }
        final YamlConfiguration guiConfig = getGuiConfig(languageManager.getPlayerLanguage(player), normalizedGuiId);
        if (guiConfig == null) {
            sendMessage(player, "unknown-gui", Map.of("gui", normalizedGuiId));
            return;
        }

        final int size = normalizeSize(guiConfig.getInt("size", 54));
        final Map<String, String> placeholders = createPlaceholders(player);
        placeholders.put("gui", normalizedGuiId);
        placeholders.put("page", String.valueOf(page + 1));

        final String title = placeholderService.apply(player, guiConfig.getString("title", normalizedGuiId), placeholders);
        final GuiHolder holder = new GuiHolder(normalizedGuiId, Math.max(0, page));
        final Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.legacy(title));
        holder.setInventory(inventory);

        applyFill(player, inventory, holder, guiConfig, placeholders);
        applyStaticItems(player, inventory, holder, guiConfig, placeholders);
        applyDynamicItems(player, inventory, holder, guiConfig, placeholders, Math.max(0, page));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        final List<String> actions = holder.actions(rawSlot);
        if (!actions.isEmpty()) {
            executeActions(player, holder, actions, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(final AsyncPlayerChatEvent event) {
        final ChatInput input = pendingChatInputs.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }

        event.setCancelled(true);
        final String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleChatInput(event.getPlayer(), input, message));
    }

    private YamlConfiguration getGuiConfig(final String language, final String guiId) {
        final Map<String, YamlConfiguration> selectedLanguage = guiConfigs.get(language.toLowerCase(Locale.ROOT));
        if (selectedLanguage != null && selectedLanguage.containsKey(guiId)) {
            return selectedLanguage.get(guiId);
        }

        final Map<String, YamlConfiguration> defaultLanguage = guiConfigs.get(languageManager.getDefaultLanguage());
        if (defaultLanguage != null && defaultLanguage.containsKey(guiId)) {
            return defaultLanguage.get(guiId);
        }

        for (final Map<String, YamlConfiguration> configs : guiConfigs.values()) {
            if (configs.containsKey(guiId)) {
                return configs.get(guiId);
            }
        }
        return null;
    }

    private void applyFill(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final YamlConfiguration guiConfig,
            final Map<String, String> placeholders
    ) {
        final ConfigurationSection fill = guiConfig.getConfigurationSection("fill");
        if (fill == null || !fill.getBoolean("enabled", false)) {
            return;
        }
        final ItemStack fillItem = buildItem(player, fill, null, placeholders);
        if (fillItem == null) {
            return;
        }
        final List<String> actions = readActions(fill);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, fillItem.clone());
            holder.setActions(slot, actions);
        }
    }

    private void applyStaticItems(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final YamlConfiguration guiConfig,
            final Map<String, String> placeholders
    ) {
        final ConfigurationSection items = guiConfig.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        for (final String key : items.getKeys(false)) {
            final ConfigurationSection itemSection = items.getConfigurationSection(key);
            if (itemSection == null || !itemSection.getBoolean("enabled", true) || !featureToggleService.sectionEnabled(itemSection)) {
                continue;
            }
            final String permission = itemSection.getString("permission", "");
            if (!permission.isBlank() && !player.hasPermission(permission) && !player.hasPermission("craftplayplotextras.admin")) {
                continue;
            }
            final ItemStack item = buildItem(player, itemSection, null, placeholders);
            if (item == null) {
                continue;
            }
            final List<String> actions = readActions(itemSection);
            if (!actionsEnabled(actions)) {
                continue;
            }
            for (final int slot : SlotParser.itemSlots(itemSection)) {
                if (isValidSlot(inventory, slot)) {
                    inventory.setItem(slot, item.clone());
                    holder.setActions(slot, actions);
                }
            }
        }
    }

    private void applyDynamicItems(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final YamlConfiguration guiConfig,
            final Map<String, String> placeholders,
            final int page
    ) {
        final ConfigurationSection dynamic = guiConfig.getConfigurationSection("dynamic");
        if (dynamic == null || !dynamic.getBoolean("enabled", false)) {
            return;
        }

        final String type = dynamic.getString("type", "").toLowerCase(Locale.ROOT);
        if (!featureToggleService.sectionEnabled(dynamic)
                || !featureToggleService.isEnabled(featureToggleService.featureForDynamicType(type))) {
            return;
        }
        switch (type) {
            case "flags" -> renderFlags(player, inventory, holder, dynamic, placeholders, page);
            case "component-categories" -> renderComponentCategories(player, inventory, holder, dynamic, placeholders, page);
            case "components" -> renderComponents(player, inventory, holder, dynamic, placeholders, page);
            case "config-options" -> renderConfigOptions(player, inventory, holder, dynamic, placeholders, page);
            case "members" -> renderMembers(player, inventory, holder, dynamic, placeholders, page);
            case "plot-roles" -> renderPlotRoles(player, inventory, holder, dynamic, placeholders, page);
            case "role-permissions" -> renderRolePermissions(player, inventory, holder, dynamic, placeholders, page);
            case "member-role-options" -> renderMemberRoleOptions(player, inventory, holder, dynamic, placeholders, page);
            case "entity-limits" -> renderEntityLimits(player, inventory, holder, dynamic, placeholders, page);
            case "plot-backups" -> renderPlotBackups(player, inventory, holder, dynamic, placeholders, page);
            case "audit-log" -> renderAuditLog(player, inventory, holder, dynamic, placeholders, page);
            case "redstone-alerts" -> renderRedstoneAlerts(player, inventory, holder, dynamic, placeholders, page);
            case "plot-warps" -> renderPlotWarps(player, inventory, holder, dynamic, placeholders, page);
            case "languages" -> renderLanguages(player, inventory, holder, dynamic, placeholders, page);
            default -> plugin.getLogger().warning("Unknown dynamic GUI type: " + type);
        }
    }

    private void renderFlags(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }
        final List<FlagEntry> flags = plotService.getAvailableFlags(player);
        final PageSlice<FlagEntry> slice = slice(flags, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final FlagEntry flag = slice.entries().get(index);
            final boolean enabled = plotService.isFlagEnabled(player, flag.name());
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("flag_name", flag.name());
            itemPlaceholders.put("flag_display", languageManager.getString(player, "flags." + flag.name() + ".name", flag.displayName()));
            itemPlaceholders.put("flag_description", languageManager.getString(player, "flags." + flag.name() + ".description", flag.description()));
            itemPlaceholders.put("flag_state", languageManager.getRawMessage(player, enabled ? "state-enabled" : "state-disabled"));
            itemPlaceholders.put("flag_state_color", enabled ? "&a" : "&c");

            final ConfigurationSection template = dynamic.getConfigurationSection(enabled ? "enabled-item" : "disabled-item");
            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of("TOGGLE_FLAG:" + flag.name()));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderComponents(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final List<PlotService.ComponentOption> options = new ArrayList<>();
        final List<String> components = dynamic.getStringList("components").isEmpty()
                ? List.of("wall", "border")
                : dynamic.getStringList("components");
        for (final String component : components) {
            final String normalizedComponent = component.toLowerCase(Locale.ROOT);
            if (featureToggleService.isEnabled("player.decor." + normalizedComponent)) {
                options.addAll(plotService.getComponentOptions(normalizedComponent));
            }
        }
        final List<String> categoryFilter = dynamic.getStringList("categories")
                .stream()
                .map(category -> category.toLowerCase(Locale.ROOT))
                .toList();
        options.removeIf(option -> (!categoryFilter.isEmpty() && !categoryFilter.contains(option.category().toLowerCase(Locale.ROOT)))
                || !plotService.canUseComponentOption(player, option));

        final PageSlice<PlotService.ComponentOption> slice = slice(options, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("option-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotService.ComponentOption option = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            final String componentDisplay = plotService.getComponentDisplayName(option.component());
            itemPlaceholders.put("component", option.component());
            itemPlaceholders.put("component_display", componentDisplay);
            itemPlaceholders.put("option", option.id());
            itemPlaceholders.put("option_display", option.display());
            itemPlaceholders.put("pattern", option.pattern());
            itemPlaceholders.put("category", option.category());
            itemPlaceholders.put("category_display", option.categoryDisplay());
            itemPlaceholders.put("permission_group", option.permissionGroup() <= 0 ? "-" : String.valueOf(option.permissionGroup()));
            itemPlaceholders.put("permission", option.permission().isBlank() ? "-" : option.permission());

            final ItemStack item = buildItem(player, option.section(), template, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                final List<String> actions = List.of("SET_COMPONENT:" + option.component() + ":" + option.pattern()
                        + ":" + componentDisplay + ":" + option.display());
                if (!actionsEnabled(actions)) {
                    continue;
                }
                inventory.setItem(slot, item);
                holder.setActions(slot, actions);
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderComponentCategories(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final String component = dynamic.getString("component", "wall").toLowerCase(Locale.ROOT);
        if (!featureToggleService.isEnabled("player.decor." + component)) {
            return;
        }
        final List<PlotService.ComponentCategory> categories = plotService.getComponentCategories(component, player);
        final PageSlice<PlotService.ComponentCategory> slice = slice(categories, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("category-item");
        final String targetFormat = dynamic.getString("target-format", "decor-" + component + "-{category}");

        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotService.ComponentCategory category = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("component", category.component());
            itemPlaceholders.put("component_display", plotService.getComponentDisplayName(category.component()));
            itemPlaceholders.put("category", category.id());
            itemPlaceholders.put("category_display", category.display());
            itemPlaceholders.put("option_count", String.valueOf(category.visibleOptions()));

            final ItemStack item = buildItem(player, category.section(), template, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                final String target = placeholderService.apply(player, targetFormat, itemPlaceholders);
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of("OPEN:" + target));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderEntityLimits(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final List<EntityLimitService.EntityLimitEntry> entries = entityLimitService.getEntries(player);
        final PageSlice<EntityLimitService.EntityLimitEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("limit-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final EntityLimitService.EntityLimitEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("limit_id", entry.id());
            itemPlaceholders.put("limit_display", entry.display());
            itemPlaceholders.put("limit_description", entry.description());
            itemPlaceholders.put("limit_count", String.valueOf(entry.count()));
            itemPlaceholders.put("limit_max", entry.max());
            itemPlaceholders.put("limit_remaining", entry.remaining());
            itemPlaceholders.put("limit_state_color", entry.exceeded() ? "&c" : "&a");
            itemPlaceholders.put("limit_material", entry.material());

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of());
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPlotBackups(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final UUID ownerUuid = backupViewOwners.get(player.getUniqueId());
        final List<PlotBackupEntry> entries = ownerUuid == null
                ? plotBackupService.listAllBackups()
                : plotBackupService.listBackups(ownerUuid);
        final PageSlice<PlotBackupEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("backup-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotBackupEntry backup = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("backup_id", backup.id());
            itemPlaceholders.put("backup_owner", backup.ownerName());
            itemPlaceholders.put("backup_owner_uuid", backup.ownerUuid().toString());
            itemPlaceholders.put("backup_created", BACKUP_TIME_FORMAT.format(backup.createdAt()));
            itemPlaceholders.put("backup_reason", backup.reason());
            itemPlaceholders.put("backup_world", backup.sourceWorld());
            itemPlaceholders.put("backup_plot", backup.sourcePlot());
            itemPlaceholders.put("backup_merge", backup.mergeSize());
            itemPlaceholders.put("backup_plot_count", String.valueOf(backup.plotCount()));

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of("SELECT_BACKUP:" + backup.id() + ":backup-restore-confirm"));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderAuditLog(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        if (!auditLogService.canView(player)) {
            return;
        }
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final Plot plot = plotService.getCurrentPlot(player);
        final boolean currentPlotOnly = dynamic.getBoolean("current-plot-only", false);
        final int limit = Math.max(slots.size(), dynamic.getInt("limit", 250));
        final List<AuditLogEntry> entries = currentPlotOnly && plot != null
                ? auditLogService.listForPlot(plot, limit)
                : auditLogService.listRecent(limit);
        final PageSlice<AuditLogEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("entry-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final AuditLogEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("audit_id", entry.id());
            itemPlaceholders.put("audit_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("audit_actor", entry.actor());
            itemPlaceholders.put("audit_action", entry.action());
            itemPlaceholders.put("audit_details", entry.details());
            itemPlaceholders.put("audit_world", entry.world());
            itemPlaceholders.put("audit_plot", entry.plotId());
            itemPlaceholders.put("audit_plot_key", entry.plotKey());

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of());
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderRedstoneAlerts(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        if (!redstoneLagProtectionService.canReceiveAlerts(player)) {
            return;
        }
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final List<RedstoneLagProtectionService.RedstoneAlertEntry> entries = redstoneLagProtectionService.listAlerts();
        final PageSlice<RedstoneLagProtectionService.RedstoneAlertEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("alert-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final RedstoneLagProtectionService.RedstoneAlertEntry alert = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("redstone_alert_id", alert.id());
            itemPlaceholders.put("redstone_alert_world", alert.worldName());
            itemPlaceholders.put("redstone_alert_plot", alert.plotId());
            itemPlaceholders.put("redstone_alert_owner", alert.ownerName());
            itemPlaceholders.put("redstone_alert_merge", alert.mergeSize());
            itemPlaceholders.put("redstone_alert_x", String.valueOf(alert.x()));
            itemPlaceholders.put("redstone_alert_y", String.valueOf(alert.y()));
            itemPlaceholders.put("redstone_alert_z", String.valueOf(alert.z()));
            itemPlaceholders.put("redstone_alert_events", String.valueOf(alert.eventCount()));
            itemPlaceholders.put("redstone_alert_source", alert.source());
            itemPlaceholders.put("redstone_alert_created", BACKUP_TIME_FORMAT.format(alert.detectedAt()));

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                final List<String> actions = resolveActions(player, readActions(template), itemPlaceholders);
                if (!actionsEnabled(actions)) {
                    continue;
                }
                inventory.setItem(slot, item);
                holder.setActions(slot, actions);
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPlotWarps(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        final Plot plot = plotService.getCurrentPlot(player);
        if (slots.isEmpty() || plot == null) {
            return;
        }

        final List<PlotWarpEntry> entries = plotWarpService.listWarps(plot);
        final PageSlice<PlotWarpEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("warp-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotWarpEntry warp = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("warp_id", warp.id());
            itemPlaceholders.put("warp_name", warp.displayName());
            itemPlaceholders.put("warp_world", warp.world());
            itemPlaceholders.put("warp_x", String.valueOf(Math.round(warp.x())));
            itemPlaceholders.put("warp_y", String.valueOf(Math.round(warp.y())));
            itemPlaceholders.put("warp_z", String.valueOf(Math.round(warp.z())));

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderConfigOptions(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        final String source = dynamic.getString("source", "");
        final ConfigurationSection sourceSection = plugin.getConfig().getConfigurationSection(source);
        if (slots.isEmpty() || sourceSection == null) {
            return;
        }

        final List<ConfigurationSection> options = new ArrayList<>();
        for (final String key : sourceSection.getKeys(false)) {
            final ConfigurationSection option = sourceSection.getConfigurationSection(key);
            if (option == null || !option.getBoolean("enabled", true) || !featureToggleService.sectionEnabled(option)) {
                continue;
            }
            final String permission = option.getString("permission", "");
            if (!permission.isBlank() && !player.hasPermission(permission) && !player.hasPermission("craftplayplotextras.admin")) {
                continue;
            }
            options.add(option);
        }

        final PageSlice<ConfigurationSection> slice = slice(options, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("option-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final ConfigurationSection option = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("option", option.getName());
            itemPlaceholders.put("option_display", option.getString("display", option.getName()));
            itemPlaceholders.put("value", option.getString("value", option.getName()));
            itemPlaceholders.put("source", source);

            final ItemStack item = buildItem(player, option, template, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                final List<String> actions = resolveActions(player, readActions(option, template), itemPlaceholders);
                if (!actionsEnabled(actions)) {
                    continue;
                }
                inventory.setItem(slot, item);
                holder.setActions(slot, actions);
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderMembers(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final List<MemberEntry> entries = plotService.getMemberEntries(player);
        final PageSlice<MemberEntry> slice = slice(entries, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final MemberEntry member = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("member_name", member.name());
            itemPlaceholders.put("member_uuid", member.uuid().toString());
            itemPlaceholders.put("member_type", member.type());
            itemPlaceholders.put("member_type_display", languageManager.getRawMessage(player, "member-type-" + member.type()));
            itemPlaceholders.put("member_role_id", plotService.getMemberRoleId(player, member.uuid()));
            itemPlaceholders.put("member_role", plotService.getMemberRoleDisplay(player, member.uuid()));

            final ConfigurationSection template = dynamic.getConfigurationSection(member.type() + "-item");
            final ItemStack item = buildItem(player, template, dynamic.getConfigurationSection("member-item"), itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template, dynamic.getConfigurationSection("member-item")), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPlotRoles(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final List<PlotRole> roles = plotService.getRoleEntries(player);
        final PageSlice<PlotRole> slice = slice(roles, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotRole role = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("role_id", role.id());
            itemPlaceholders.put("role_name", role.displayName());
            itemPlaceholders.put("role_permissions", plotService.getRolePermissionSummary(role));
            itemPlaceholders.put("role_protected", role.protectedRole() ? "true" : "false");
            itemPlaceholders.put("role_removable", role.protectedRole() ? "false" : "true");

            final ConfigurationSection template = dynamic.getConfigurationSection(role.protectedRole() ? "protected-role-item" : "role-item");
            final ItemStack item = buildItem(player, template, dynamic.getConfigurationSection("role-item"), itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template, dynamic.getConfigurationSection("role-item")), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderRolePermissions(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        final String roleId = selectedRoles.get(player.getUniqueId());
        final Optional<PlotRole> selectedRole = roleId == null ? Optional.empty() : plotService.getRoleEntry(player, roleId);
        if (slots.isEmpty() || selectedRole.isEmpty()) {
            return;
        }

        final List<PlotRolePermission> permissions = List.of(PlotRolePermission.values());
        final PageSlice<PlotRolePermission> slice = slice(permissions, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotRolePermission permission = slice.entries().get(index);
            final boolean enabled = selectedRole.get().hasPermission(permission);
            final boolean protectedRole = PlotRoleService.OWNER_ROLE_ID.equals(selectedRole.get().id());
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("role_id", selectedRole.get().id());
            itemPlaceholders.put("role_name", selectedRole.get().displayName());
            itemPlaceholders.put("permission_key", permission.key());
            itemPlaceholders.put("permission_display", permission.displayName());
            itemPlaceholders.put("permission_state", languageManager.getRawMessage(player, enabled ? "state-enabled" : "state-disabled"));
            itemPlaceholders.put("permission_state_color", enabled ? "&a" : "&c");

            final ConfigurationSection template = protectedRole
                    ? dynamic.getConfigurationSection("protected-item")
                    : dynamic.getConfigurationSection(enabled ? "enabled-item" : "disabled-item");
            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                if (!protectedRole) {
                    holder.setActions(slot, List.of("TOGGLE_ROLE_PERMISSION:" + permission.key()));
                }
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderMemberRoleOptions(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (slots.isEmpty() || memberId == null) {
            return;
        }

        final List<PlotRole> roles = plotService.getAssignableRoleEntries(player);
        final String currentRoleId = plotService.getMemberRoleId(player, memberId);
        final PageSlice<PlotRole> slice = slice(roles, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotRole role = slice.entries().get(index);
            final boolean selected = role.id().equalsIgnoreCase(currentRoleId);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("role_id", role.id());
            itemPlaceholders.put("role_name", role.displayName());
            itemPlaceholders.put("role_permissions", plotService.getRolePermissionSummary(role));
            itemPlaceholders.put("member_uuid", memberId.toString());
            itemPlaceholders.put("member_name", Bukkit.getOfflinePlayer(memberId).getName() == null ? memberId.toString() : Bukkit.getOfflinePlayer(memberId).getName());
            itemPlaceholders.put("member_role_id", currentRoleId);
            itemPlaceholders.put("member_role", plotService.getMemberRoleDisplay(player, memberId));

            final ConfigurationSection template = dynamic.getConfigurationSection(selected ? "selected-role-item" : "role-item");
            final ItemStack item = buildItem(player, template, dynamic.getConfigurationSection("role-item"), itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of("ASSIGN_SELECTED_MEMBER_ROLE:" + role.id()));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderLanguages(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty()) {
            return;
        }

        final List<LanguageDefinition> languages = new ArrayList<>(languageManager.getLanguages());
        final String selectedLanguage = languageManager.getPlayerLanguage(player);
        final PageSlice<LanguageDefinition> slice = slice(languages, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final LanguageDefinition language = slice.entries().get(index);
            final boolean selected = selectedLanguage.equals(language.code());
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("language", language.code());
            itemPlaceholders.put("language_code", language.code());
            itemPlaceholders.put("language_name", language.name());
            itemPlaceholders.put("language_native", language.nativeName());

            final ConfigurationSection template = dynamic.getConfigurationSection(selected ? "selected-item" : "available-item");
            final ItemStack item = buildItem(player, language.itemSection(), template, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of("SET_LANGUAGE:" + language.code()));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private <T> void renderNavigation(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final PageSlice<T> slice
    ) {
        final Map<String, String> navigationPlaceholders = new HashMap<>(placeholders);
        navigationPlaceholders.put("page", String.valueOf(slice.page() + 1));
        navigationPlaceholders.put("pages", String.valueOf(slice.pages()));

        if (slice.page() > 0) {
            placeActionItem(player, inventory, holder, dynamic.getConfigurationSection("previous-item"), navigationPlaceholders, "PREVIOUS_PAGE");
        }
        if (slice.page() + 1 < slice.pages()) {
            placeActionItem(player, inventory, holder, dynamic.getConfigurationSection("next-item"), navigationPlaceholders, "NEXT_PAGE");
        }
    }

    private void placeActionItem(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection section,
            final Map<String, String> placeholders,
            final String action
    ) {
        if (section == null) {
            return;
        }
        final ItemStack item = buildItem(player, section, null, placeholders);
        if (item == null) {
            return;
        }
        for (final int slot : SlotParser.itemSlots(section)) {
            if (isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item.clone());
                holder.setActions(slot, List.of(action));
            }
        }
    }

    private void executeActions(
            final Player player,
            final GuiHolder holder,
            final List<String> actions,
            final InventoryClickEvent event
    ) {
        for (final String rawAction : actions) {
            final String action = rawAction == null ? "" : rawAction.trim();
            if (action.isEmpty()) {
                continue;
            }

            final String feature = featureToggleService.featureForAction(action);
            if (!feature.isBlank() && !featureToggleService.isEnabled(feature)) {
                sendMessage(player, "feature-disabled", Map.of("feature", feature));
                return;
            }

            final String upperAction = action.toUpperCase(Locale.ROOT);
            if (upperAction.equals("CLOSE")) {
                player.closeInventory();
                return;
            }
            if (upperAction.equals("BACK")) {
                scheduleOpen(player, "main", 0);
                return;
            }
            if (upperAction.equals("OPEN_LANGUAGE")) {
                scheduleOpen(player, "language", 0);
                return;
            }
            if (upperAction.equals("NEXT_PAGE")) {
                scheduleOpen(player, holder.guiId(), holder.page() + 1);
                return;
            }
            if (upperAction.equals("PREVIOUS_PAGE")) {
                scheduleOpen(player, holder.guiId(), Math.max(0, holder.page() - 1));
                return;
            }
            if (upperAction.startsWith("OPEN:")) {
                scheduleOpen(player, action.substring("OPEN:".length()), 0);
                return;
            }
            if (upperAction.startsWith("SET_LANGUAGE:")) {
                setLanguage(player, action.substring("SET_LANGUAGE:".length()));
                return;
            }
            if (upperAction.equals("TELEPORT_PLOT_HOME")) {
                teleportPlotHome(player);
                return;
            }
            if (upperAction.equals("SET_PLOT_HOME")) {
                setPlotHome(player);
                return;
            }
            if (upperAction.startsWith("TOGGLE_FLAG:")) {
                toggleFlag(player, holder, action.substring("TOGGLE_FLAG:".length()));
                return;
            }
            if (upperAction.startsWith("APPLY_FLAG_PRESET:")) {
                applyFlagPreset(player, action.substring("APPLY_FLAG_PRESET:".length()));
                return;
            }
            if (upperAction.startsWith("SET_FLAG:")) {
                setFlagValue(player, action);
                return;
            }
            if (upperAction.startsWith("SET_BIOME:")) {
                setBiome(player, action.substring("SET_BIOME:".length()));
                return;
            }
            if (upperAction.startsWith("SET_COMPONENT:")) {
                setComponent(player, action);
                return;
            }
            if (upperAction.equals("ROLE_CREATE_PROMPT")) {
                startRoleCreatePrompt(player);
                return;
            }
            if (upperAction.equals("INVITE_MEMBER_PROMPT")) {
                startMemberInvitePrompt(player);
                return;
            }
            if (upperAction.startsWith("SELECT_ROLE:")) {
                selectRole(player, action.substring("SELECT_ROLE:".length()));
                return;
            }
            if (upperAction.equals("ROLE_RENAME_PROMPT")) {
                startRoleRenamePrompt(player);
                return;
            }
            if (upperAction.equals("ROLE_DELETE_SELECTED")) {
                deleteSelectedRole(player);
                return;
            }
            if (upperAction.startsWith("TOGGLE_ROLE_PERMISSION:")) {
                toggleSelectedRolePermission(player, action.substring("TOGGLE_ROLE_PERMISSION:".length()));
                return;
            }
            if (upperAction.startsWith("SELECT_MEMBER:")) {
                selectMember(player, action.substring("SELECT_MEMBER:".length()));
                return;
            }
            if (upperAction.startsWith("ASSIGN_SELECTED_MEMBER_ROLE:")) {
                assignSelectedMemberRole(player, action.substring("ASSIGN_SELECTED_MEMBER_ROLE:".length()));
                return;
            }
            if (upperAction.equals("UNASSIGN_SELECTED_MEMBER_ROLE")) {
                unassignSelectedMemberRole(player);
                return;
            }
            if (upperAction.equals("PROMOTE_SELECTED_MEMBER")) {
                promoteSelectedMember(player);
                return;
            }
            if (upperAction.equals("DEMOTE_SELECTED_MEMBER")) {
                demoteSelectedMember(player);
                return;
            }
            if (upperAction.equals("UNTRUST_SELECTED_MEMBER_PROMPT") || upperAction.equals("REMOVE_SELECTED_MEMBER_PROMPT")) {
                startSelectedMemberUntrustPrompt(player, event);
                return;
            }
            if (upperAction.equals("CONFIRM_UNTRUST_SELECTED_MEMBER") || upperAction.equals("CONFIRM_REMOVE_SELECTED_MEMBER")) {
                untrustSelectedMember(player);
                return;
            }
            if (upperAction.startsWith("SELECT_BACKUP:")) {
                selectBackup(player, action.substring("SELECT_BACKUP:".length()));
                return;
            }
            if (upperAction.equals("RESTORE_SELECTED_BACKUP")) {
                restoreSelectedBackup(player);
                return;
            }
            if (upperAction.equals("PLOT_NOTE_PROMPT")) {
                startPlotNotePrompt(player);
                return;
            }
            if (upperAction.equals("TEAM_NOTE_PROMPT")) {
                startTeamNotePrompt(player);
                return;
            }
            if (upperAction.startsWith("SET_PLOT_STATUS:")) {
                setPlotStatus(player, action.substring("SET_PLOT_STATUS:".length()));
                return;
            }
            if (upperAction.equals("TOGGLE_PLOT_LIKE")) {
                togglePlotLike(player);
                return;
            }
            if (upperAction.equals("WARP_SET_PROMPT")) {
                startWarpSetPrompt(player);
                return;
            }
            if (upperAction.startsWith("TELEPORT_PLOT_WARP:")) {
                teleportPlotWarp(player, action.substring("TELEPORT_PLOT_WARP:".length()));
                return;
            }
            if (upperAction.startsWith("DELETE_PLOT_WARP:")) {
                deletePlotWarp(player, action.substring("DELETE_PLOT_WARP:".length()));
                return;
            }
            if (upperAction.startsWith("COMMAND:")) {
                runCommand(player, action.substring("COMMAND:".length()));
                continue;
            }
            if (upperAction.startsWith("PLAYER_COMMAND:")) {
                runCommand(player, "player:" + action.substring("PLAYER_COMMAND:".length()));
                continue;
            }
            if (upperAction.startsWith("CONSOLE_COMMAND:")) {
                runCommand(player, "console:" + action.substring("CONSOLE_COMMAND:".length()));
                continue;
            }
            if (upperAction.startsWith("MESSAGE:")) {
                player.sendMessage(TextUtil.component(placeholderService.apply(player, action.substring("MESSAGE:".length()), createPlaceholders(player))));
            }
        }
    }

    private void setLanguage(final Player player, final String language) {
        final String normalizedLanguage = language.toLowerCase(Locale.ROOT);
        if (!languageManager.setPlayerLanguage(player, normalizedLanguage)) {
            sendMessage(player, "unknown-language", Map.of("language", normalizedLanguage));
            return;
        }
        sendMessage(player, "language-set", Map.of("language", normalizedLanguage));
        scheduleOpen(player, "language", 0);
    }

    private void toggleFlag(final Player player, final GuiHolder holder, final String flag) {
        if (!player.hasPermission("craftplayplotextras.flags")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifyFlags(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        final Boolean enabled = plotService.toggleFlag(player, flag);
        if (enabled == null) {
            sendMessage(player, "flag-failed", Map.of("flag", flag));
            return;
        }
        auditLogService.log(player, plot, "Flag geändert", flag + " -> " + (enabled ? "aktiv" : "inaktiv"));
        sendMessage(player, enabled ? "flag-enabled" : "flag-disabled", Map.of("flag", flag));
        scheduleOpen(player, holder.guiId(), holder.page());
    }

    private void applyFlagPreset(final Player player, final String rawPresetId) {
        if (!player.hasPermission("craftplayplotextras.presets")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifyFlags(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        final String presetId = rawPresetId.toLowerCase(Locale.ROOT).trim();
        final ConfigurationSection preset = plugin.getConfig().getConfigurationSection("plot-presets.flags.options." + presetId);
        final ConfigurationSection flags = preset == null ? null : preset.getConfigurationSection("flags");
        if (preset == null || flags == null || !preset.getBoolean("enabled", true) || !featureToggleService.sectionEnabled(preset)) {
            sendMessage(player, "preset-unknown", Map.of("preset", presetId));
            return;
        }

        int changed = 0;
        for (final String flag : flags.getKeys(false)) {
            if (!plotService.canUseFlag(player, flag)) {
                continue;
            }
            if (plotService.setBooleanFlagOnConnectedPlots(plot, flag, flags.getBoolean(flag))) {
                changed++;
            }
        }
        final String display = preset.getString("display", presetId);
        auditLogService.log(player, plot, "Flag-Preset angewendet", display + " (" + changed + " Flags)");
        sendMessage(player, "preset-applied", Map.of("preset", display, "count", String.valueOf(changed)));
        scheduleOpen(player, "flag-presets", 0);
    }

    private void setFlagValue(final Player player, final String action) {
        if (!player.hasPermission("craftplayplotextras.settings")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String[] parts = action.split(":", 4);
        if (parts.length < 3) {
            return;
        }
        final String flag = parts[1].toLowerCase(Locale.ROOT);
        final String value = parts[2];
        final String valueDisplay = parts.length >= 4 ? parts[3] : humanizeValue(flag, value);
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifySetting(player, plot, flag)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        final String flagDisplay = languageManager.getString(player, "settings." + flag, humanizeSettingName(flag));
        if (plotService.setFlagValue(player, flag, value)) {
            auditLogService.log(player, plot, "Einstellung geändert", flagDisplay + " -> " + valueDisplay);
            sendMessage(player, "flag-value-set", Map.of("flag", flagDisplay, "value", valueDisplay));
        } else {
            sendMessage(player, "flag-failed", Map.of("flag", flagDisplay));
        }
    }

    private void setBiome(final Player player, final String rawBiome) {
        if (!player.hasPermission("craftplayplotextras.settings")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String[] parts = rawBiome.split(":", 2);
        final String biome = parts[0];
        final String biomeDisplay = parts.length >= 2 ? parts[1] : humanizeValue("biome", biome);
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifySetting(player, plot, "biome")) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        if (plotService.setBiome(player, biome)) {
            auditLogService.log(player, plot, "Biom geändert", biomeDisplay);
            sendMessage(player, "biome-started", Map.of("biome", biomeDisplay));
        } else {
            sendMessage(player, "biome-failed", Map.of("biome", biomeDisplay));
        }
    }

    private void teleportPlotHome(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (plotService.teleportHome(player)) {
            sendMessage(player, "plot-home-teleported", Map.of());
        } else {
            sendMessage(player, "plot-home-failed", Map.of());
        }
    }

    private void setPlotHome(final Player player) {
        if (!player.hasPermission("craftplayplotextras.settings")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifySetting(player, plot, "home")) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        if (plotService.setHome(player)) {
            auditLogService.log(player, plot, "Home gesetzt", "Plot-Home wurde an der aktuellen Position gesetzt.");
            sendMessage(player, "plot-home-set", Map.of());
        } else {
            sendMessage(player, "plot-home-failed", Map.of());
        }
    }

    private void setComponent(final Player player, final String action) {
        if (!player.hasPermission("craftplayplotextras.decor")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String[] parts = action.split(":", 5);
        if (parts.length < 3) {
            return;
        }
        final String component = parts[1].toLowerCase(Locale.ROOT);
        final String pattern = parts[2];
        final String componentDisplay = parts.length >= 4 ? parts[3] : humanizeComponent(component);
        final String optionDisplay = parts.length >= 5 ? parts[4] : humanizeValue(component, pattern);

        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifyComponent(player, plot, component)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        player.closeInventory();
        if (plotService.setComponent(player, component, pattern)) {
            auditLogService.log(player, plot, humanizeComponent(component) + " geändert", optionDisplay + " (" + pattern + ")");
            sendMessage(player, "component-started", Map.of("component", componentDisplay, "pattern", optionDisplay));
        } else {
            sendMessage(player, "component-failed", Map.of("component", componentDisplay, "pattern", optionDisplay));
        }
    }

    private void startRoleCreatePrompt(final Player player) {
        if (!canManageRolesHere(player)) {
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.CREATE_ROLE, ""));
        player.closeInventory();
        sendMessage(player, "role-input-create", Map.of());
    }

    private void startRoleRenamePrompt(final Player player) {
        if (!canManageRolesHere(player)) {
            return;
        }
        final String roleId = selectedRoles.get(player.getUniqueId());
        if (roleId == null || plotService.getRoleEntry(player, roleId).isEmpty()) {
            sendMessage(player, "role-unknown", Map.of("role", "-"));
            scheduleOpen(player, "roles", 0);
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.RENAME_ROLE, roleId));
        player.closeInventory();
        sendMessage(player, "role-input-rename", Map.of("role", roleId));
    }

    private void startMemberInvitePrompt(final Player player) {
        if (!canInviteMembersHere(player)) {
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.INVITE_MEMBER, ""));
        player.closeInventory();
        sendMessage(player, "member-invite-input", Map.of());
    }

    private void startPlotNotePrompt(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canManageRoles(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.PLOT_NOTE, ""));
        player.closeInventory();
        sendMessage(player, "plot-note-input", Map.of());
    }

    private void startTeamNotePrompt(final Player player) {
        if (!plotMetaService.canManageTeamMeta(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        if (plotService.getCurrentPlot(player) == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.TEAM_NOTE, ""));
        player.closeInventory();
        sendMessage(player, "team-note-input", Map.of());
    }

    private void startWarpSetPrompt(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifySetting(player, plot, "home")) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.SET_WARP, ""));
        player.closeInventory();
        sendMessage(player, "warp-set-input", Map.of());
    }

    private void handleChatInput(final Player player, final ChatInput input, final String message) {
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("abbrechen")) {
            if (input.type() == ChatInputType.INVITE_MEMBER) {
                sendMessage(player, "member-input-cancelled", Map.of());
                scheduleOpen(player, "members", 0);
            } else if (input.type() == ChatInputType.PLOT_NOTE) {
                sendMessage(player, "plot-note-cancelled", Map.of());
                scheduleOpen(player, "plot-dashboard", 0);
            } else if (input.type() == ChatInputType.TEAM_NOTE) {
                sendMessage(player, "team-note-cancelled", Map.of());
                scheduleOpen(player, "team-inspector", 0);
            } else if (input.type() == ChatInputType.SET_WARP) {
                sendMessage(player, "warp-input-cancelled", Map.of());
                scheduleOpen(player, "plot-warps", 0);
            } else {
                sendMessage(player, "role-input-cancelled", Map.of());
                scheduleOpen(player, "roles", 0);
            }
            return;
        }

        if (input.type() == ChatInputType.INVITE_MEMBER) {
            handleMemberInviteInput(player, message);
            return;
        }

        if (input.type() == ChatInputType.PLOT_NOTE) {
            handlePlotNoteInput(player, message);
            return;
        }

        if (input.type() == ChatInputType.TEAM_NOTE) {
            handleTeamNoteInput(player, message);
            return;
        }

        if (input.type() == ChatInputType.SET_WARP) {
            handleWarpSetInput(player, message);
            return;
        }

        if (input.type() == ChatInputType.CREATE_ROLE) {
            if (!canManageRolesHere(player)) {
                return;
            }
            final String[] parts = message.split("\\s+", 2);
            if (parts.length == 0 || parts[0].isBlank()) {
                sendMessage(player, "role-invalid-id", Map.of());
                return;
            }
            final String roleId = PlotRoleService.normalizeRoleId(parts[0]);
            final String displayName = parts.length >= 2 && !parts[1].isBlank() ? parts[1] : roleId;
            final PlotRoleService.RoleResult result = plotService.createRole(player, roleId, displayName);
            if (result == PlotRoleService.RoleResult.SUCCESS) {
                selectedRoles.put(player.getUniqueId(), roleId);
                auditLogService.log(player, plotService.getCurrentPlot(player), "Rolle erstellt", roleId + " (" + displayName + ")");
                sendMessage(player, "role-updated", Map.of("role", roleId, "name", displayName));
                scheduleOpen(player, "role-edit", 0);
                return;
            }
            sendRoleResult(player, result, roleId);
            scheduleOpen(player, "roles", 0);
            return;
        }

        if (!canManageRolesHere(player)) {
            return;
        }
        final String roleId = input.roleId();
        final PlotRoleService.RoleResult result = plotService.renameRole(player, roleId, message);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Rolle umbenannt", roleId + " -> " + message);
            sendMessage(player, "role-updated", Map.of("role", roleId, "name", message));
            scheduleOpen(player, "role-edit", 0);
            return;
        }
        sendRoleResult(player, result, roleId);
        scheduleOpen(player, "roles", 0);
    }

    private void handleMemberInviteInput(final Player player, final String message) {
        if (!canInviteMembersHere(player)) {
            return;
        }
        if (message.isBlank()) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }

        final OfflinePlayer target = Bukkit.getOfflinePlayer(message);
        final String targetName = target.getName() == null ? message : target.getName();
        final PlotRoleService.RoleResult result = plotService.inviteMember(player, target.getUniqueId());
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Mitglied eingeladen", targetName);
            sendMessage(player, "member-invited", Map.of("player", targetName));
            scheduleOpen(player, "members", 0);
            return;
        }
        sendRoleResult(player, result, targetName);
        scheduleOpen(player, "members", 0);
    }

    private void handlePlotNoteInput(final Player player, final String message) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canManageRoles(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        final boolean clear = message.equalsIgnoreCase("leer") || message.equalsIgnoreCase("clear");
        plotMetaService.setOwnerNote(plot, clear ? "" : message);
        auditLogService.log(player, plot, "Plotnotiz geändert", clear ? "geleert" : message);
        sendMessage(player, "plot-note-set", Map.of());
        scheduleOpen(player, "plot-dashboard", 0);
    }

    private void handleTeamNoteInput(final Player player, final String message) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotMetaService.canManageTeamMeta(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final boolean clear = message.equalsIgnoreCase("leer") || message.equalsIgnoreCase("clear");
        plotMetaService.setTeamNote(plot, clear ? "" : message);
        auditLogService.log(player, plot, "Teamnotiz geändert", clear ? "geleert" : message);
        sendMessage(player, "team-note-set", Map.of());
        scheduleOpen(player, "team-inspector", 0);
    }

    private void handleWarpSetInput(final Player player, final String message) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifySetting(player, plot, "home")) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        if (plotWarpService.setWarp(plot, message, player.getLocation())) {
            auditLogService.log(player, plot, "Plot-Warp gesetzt", message);
            sendMessage(player, "warp-set", Map.of("warp", message));
        } else {
            sendMessage(player, "warp-failed", Map.of("warp", message));
        }
        scheduleOpen(player, "plot-warps", 0);
    }

    private void selectRole(final Player player, final String rawAction) {
        final String[] parts = rawAction.split(":", 2);
        final String roleId = PlotRoleService.normalizeRoleId(parts[0]);
        final String targetGui = parts.length >= 2 ? parts[1] : "role-edit";
        if (plotService.getRoleEntry(player, roleId).isEmpty()) {
            sendMessage(player, "role-unknown", Map.of("role", roleId));
            scheduleOpen(player, "roles", 0);
            return;
        }
        selectedRoles.put(player.getUniqueId(), roleId);
        scheduleOpen(player, targetGui, 0);
    }

    private void deleteSelectedRole(final Player player) {
        if (!canManageRolesHere(player)) {
            return;
        }
        final String roleId = selectedRoles.get(player.getUniqueId());
        if (roleId == null) {
            sendMessage(player, "role-unknown", Map.of("role", "-"));
            scheduleOpen(player, "roles", 0);
            return;
        }
        final PlotRoleService.RoleResult result = plotService.deleteRole(player, roleId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            selectedRoles.remove(player.getUniqueId());
            auditLogService.log(player, plotService.getCurrentPlot(player), "Rolle gelöscht", roleId);
            sendMessage(player, "role-deleted", Map.of("role", roleId));
            scheduleOpen(player, "roles", 0);
            return;
        }
        sendRoleResult(player, result, roleId);
        scheduleOpen(player, "role-edit", 0);
    }

    private void toggleSelectedRolePermission(final Player player, final String rawPermission) {
        if (!canManageRolesHere(player)) {
            return;
        }
        final String roleId = selectedRoles.get(player.getUniqueId());
        final Optional<PlotRolePermission> permission = PlotRolePermission.fromKey(rawPermission);
        if (roleId == null || permission.isEmpty()) {
            sendMessage(player, "role-permission-unknown", Map.of(
                    "permission", rawPermission,
                    "permissions", String.join(", ", PlotRolePermission.keys())
            ));
            return;
        }

        final Optional<PlotRole> role = plotService.getRoleEntry(player, roleId);
        if (role.isEmpty()) {
            sendMessage(player, "role-unknown", Map.of("role", roleId));
            scheduleOpen(player, "roles", 0);
            return;
        }

        final boolean enabled = !role.get().hasPermission(permission.get());
        final PlotRoleService.RoleResult result = plotService.setRolePermission(player, roleId, permission.get(), enabled);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Rollenrecht geändert",
                    roleId + ": " + permission.get().key() + " -> " + (enabled ? "aktiv" : "inaktiv"));
            sendMessage(player, "role-permission-set", Map.of(
                    "role", roleId,
                    "permission", permission.get().displayName(),
                    "state", languageManager.getRawMessage(player, enabled ? "state-enabled" : "state-disabled")
            ));
            scheduleOpen(player, "role-edit", 0);
            return;
        }
        sendRoleResult(player, result, roleId);
    }

    private void selectMember(final Player player, final String rawAction) {
        final String[] parts = rawAction.split(":", 2);
        try {
            selectedMembers.put(player.getUniqueId(), UUID.fromString(parts[0]));
        } catch (final IllegalArgumentException exception) {
            sendMessage(player, "role-member-unknown", Map.of("player", parts[0]));
            return;
        }
        scheduleOpen(player, parts.length >= 2 ? parts[1] : "member-roles", 0);
    }

    private void assignSelectedMemberRole(final Player player, final String rawRoleId) {
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (memberId == null) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }
        if (!canManageRolesHere(player)) {
            return;
        }
        final String roleId = PlotRoleService.normalizeRoleId(rawRoleId);
        final PlotRoleService.RoleResult result = plotService.assignRole(player, memberId, roleId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Mitgliedsrolle geändert",
                    memberName(memberId) + " -> " + roleId);
            sendMessage(player, "role-assigned", Map.of("player", memberName(memberId), "role", roleId));
            scheduleOpen(player, "member-roles", 0);
            return;
        }
        sendRoleResult(player, result, roleId);
    }

    private void unassignSelectedMemberRole(final Player player) {
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (memberId == null) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }
        if (!canManageRolesHere(player)) {
            return;
        }
        final PlotRoleService.RoleResult result = plotService.unassignRole(player, memberId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Mitgliedsrolle entfernt", memberName(memberId));
            sendMessage(player, "role-unassigned", Map.of("player", memberName(memberId)));
            scheduleOpen(player, "member-roles", 0);
            return;
        }
        sendRoleResult(player, result, "-");
    }

    private void promoteSelectedMember(final Player player) {
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (memberId == null) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }
        final PlotRoleService.RoleResult result = plotService.promoteMember(player, memberId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Mitglied befördert",
                    memberName(memberId) + " -> " + plotService.getMemberRoleDisplay(player, memberId));
            sendMessage(player, "role-promoted", Map.of("player", memberName(memberId), "role", plotService.getMemberRoleDisplay(player, memberId)));
            scheduleOpen(player, "member-roles", 0);
            return;
        }
        sendRoleResult(player, result, "-");
    }

    private void demoteSelectedMember(final Player player) {
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (memberId == null) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }
        final PlotRoleService.RoleResult result = plotService.demoteMember(player, memberId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Mitglied degradiert",
                    memberName(memberId) + " -> " + plotService.getMemberRoleDisplay(player, memberId));
            sendMessage(player, "role-demoted", Map.of("player", memberName(memberId), "role", plotService.getMemberRoleDisplay(player, memberId)));
            scheduleOpen(player, "member-roles", 0);
            return;
        }
        sendRoleResult(player, result, "-");
    }

    private void startSelectedMemberUntrustPrompt(final Player player, final InventoryClickEvent event) {
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (memberId == null) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }
        if (!event.isShiftClick() || !event.isRightClick()) {
            sendMessage(player, "member-remove-shift-right", Map.of("player", memberName(memberId)));
            return;
        }
        if (!canUntrustMembersHere(player)) {
            return;
        }
        scheduleOpen(player, "member-remove-confirm", 0);
    }

    private void untrustSelectedMember(final Player player) {
        final UUID memberId = selectedMembers.get(player.getUniqueId());
        if (memberId == null) {
            sendMessage(player, "role-member-unknown", Map.of("player", "-"));
            scheduleOpen(player, "members", 0);
            return;
        }
        if (!canUntrustMembersHere(player)) {
            return;
        }
        final String memberName = memberName(memberId);
        final PlotRoleService.RoleResult result = plotService.untrustMember(player, memberId);
        if (result == PlotRoleService.RoleResult.SUCCESS) {
            selectedMembers.remove(player.getUniqueId());
            auditLogService.log(player, plotService.getCurrentPlot(player), "Mitglied entfernt", memberName);
            sendMessage(player, "member-removed", Map.of("player", memberName));
            scheduleOpen(player, "members", 0);
            return;
        }
        sendRoleResult(player, result, memberName);
        scheduleOpen(player, "member-roles", 0);
    }

    private void selectBackup(final Player player, final String rawAction) {
        if (!plotBackupService.canManage(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String[] parts = rawAction.split(":", 2);
        final String backupId = parts[0].toLowerCase(Locale.ROOT);
        if (plotBackupService.getBackup(backupId).isEmpty()) {
            player.sendMessage(TextUtil.component("&cBackup &e" + backupId + " &cwurde nicht gefunden."));
            scheduleOpen(player, "backups", 0);
            return;
        }
        selectedBackups.put(player.getUniqueId(), backupId);
        scheduleOpen(player, parts.length >= 2 ? parts[1] : "backup-restore-confirm", 0);
    }

    private void restoreSelectedBackup(final Player player) {
        if (!plotBackupService.canRestore(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String backupId = selectedBackups.get(player.getUniqueId());
        if (backupId == null || plotBackupService.getBackup(backupId).isEmpty()) {
            player.sendMessage(TextUtil.component("&cEs ist kein Backup ausgewählt."));
            scheduleOpen(player, "backups", 0);
            return;
        }
        if (plotService.getCurrentPlot(player) == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (plotBackupService.restoreBackup(player, backupId)) {
            auditLogService.log(player, plotService.getCurrentPlot(player), "Backup wiederhergestellt", backupId);
            player.sendMessage(TextUtil.component("&aWiederherstellung von &e" + backupId + " &awurde gestartet."));
            player.closeInventory();
        } else {
            player.sendMessage(TextUtil.component("&cBackup &e" + backupId + " &ckonnte nicht gestartet werden."));
            scheduleOpen(player, "backup-restore-confirm", 0);
        }
    }

    private void setPlotStatus(final Player player, final String status) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotMetaService.canSetStatus(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        if (plotMetaService.setStatus(plot, status)) {
            auditLogService.log(player, plot, "Plotstatus geändert", status);
            sendMessage(player, "plot-status-set", Map.of("status", status));
            scheduleOpen(player, "team-inspector", 0);
        }
    }

    private void togglePlotLike(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        final boolean liked = plotMetaService.toggleLike(plot, player);
        sendMessage(player, liked ? "plot-liked" : "plot-unliked", Map.of());
        scheduleOpen(player, "plot-dashboard", 0);
    }

    private void teleportPlotWarp(final Player player, final String warpId) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (plotWarpService.teleport(player, plot, warpId)) {
            sendMessage(player, "warp-teleported", Map.of("warp", warpId));
        } else {
            sendMessage(player, "warp-unknown", Map.of("warp", warpId));
            scheduleOpen(player, "plot-warps", 0);
        }
    }

    private void deletePlotWarp(final Player player, final String warpId) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canModifySetting(player, plot, "home")) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        if (plotWarpService.deleteWarp(plot, warpId)) {
            auditLogService.log(player, plot, "Plot-Warp gelöscht", warpId);
            sendMessage(player, "warp-deleted", Map.of("warp", warpId));
        } else {
            sendMessage(player, "warp-unknown", Map.of("warp", warpId));
        }
        scheduleOpen(player, "plot-warps", 0);
    }

    private boolean canManageRolesHere(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return false;
        }
        if (!plotService.canManageRoles(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return false;
        }
        return true;
    }

    private boolean canInviteMembersHere(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return false;
        }
        if (!plotService.canInviteMembers(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return false;
        }
        return true;
    }

    private boolean canUntrustMembersHere(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return false;
        }
        if (!plotService.canUntrustMembers(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return false;
        }
        return true;
    }

    private void sendRoleResult(final Player player, final PlotRoleService.RoleResult result, final String roleId) {
        switch (result) {
            case SUCCESS -> sendMessage(player, "role-updated", Map.of("role", roleId, "name", roleId));
            case INVALID_ID -> sendMessage(player, "role-invalid-id", Map.of());
            case ALREADY_EXISTS -> sendMessage(player, "role-exists", Map.of("role", roleId));
            case NOT_FOUND -> sendMessage(player, "role-unknown", Map.of("role", roleId));
            case PROTECTED -> sendMessage(player, "role-delete-protected", Map.of("role", roleId));
            case PROTECTED_PERMISSION -> sendMessage(player, "role-owner-permissions-fixed", Map.of());
            case TARGET_OWNER -> sendMessage(player, "role-target-owner", Map.of());
            case ALREADY_AT_LIMIT -> sendMessage(player, "role-no-next-rank", Map.of());
            case NO_PERMISSION -> sendMessage(player, "no-permission", Map.of());
        }
    }

    private String memberName(final UUID memberId) {
        final String name = Bukkit.getOfflinePlayer(memberId).getName();
        return name == null ? memberId.toString() : name;
    }

    private void runCommand(final Player player, final String commandAction) {
        final String[] parts = commandAction.split(":", 2);
        if (parts.length < 2) {
            return;
        }
        final String executor = parts[0].toLowerCase(Locale.ROOT);
        final String command = placeholderService.apply(player, parts[1], createPlaceholders(player)).replaceFirst("^/", "");
        if (executor.equals("console")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return;
        }
        player.performCommand(command);
    }

    private void scheduleOpen(final Player player, final String gui, final int page) {
        Bukkit.getScheduler().runTask(plugin, () -> open(player, gui, page));
    }

    private void sendMessage(final Player player, final String key, final Map<String, String> placeholders) {
        final Map<String, String> messagePlaceholders = createPlaceholders(player);
        messagePlaceholders.putAll(placeholders);
        player.sendMessage(TextUtil.component(placeholderService.message(player, key, messagePlaceholders)));
    }

    private Map<String, String> createPlaceholders(final Player player) {
        final Map<String, String> placeholders = new HashMap<>(plotService.getPlotPlaceholders(player));
        placeholders.putAll(plotMetaService.placeholders(plotService.getCurrentPlot(player)));
        placeholders.put("plot_warps", String.valueOf(plotWarpService.listWarps(plotService.getCurrentPlot(player)).size()));
        placeholders.put("language", languageManager.getPlayerLanguage(player));
        placeholders.putAll(placeholderService.getIntegrationPlaceholders(player));
        final String selectedRoleId = selectedRoles.getOrDefault(player.getUniqueId(), "-");
        placeholders.put("selected_role_id", selectedRoleId);
        plotService.getRoleEntry(player, selectedRoleId).ifPresentOrElse(
                role -> {
                    placeholders.put("selected_role_name", role.displayName());
                    placeholders.put("selected_role_permissions", plotService.getRolePermissionSummary(role));
                },
                () -> {
                    placeholders.put("selected_role_name", "-");
                    placeholders.put("selected_role_permissions", "-");
                }
        );
        final UUID selectedMemberId = selectedMembers.get(player.getUniqueId());
        placeholders.put("selected_member_uuid", selectedMemberId == null ? "-" : selectedMemberId.toString());
        placeholders.put("selected_member_name", selectedMemberId == null ? "-" : memberName(selectedMemberId));
        placeholders.put("selected_member_role_id", selectedMemberId == null ? "-" : plotService.getMemberRoleId(player, selectedMemberId));
        placeholders.put("selected_member_role", selectedMemberId == null ? "-" : plotService.getMemberRoleDisplay(player, selectedMemberId));
        final String selectedBackupId = selectedBackups.get(player.getUniqueId());
        placeholders.put("selected_backup_id", selectedBackupId == null ? "-" : selectedBackupId);
        plotBackupService.getBackup(selectedBackupId).ifPresentOrElse(
                backup -> {
                    placeholders.put("selected_backup_owner", backup.ownerName());
                    placeholders.put("selected_backup_created", BACKUP_TIME_FORMAT.format(backup.createdAt()));
                    placeholders.put("selected_backup_reason", backup.reason());
                    placeholders.put("selected_backup_world", backup.sourceWorld());
                    placeholders.put("selected_backup_plot", backup.sourcePlot());
                    placeholders.put("selected_backup_merge", backup.mergeSize());
                    placeholders.put("selected_backup_plot_count", String.valueOf(backup.plotCount()));
                },
                () -> {
                    placeholders.put("selected_backup_owner", "-");
                    placeholders.put("selected_backup_created", "-");
                    placeholders.put("selected_backup_reason", "-");
                    placeholders.put("selected_backup_world", "-");
                    placeholders.put("selected_backup_plot", "-");
                    placeholders.put("selected_backup_merge", "-");
                    placeholders.put("selected_backup_plot_count", "-");
                }
        );
        return placeholders;
    }

    private ItemStack buildItem(
            final Player player,
            final ConfigurationSection primary,
            final ConfigurationSection fallback,
            final Map<String, String> placeholders
    ) {
        if (primary == null && fallback == null) {
            return null;
        }

        final String headDatabaseId = readString(primary, fallback, "head-database", "");
        ItemStack item = headDatabaseService.getHead(headDatabaseId).orElse(null);
        if (item == null) {
            final String materialName = readString(primary, fallback, "material", "BARRIER");
            final Material material = Material.matchMaterial(placeholderService.apply(player, materialName, placeholders));
            item = new ItemStack(material == null ? Material.BARRIER : material);
        }

        item.setAmount(Math.max(1, Math.min(64, readInt(primary, fallback, "amount", 1))));
        final String skullOwner = readString(primary, fallback, "skull-owner", "");
        if (skullOwnerLookupsEnabled() && !skullOwner.isBlank() && item.getItemMeta() instanceof SkullMeta skullMeta) {
            final String resolvedOwner = placeholderService.apply(player, skullOwner, placeholders).trim();
            if (!resolvedOwner.isBlank() && !"-".equals(resolvedOwner)) {
                final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(resolvedOwner);
                skullMeta.setOwningPlayer(offlinePlayer);
                item.setItemMeta(skullMeta);
            }
        }

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        final String name = readString(primary, fallback, "name", "");
        if (!name.isBlank()) {
            meta.displayName(TextUtil.component(placeholderService.apply(player, name, placeholders)));
        }

        final List<String> lore = readStringList(primary, fallback, "lore");
        if (!lore.isEmpty()) {
            final List<String> resolvedLore = new ArrayList<>();
            for (final String line : lore) {
                resolvedLore.add(placeholderService.apply(player, line, placeholders));
            }
            meta.lore(TextUtil.components(resolvedLore));
        }

        if (readBoolean(primary, fallback, "glow", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (readBoolean(primary, fallback, "hide-flags", true)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        }
        if (contains(primary, fallback, "custom-model-data")) {
            meta.setCustomModelData(readInt(primary, fallback, "custom-model-data", 0));
        }

        item.setItemMeta(meta);
        return item;
    }

    private boolean skullOwnerLookupsEnabled() {
        return featureToggleService.isEnabled("player.gui.skull-owner-lookups")
                && plugin.getConfig().getBoolean("gui.skull-owner-lookups.enabled", false);
    }

    private List<String> readActions(final ConfigurationSection section) {
        return readActions(section, null);
    }

    private List<String> readActions(final ConfigurationSection primary, final ConfigurationSection fallback) {
        final List<String> actions = new ArrayList<>();
        final ConfigurationSection section = primary != null ? primary : fallback;
        if (section == null) {
            return actions;
        }
        if (section.isList("actions")) {
            actions.addAll(section.getStringList("actions"));
        } else if (section.isString("action")) {
            actions.add(section.getString("action"));
        } else if (fallback != null && fallback.isList("actions")) {
            actions.addAll(fallback.getStringList("actions"));
        } else if (fallback != null && fallback.isString("action")) {
            actions.add(fallback.getString("action"));
        }
        for (final String command : section.getStringList("commands")) {
            actions.add(command.toUpperCase(Locale.ROOT).startsWith("COMMAND:")
                    ? command
                    : "COMMAND:" + command);
        }
        if (actions.isEmpty() && fallback != null) {
            for (final String command : fallback.getStringList("commands")) {
                actions.add(command.toUpperCase(Locale.ROOT).startsWith("COMMAND:")
                        ? command
                        : "COMMAND:" + command);
            }
        }
        if (section.getBoolean("close", false)) {
            actions.add("CLOSE");
        } else if (fallback != null && fallback.getBoolean("close", false)) {
            actions.add("CLOSE");
        }
        return actions;
    }

    private List<String> resolveActions(final Player player, final List<String> actions, final Map<String, String> placeholders) {
        final List<String> resolved = new ArrayList<>();
        for (final String action : actions) {
            resolved.add(placeholderService.apply(player, action, placeholders));
        }
        return resolved;
    }

    private boolean actionsEnabled(final List<String> actions) {
        for (final String action : actions) {
            final String feature = featureToggleService.featureForAction(action);
            if (!feature.isBlank() && !featureToggleService.isEnabled(feature)) {
                return false;
            }
        }
        return true;
    }

    private String humanizeValue(final String group, final String value) {
        final String translation = languageManager.getRawMessage(languageManager.getDefaultLanguage(), "value-" + group + "-" + value);
        if (!translation.equals("value-" + group + "-" + value)) {
            return translation;
        }
        if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) {
            return "Standard";
        }
        final StringBuilder builder = new StringBuilder();
        for (final String part : value.replace('_', '-').split("-")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String humanizeSettingName(final String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "weather" -> "Wetter";
            case "time" -> "Zeit";
            default -> humanizeValue("setting", key);
        };
    }

    private String humanizeComponent(final String key) {
        return plotService.getComponentDisplayName(key);
    }

    private String readString(final ConfigurationSection primary, final ConfigurationSection fallback, final String path, final String defaultValue) {
        if (primary != null && primary.contains(path)) {
            return primary.getString(path, defaultValue);
        }
        if (fallback != null && fallback.contains(path)) {
            return fallback.getString(path, defaultValue);
        }
        return defaultValue;
    }

    private int readInt(final ConfigurationSection primary, final ConfigurationSection fallback, final String path, final int defaultValue) {
        if (primary != null && primary.contains(path)) {
            return primary.getInt(path, defaultValue);
        }
        if (fallback != null && fallback.contains(path)) {
            return fallback.getInt(path, defaultValue);
        }
        return defaultValue;
    }

    private boolean readBoolean(final ConfigurationSection primary, final ConfigurationSection fallback, final String path, final boolean defaultValue) {
        if (primary != null && primary.contains(path)) {
            return primary.getBoolean(path, defaultValue);
        }
        if (fallback != null && fallback.contains(path)) {
            return fallback.getBoolean(path, defaultValue);
        }
        return defaultValue;
    }

    private List<String> readStringList(final ConfigurationSection primary, final ConfigurationSection fallback, final String path) {
        if (primary != null && primary.contains(path)) {
            return primary.getStringList(path);
        }
        if (fallback != null && fallback.contains(path)) {
            return fallback.getStringList(path);
        }
        return List.of();
    }

    private boolean contains(final ConfigurationSection primary, final ConfigurationSection fallback, final String path) {
        return (primary != null && primary.contains(path)) || (fallback != null && fallback.contains(path));
    }

    private int normalizeSize(final int configuredSize) {
        final int size = Math.max(9, Math.min(54, configuredSize));
        return (size / 9) * 9;
    }

    private boolean isValidSlot(final Inventory inventory, final int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }

    private <T> PageSlice<T> slice(final List<T> entries, final int pageSize, final int requestedPage) {
        if (pageSize <= 0 || entries.isEmpty()) {
            return new PageSlice<>(List.of(), 0, 1);
        }
        final int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        final int page = Math.max(0, Math.min(requestedPage, pages - 1));
        final int from = Math.min(entries.size(), page * pageSize);
        final int to = Math.min(entries.size(), from + pageSize);
        return new PageSlice<>(entries.subList(from, to), page, pages);
    }

    private record PageSlice<T>(List<T> entries, int page, int pages) {
    }

    private record ChatInput(ChatInputType type, String roleId) {
    }

    private enum ChatInputType {
        CREATE_ROLE,
        RENAME_ROLE,
        INVITE_MEMBER,
        PLOT_NOTE,
        TEAM_NOTE,
        SET_WARP
    }
}
