package de.craftplay.plotextras.gui;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.audit.AuditLogEntry;
import de.craftplay.plotextras.audit.AuditLogService;
import de.craftplay.plotextras.backup.PlotBackupEntry;
import de.craftplay.plotextras.backup.PlotBackupService;
import de.craftplay.plotextras.competition.CompetitionEntry;
import de.craftplay.plotextras.competition.CompetitionService;
import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.integration.BedrockService;
import de.craftplay.plotextras.integration.HeadDatabaseService;
import de.craftplay.plotextras.integration.PlaceholderService;
import de.craftplay.plotextras.language.LanguageDefinition;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.limit.EntityLimitService;
import de.craftplay.plotextras.moderation.PlotModerationService;
import de.craftplay.plotextras.performance.PlotPerformanceService;
import de.craftplay.plotextras.performance.PlotPerformanceSnapshot;
import de.craftplay.plotextras.player.PlayerDataManager;
import de.craftplay.plotextras.plot.FlagEntry;
import de.craftplay.plotextras.plot.MemberEntry;
import de.craftplay.plotextras.plot.PlotRole;
import de.craftplay.plotextras.plot.PlotRolePermission;
import de.craftplay.plotextras.plot.PlotRoleService;
import de.craftplay.plotextras.plot.PlotService;
import de.craftplay.plotextras.plotmeta.PlotMetaService;
import de.craftplay.plotextras.redstone.RedstoneLagProtectionService;
import de.craftplay.plotextras.report.PlotReportEntry;
import de.craftplay.plotextras.report.PlotReportService;
import de.craftplay.plotextras.util.SlotParser;
import de.craftplay.plotextras.util.TextUtil;
import de.craftplay.plotextras.utility.PlotUtilityService;
import de.craftplay.plotextras.validation.ConfigValidationService;
import de.craftplay.plotextras.warp.PlotWarpEntry;
import de.craftplay.plotextras.warp.PlotWarpService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private final BedrockService bedrockService;
    private final PlotService plotService;
    private final EntityLimitService entityLimitService;
    private final PlotBackupService plotBackupService;
    private final AuditLogService auditLogService;
    private final RedstoneLagProtectionService redstoneLagProtectionService;
    private final PlotMetaService plotMetaService;
    private final PlotWarpService plotWarpService;
    private final PlotUtilityService plotUtilityService;
    private final PlotReportService plotReportService;
    private final PlotModerationService plotModerationService;
    private final PlotPerformanceService plotPerformanceService;
    private final CompetitionService competitionService;
    private final ConfigValidationService configValidationService;
    private final FeatureToggleService featureToggleService;
    @SuppressWarnings("unused")
    private final PlayerDataManager playerDataManager;
    private final Map<String, Map<String, YamlConfiguration>> guiConfigs = new HashMap<>();
    private YamlConfiguration plotSettingsConfig = new YamlConfiguration();
    private final Map<UUID, String> selectedRoles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> selectedMembers = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedBackups = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> backupViewOwners = new ConcurrentHashMap<>();
    private final Map<UUID, List<PlotUtilityService.PlotProfileEntry>> searchResults = new ConcurrentHashMap<>();
    private final Map<UUID, ChatInput> pendingChatInputs = new ConcurrentHashMap<>();

    public GuiManager(
            final JavaPlugin plugin,
            final LanguageManager languageManager,
            final PlaceholderService placeholderService,
            final HeadDatabaseService headDatabaseService,
            final BedrockService bedrockService,
            final PlotService plotService,
            final EntityLimitService entityLimitService,
            final PlotBackupService plotBackupService,
            final AuditLogService auditLogService,
            final RedstoneLagProtectionService redstoneLagProtectionService,
            final PlotMetaService plotMetaService,
            final PlotWarpService plotWarpService,
            final PlotUtilityService plotUtilityService,
            final PlotReportService plotReportService,
            final PlotModerationService plotModerationService,
            final PlotPerformanceService plotPerformanceService,
            final CompetitionService competitionService,
            final ConfigValidationService configValidationService,
            final FeatureToggleService featureToggleService,
            final PlayerDataManager playerDataManager
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.placeholderService = placeholderService;
        this.headDatabaseService = headDatabaseService;
        this.bedrockService = bedrockService;
        this.plotService = plotService;
        this.entityLimitService = entityLimitService;
        this.plotBackupService = plotBackupService;
        this.auditLogService = auditLogService;
        this.redstoneLagProtectionService = redstoneLagProtectionService;
        this.plotMetaService = plotMetaService;
        this.plotWarpService = plotWarpService;
        this.plotUtilityService = plotUtilityService;
        this.plotReportService = plotReportService;
        this.plotModerationService = plotModerationService;
        this.plotPerformanceService = plotPerformanceService;
        this.competitionService = competitionService;
        this.configValidationService = configValidationService;
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
        plotSettingsConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "plot-settings.yml"));
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
        final YamlConfiguration guiConfig = getGuiConfig(player, languageManager.getPlayerLanguage(player), normalizedGuiId);
        if (guiConfig == null) {
            sendMessage(player, "unknown-gui", Map.of("gui", normalizedGuiId));
            return;
        }
        final String permission = guiConfig.getString("permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission) && !player.hasPermission("craftplayplotextras.admin")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }

        final int size = normalizeSize(guiConfig.getInt("size", 54));
        final Map<String, String> placeholders = createPlaceholders(player);
        placeholders.put("gui", normalizedGuiId);
        placeholders.put("page", String.valueOf(page + 1));

        final String title = placeholderService.apply(player, guiConfig.getString("title", normalizedGuiId), placeholders);
        if (tryOpenBedrockForm(player, normalizedGuiId, Math.max(0, page), guiConfig, placeholders, title, size)) {
            return;
        }

        final GuiHolder holder = new GuiHolder(normalizedGuiId, Math.max(0, page));
        final Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.legacy(title));
        holder.setInventory(inventory);

        applyFill(player, inventory, holder, guiConfig, placeholders);
        applyStaticItems(player, inventory, holder, guiConfig, placeholders);
        applyDynamicItems(player, inventory, holder, guiConfig, placeholders, Math.max(0, page));

        player.openInventory(inventory);
    }

    private boolean tryOpenBedrockForm(
            final Player player,
            final String guiId,
            final int page,
            final YamlConfiguration guiConfig,
            final Map<String, String> placeholders,
            final String title,
            final int size
    ) {
        if (!bedrockService.canUseForms(player)) {
            return false;
        }

        final GuiHolder holder = new GuiHolder(guiId, page);
        final Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.legacy(title));
        holder.setInventory(inventory);
        applyFill(player, inventory, holder, guiConfig, placeholders);
        applyStaticItems(player, inventory, holder, guiConfig, placeholders);
        applyDynamicItems(player, inventory, holder, guiConfig, placeholders, page);

        final List<BedrockButton> buttons = bedrockButtons(inventory, holder);
        if (buttons.isEmpty()) {
            return false;
        }
        return bedrockService.sendSimpleForm(
                player,
                stripLegacy(title),
                bedrockContent(player, guiConfig, placeholders),
                buttons.stream().map(BedrockButton::label).toList(),
                index -> executeActions(player, holder, buttons.get(index).actions(), null)
        );
    }

    private List<BedrockButton> bedrockButtons(final Inventory inventory, final GuiHolder holder) {
        final List<BedrockButton> buttons = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final List<String> actions = holder.actions(slot);
            if (actions.isEmpty()) {
                continue;
            }
            final ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
                continue;
            }
            final ItemMeta meta = item.getItemMeta();
            final String label = meta.hasDisplayName()
                    ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                    : item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            buttons.add(new BedrockButton(label.isBlank() ? item.getType().name() : label, actions));
        }
        return buttons;
    }

    private String bedrockContent(
            final Player player,
            final YamlConfiguration guiConfig,
            final Map<String, String> placeholders
    ) {
        final String configuredContent = guiConfig.getString("bedrock.content", "");
        if (!configuredContent.isBlank()) {
            return stripLegacy(placeholderService.apply(player, configuredContent, placeholders));
        }
        final ConfigurationSection items = guiConfig.getConfigurationSection("items");
        if (items != null) {
            for (final String key : List.of("overview", "info")) {
                final ConfigurationSection section = items.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                final List<String> lines = new ArrayList<>();
                final String name = section.getString("name", "");
                if (!name.isBlank()) {
                    lines.add(stripLegacy(placeholderService.apply(player, name, placeholders)));
                }
                for (final String loreLine : section.getStringList("lore")) {
                    lines.add(stripLegacy(placeholderService.apply(player, loreLine, placeholders)));
                }
                if (!lines.isEmpty()) {
                    return String.join("\n", lines);
                }
            }
        }
        return languageManager.getPlayerLanguage(player).startsWith("en")
                ? "Choose an action."
                : "Wähle eine Aktion.";
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

    private YamlConfiguration getGuiConfig(final Player player, final String language, final String guiId) {
        if (bedrockService.isBedrockPlayer(player) && featureToggleService.isEnabled("player.gui.bedrock")) {
            final YamlConfiguration bedrockConfig = getGuiConfig(language + "-bedrock", guiId);
            if (bedrockConfig != null) {
                return bedrockConfig;
            }
            final YamlConfiguration defaultBedrockConfig = getGuiConfig(languageManager.getDefaultLanguage() + "-bedrock", guiId);
            if (defaultBedrockConfig != null) {
                return defaultBedrockConfig;
            }
        }
        return getGuiConfig(language, guiId);
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
            case "plot-search" -> renderPlotSearch(player, inventory, holder, dynamic, placeholders, page);
            case "guestbook" -> renderGuestbook(player, inventory, holder, dynamic, placeholders, page);
            case "plot-requests" -> renderPlotRequests(player, inventory, holder, dynamic, placeholders, page);
            case "temporary-trusts" -> renderTemporaryTrusts(player, inventory, holder, dynamic, placeholders, page);
            case "plot-reports" -> renderPlotReports(player, inventory, holder, dynamic, placeholders, page);
            case "build-tasks" -> renderBuildTasks(player, inventory, holder, dynamic, placeholders, page);
            case "permission-check" -> renderPermissionCheck(player, inventory, holder, dynamic, placeholders, page);
            case "performance-snapshot" -> renderPerformanceSnapshot(player, inventory, holder, dynamic, placeholders, page);
            case "competitions" -> renderCompetitions(player, inventory, holder, dynamic, placeholders, page);
            case "config-issues" -> renderConfigIssues(player, inventory, holder, dynamic, placeholders, page);
            case "statistics" -> renderStatistics(player, inventory, holder, dynamic, placeholders, page);
            case "feature-toggles" -> renderFeatureToggles(player, inventory, holder, dynamic, placeholders, page);
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

    private void renderPlotSearch(
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
        final List<PlotUtilityService.PlotProfileEntry> entries = searchResults.getOrDefault(player.getUniqueId(), List.of());
        final PageSlice<PlotUtilityService.PlotProfileEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("result-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotUtilityService.PlotProfileEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("result_plot_key", entry.plotKey());
            itemPlaceholders.put("result_world", entry.world());
            itemPlaceholders.put("result_plot", entry.plotId());
            itemPlaceholders.put("result_owner", entry.ownerName());
            itemPlaceholders.put("result_description", entry.description());
            itemPlaceholders.put("result_category", entry.category());
            itemPlaceholders.put("result_tags", String.join(", ", entry.tags()));
            itemPlaceholders.put("result_access_mode", entry.accessMode());

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                final List<String> actions = readActions(template).isEmpty()
                        ? List.of("TELEPORT_PLOT_KEY:" + entry.plotKey())
                        : resolveActions(player, readActions(template), itemPlaceholders);
                inventory.setItem(slot, item);
                holder.setActions(slot, actions);
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderGuestbook(
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
        final List<PlotUtilityService.GuestbookEntry> entries = plotUtilityService.guestbook(plot, Integer.MAX_VALUE);
        final boolean canManage = plotUtilityService.canManageGuestbook(player, plot);
        final PageSlice<PlotUtilityService.GuestbookEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection(canManage ? "entry-item" : "readonly-entry-item");
        final ConfigurationSection fallbackTemplate = dynamic.getConfigurationSection("entry-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotUtilityService.GuestbookEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("guestbook_id", entry.id());
            itemPlaceholders.put("guestbook_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("guestbook_player", entry.playerName());
            itemPlaceholders.put("guestbook_message", entry.message());

            final ItemStack item = buildItem(player, template, fallbackTemplate, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, canManage ? resolveActions(player, readActions(template, fallbackTemplate), itemPlaceholders) : List.of());
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPlotRequests(
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
        final String scope = dynamic.getString("scope", "own").toLowerCase(Locale.ROOT);
        final List<PlotUtilityService.UtilityRequestEntry> entries = switch (scope) {
            case "open" -> plotUtilityService.canHandleRequests(player) ? plotUtilityService.listOpenRequests() : List.of();
            case "all" -> plotUtilityService.canHandleRequests(player) ? plotUtilityService.listRequests() : List.of();
            default -> plotUtilityService.listOwnRequests(player);
        };
        final PageSlice<PlotUtilityService.UtilityRequestEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("request-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotUtilityService.UtilityRequestEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("request_id", entry.id());
            itemPlaceholders.put("request_type", entry.type());
            itemPlaceholders.put("request_status", entry.status());
            itemPlaceholders.put("request_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("request_player", entry.requesterName());
            itemPlaceholders.put("request_plot_key", entry.plotKey());
            itemPlaceholders.put("request_owner", entry.ownerName());
            itemPlaceholders.put("request_note", entry.note());
            itemPlaceholders.put("request_response", entry.response());

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderTemporaryTrusts(
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

        final List<PlotUtilityService.TemporaryTrustEntry> entries = plotUtilityService.temporaryTrusts(plot);
        final PageSlice<PlotUtilityService.TemporaryTrustEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("entry-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotUtilityService.TemporaryTrustEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("temptrust_player", entry.playerName());
            itemPlaceholders.put("temptrust_uuid", entry.playerUuid().toString());
            itemPlaceholders.put("temptrust_plot_key", entry.plotKey());
            itemPlaceholders.put("temptrust_created_by", entry.createdBy());
            itemPlaceholders.put("temptrust_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("temptrust_expires", BACKUP_TIME_FORMAT.format(entry.expiresAt()));
            itemPlaceholders.put("temptrust_remaining", formatDuration(player, Duration.between(Instant.now(), entry.expiresAt())));

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPlotReports(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || !plotReportService.canView(player)) {
            return;
        }
        final boolean all = dynamic.getBoolean("show-closed", false);
        final List<PlotReportEntry> entries = all ? plotReportService.listAll() : plotReportService.listOpen();
        final boolean canClose = plotReportService.canClose(player);
        final PageSlice<PlotReportEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection(canClose ? "report-item" : "readonly-report-item");
        final ConfigurationSection fallbackTemplate = dynamic.getConfigurationSection("report-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotReportEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("report_id", entry.id());
            itemPlaceholders.put("report_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("report_player", entry.reporterName());
            itemPlaceholders.put("report_plot_key", entry.plotKey());
            itemPlaceholders.put("report_owner", entry.ownerName());
            itemPlaceholders.put("report_reason", entry.reason());
            itemPlaceholders.put("report_status", entry.status());
            itemPlaceholders.put("report_note", entry.note());

            final ItemStack item = buildItem(player, template, fallbackTemplate, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, canClose ? resolveActions(player, readActions(template, fallbackTemplate), itemPlaceholders) : List.of());
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderBuildTasks(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || !plotUtilityService.canManageBuildTasks(player)) {
            return;
        }
        final List<PlotUtilityService.BuildTaskEntry> entries = plotUtilityService.listBuildTasks(dynamic.getBoolean("show-closed", false));
        final PageSlice<PlotUtilityService.BuildTaskEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("task-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PlotUtilityService.BuildTaskEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("task_id", entry.id());
            itemPlaceholders.put("task_status", entry.status());
            itemPlaceholders.put("task_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("task_created_by", entry.createdBy());
            itemPlaceholders.put("task_plot_key", entry.plotKey());
            itemPlaceholders.put("task_title", entry.title());
            itemPlaceholders.put("task_note", entry.note());

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPermissionCheck(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || (!player.hasPermission("craftplayplotextras.permissioncheck") && !player.hasPermission("craftplayplotextras.admin"))) {
            return;
        }
        final List<Player> entries = new ArrayList<>(Bukkit.getOnlinePlayers());
        entries.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        final PageSlice<Player> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("player-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final Player target = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            final Map<String, String> targetPlaceholders = plotService.getPlotPlaceholders(target);
            itemPlaceholders.put("permission_player", target.getName());
            itemPlaceholders.put("permission_uuid", target.getUniqueId().toString());
            itemPlaceholders.put("permission_plot_count", targetPlaceholders.getOrDefault("plot_count", "0"));
            itemPlaceholders.put("permission_plot_max", targetPlaceholders.getOrDefault("plot_max", "0"));
            itemPlaceholders.put("permission_is_admin", target.hasPermission("craftplayplotextras.admin") ? "true" : "false");

            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderPerformanceSnapshot(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || !plotPerformanceService.canView(player)) {
            return;
        }
        final PlotPerformanceSnapshot snapshot = plotPerformanceService.snapshot(plotService.getCurrentPlot(player));
        final List<PerformanceGuiEntry> entries = new ArrayList<>();
        for (final String warning : snapshot.warnings()) {
            entries.add(new PerformanceGuiEntry("warning", warning, ""));
        }
        snapshot.entityCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> entries.add(new PerformanceGuiEntry("entity", entry.getKey(), String.valueOf(entry.getValue()))));

        final PageSlice<PerformanceGuiEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection fallback = dynamic.getConfigurationSection("entry-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final PerformanceGuiEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("performance_plot", snapshot.plotKey());
            itemPlaceholders.put("performance_total", String.valueOf(snapshot.totalEntities()));
            itemPlaceholders.put("performance_type", entry.type());
            itemPlaceholders.put("performance_name", entry.name());
            itemPlaceholders.put("performance_value", entry.value());
            itemPlaceholders.put("performance_warning", entry.name());
            itemPlaceholders.put("performance_entity_type", entry.name());
            itemPlaceholders.put("performance_entity_count", entry.value());

            final ConfigurationSection template = dynamic.getConfigurationSection(entry.type().equals("warning") ? "warning-item" : "entity-item");
            final ItemStack item = buildItem(player, template, fallback, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, resolveActions(player, readActions(template, fallback), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderCompetitions(
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
        final String competition = dynamic.getString("competition", "");
        final List<CompetitionEntry> entries = competitionService.list(competition);
        final PageSlice<CompetitionEntry> slice = slice(entries, slots.size(), page);
        final ConfigurationSection fallback = dynamic.getConfigurationSection("entry-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final CompetitionEntry entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("competition_id", entry.id());
            itemPlaceholders.put("competition_name", entry.competition());
            itemPlaceholders.put("competition_created", BACKUP_TIME_FORMAT.format(entry.createdAt()));
            itemPlaceholders.put("competition_owner", entry.ownerName());
            itemPlaceholders.put("competition_owner_uuid", entry.ownerUuid().toString());
            itemPlaceholders.put("competition_world", entry.world());
            itemPlaceholders.put("competition_plot", entry.plotId());
            itemPlaceholders.put("competition_plot_key", entry.plotKey());
            itemPlaceholders.put("competition_note", entry.note());
            itemPlaceholders.put("competition_score", String.valueOf(entry.score()));
            itemPlaceholders.put("competition_scored_by", entry.scoredBy());
            itemPlaceholders.put("competition_score_note", entry.scoreNote());

            final ConfigurationSection template = dynamic.getConfigurationSection(entry.score() > 0 ? "scored-item" : "entry-item");
            final ItemStack item = buildItem(player, template, fallback, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, competitionService.canJudge(player)
                        ? List.of("COMPETITION_SCORE_PROMPT:" + entry.id())
                        : resolveActions(player, readActions(template, fallback), itemPlaceholders));
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderConfigIssues(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || !configValidationService.canValidate(player)) {
            return;
        }
        final List<String> entries = configValidationService.validate();
        final PageSlice<String> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("issue-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final String issue = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("issue", issue);
            itemPlaceholders.put("issue_number", String.valueOf(index + 1 + slice.page() * slots.size()));
            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of());
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderStatistics(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || (!player.hasPermission("craftplayplotextras.statistics") && !player.hasPermission("craftplayplotextras.admin"))) {
            return;
        }
        final List<Map.Entry<String, Integer>> entries = new ArrayList<>(plotUtilityService.statistics().entrySet());
        entries.add(Map.entry("openReports", plotReportService.listOpen().size()));
        entries.add(Map.entry("allBackups", plotBackupService.listAllBackups().size()));
        final PageSlice<Map.Entry<String, Integer>> slice = slice(entries, slots.size(), page);
        final ConfigurationSection template = dynamic.getConfigurationSection("stat-item");
        for (int index = 0; index < slice.entries().size(); index++) {
            final Map.Entry<String, Integer> entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("stat_key", entry.getKey());
            itemPlaceholders.put("stat_display", humanizeValue("stat", entry.getKey()));
            itemPlaceholders.put("stat_value", String.valueOf(entry.getValue()));
            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of());
            }
        }
        renderNavigation(player, inventory, holder, dynamic, placeholders, slice);
    }

    private void renderFeatureToggles(
            final Player player,
            final Inventory inventory,
            final GuiHolder holder,
            final ConfigurationSection dynamic,
            final Map<String, String> placeholders,
            final int page
    ) {
        final List<Integer> slots = SlotParser.slots(dynamic, "slots");
        if (slots.isEmpty() || (!player.hasPermission("craftplayplotextras.features.manage") && !player.hasPermission("craftplayplotextras.admin"))) {
            return;
        }
        final List<Map.Entry<String, Boolean>> entries = new ArrayList<>(featureToggleService.allFeatureToggles().entrySet());
        final PageSlice<Map.Entry<String, Boolean>> slice = slice(entries, slots.size(), page);
        for (int index = 0; index < slice.entries().size(); index++) {
            final Map.Entry<String, Boolean> entry = slice.entries().get(index);
            final Map<String, String> itemPlaceholders = new HashMap<>(placeholders);
            itemPlaceholders.put("feature", entry.getKey());
            itemPlaceholders.put("feature_state", languageManager.getRawMessage(player, entry.getValue() ? "state-enabled" : "state-disabled"));
            itemPlaceholders.put("feature_state_color", entry.getValue() ? "&a" : "&c");
            final ConfigurationSection template = dynamic.getConfigurationSection(entry.getValue() ? "enabled-item" : "disabled-item");
            final ItemStack item = buildItem(player, template, null, itemPlaceholders);
            final int slot = slots.get(index);
            if (item != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, item);
                holder.setActions(slot, List.of("TOGGLE_FEATURE:" + entry.getKey()));
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
        final ConfigurationSection sourceSection = resolveConfigSource(source);
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

    private ConfigurationSection resolveConfigSource(final String source) {
        if (source.equalsIgnoreCase("plot-settings")) {
            return plotSettingsConfig;
        }
        final String plotSettingsPrefix = "plot-settings.";
        if (source.toLowerCase(Locale.ROOT).startsWith(plotSettingsPrefix)) {
            final ConfigurationSection section = plotSettingsConfig.getConfigurationSection(source.substring(plotSettingsPrefix.length()));
            if (section != null) {
                return section;
            }
        }
        return plugin.getConfig().getConfigurationSection(source);
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
            if (upperAction.equals("SHOW_PLOT_INFO")) {
                showPlotInfo(player);
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
            if (upperAction.equals("BUILD_TASK_CREATE_PROMPT")) {
                startBuildTaskCreatePrompt(player);
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
            if (upperAction.startsWith("PLOT_WARP_CLICK:")) {
                handlePlotWarpClick(player, event, action.substring("PLOT_WARP_CLICK:".length()));
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
            if (upperAction.equals("SEARCH_PLOTS_PROMPT")) {
                startSearchPrompt(player);
                return;
            }
            if (upperAction.startsWith("TELEPORT_PLOT_KEY:")) {
                teleportPlotKey(player, action.substring("TELEPORT_PLOT_KEY:".length()));
                return;
            }
            if (upperAction.equals("GUESTBOOK_SIGN_PROMPT")) {
                startGuestbookPrompt(player);
                return;
            }
            if (upperAction.startsWith("DELETE_GUESTBOOK_ENTRY:")) {
                deleteGuestbookEntry(player, action.substring("DELETE_GUESTBOOK_ENTRY:".length()));
                return;
            }
            if (upperAction.startsWith("REQUEST_PROMPT:")) {
                startRequestPrompt(player, action.substring("REQUEST_PROMPT:".length()));
                return;
            }
            if (upperAction.startsWith("TEMPTRUST_ADD_PROMPT:")) {
                startTemporaryTrustPrompt(player, action.substring("TEMPTRUST_ADD_PROMPT:".length()));
                return;
            }
            if (upperAction.startsWith("TEMPTRUST_REMOVE:")) {
                removeTemporaryTrust(player, event, action.substring("TEMPTRUST_REMOVE:".length()));
                return;
            }
            if (upperAction.startsWith("CLOSE_REQUEST:")) {
                closeRequest(player, action.substring("CLOSE_REQUEST:".length()));
                return;
            }
            if (upperAction.startsWith("ACCEPT_TRUST_REQUEST:")) {
                acceptTrustRequest(player, action.substring("ACCEPT_TRUST_REQUEST:".length()));
                return;
            }
            if (upperAction.startsWith("CLOSE_REPORT:")) {
                closeReport(player, action.substring("CLOSE_REPORT:".length()));
                return;
            }
            if (upperAction.startsWith("CREATE_REPORT:")) {
                createReport(player, action.substring("CREATE_REPORT:".length()));
                return;
            }
            if (upperAction.startsWith("PLAYER_CLEANUP:")) {
                cleanupPlayerPlot(player, action.substring("PLAYER_CLEANUP:".length()));
                return;
            }
            if (upperAction.equals("SHOW_SELFCHECK")) {
                showSelfCheck(player);
                return;
            }
            if (upperAction.equals("SHOW_ASSISTANT")) {
                showAssistant(player);
                return;
            }
            if (upperAction.equals("SHOW_PROFILE")) {
                showProfile(player);
                return;
            }
            if (upperAction.startsWith("SET_PROFILE_ACCESS:")) {
                setProfileAccess(player, action.substring("SET_PROFILE_ACCESS:".length()));
                return;
            }
            if (upperAction.equals("SHOW_PERFORMANCE")) {
                showPerformance(player);
                return;
            }
            if (upperAction.equals("TEAM_MOD_LIST")) {
                showFrozenPlots(player);
                return;
            }
            if (upperAction.startsWith("TEAM_FREEZE:")) {
                freezeCurrentPlot(player, action.substring("TEAM_FREEZE:".length()));
                return;
            }
            if (upperAction.equals("TEAM_UNFREEZE")) {
                unfreezeCurrentPlot(player);
                return;
            }
            if (upperAction.startsWith("TEAM_CLEANUP:")) {
                cleanupTeamPlot(player, action.substring("TEAM_CLEANUP:".length()));
                return;
            }
            if (upperAction.startsWith("REDSTONE_ENABLE:")) {
                enableRedstone(player, action.substring("REDSTONE_ENABLE:".length()));
                return;
            }
            if (upperAction.equals("REDSTONE_ENABLE")) {
                enableRedstone(player, "");
                return;
            }
            if (upperAction.startsWith("REDSTONE_TELEPORT:")) {
                teleportRedstoneAlert(player, action.substring("REDSTONE_TELEPORT:".length()));
                return;
            }
            if (upperAction.startsWith("CREATE_BACKUP:")) {
                createManualBackup(player, action.substring("CREATE_BACKUP:".length()));
                return;
            }
            if (upperAction.startsWith("PERMISSION_CHECK:")) {
                showPermissionCheck(player, action.substring("PERMISSION_CHECK:".length()));
                return;
            }
            if (upperAction.startsWith("COMPETITION_JOIN:")) {
                joinCompetition(player, action.substring("COMPETITION_JOIN:".length()));
                return;
            }
            if (upperAction.equals("COMPETITION_LIST")) {
                showCompetitions(player);
                return;
            }
            if (upperAction.startsWith("COMPETITION_SCORE_PROMPT:")) {
                startCompetitionScorePrompt(player, action.substring("COMPETITION_SCORE_PROMPT:".length()));
                return;
            }
            if (upperAction.startsWith("COMPLETE_BUILD_TASK:")) {
                completeBuildTask(player, action.substring("COMPLETE_BUILD_TASK:".length()));
                return;
            }
            if (upperAction.startsWith("TOGGLE_FEATURE:")) {
                toggleFeature(player, action.substring("TOGGLE_FEATURE:".length()));
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

    private void showPlotInfo(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        final Map<String, String> placeholders = createPlaceholders(player);
        final boolean english = languageManager.getPlayerLanguage(player).toLowerCase(Locale.ROOT).startsWith("en");
        player.closeInventory();
        final List<String> lines = english
                ? List.of(
                "&8&m----------------",
                "&aPlot Info",
                "&7Plot: &f{plot_world} {plot_id}",
                "&7Owner: &f{plot_owner}",
                "&7Role here: &f{plot_role}",
                "&7Members: &f{plot_members} &8| &7Trusted: &f{plot_trusted} &8| &7Denied: &f{plot_denied}",
                "&7Plots: &f{plot_count}&7/&f{plot_max} &8| &7PlotSquared max: &f{plotsquared_plot_max}",
                "&7Status: &f{plot_status_display} &8| &7Visits: &f{plot_visits} &8| &7Likes: &f{plot_likes}",
                "&7Jobs: &f{jobs} &8| &7Money: &f{cmi_money} &8| &7Quests: &f{quests_completed}&7/&f{quests_total}",
                "&8&m----------------")
                : List.of(
                "&8&m----------------",
                "&aPlot-Info",
                "&7Plot: &f{plot_world} {plot_id}",
                "&7Besitzer: &f{plot_owner}",
                "&7Rolle hier: &f{plot_role}",
                "&7Mitglieder: &f{plot_members} &8| &7Vertraute: &f{plot_trusted} &8| &7Gesperrte: &f{plot_denied}",
                "&7Plots: &f{plot_count}&7/&f{plot_max} &8| &7PlotSquared-Max: &f{plotsquared_plot_max}",
                "&7Status: &f{plot_status_display} &8| &7Besuche: &f{plot_visits} &8| &7Favoriten: &f{plot_likes}",
                "&7Jobs: &f{jobs} &8| &7Geld: &f{cmi_money} &8| &7Quests: &f{quests_completed}&7/&f{quests_total}",
                "&8&m----------------");
        for (final String line : lines) {
            player.sendMessage(TextUtil.component(placeholderService.apply(player, line, placeholders)));
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

    private void startSearchPrompt(final Player player) {
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.SEARCH_PLOTS, ""));
        player.closeInventory();
        sendMessage(player, "search-input", Map.of());
    }

    private void startGuestbookPrompt(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.GUESTBOOK_SIGN, ""));
        player.closeInventory();
        sendMessage(player, "guestbook-input", Map.of());
    }

    private void startRequestPrompt(final Player player, final String type) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.CREATE_REQUEST, type));
        player.closeInventory();
        sendMessage(player, "request-input", Map.of("type", type));
    }

    private void startTemporaryTrustPrompt(final Player player, final String durationText) {
        if (!canInviteMembersHere(player)) {
            return;
        }
        final Duration duration = parseDuration(durationText);
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.CREATE_TEMPORARY_TRUST, durationText.trim()));
        player.closeInventory();
        sendMessage(player, "temptrust-input", Map.of("duration", formatDuration(player, duration)));
    }

    private void startBuildTaskCreatePrompt(final Player player) {
        if (!plotUtilityService.canManageBuildTasks(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        if (plotService.getCurrentPlot(player) == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.CREATE_BUILD_TASK, ""));
        player.closeInventory();
        sendMessage(player, "build-task-input", Map.of());
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
            } else if (input.type() == ChatInputType.SEARCH_PLOTS) {
                sendMessage(player, "search-cancelled", Map.of());
                scheduleOpen(player, "plot-search", 0);
            } else if (input.type() == ChatInputType.GUESTBOOK_SIGN) {
                sendMessage(player, "guestbook-cancelled", Map.of());
                scheduleOpen(player, "guestbook", 0);
            } else if (input.type() == ChatInputType.CREATE_REQUEST) {
                sendMessage(player, "request-cancelled", Map.of());
                scheduleOpen(player, "requests", 0);
            } else if (input.type() == ChatInputType.CREATE_TEMPORARY_TRUST) {
                sendMessage(player, "temptrust-cancelled", Map.of());
                scheduleOpen(player, "temporary-trusts", 0);
            } else if (input.type() == ChatInputType.SCORE_COMPETITION) {
                player.sendMessage(TextUtil.component("&7Bewertung abgebrochen."));
                scheduleOpen(player, "competitions", 0);
            } else if (input.type() == ChatInputType.CREATE_BUILD_TASK) {
                sendMessage(player, "build-task-cancelled", Map.of());
                scheduleOpen(player, "build-tasks", 0);
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

        if (input.type() == ChatInputType.SEARCH_PLOTS) {
            handleSearchInput(player, message);
            return;
        }

        if (input.type() == ChatInputType.GUESTBOOK_SIGN) {
            handleGuestbookInput(player, message);
            return;
        }

        if (input.type() == ChatInputType.CREATE_REQUEST) {
            handleRequestInput(player, input.roleId(), message);
            return;
        }

        if (input.type() == ChatInputType.CREATE_TEMPORARY_TRUST) {
            handleTemporaryTrustInput(player, input, message);
            return;
        }

        if (input.type() == ChatInputType.SCORE_COMPETITION) {
            handleCompetitionScoreInput(player, input, message);
            return;
        }

        if (input.type() == ChatInputType.CREATE_BUILD_TASK) {
            handleBuildTaskInput(player, message);
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

    private void handleTemporaryTrustInput(final Player player, final ChatInput input, final String message) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canInviteMembers(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }
        if (message.isBlank()) {
            pendingChatInputs.put(player.getUniqueId(), input);
            sendMessage(player, "temptrust-invalid", Map.of());
            return;
        }

        final OfflinePlayer target = Bukkit.getOfflinePlayer(message);
        final String targetName = target.getName() == null ? message : target.getName();
        final Duration duration = parseDuration(input.roleId());
        final PlotUtilityService.TemporaryTrustEntry entry = plotUtilityService.createTemporaryTrust(player, plot, target, duration);
        if (entry == null) {
            sendMessage(player, "temptrust-failed", Map.of("player", targetName));
            scheduleOpen(player, "temporary-trusts", 0);
            return;
        }

        auditLogService.log(player, plot, "Temporärer Trust gesetzt",
                entry.playerName() + " bis " + BACKUP_TIME_FORMAT.format(entry.expiresAt()));
        sendMessage(player, "temptrust-created", Map.of(
                "player", entry.playerName(),
                "duration", formatDuration(player, duration),
                "expires", BACKUP_TIME_FORMAT.format(entry.expiresAt())
        ));
        scheduleOpen(player, "temporary-trusts", 0);
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

    private void handleSearchInput(final Player player, final String message) {
        final List<PlotUtilityService.PlotProfileEntry> results = plotUtilityService.searchProfiles(message);
        searchResults.put(player.getUniqueId(), results);
        sendMessage(player, "search-results", Map.of("count", String.valueOf(results.size())));
        scheduleOpen(player, "plot-search", 0);
    }

    private void handleGuestbookInput(final Player player, final String message) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        final PlotUtilityService.GuestbookEntry entry = plotUtilityService.signGuestbook(player, plot, message);
        if (entry == null) {
            sendMessage(player, "guestbook-failed", Map.of());
        } else {
            auditLogService.log(player, plot, "Gästebuch-Eintrag erstellt", entry.id());
            sendMessage(player, "guestbook-signed", Map.of());
        }
        scheduleOpen(player, "guestbook", 0);
    }

    private void handleRequestInput(final Player player, final String type, final String message) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        final PlotUtilityService.UtilityRequestEntry entry = plotUtilityService.createRequest(player, plot, type, message);
        if (entry == null) {
            sendMessage(player, "request-failed", Map.of("type", type));
        } else {
            auditLogService.log(player, plot, "Plot-Anfrage erstellt", entry.id());
            sendMessage(player, "request-created", Map.of("request", entry.id(), "type", entry.type()));
        }
        scheduleOpen(player, "requests", 0);
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
        if (event != null && (!event.isShiftClick() || !event.isRightClick())) {
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

    private void removeTemporaryTrust(final Player player, final InventoryClickEvent event, final String uuidText) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (!plotService.canUntrustMembers(player, plot)) {
            sendMessage(player, "not-owner", Map.of());
            return;
        }

        final UUID targetId;
        try {
            targetId = UUID.fromString(uuidText.trim());
        } catch (final IllegalArgumentException exception) {
            sendMessage(player, "temptrust-remove-failed", Map.of("player", uuidText));
            scheduleOpen(player, "temporary-trusts", 0);
            return;
        }

        final String targetName = plotUtilityService.temporaryTrusts(plot).stream()
                .filter(entry -> entry.playerUuid().equals(targetId))
                .map(PlotUtilityService.TemporaryTrustEntry::playerName)
                .findFirst()
                .orElse(memberName(targetId));
        if (event == null) {
            confirmBedrockTemporaryTrustRemoval(player, targetId, targetName);
            return;
        }
        if (!event.isShiftClick() || !event.isRightClick()) {
            sendMessage(player, "temptrust-remove-shift-right", Map.of("player", targetName));
            return;
        }
        removeTemporaryTrustConfirmed(player, plot, targetId, targetName);
    }

    private void confirmBedrockTemporaryTrustRemoval(final Player player, final UUID targetId, final String targetName) {
        final boolean english = languageManager.getPlayerLanguage(player).toLowerCase(Locale.ROOT).startsWith("en");
        final boolean opened = bedrockService.sendSimpleForm(
                player,
                english ? "Remove temporary trust?" : "Temporären Trust entfernen?",
                english
                        ? "Remove temporary trust for " + targetName + "?"
                        : "Temporären Trust für " + targetName + " entfernen?",
                english ? List.of("Cancel", "Remove") : List.of("Abbrechen", "Entfernen"),
                index -> {
                    if (index != 1) {
                        scheduleOpen(player, "temporary-trusts", 0);
                        return;
                    }
                    final Plot currentPlot = plotService.getCurrentPlot(player);
                    if (currentPlot == null) {
                        sendMessage(player, "no-plot", Map.of());
                        return;
                    }
                    removeTemporaryTrustConfirmed(player, currentPlot, targetId, targetName);
                }
        );
        if (!opened) {
            sendMessage(player, "temptrust-remove-shift-right", Map.of("player", targetName));
        }
    }

    private void removeTemporaryTrustConfirmed(final Player player, final Plot plot, final UUID targetId, final String targetName) {
        if (plotUtilityService.removeTemporaryTrust(player, plot, targetId)) {
            auditLogService.log(player, plot, "Temporärer Trust entfernt", targetName);
            sendMessage(player, "temptrust-removed", Map.of("player", targetName));
        } else {
            sendMessage(player, "temptrust-remove-failed", Map.of("player", targetName));
        }
        scheduleOpen(player, "temporary-trusts", 0);
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

    private void handlePlotWarpClick(final Player player, final InventoryClickEvent event, final String warpId) {
        if (event != null && event.isShiftClick() && event.isRightClick()) {
            deletePlotWarp(player, warpId);
            return;
        }
        teleportPlotWarp(player, warpId);
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

    private void teleportPlotKey(final Player player, final String plotKey) {
        if (plotUtilityService.teleportToPlot(player, plotKey)) {
            sendMessage(player, "plot-teleported", Map.of("plot", plotKey));
        } else {
            sendMessage(player, "plot-teleport-failed", Map.of("plot", plotKey));
            scheduleOpen(player, "plot-search", 0);
        }
    }

    private void deleteGuestbookEntry(final Player player, final String entryId) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        if (plotUtilityService.deleteGuestbookEntry(player, plot, entryId)) {
            auditLogService.log(player, plot, "Gästebuch-Eintrag gelöscht", entryId);
            sendMessage(player, "guestbook-deleted", Map.of("entry", entryId));
        } else {
            sendMessage(player, "guestbook-delete-failed", Map.of("entry", entryId));
        }
        scheduleOpen(player, "guestbook", 0);
    }

    private void closeRequest(final Player player, final String requestId) {
        if (plotUtilityService.closeRequest(player, requestId, "Per GUI geschlossen.")) {
            sendMessage(player, "request-closed", Map.of("request", requestId));
        } else {
            sendMessage(player, "request-close-failed", Map.of("request", requestId));
        }
        scheduleOpen(player, "team-requests", 0);
    }

    private void acceptTrustRequest(final Player player, final String requestId) {
        if (plotUtilityService.acceptTrustRequest(player, requestId)) {
            sendMessage(player, "request-trust-accepted", Map.of("request", requestId));
        } else {
            sendMessage(player, "request-trust-failed", Map.of("request", requestId));
        }
        scheduleOpen(player, "team-requests", 0);
    }

    private void closeReport(final Player player, final String reportId) {
        if (plotReportService.close(player, reportId, "Per GUI geschlossen.")) {
            sendMessage(player, "report-closed", Map.of("report", reportId));
        } else {
            sendMessage(player, "report-close-failed", Map.of("report", reportId));
        }
        scheduleOpen(player, "reports", 0);
    }

    private void createReport(final Player player, final String reason) {
        if (!plotReportService.canCreate(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String reportReason = blankInput(reason, "Keine Begründung angegeben.");
        final PlotReportEntry report = plotReportService.create(player, plot, reportReason);
        player.closeInventory();
        if (report == null) {
            player.sendMessage(TextUtil.component("&cMeldung konnte nicht erstellt werden."));
            return;
        }

        auditLogService.log(player, plot, "Plot gemeldet", report.id() + ": " + reportReason);
        player.sendMessage(TextUtil.component("&aMeldung &e" + report.id() + " &awurde an das Team gesendet."));
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (plotReportService.canView(online)) {
                online.sendMessage(TextUtil.component("&cNeue Plot-Meldung &e" + report.id()
                        + " &7von &f" + player.getName()
                        + " &7auf &f" + report.plotKey()
                        + " &8- &föffne das Team-Menü."));
            }
        }
    }

    private void cleanupPlayerPlot(final Player player, final String mode) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String cleanupMode = blankInput(mode, "drops");
        final int removed = plotUtilityService.cleanupOwnedPlot(player, plot, cleanupMode);
        if (removed < 0) {
            player.sendMessage(TextUtil.component("&cCleanup konnte nicht ausgeführt werden. Nur berechtigte Plotmitglieder können das nutzen."));
        } else {
            auditLogService.log(player, plot, "Spieler-Cleanup", cleanupMode + ": " + removed);
            player.sendMessage(TextUtil.component("&aEntfernt: &e" + removed + " &7(" + cleanupMode + ")"));
        }
        scheduleOpen(player, "cleanup", 0);
    }

    private void showSelfCheck(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        player.closeInventory();
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }
        final PlotPerformanceSnapshot snapshot = plotPerformanceService.snapshot(plot);
        sendSnapshot(player, "&aPlot-Selbstcheck", snapshot, 8);
    }

    private void showAssistant(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        final Map<String, String> plotPlaceholders = plotService.getPlotPlaceholders(player);
        player.closeInventory();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlot-Assistent"));
        player.sendMessage(TextUtil.component("&7Plots: &f" + plotPlaceholders.getOrDefault("plot_count", "0")
                + "&7/&f" + plotService.getPlotLimit(player)));
        if (plot == null) {
            player.sendMessage(TextUtil.component("&cDu stehst auf keinem Plot."));
            player.sendMessage(TextUtil.component("&8Tipp: &fNutze Plot-Auto oder das PlotSquared-Claim-Menü."));
        } else {
            final Map<String, String> meta = plotUtilityService.placeholders(plot);
            player.sendMessage(TextUtil.component("&7Besuchsmodus: &f"
                    + profileAccessDisplay(meta.getOrDefault("plot_access_mode", "normal"))
                    + " &8| &7Kategorie: &f" + meta.getOrDefault("plot_category", "-")));
            player.sendMessage(TextUtil.component("&7Beschreibung: &f" + meta.getOrDefault("plot_description", "-")));
            player.sendMessage(TextUtil.component("&7Tags: &f" + meta.getOrDefault("plot_tags", "-")));
            player.sendMessage(TextUtil.component("&7Werkzeuge, Profil, Warps und Selbstcheck findest du direkt im Menü."));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void showProfile(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        player.closeInventory();
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final Map<String, String> meta = plotUtilityService.placeholders(plot);
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aPlotprofil"));
        player.sendMessage(TextUtil.component("&7Beschreibung: &f" + meta.getOrDefault("plot_description", "-")));
        player.sendMessage(TextUtil.component("&7Kategorie: &f" + meta.getOrDefault("plot_category", "-")));
        player.sendMessage(TextUtil.component("&7Tags: &f" + meta.getOrDefault("plot_tags", "-")));
        player.sendMessage(TextUtil.component("&7Besuchsmodus: &f"
                + profileAccessDisplay(meta.getOrDefault("plot_access_mode", "normal"))));
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void setProfileAccess(final Player player, final String mode) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String accessMode = blankInput(mode, "normal");
        if (plotUtilityService.setAccessMode(player, plot, accessMode)) {
            auditLogService.log(player, plot, "Plotprofil geändert", "Besuchsmodus: " + profileAccessDisplay(accessMode));
            player.sendMessage(TextUtil.component("&aBesuchsmodus wurde auf &e" + profileAccessDisplay(accessMode) + " &agesetzt."));
        } else {
            player.sendMessage(TextUtil.component("&cBesuchsmodus konnte nicht geändert werden. Prüfe deine Rechte."));
        }
        scheduleOpen(player, "plot-profile", 0);
    }

    private void showPerformance(final Player player) {
        if (!plotPerformanceService.canView(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        player.closeInventory();
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final PlotPerformanceSnapshot snapshot = plotPerformanceService.snapshot(plot);
        sendSnapshot(player, "&aPerformance: &f" + snapshot.plotKey(), snapshot, 10);
    }

    private void showFrozenPlots(final Player player) {
        if (!plotModerationService.canModerate(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        player.closeInventory();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aGesperrte Plots"));
        final List<String> entries = plotModerationService.listFrozen();
        if (entries.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine gesperrten Plots vorhanden."));
        }
        for (final String line : entries) {
            player.sendMessage(TextUtil.component("&e" + line));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void freezeCurrentPlot(final Player player, final String reason) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String freezeReason = blankInput(reason, "Teamprüfung");
        if (plotModerationService.freeze(player, plot, freezeReason)) {
            auditLogService.log(player, plot, "Plot eingefroren", freezeReason);
            player.sendMessage(TextUtil.component("&aDer aktuelle Plot wurde eingefroren."));
        } else {
            player.sendMessage(TextUtil.component("&cDer Plot konnte nicht eingefroren werden."));
        }
        scheduleOpen(player, "team-moderation", 0);
    }

    private void unfreezeCurrentPlot(final Player player) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        if (plotModerationService.unfreeze(player, plot)) {
            auditLogService.log(player, plot, "Plot-Freeze aufgehoben", "-");
            player.sendMessage(TextUtil.component("&aDer aktuelle Plot wurde freigegeben."));
        } else {
            player.sendMessage(TextUtil.component("&cDer Plot war nicht eingefroren oder du hast keine Rechte."));
        }
        scheduleOpen(player, "team-moderation", 0);
    }

    private void cleanupTeamPlot(final Player player, final String mode) {
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String cleanupMode = blankInput(mode, "drops");
        final int removed = plotModerationService.cleanup(player, plot, cleanupMode);
        if (removed >= 0) {
            auditLogService.log(player, plot, "Plot-Cleanup", cleanupMode + ": " + removed);
            player.sendMessage(TextUtil.component("&aEntfernt: &e" + removed + " &7(" + cleanupMode + ")"));
        } else {
            player.sendMessage(TextUtil.component("&cCleanup konnte nicht ausgeführt werden."));
        }
        scheduleOpen(player, "team-moderation", 0);
    }

    private void enableRedstone(final Player player, final String alertId) {
        if (!redstoneLagProtectionService.canAdmin(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final boolean enabled = isBlankPlaceholder(alertId)
                ? redstoneLagProtectionService.enableRedstoneAtCurrentPlot(player)
                : redstoneLagProtectionService.enableRedstoneAtAlert(player, alertId);
        if (enabled) {
            player.sendMessage(TextUtil.component("&aRedstone wurde auf dem Plot wieder aktiviert."));
        } else {
            player.sendMessage(TextUtil.component("&cRedstone konnte auf diesem Plot nicht aktiviert werden."));
        }
        scheduleOpen(player, isBlankPlaceholder(alertId) ? "team-moderation" : "redstone-alerts", 0);
    }

    private void teleportRedstoneAlert(final Player player, final String alertId) {
        if (!redstoneLagProtectionService.canReceiveAlerts(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        if (isBlankPlaceholder(alertId) || !redstoneLagProtectionService.teleportToAlert(player, alertId)) {
            player.sendMessage(TextUtil.component("&cRedstone-Alarm wurde nicht gefunden."));
            scheduleOpen(player, "redstone-alerts", 0);
            return;
        }
        player.closeInventory();
        player.sendMessage(TextUtil.component("&aDu wurdest zur Redstone-Lagmaschine teleportiert."));
    }

    private void createManualBackup(final Player player, final String reason) {
        if (!plotBackupService.canCreateManual(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String backupReason = blankInput(reason, "GUI-Team");
        if (plotBackupService.createManualBackup(player, plot, backupReason)) {
            auditLogService.log(player, plot, "Manuelles Backup gestartet", backupReason);
            player.sendMessage(TextUtil.component("&aManuelles Plot-Backup wurde gestartet."));
        } else {
            player.sendMessage(TextUtil.component("&cManuelles Plot-Backup konnte nicht gestartet werden."));
        }
        scheduleOpen(player, "team-tools", 0);
    }

    private void showPermissionCheck(final Player player, final String targetName) {
        if (!player.hasPermission("craftplayplotextras.permissioncheck") && !player.hasPermission("craftplayplotextras.admin")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String cleanTargetName = blankInput(targetName, player.getName());
        final Player target = Bukkit.getPlayerExact(cleanTargetName);
        player.closeInventory();
        if (target == null) {
            player.sendMessage(TextUtil.component("&cDer Spieler muss online sein."));
            return;
        }

        final List<String> permissions = target.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .filter(permission -> permission.startsWith("craftplayplotextras.") || permission.startsWith("plots.plot."))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aRechtecheck: &f" + target.getName()));
        player.sendMessage(TextUtil.component("&7Plotlimit: &f" + plotService.getPlotLimit(target)));
        permissions.stream().limit(30).forEach(permission -> player.sendMessage(TextUtil.component("&e" + permission)));
        if (permissions.size() > 30) {
            player.sendMessage(TextUtil.component("&7Weitere Rechte: &f" + (permissions.size() - 30)));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void joinCompetition(final Player player, final String rawCompetition) {
        if (!competitionService.canJoin(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String[] parts = blankInput(rawCompetition, "default").split(":", 2);
        final String competition = blankInput(parts[0], "default");
        final String note = parts.length >= 2 ? blankInput(parts[1], "-") : "-";
        final CompetitionEntry entry = competitionService.join(player, plot, competition, note);
        if (entry == null) {
            player.sendMessage(TextUtil.component("&cTeilnahme konnte nicht gespeichert werden."));
        } else {
            auditLogService.log(player, plot, "Wettbewerb angemeldet", entry.competition());
            player.sendMessage(TextUtil.component("&aPlot wurde für Wettbewerb &e" + entry.competition() + " &aangemeldet."));
        }
        scheduleOpen(player, "competitions", 0);
    }

    private void showCompetitions(final Player player) {
        final List<CompetitionEntry> entries = competitionService.list("");
        player.closeInventory();
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component("&aWettbewerbe &8(" + entries.size() + ")"));
        if (entries.isEmpty()) {
            player.sendMessage(TextUtil.component("&7Keine Einträge vorhanden."));
        }
        for (final CompetitionEntry entry : entries.stream().limit(10).toList()) {
            player.sendMessage(TextUtil.component("&e" + entry.id()
                    + " &7| &f" + entry.ownerName()
                    + " &7| &f" + entry.plotKey()
                    + " &7| &a" + entry.score()));
        }
        if (competitionService.canJudge(player)) {
            player.sendMessage(TextUtil.component("&7Klicke einen Wettbewerbseintrag im Menü, um ihn zu bewerten."));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private void startCompetitionScorePrompt(final Player player, final String competitionId) {
        if (!competitionService.canJudge(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final String cleanCompetitionId = blankInput(competitionId, "");
        if (cleanCompetitionId.isBlank() || competitionService.get(cleanCompetitionId).isEmpty()) {
            player.sendMessage(TextUtil.component("&cWettbewerbseintrag wurde nicht gefunden."));
            scheduleOpen(player, "competitions", 0);
            return;
        }

        pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.SCORE_COMPETITION, cleanCompetitionId));
        player.closeInventory();
        player.sendMessage(TextUtil.component("&eGib Punktzahl 0-100 und optional eine Notiz ein. &7Beispiel: &f85 Sehr schön"));
        player.sendMessage(TextUtil.component("&7Schreibe &fabbrechen &7zum Abbrechen."));
    }

    private void handleCompetitionScoreInput(final Player player, final ChatInput input, final String message) {
        final String[] parts = message.split("\\s+", 2);
        final int score;
        try {
            score = Integer.parseInt(parts[0]);
        } catch (final NumberFormatException exception) {
            pendingChatInputs.put(player.getUniqueId(), input);
            player.sendMessage(TextUtil.component("&cBitte gib eine Zahl zwischen 0 und 100 an."));
            return;
        }
        if (score < 0 || score > 100) {
            pendingChatInputs.put(player.getUniqueId(), input);
            player.sendMessage(TextUtil.component("&cBitte gib eine Zahl zwischen 0 und 100 an."));
            return;
        }

        final String note = parts.length >= 2 && !parts[1].isBlank() ? parts[1].trim() : "-";
        if (competitionService.score(player, input.roleId(), score, note)) {
            auditLogService.log(player, null, "Wettbewerb bewertet", input.roleId() + ": " + score + " (" + note + ")");
            player.sendMessage(TextUtil.component("&aBewertung gespeichert."));
        } else {
            player.sendMessage(TextUtil.component("&cEintrag wurde nicht gefunden."));
        }
        scheduleOpen(player, "competitions", 0);
    }

    private void handleBuildTaskInput(final Player player, final String message) {
        if (!plotUtilityService.canManageBuildTasks(player)) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final Plot plot = plotService.getCurrentPlot(player);
        if (plot == null) {
            sendMessage(player, "no-plot", Map.of());
            return;
        }

        final String[] parts = message.split("\\|", 2);
        final String title = parts[0].trim();
        if (title.isBlank()) {
            pendingChatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.CREATE_BUILD_TASK, ""));
            sendMessage(player, "build-task-invalid", Map.of());
            return;
        }

        final String note = parts.length >= 2 && !parts[1].trim().isBlank() ? parts[1].trim() : "-";
        final PlotUtilityService.BuildTaskEntry entry = plotUtilityService.createBuildTask(player, plot, title, note);
        if (entry == null) {
            sendMessage(player, "build-task-create-failed", Map.of());
        } else {
            auditLogService.log(player, plot, "Bauaufgabe erstellt", entry.id() + ": " + entry.title());
            sendMessage(player, "build-task-created", Map.of("task", entry.id(), "title", entry.title()));
        }
        scheduleOpen(player, "build-tasks", 0);
    }

    private void completeBuildTask(final Player player, final String taskId) {
        if (plotUtilityService.completeBuildTask(player, taskId)) {
            sendMessage(player, "build-task-completed", Map.of("task", taskId));
        } else {
            sendMessage(player, "build-task-failed", Map.of("task", taskId));
        }
        scheduleOpen(player, "build-tasks", 0);
    }

    private void toggleFeature(final Player player, final String feature) {
        if (!player.hasPermission("craftplayplotextras.features.manage") && !player.hasPermission("craftplayplotextras.admin")) {
            sendMessage(player, "no-permission", Map.of());
            return;
        }
        final boolean enabled = featureToggleService.toggleFeature(feature);
        sendMessage(player, "feature-toggle-set", Map.of(
                "feature", feature,
                "state", languageManager.getRawMessage(player, enabled ? "state-enabled" : "state-disabled")
        ));
        scheduleOpen(player, "feature-toggles", 0);
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

    private Duration parseDuration(final String input) {
        if (input == null || input.isBlank()) {
            return Duration.ofHours(1);
        }
        final String normalized = input.toLowerCase(Locale.ROOT).trim();
        final long amount;
        try {
            amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
        } catch (final RuntimeException exception) {
            return Duration.ofHours(1);
        }
        return switch (normalized.charAt(normalized.length() - 1)) {
            case 'm' -> Duration.ofMinutes(Math.max(1L, amount));
            case 'h' -> Duration.ofHours(Math.max(1L, amount));
            case 'd' -> Duration.ofDays(Math.max(1L, amount));
            case 'w' -> Duration.ofDays(Math.max(1L, amount) * 7L);
            default -> Duration.ofHours(Math.max(1L, amount));
        };
    }

    private String formatDuration(final Player player, final Duration duration) {
        final boolean english = languageManager.getPlayerLanguage(player).toLowerCase(Locale.ROOT).startsWith("en");
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return english ? "expired" : "abgelaufen";
        }
        final long days = duration.toDays();
        final long hours = duration.minusDays(days).toHours();
        final long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        final List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + (english ? (days == 1 ? " day" : " days") : (days == 1 ? " Tag" : " Tage")));
        }
        if (hours > 0) {
            parts.add(hours + (english ? (hours == 1 ? " hour" : " hours") : (hours == 1 ? " Stunde" : " Stunden")));
        }
        if (minutes > 0 || parts.isEmpty()) {
            final long shownMinutes = Math.max(1L, minutes);
            parts.add(shownMinutes + (english
                    ? (shownMinutes == 1 ? " minute" : " minutes")
                    : (shownMinutes == 1 ? " Minute" : " Minuten")));
        }
        return String.join(" ", parts);
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
        placeholders.putAll(plotUtilityService.placeholders(plotService.getCurrentPlot(player)));
        placeholders.put("plot_warps", String.valueOf(plotWarpService.listWarps(plotService.getCurrentPlot(player)).size()));
        placeholders.put("temporary_trusts", String.valueOf(plotUtilityService.temporaryTrusts(plotService.getCurrentPlot(player)).size()));
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

    private String stripLegacy(final String text) {
        return ChatColor.stripColor(TextUtil.legacy(text == null ? "" : text)).trim();
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

    private void sendSnapshot(final Player player, final String title, final PlotPerformanceSnapshot snapshot, final int limit) {
        player.sendMessage(TextUtil.component("&8&m----------------"));
        player.sendMessage(TextUtil.component(title));
        player.sendMessage(TextUtil.component("&7Entities gesamt: &f" + snapshot.totalEntities()));
        snapshot.entityCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> player.sendMessage(TextUtil.component("&e" + entry.getKey() + " &7- &f" + entry.getValue())));
        for (final String warning : snapshot.warnings()) {
            player.sendMessage(TextUtil.component("&6" + warning));
        }
        player.sendMessage(TextUtil.component("&8&m----------------"));
    }

    private String profileAccessDisplay(final String mode) {
        return switch (mode == null ? "normal" : mode.toLowerCase(Locale.ROOT)) {
            case "public" -> "Öffentlich";
            case "members" -> "Mitglieder";
            case "friends" -> "Freunde";
            case "private" -> "Privat";
            case "locked" -> "Gesperrt";
            default -> "Normal";
        };
    }

    private String blankInput(final String value, final String fallback) {
        return isBlankPlaceholder(value) ? fallback : value.trim();
    }

    private boolean isBlankPlaceholder(final String value) {
        if (value == null) {
            return true;
        }
        final String trimmed = value.trim();
        return trimmed.isBlank()
                || trimmed.equals("-")
                || trimmed.equals("{}")
                || (trimmed.startsWith("{") && trimmed.endsWith("}"));
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

    private record PerformanceGuiEntry(String type, String name, String value) {
    }

    private record BedrockButton(String label, List<String> actions) {
    }

    private record ChatInput(ChatInputType type, String roleId) {
    }

    private enum ChatInputType {
        CREATE_ROLE,
        RENAME_ROLE,
        INVITE_MEMBER,
        PLOT_NOTE,
        TEAM_NOTE,
        SET_WARP,
        SEARCH_PLOTS,
        GUESTBOOK_SIGN,
        CREATE_REQUEST,
        CREATE_TEMPORARY_TRUST,
        SCORE_COMPETITION,
        CREATE_BUILD_TASK
    }
}
