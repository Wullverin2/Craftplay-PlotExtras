package de.craftplay.plotextras.extras;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.language.LanguageManager;
import de.craftplay.plotextras.plotsquared.PlotContext;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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

    private YamlConfiguration data;
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
        setupEconomy();
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
                && context.getOwnerUuid() != null
                && !context.getOwnerUuid().equals(player.getUniqueId())) {
            languageManager.send(player, "extras-worldedit-not-owner", contextPlaceholders(context));
            return;
        }
        if (!context.getBounds().contains(location)) {
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
    }

    private void processSelection(final Player player, final PlotContext context, final Selection selection) {
        final Location first = selection.getFirst();
        final Location second = selection.getSecond();
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            languageManager.send(player, "extras-worldedit-different-world");
            return;
        }
        if (!context.getBounds().contains(first) || !context.getBounds().contains(second)) {
            languageManager.send(player, "extras-worldedit-outside-plot", contextPlaceholders(context));
            return;
        }

        final long blocks = volume(first, second);
        if (blocks > maxSelectionBlocks) {
            final Map<String, String> placeholders = placeholders(player, blocks, 0.0D, isOnCooldown(player.getUniqueId()), cooldownRemaining(player.getUniqueId()));
            placeholders.put("max_blocks", String.valueOf(maxSelectionBlocks));
            languageManager.send(player, "extras-worldedit-too-large", placeholders);
            return;
        }
        if (!isWorldEditAvailable()) {
            languageManager.send(player, "extras-worldedit-worldedit-missing");
            return;
        }

        final boolean onCooldown = isOnCooldown(player.getUniqueId());
        final boolean free = player.hasPermission(freePermission);
        final boolean usesIncludedPackage = !free && !onCooldown && includedBlocks > 0L;
        final double price = free ? 0.0D : price(blocks, onCooldown);
        final Map<String, String> placeholders = placeholders(player, blocks, price, onCooldown, cooldownRemaining(player.getUniqueId()));
        if (!withdraw(player, price, placeholders)) {
            return;
        }

        new WorldEditSelectionAdapter().setSelection(player, first, second);
        if (usesIncludedPackage) {
            setIncludedPackageCooldown(player.getUniqueId());
            placeholders.put("cooldown", "Ja");
            placeholders.put("cooldown_remaining", formatDuration(cooldownRemaining(player.getUniqueId())));
        }
        runSuccessCommands(player, first, second, placeholders);
        selections.remove(player.getUniqueId());
        languageManager.send(player, "extras-worldedit-paid", placeholders);
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
}
