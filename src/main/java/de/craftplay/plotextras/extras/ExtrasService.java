package de.craftplay.plotextras.extras;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.plotsquared.PlotContext;
import de.craftplay.plotextras.plotsquared.PlotRegion;
import de.craftplay.plotextras.plotsquared.PlotSquaredFlagService;
import de.craftplay.plotextras.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class ExtrasService implements Listener {

    private static final String WAND_ID = "worldedit-service";

    private final CraftplayPlotExtrasPlugin plugin;
    private final LanguageManager languageManager;
    private final PlotSquaredFlagService flagService;
    private final NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, PendingWorldEditAction> pendingActions = new HashMap<>();
    private final Map<String, WorldEditMode> modes = new LinkedHashMap<>();

    private YamlConfiguration data;
    private YamlConfiguration guiConfig;
    private String dataFile;
    private boolean enabled;
    private boolean worldEditServiceEnabled;
    private String permission;
    private String bypassPlotPermission;
    private String freePermission;
    private boolean requireOwnPlot;
    private boolean economyEnabled;
    private long includedBlocks;
    private double includedPrice;
    private double extraPricePerBlock;
    private long cooldownMillis;
    private long maxSelectionBlocks;
    private Material wandMaterial;
    private String wandName;
    private List<String> wandLore;
    private int wandCustomModelData;
    private List<String> successCommands;
    private int confirmSize;
    private String confirmTitle;
    private String confirmSelectedText;
    private String confirmNotSelectedText;
    private ConfirmButton confirmInfoButton;
    private ConfirmButton confirmAcceptButton;
    private ConfirmButton confirmCancelButton;
    private ConfirmButton confirmFiller;
    private Object economy;
    private Method economyHasMethod;
    private Method economyWithdrawMethod;
    private Method economyFormatMethod;

    public ExtrasService(
            final CraftplayPlotExtrasPlugin plugin,
            final LanguageManager languageManager,
            final PlotSquaredFlagService flagService
    ) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.flagService = flagService;
        this.wandKey = new NamespacedKey(plugin, "extras_wand");
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("extras.enabled", true);
        dataFile = plugin.getConfig().getString("extras.data-file", "extras.yml");
        data = plugin.getStorageService().load("extras", dataFile);
        if (data.getInt("file-version", 0) < 1) {
            data.set("file-version", 1);
            save();
        }

        final String root = "extras.worldedit-service.";
        worldEditServiceEnabled = plugin.getConfig().getBoolean(root + "enabled", true);
        permission = plugin.getConfig().getString(root + "permission", "craftplayplotextras.extras.worldedit");
        bypassPlotPermission = plugin.getConfig().getString(root + "bypass-plot-permission", "craftplayplotextras.extras.worldedit.bypass-plot");
        freePermission = plugin.getConfig().getString(root + "free-permission", "craftplayplotextras.extras.worldedit.free");
        requireOwnPlot = plugin.getConfig().getBoolean(root + "require-own-plot", true);
        economyEnabled = plugin.getConfig().getBoolean(root + "economy.enabled", true);
        includedBlocks = Math.max(0L, plugin.getConfig().getLong(root + "pricing.included-blocks", 200000L));
        includedPrice = Math.max(0.0D, plugin.getConfig().getDouble(root + "pricing.included-price", 5000.0D));
        extraPricePerBlock = Math.max(0.0D, plugin.getConfig().getDouble(root + "pricing.extra-price-per-block", 0.05D));
        cooldownMillis = Math.max(0L, plugin.getConfig().getLong(root + "pricing.cooldown-days", 3L)) * 24L * 60L * 60L * 1000L;
        maxSelectionBlocks = Math.max(1L, plugin.getConfig().getLong(root + "max-selection-blocks", 1000000L));
        wandMaterial = material(plugin.getConfig().getString(root + "item.material", "GOLDEN_AXE"), Material.GOLDEN_AXE);
        wandName = plugin.getConfig().getString(root + "item.name", "&6WorldEdit-Service");
        wandLore = plugin.getConfig().getStringList(root + "item.lore");
        wandCustomModelData = plugin.getConfig().getInt(root + "item.custom-model-data", 0);
        successCommands = plugin.getConfig().getStringList(root + "commands-after-payment");
        loadModes(root + "modes.");
        loadConfirmGui();
        setupEconomy();
    }

    private void loadModes(final String root) {
        modes.clear();
        loadMode("air", root + "air.", true, Material.AIR, "");
        loadMode("water", root + "water.", true, Material.WATER, "");
        loadMode("lava", root + "lava.", true, Material.LAVA, "");
    }

    private void loadMode(
            final String id,
            final String path,
            final boolean fallbackEnabled,
            final Material fallbackMaterial,
            final String fallbackPermission
    ) {
        final boolean modeEnabled = plugin.getConfig().getBoolean(path + "enabled", fallbackEnabled);
        final Material targetMaterial = material(plugin.getConfig().getString(path + "target-material", fallbackMaterial.name()), fallbackMaterial);
        final String modePermission = plugin.getConfig().getString(path + "permission", fallbackPermission);
        modes.put(id, new WorldEditMode(id, modeEnabled, targetMaterial, modePermission));
    }

    private void loadConfirmGui() {
        guiConfig = loadExtrasGuiConfig();
        confirmSize = normalizeSize(guiConfig.getInt("worldedit-confirm.size", 27));
        confirmTitle = guiConfig.getString("worldedit-confirm.title", "&8WorldEdit-Service");
        confirmSelectedText = guiConfig.getString("worldedit-confirm.selected-text", "&aAusgewählt");
        confirmNotSelectedText = guiConfig.getString("worldedit-confirm.not-selected-text", "&7Nicht ausgewählt");
        confirmFiller = loadConfirmButton("worldedit-confirm.filler.", true, 0, Material.BLACK_STAINED_GLASS_PANE, "&r", Collections.emptyList(), "");
        confirmInfoButton = loadConfirmButton("worldedit-confirm.buttons.info.", true, 4, Material.PAPER, "&eAuswahl", Arrays.asList(
                "&7Blöcke: &f{blocks}",
                "&7Kosten: &f{price}",
                "&7Aktion: &f{mode}"
        ), "");
        confirmAcceptButton = loadConfirmButton("worldedit-confirm.buttons.confirm.", true, 21, Material.LIME_CONCRETE, "&aBestätigen", Arrays.asList(
                "&7Zieht &f{price} &7ab",
                "&7und setzt &f{mode}&7."
        ), "");
        confirmCancelButton = loadConfirmButton("worldedit-confirm.buttons.cancel.", true, 23, Material.RED_CONCRETE, "&cAbbrechen", Arrays.asList(
                "&7Bricht den Vorgang ab."
        ), "");
        for (final Map.Entry<String, WorldEditMode> entry : new ArrayList<>(modes.entrySet())) {
            final String id = entry.getKey();
            final WorldEditMode mode = entry.getValue();
            final ConfirmButton button = loadConfirmButton(
                    "worldedit-confirm.buttons." + id + ".",
                    true,
                    "water".equals(id) ? 13 : "lava".equals(id) ? 15 : 11,
                    "water".equals(id) ? Material.WATER_BUCKET : "lava".equals(id) ? Material.LAVA_BUCKET : Material.WHITE_STAINED_GLASS,
                    modeName(id),
                    Arrays.asList("&7Status: {selected}", "&7Klick wählt diese Aktion."),
                    ""
            );
            modes.put(id, mode.withButton(button));
        }
    }

    private YamlConfiguration loadExtrasGuiConfig() {
        final String language = languageManager.getDefaultLanguage();
        final String menuFile = plugin.getConfig().getString("gui.extras-menu", "extras.yml");
        final File localized = new File(plugin.getDataFolder(), "gui/" + language + "/" + menuFile);
        if (localized.exists()) {
            return YamlConfiguration.loadConfiguration(localized);
        }
        final File fallback = new File(plugin.getDataFolder(), "gui/de/" + menuFile);
        if (fallback.exists()) {
            return YamlConfiguration.loadConfiguration(fallback);
        }
        return new YamlConfiguration();
    }

    private ConfirmButton loadConfirmButton(
            final String path,
            final boolean fallbackEnabled,
            final int fallbackSlot,
            final Material fallbackMaterial,
            final String fallbackName,
            final List<String> fallbackLore,
            final String fallbackPermission
    ) {
        final boolean buttonEnabled = guiConfig.getBoolean(path + "enabled", fallbackEnabled);
        final int slot = guiConfig.getInt(path + "slot", fallbackSlot);
        final Material buttonMaterial = material(guiConfig.getString(path + "material", fallbackMaterial.name()), fallbackMaterial);
        final String buttonName = guiConfig.getString(path + "name", fallbackName);
        final List<String> buttonLore = guiConfig.contains(path + "lore") ? guiConfig.getStringList(path + "lore") : fallbackLore;
        final String buttonPermission = guiConfig.getString(path + "permission", fallbackPermission);
        return new ConfirmButton(buttonEnabled, slot, buttonMaterial, buttonName, buttonLore, buttonPermission);
    }

    public void runCommand(final Player player, final String payload) {
        if ("worldedit-service".equalsIgnoreCase(payload) || "worldedit".equalsIgnoreCase(payload)) {
            giveWorldEditWand(player);
            return;
        }
        languageManager.send(player, "chat-input-invalid");
    }

    public void giveWorldEditWand(final Player player) {
        if (!enabled || !worldEditServiceEnabled) {
            languageManager.send(player, "extras-worldedit-disabled");
            return;
        }
        if (!permission.trim().isEmpty() && !player.hasPermission(permission)) {
            languageManager.send(player, "no-permission");
            return;
        }
        final ItemStack item = createWandItem(player);
        final Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        languageManager.send(player, "extras-worldedit-given", placeholders(player, 0L, 0.0D, false, 0L));
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null || !isWand(event.getItem())) {
            return;
        }
        final Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        final Player player = event.getPlayer();
        if (!enabled || !worldEditServiceEnabled) {
            languageManager.send(player, "extras-worldedit-disabled");
            return;
        }
        if (!permission.trim().isEmpty() && !player.hasPermission(permission)) {
            languageManager.send(player, "no-permission");
            return;
        }

        final Location location = event.getClickedBlock().getLocation();
        final Optional<PlotContext> optionalContext = flagService.currentPlotContext(player);
        if (!optionalContext.isPresent() || !optionalContext.get().isComplete()) {
            languageManager.send(player, "extras-worldedit-no-plot");
            return;
        }
        final PlotContext context = optionalContext.get();
        if (requireOwnPlot && !player.hasPermission(bypassPlotPermission)
                && (context.getOwnerUuid() == null || !context.getOwnerUuid().equals(player.getUniqueId()))) {
            languageManager.send(player, "extras-worldedit-not-owner", contextPlaceholders(context));
            return;
        }
        if (!isAllowedPlotBlock(context, location)) {
            languageManager.send(player, "extras-worldedit-outside-plot", contextPlaceholders(context));
            return;
        }

        final Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection(plotKey(context)));
        if (!selection.getPlotKey().equals(plotKey(context))) {
            selection.clear(plotKey(context));
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            selection.setFirst(location);
            languageManager.send(player, "extras-worldedit-pos1", locationPlaceholders(location));
        } else {
            selection.setSecond(location);
            languageManager.send(player, "extras-worldedit-pos2", locationPlaceholders(location));
        }
        if (selection.isComplete()) {
            processSelection(player, context, selection);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
        pendingActions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WorldEditConfirmHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        final PendingWorldEditAction action = pendingActions.get(player.getUniqueId());
        if (action == null) {
            player.closeInventory();
            languageManager.send(player, "extras-worldedit-no-pending");
            return;
        }
        final int slot = event.getSlot();
        for (final WorldEditMode mode : modes.values()) {
            if (!canUseMode(player, mode)) {
                continue;
            }
            final ConfirmButton button = mode.getButton();
            if (button != null && button.isEnabled() && button.getSlot() == slot) {
                action.setModeId(mode.getId());
                openConfirmation(player, action);
                return;
            }
        }
        if (isButtonClick(player, confirmAcceptButton, slot)) {
            player.closeInventory();
            confirmAction(player);
            return;
        }
        if (isButtonClick(player, confirmCancelButton, slot)) {
            pendingActions.remove(player.getUniqueId());
            player.closeInventory();
            languageManager.send(player, "extras-worldedit-cancelled");
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WorldEditConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void processSelection(final Player player, final PlotContext context, final Selection selection) {
        final Location first = selection.getFirst();
        final Location second = selection.getSecond();
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            languageManager.send(player, "extras-worldedit-different-world");
            return;
        }
        if (!isAllowedPlotBlock(context, first) || !isAllowedPlotBlock(context, second)) {
            languageManager.send(player, "extras-worldedit-outside-plot", contextPlaceholders(context));
            return;
        }

        final long rawBlocks = volume(first, second);
        if (rawBlocks > maxSelectionBlocks) {
            final Map<String, String> placeholders = placeholders(player, rawBlocks, 0.0D, isOnCooldown(player.getUniqueId()), cooldownRemaining(player.getUniqueId()));
            placeholders.put("max_blocks", String.valueOf(maxSelectionBlocks));
            languageManager.send(player, "extras-worldedit-too-large", placeholders);
            return;
        }
        final long blocks = allowedSelectionBlocks(first, second, context);
        if (blocks < 0L) {
            languageManager.send(player, "extras-worldedit-outside-plot", contextPlaceholders(context));
            return;
        }
        if (blocks == 0L) {
            languageManager.send(player, "extras-worldedit-no-editable-blocks", contextPlaceholders(context));
            return;
        }
        if (!isWorldEditAvailable()) {
            languageManager.send(player, "extras-worldedit-worldedit-missing");
            return;
        }

        final Optional<WorldEditMode> defaultMode = firstAvailableMode(player);
        if (!defaultMode.isPresent()) {
            languageManager.send(player, "extras-worldedit-mode-unavailable");
            return;
        }

        final PendingWorldEditAction action = new PendingWorldEditAction(
                plotKey(context),
                first.clone(),
                second.clone(),
                blocks,
                defaultMode.get().getId()
        );
        pendingActions.put(player.getUniqueId(), action);
        selections.remove(player.getUniqueId());
        languageManager.send(player, "extras-worldedit-confirm-opened", confirmationPlaceholders(player, action, defaultMode.get(), currentPricing(player, blocks)));
        openConfirmation(player, action);
    }

    private void openConfirmation(final Player player, final PendingWorldEditAction action) {
        final WorldEditMode selectedMode = selectedMode(player, action);
        if (selectedMode == null) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-mode-unavailable");
            return;
        }
        final Pricing pricing = currentPricing(player, action.getBlocks());
        final Map<String, String> placeholders = confirmationPlaceholders(player, action, selectedMode, pricing);
        final Inventory inventory = Bukkit.createInventory(
                new WorldEditConfirmHolder(),
                confirmSize,
                Text.color(apply(confirmTitle, placeholders))
        );
        if (confirmFiller != null && confirmFiller.isEnabled()) {
            final ItemStack fillerItem = createConfirmItem(confirmFiller, placeholders);
            for (int slot = 0; slot < confirmSize; slot++) {
                inventory.setItem(slot, fillerItem);
            }
        }
        placeConfirmButton(player, inventory, confirmInfoButton, placeholders);
        for (final WorldEditMode mode : modes.values()) {
            if (!canUseMode(player, mode)) {
                continue;
            }
            final Map<String, String> modePlaceholders = new HashMap<>(placeholders);
            modePlaceholders.put("mode", modeName(mode.getId()));
            modePlaceholders.put("target_material", mode.getTargetMaterial().name());
            modePlaceholders.put("selected", mode.getId().equalsIgnoreCase(selectedMode.getId())
                    ? confirmSelectedText
                    : confirmNotSelectedText);
            placeConfirmButton(player, inventory, mode.getButton(), modePlaceholders);
        }
        placeConfirmButton(player, inventory, confirmAcceptButton, placeholders);
        placeConfirmButton(player, inventory, confirmCancelButton, placeholders);
        player.openInventory(inventory);
    }

    private void placeConfirmButton(
            final Player player,
            final Inventory inventory,
            final ConfirmButton button,
            final Map<String, String> placeholders
    ) {
        if (button == null || !button.isEnabled() || !canSee(player, button)) {
            return;
        }
        if (button.getSlot() < 0 || button.getSlot() >= inventory.getSize()) {
            plugin.getLogger().warning("WorldEdit-Bestätigungsbutton hat einen ungültigen Slot: " + button.getSlot());
            return;
        }
        inventory.setItem(button.getSlot(), createConfirmItem(button, placeholders));
    }

    private ItemStack createConfirmItem(final ConfirmButton button, final Map<String, String> placeholders) {
        final Material itemMaterial = button.getMaterial() == Material.AIR ? Material.BARRIER : button.getMaterial();
        final ItemStack item = new ItemStack(itemMaterial);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(apply(button.getName(), placeholders)));
            meta.setLore(Text.color(apply(button.getLore(), placeholders)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void confirmAction(final Player player) {
        final PendingWorldEditAction action = pendingActions.get(player.getUniqueId());
        if (action == null) {
            languageManager.send(player, "extras-worldedit-no-pending");
            return;
        }
        final WorldEditMode mode = selectedMode(player, action);
        if (mode == null) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-mode-unavailable");
            return;
        }
        final Optional<PlotContext> optionalContext = flagService.currentPlotContext(player);
        if (!optionalContext.isPresent() || !optionalContext.get().isComplete()) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-no-plot");
            return;
        }
        final PlotContext context = optionalContext.get();
        if (!action.getPlotKey().equals(plotKey(context))) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-no-plot");
            return;
        }
        if (requireOwnPlot && !player.hasPermission(bypassPlotPermission)
                && (context.getOwnerUuid() == null || !context.getOwnerUuid().equals(player.getUniqueId()))) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-not-owner", contextPlaceholders(context));
            return;
        }
        final Location first = action.getFirst();
        final Location second = action.getSecond();
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-different-world");
            return;
        }
        final long editableBlocks = allowedSelectionBlocks(first, second, context);
        if (!isAllowedPlotBlock(context, first) || !isAllowedPlotBlock(context, second)
                || editableBlocks < 0L) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-outside-plot", contextPlaceholders(context));
            return;
        }
        if (editableBlocks == 0L) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-no-editable-blocks", contextPlaceholders(context));
            return;
        }
        action.setBlocks(editableBlocks);
        if (!isWorldEditAvailable()) {
            pendingActions.remove(player.getUniqueId());
            languageManager.send(player, "extras-worldedit-worldedit-missing");
            return;
        }

        final Pricing pricing = currentPricing(player, action.getBlocks());
        final Map<String, String> placeholders = confirmationPlaceholders(player, action, mode, pricing);
        if (!withdraw(player, pricing.getPrice(), placeholders)) {
            return;
        }

        try {
            final WorldEditSelectionAdapter adapter = new WorldEditSelectionAdapter();
            adapter.setSelection(player, first, second);
            adapter.setBlocks(first, second, mode.getTargetMaterial());
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.WARNING, "WorldEdit-Service konnte die Auswahl nicht setzen.", exception);
            languageManager.send(player, "extras-worldedit-failed", placeholders);
            return;
        }

        if (pricing.isUsesIncludedPackage()) {
            setIncludedPackageCooldown(player.getUniqueId());
            placeholders.put("cooldown", "Ja");
            placeholders.put("cooldown_remaining", formatDuration(cooldownRemaining(player.getUniqueId())));
        }
        runSuccessCommands(player, first, second, placeholders);
        pendingActions.remove(player.getUniqueId());
        languageManager.send(player, "extras-worldedit-applied", placeholders);
    }

    private boolean isButtonClick(final Player player, final ConfirmButton button, final int slot) {
        return button != null && button.isEnabled() && button.getSlot() == slot && canSee(player, button);
    }

    private boolean canSee(final Player player, final ConfirmButton button) {
        final String buttonPermission = button.getPermission();
        return buttonPermission == null || buttonPermission.trim().isEmpty() || player.hasPermission(buttonPermission.trim());
    }

    private Optional<WorldEditMode> firstAvailableMode(final Player player) {
        for (final WorldEditMode mode : modes.values()) {
            if (canUseMode(player, mode)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    private WorldEditMode selectedMode(final Player player, final PendingWorldEditAction action) {
        WorldEditMode mode = modes.get(action.getModeId());
        if (canUseMode(player, mode)) {
            return mode;
        }
        final Optional<WorldEditMode> fallback = firstAvailableMode(player);
        if (!fallback.isPresent()) {
            return null;
        }
        mode = fallback.get();
        action.setModeId(mode.getId());
        return mode;
    }

    private boolean canUseMode(final Player player, final WorldEditMode mode) {
        if (mode == null || !mode.isEnabled() || mode.getButton() == null || !mode.getButton().isEnabled()) {
            return false;
        }
        final String modePermission = mode.getPermission();
        if (modePermission != null && !modePermission.trim().isEmpty() && !player.hasPermission(modePermission.trim())) {
            return false;
        }
        return canSee(player, mode.getButton());
    }

    private Pricing currentPricing(final Player player, final long blocks) {
        final boolean onCooldown = isOnCooldown(player.getUniqueId());
        final boolean free = player.hasPermission(freePermission);
        final boolean usesIncludedPackage = !free && !onCooldown && includedBlocks > 0L;
        final double currentPrice = free ? 0.0D : price(blocks, onCooldown);
        return new Pricing(currentPrice, onCooldown, cooldownRemaining(player.getUniqueId()), usesIncludedPackage);
    }

    private boolean withdraw(final Player player, final double price, final Map<String, String> placeholders) {
        if (price <= 0.0D || !economyEnabled) {
            return true;
        }
        if (economy == null || economyHasMethod == null || economyWithdrawMethod == null) {
            languageManager.send(player, "extras-worldedit-economy-missing", placeholders);
            return false;
        }
        try {
            final Boolean hasMoney = (Boolean) economyHasMethod.invoke(economy, player, price);
            if (hasMoney == null || !hasMoney) {
                languageManager.send(player, "extras-worldedit-no-money", placeholders);
                return false;
            }
            final Object response = economyWithdrawMethod.invoke(economy, player, price);
            final Method successMethod = response.getClass().getMethod("transactionSuccess");
            final Object success = successMethod.invoke(response);
            if (!(success instanceof Boolean) || !(Boolean) success) {
                languageManager.send(player, "extras-worldedit-no-money", placeholders);
                return false;
            }
            return true;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "WorldEdit-Service konnte kein Geld abbuchen.", exception);
            languageManager.send(player, "extras-worldedit-economy-missing", placeholders);
            return false;
        }
    }

    private boolean isWorldEditAvailable() {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit");
            Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class.forName("com.sk89q.worldedit.regions.selector.CuboidRegionSelector");
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    private void setupEconomy() {
        economy = null;
        economyHasMethod = null;
        economyWithdrawMethod = null;
        economyFormatMethod = null;
        if (!economyEnabled) {
            return;
        }
        try {
            final Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            final RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                return;
            }
            economy = registration.getProvider();
            economyHasMethod = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            economyWithdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            economyFormatMethod = economyClass.getMethod("format", double.class);
        } catch (final ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().fine("Vault-Economy ist nicht verfügbar: " + exception.getMessage());
        }
    }

    private ItemStack createWandItem(final Player player) {
        final ItemStack item = new ItemStack(wandMaterial);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Map<String, String> placeholders = placeholders(player, 0L, 0.0D, isOnCooldown(player.getUniqueId()), cooldownRemaining(player.getUniqueId()));
            meta.setDisplayName(Text.color(apply(wandName, placeholders)));
            meta.setLore(Text.color(apply(wandLore, placeholders)));
            if (wandCustomModelData > 0) {
                meta.setCustomModelData(wandCustomModelData);
            }
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, WAND_ID);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isWand(final ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && WAND_ID.equals(meta.getPersistentDataContainer().get(wandKey, PersistentDataType.STRING));
    }

    private double price(final long blocks, final boolean onCooldown) {
        if (onCooldown || includedBlocks <= 0L) {
            return blocks * extraPricePerBlock;
        }
        final long extraBlocks = Math.max(0L, blocks - includedBlocks);
        return includedPrice + (extraBlocks * extraPricePerBlock);
    }

    private long volume(final Location first, final Location second) {
        final long width = Math.abs(first.getBlockX() - second.getBlockX()) + 1L;
        final long height = Math.abs(first.getBlockY() - second.getBlockY()) + 1L;
        final long depth = Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L;
        if (width > Long.MAX_VALUE / Math.max(1L, height)) {
            return Long.MAX_VALUE;
        }
        final long area = width * height;
        if (area > Long.MAX_VALUE / Math.max(1L, depth)) {
            return Long.MAX_VALUE;
        }
        return area * depth;
    }

    private long allowedSelectionBlocks(final Location first, final Location second, final PlotContext context) {
        if (first.getWorld() == null) {
            return -1L;
        }
        final String worldName = first.getWorld().getName();
        final int minX = Math.min(first.getBlockX(), second.getBlockX());
        final int maxX = Math.max(first.getBlockX(), second.getBlockX());
        final int minY = Math.min(first.getBlockY(), second.getBlockY());
        final int maxY = Math.max(first.getBlockY(), second.getBlockY());
        final int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        final int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        final List<Long> xBounds = new ArrayList<>(Arrays.asList((long) minX, (long) maxX + 1L));
        final List<Long> yBounds = new ArrayList<>(Arrays.asList((long) minY, (long) maxY + 1L));
        final List<Long> zBounds = new ArrayList<>(Arrays.asList((long) minZ, (long) maxZ + 1L));
        boolean hasIntersectingRegion = false;
        for (final PlotRegion region : context.getRegions()) {
            if (!region.getWorldName().equalsIgnoreCase(worldName)) {
                continue;
            }
            final int regionMinX = Math.max(minX, region.getMinX());
            final int regionMaxX = Math.min(maxX, region.getMaxX());
            final int regionMinY = Math.max(minY, region.getMinY());
            final int regionMaxY = Math.min(maxY, region.getMaxY());
            final int regionMinZ = Math.max(minZ, region.getMinZ());
            final int regionMaxZ = Math.min(maxZ, region.getMaxZ());
            if (regionMinX > regionMaxX || regionMinY > regionMaxY || regionMinZ > regionMaxZ) {
                continue;
            }
            hasIntersectingRegion = true;
            addBounds(xBounds, regionMinX, regionMaxX);
            addBounds(yBounds, regionMinY, regionMaxY);
            addBounds(zBounds, regionMinZ, regionMaxZ);
        }
        if (!hasIntersectingRegion) {
            return -1L;
        }

        Collections.sort(xBounds);
        Collections.sort(yBounds);
        Collections.sort(zBounds);
        long covered = 0L;
        for (int xi = 0; xi < xBounds.size() - 1; xi++) {
            final long xStart = xBounds.get(xi);
            final long xEnd = xBounds.get(xi + 1);
            if (xStart >= xEnd) {
                continue;
            }
            for (int yi = 0; yi < yBounds.size() - 1; yi++) {
                final long yStart = yBounds.get(yi);
                final long yEnd = yBounds.get(yi + 1);
                if (yStart >= yEnd) {
                    continue;
                }
                for (int zi = 0; zi < zBounds.size() - 1; zi++) {
                    final long zStart = zBounds.get(zi);
                    final long zEnd = zBounds.get(zi + 1);
                    if (zStart >= zEnd) {
                        continue;
                    }
                    if (!isAllowedPlotBlock(context, worldName, (int) xStart, (int) yStart, (int) zStart)) {
                        return -1L;
                    }
                    covered += (xEnd - xStart) * (yEnd - yStart) * (zEnd - zStart);
                }
            }
        }
        return covered == volume(first, second) ? covered : -1L;
    }

    private void addBounds(final List<Long> bounds, final int min, final int max) {
        addUniqueBound(bounds, min);
        addUniqueBound(bounds, (long) max + 1L);
    }

    private void addUniqueBound(final List<Long> bounds, final long value) {
        if (!bounds.contains(value)) {
            bounds.add(value);
        }
    }

    private boolean isAllowedPlotBlock(final PlotContext context, final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return isAllowedPlotBlock(context, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private boolean isAllowedPlotBlock(final PlotContext context, final String worldName, final int x, final int y, final int z) {
        for (final PlotRegion region : context.getRegions()) {
            if (region.contains(worldName, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnCooldown(final UUID playerUuid) {
        if (cooldownMillis <= 0L) {
            return false;
        }
        final long lastUse = data.getLong(playerPath(playerUuid) + ".last-included-use", 0L);
        return lastUse > 0L && System.currentTimeMillis() - lastUse < cooldownMillis;
    }

    private long cooldownRemaining(final UUID playerUuid) {
        if (cooldownMillis <= 0L) {
            return 0L;
        }
        final long lastUse = data.getLong(playerPath(playerUuid) + ".last-included-use", 0L);
        return Math.max(0L, cooldownMillis - (System.currentTimeMillis() - lastUse));
    }

    private void setIncludedPackageCooldown(final UUID playerUuid) {
        data.set(playerPath(playerUuid) + ".last-included-use", System.currentTimeMillis());
        save();
    }

    private String playerPath(final UUID playerUuid) {
        return "players." + playerUuid + ".worldedit-service";
    }

    private void runSuccessCommands(
            final Player player,
            final Location first,
            final Location second,
            final Map<String, String> placeholders
    ) {
        final Map<String, String> extended = new HashMap<>(placeholders);
        extended.put("world", first.getWorld() == null ? "" : first.getWorld().getName());
        extended.put("x1", String.valueOf(first.getBlockX()));
        extended.put("y1", String.valueOf(first.getBlockY()));
        extended.put("z1", String.valueOf(first.getBlockZ()));
        extended.put("x2", String.valueOf(second.getBlockX()));
        extended.put("y2", String.valueOf(second.getBlockY()));
        extended.put("z2", String.valueOf(second.getBlockZ()));
        for (final String configuredCommand : successCommands) {
            final String command = strip(apply(configuredCommand, extended));
            if (command.isEmpty()) {
                continue;
            }
            if (command.toLowerCase(Locale.ROOT).startsWith("console:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(command.substring("console:".length()).trim()));
            } else if (command.toLowerCase(Locale.ROOT).startsWith("player:")) {
                player.performCommand(strip(command.substring("player:".length()).trim()));
            } else if (command.toLowerCase(Locale.ROOT).startsWith("op:")) {
                final boolean wasOp = player.isOp();
                try {
                    player.setOp(true);
                    player.performCommand(strip(command.substring("op:".length()).trim()));
                } finally {
                    player.setOp(wasOp);
                }
            } else {
                player.performCommand(command);
            }
        }
    }

    private Map<String, String> placeholders(
            final Player player,
            final long blocks,
            final double price,
            final boolean cooldown,
            final long cooldownRemainingMillis
    ) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("blocks", String.valueOf(blocks));
        placeholders.put("included_blocks", String.valueOf(includedBlocks));
        placeholders.put("extra_blocks", String.valueOf(Math.max(0L, blocks - includedBlocks)));
        placeholders.put("included_price", formatMoney(includedPrice));
        placeholders.put("extra_price_per_block", formatMoney(extraPricePerBlock));
        placeholders.put("price", formatMoney(price));
        placeholders.put("cooldown", cooldown ? "Ja" : "Nein");
        placeholders.put("cooldown_remaining", formatDuration(cooldownRemainingMillis));
        return placeholders;
    }

    private Map<String, String> confirmationPlaceholders(
            final Player player,
            final PendingWorldEditAction action,
            final WorldEditMode mode,
            final Pricing pricing
    ) {
        final Map<String, String> placeholders = placeholders(
                player,
                action.getBlocks(),
                pricing.getPrice(),
                pricing.isOnCooldown(),
                pricing.getCooldownRemainingMillis()
        );
        placeholders.put("mode", modeName(mode.getId()));
        placeholders.put("mode_id", mode.getId());
        placeholders.put("target_material", mode.getTargetMaterial().name());
        placeholders.put("x1", String.valueOf(action.getFirst().getBlockX()));
        placeholders.put("y1", String.valueOf(action.getFirst().getBlockY()));
        placeholders.put("z1", String.valueOf(action.getFirst().getBlockZ()));
        placeholders.put("x2", String.valueOf(action.getSecond().getBlockX()));
        placeholders.put("y2", String.valueOf(action.getSecond().getBlockY()));
        placeholders.put("z2", String.valueOf(action.getSecond().getBlockZ()));
        placeholders.put("world", action.getFirst().getWorld() == null ? "" : action.getFirst().getWorld().getName());
        return placeholders;
    }

    private Map<String, String> contextPlaceholders(final PlotContext context) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", context.getWorldName());
        placeholders.put("plot", context.getPlotId());
        placeholders.put("owner", context.getOwnerName());
        placeholders.put("merge", context.getMergeType());
        return placeholders;
    }

    private Map<String, String> locationPlaceholders(final Location location) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("world", location.getWorld() == null ? "-" : location.getWorld().getName());
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        return placeholders;
    }

    private String plotKey(final PlotContext context) {
        return context.getWorldName() + ";" + String.join("+", context.getPlotIds());
    }

    private String formatMoney(final double amount) {
        if (economy != null && economyFormatMethod != null) {
            try {
                final Object formatted = economyFormatMethod.invoke(economy, amount);
                if (formatted != null) {
                    return formatted.toString();
                }
            } catch (final IllegalAccessException | InvocationTargetException ignored) {
                // Fall back to plain formatting.
            }
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private String formatDuration(final long millis) {
        if (millis <= 0L) {
            return "0m";
        }
        final Duration duration = Duration.ofMillis(millis);
        final long days = duration.toDays();
        final long hours = duration.minusDays(days).toHours();
        final long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    private int normalizeSize(final int configuredSize) {
        final int clamped = Math.max(9, Math.min(54, configuredSize));
        return ((clamped + 8) / 9) * 9;
    }

    private String modeName(final String id) {
        if ("water".equalsIgnoreCase(id)) {
            return guiConfig.getString("worldedit-confirm.mode-names.water", "&bWasser");
        }
        if ("lava".equalsIgnoreCase(id)) {
            return guiConfig.getString("worldedit-confirm.mode-names.lava", "&6Lava");
        }
        return guiConfig.getString("worldedit-confirm.mode-names.air", "&fLuft");
    }

    private Material material(final String configured, final Material fallback) {
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        final String normalized = configured.trim().toUpperCase(Locale.ROOT).replace("PLAYERHEAD", "PLAYER_HEAD");
        final Material material = Material.matchMaterial(normalized);
        return material == null ? fallback : material;
    }

    private String apply(final String text, final Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private List<String> apply(final List<String> lines, final Map<String, String> placeholders) {
        final List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (final String line : lines) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    private String strip(final String command) {
        final String stripped = ChatColor.stripColor(command == null ? "" : command).trim();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    private void save() {
        plugin.getStorageService().save("extras", dataFile, data);
    }

    private static final class Selection {

        private String plotKey;
        private Location first;
        private Location second;

        private Selection(final String plotKey) {
            this.plotKey = plotKey;
        }

        private String getPlotKey() {
            return plotKey;
        }

        private Location getFirst() {
            return first;
        }

        private void setFirst(final Location first) {
            this.first = first;
        }

        private Location getSecond() {
            return second;
        }

        private void setSecond(final Location second) {
            this.second = second;
        }

        private boolean isComplete() {
            return first != null && second != null;
        }

        private void clear(final String plotKey) {
            this.plotKey = plotKey;
            first = null;
            second = null;
        }
    }

    private static final class PendingWorldEditAction {

        private final String plotKey;
        private final Location first;
        private final Location second;
        private long blocks;
        private String modeId;

        private PendingWorldEditAction(
                final String plotKey,
                final Location first,
                final Location second,
                final long blocks,
                final String modeId
        ) {
            this.plotKey = plotKey;
            this.first = first;
            this.second = second;
            this.blocks = blocks;
            this.modeId = modeId;
        }

        private String getPlotKey() {
            return plotKey;
        }

        private Location getFirst() {
            return first;
        }

        private Location getSecond() {
            return second;
        }

        private long getBlocks() {
            return blocks;
        }

        private void setBlocks(final long blocks) {
            this.blocks = blocks;
        }

        private String getModeId() {
            return modeId;
        }

        private void setModeId(final String modeId) {
            this.modeId = modeId;
        }
    }

    private static final class WorldEditMode {

        private final String id;
        private final boolean enabled;
        private final Material targetMaterial;
        private final String permission;
        private final ConfirmButton button;

        private WorldEditMode(
                final String id,
                final boolean enabled,
                final Material targetMaterial,
                final String permission
        ) {
            this(id, enabled, targetMaterial, permission, null);
        }

        private WorldEditMode(
                final String id,
                final boolean enabled,
                final Material targetMaterial,
                final String permission,
                final ConfirmButton button
        ) {
            this.id = id;
            this.enabled = enabled;
            this.targetMaterial = targetMaterial;
            this.permission = permission == null ? "" : permission;
            this.button = button;
        }

        private String getId() {
            return id;
        }

        private boolean isEnabled() {
            return enabled;
        }

        private Material getTargetMaterial() {
            return targetMaterial;
        }

        private String getPermission() {
            return permission;
        }

        private ConfirmButton getButton() {
            return button;
        }

        private WorldEditMode withButton(final ConfirmButton button) {
            return new WorldEditMode(id, enabled, targetMaterial, permission, button);
        }
    }

    private static final class ConfirmButton {

        private final boolean enabled;
        private final int slot;
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final String permission;

        private ConfirmButton(
                final boolean enabled,
                final int slot,
                final Material material,
                final String name,
                final List<String> lore,
                final String permission
        ) {
            this.enabled = enabled;
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore == null ? Collections.emptyList() : lore;
            this.permission = permission == null ? "" : permission;
        }

        private boolean isEnabled() {
            return enabled;
        }

        private int getSlot() {
            return slot;
        }

        private Material getMaterial() {
            return material;
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

    private static final class Pricing {

        private final double price;
        private final boolean onCooldown;
        private final long cooldownRemainingMillis;
        private final boolean usesIncludedPackage;

        private Pricing(
                final double price,
                final boolean onCooldown,
                final long cooldownRemainingMillis,
                final boolean usesIncludedPackage
        ) {
            this.price = price;
            this.onCooldown = onCooldown;
            this.cooldownRemainingMillis = cooldownRemainingMillis;
            this.usesIncludedPackage = usesIncludedPackage;
        }

        private double getPrice() {
            return price;
        }

        private boolean isOnCooldown() {
            return onCooldown;
        }

        private long getCooldownRemainingMillis() {
            return cooldownRemainingMillis;
        }

        private boolean isUsesIncludedPackage() {
            return usesIncludedPackage;
        }
    }

    private static final class WorldEditConfirmHolder implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
