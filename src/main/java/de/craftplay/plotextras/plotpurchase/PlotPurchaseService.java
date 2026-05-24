package de.craftplay.plotextras.plotpurchase;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.plotsquared.PlotSquaredPlotService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotPurchaseService {

    private static final String NAMESPACE = "plotpurchase";

    private final CraftplayPlotExtrasPlugin plugin;
    private final PlotSquaredPlotService plotService;
    private final Map<UUID, Integer> selectedAmounts = new HashMap<>();
    private final Map<String, Integer> freePermissionLimits = new LinkedHashMap<>();

    private YamlConfiguration data;
    private String dataFile;
    private BukkitTask saveTask;
    private boolean dirty;

    private boolean enabled;
    private boolean logging;
    private boolean economyEnabled;
    private String purchasePermission;
    private String plotPermissionPrefix;
    private String currencyName;
    private int baseFreePlots;
    private int defaultSelection;
    private int maximumSelection;
    private long saveDelayTicks;
    private double pricePerPlot;

    private Object economy;
    private Method economyHasMethod;
    private Method economyWithdrawMethod;
    private Method economyDepositMethod;
    private Method economyFormatMethod;

    private Object permissionProvider;
    private Method permissionPlayerAddMethod;
    private Method permissionPlayerRemoveMethod;

    public PlotPurchaseService(final CraftplayPlotExtrasPlugin plugin, final PlotSquaredPlotService plotService) {
        this.plugin = plugin;
        this.plotService = plotService;
    }

    public void reload() {
        flushSave();
        selectedAmounts.clear();
        freePermissionLimits.clear();

        enabled = plugin.getConfig().getBoolean("plot-purchase.enabled", true);
        logging = plugin.getConfig().getBoolean("plot-purchase.logging", true);
        economyEnabled = plugin.getConfig().getBoolean("plot-purchase.economy.enabled", true);
        purchasePermission = plugin.getConfig().getString("plot-purchase.purchase-permission", "craftplayplotextras.plotbuy");
        plotPermissionPrefix = plugin.getConfig().getString("plot-purchase.plot-permission-prefix", "plots.plot.");
        currencyName = plugin.getConfig().getString("plot-purchase.currency-name", "Taler");
        baseFreePlots = Math.max(0, plugin.getConfig().getInt("plot-purchase.base-free-plots", 0));
        defaultSelection = Math.max(1, plugin.getConfig().getInt("plot-purchase.default-selection", 1));
        maximumSelection = Math.max(defaultSelection, plugin.getConfig().getInt("plot-purchase.maximum-selection", 100));
        saveDelayTicks = Math.max(1L, plugin.getConfig().getLong("plot-purchase.save-delay-ticks", 40L));
        pricePerPlot = Math.max(0.0D, plugin.getConfig().getDouble("plot-purchase.plot-price", 50000.0D));
        dataFile = plugin.getConfig().getString("plot-purchase.data-file", "plotpurchase.yml");

        loadFreePermissions();
        data = plugin.getStorageService().load(NAMESPACE, dataFile);
        dirty = false;
        if (data.getInt("file-version", 0) < 1) {
            data.set("file-version", 1);
            dirty = true;
            saveNow();
        }
        setupEconomy();
        setupPermissions();
    }

    public void shutdown() {
        flushSave();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canUse(final Player player) {
        return player != null && enabled && hasPurchasePermission(player);
    }

    public void adjustSelection(final Player player, final int delta) {
        final int current = selectedAmount(player);
        selectedAmounts.put(player.getUniqueId(), clampSelection(current + delta));
    }

    public int selectedAmount(final Player player) {
        final Integer selected = selectedAmounts.get(player.getUniqueId());
        if (selected != null) {
            return clampSelection(selected);
        }
        final int initial = clampSelection(defaultSelection);
        selectedAmounts.put(player.getUniqueId(), initial);
        return initial;
    }

    public PurchaseSnapshot snapshot(final Player player) {
        importLegacyPurchaseIfNeeded(player);
        synchronizeLimit(player);
        final int selected = selectedAmount(player);
        final int freePlots = freePlots(player);
        final int purchasedPlots = purchasedPlots(player.getUniqueId());
        final int allowedPlots = freePlots + purchasedPlots;
        final int permissionLimit = highestPlotPermissionLimit(player);
        final int claimedPlots = plotService.ownedPlots(player).size();
        final double singlePrice = discountedPricePerPlot(player);
        final double totalPrice = Math.max(0.0D, singlePrice * selected);
        return new PurchaseSnapshot(
                freePlots,
                purchasedPlots,
                allowedPlots,
                permissionLimit,
                claimedPlots,
                selected,
                allowedPlots + selected,
                singlePrice,
                totalPrice,
                formatMoney(singlePrice),
                formatMoney(totalPrice)
        );
    }

    public boolean purchase(final Player player) {
        if (!enabled) {
            plugin.getLanguageManager().send(player, "plotbuy-disabled");
            return false;
        }
        if (!hasPurchasePermission(player)) {
            plugin.getLanguageManager().send(player, "no-permission");
            return false;
        }

        final PurchaseSnapshot before = snapshot(player);
        final int amount = before.getSelectedPlots();
        if (amount <= 0) {
            plugin.getLanguageManager().send(player, "plotbuy-invalid-amount");
            return false;
        }

        final Map<String, String> placeholders = placeholders(player, before);
        if (!withdraw(player, before.getTotalPrice(), placeholders)) {
            return false;
        }

        final int newPurchasedPlots = before.getPurchasedPlots() + amount;
        final int newAllowedPlots = before.getFreePlots() + newPurchasedPlots;
        if (!applyPlotPermissionLimit(player, newAllowedPlots)) {
            refund(player, before.getTotalPrice());
            plugin.getLanguageManager().send(player, "plotbuy-permission-update-failed", placeholders);
            return false;
        }

        setPurchasedPlots(player.getUniqueId(), player.getName(), newPurchasedPlots);
        selectedAmounts.put(player.getUniqueId(), clampSelection(defaultSelection));
        final PurchaseSnapshot after = snapshot(player);
        final Map<String, String> successPlaceholders = placeholders(player, after);
        successPlaceholders.put("amount", String.valueOf(amount));
        successPlaceholders.put("price", before.getFormattedTotalPrice());
        successPlaceholders.put("before_allowed_plots", String.valueOf(before.getAllowedPlots()));
        successPlaceholders.put("after_allowed_plots", String.valueOf(newAllowedPlots));
        plugin.getLanguageManager().send(player, "plotbuy-success", successPlaceholders);
        if (logging) {
            plugin.getLogger().info(player.getName() + " hat " + amount + " Plotlimit-Erweiterung(en) fuer "
                    + before.getFormattedTotalPrice() + " gekauft. Neues Limit: " + newAllowedPlots);
        }
        return true;
    }

    public Map<String, String> placeholders(final Player player) {
        return placeholders(player, snapshot(player));
    }

    public Map<String, String> placeholders(final Player player, final PurchaseSnapshot snapshot) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("free_plots", String.valueOf(snapshot.getFreePlots()));
        placeholders.put("purchased_plots", String.valueOf(snapshot.getPurchasedPlots()));
        placeholders.put("allowed_plots", String.valueOf(snapshot.getAllowedPlots()));
        placeholders.put("permission_plots", String.valueOf(snapshot.getPermissionLimit()));
        placeholders.put("claimed_plots", String.valueOf(snapshot.getClaimedPlots()));
        placeholders.put("selected_plots", String.valueOf(snapshot.getSelectedPlots()));
        placeholders.put("amount", String.valueOf(snapshot.getSelectedPlots()));
        placeholders.put("after_allowed_plots", String.valueOf(snapshot.getAfterAllowedPlots()));
        placeholders.put("price_each", snapshot.getFormattedPricePerPlot());
        placeholders.put("price", snapshot.getFormattedTotalPrice());
        placeholders.put("total_price", snapshot.getFormattedTotalPrice());
        placeholders.put("raw_price_each", String.format(Locale.US, "%.2f", snapshot.getPricePerPlot()));
        placeholders.put("raw_price", String.format(Locale.US, "%.2f", snapshot.getTotalPrice()));
        placeholders.put("currency", currencyName);
        placeholders.put("plot_permission", plotPermissionPrefix + snapshot.getAllowedPlots());
        placeholders.put("after_plot_permission", plotPermissionPrefix + snapshot.getAfterAllowedPlots());
        return placeholders;
    }

    private void loadFreePermissions() {
        final ConfigurationSection section = plugin.getConfig().getConfigurationSection("plot-purchase.free-plot-permissions");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final String path = "plot-purchase.free-plot-permissions." + id + ".";
            final String permission = plugin.getConfig().getString(path + "permission", "");
            final int plots = Math.max(0, plugin.getConfig().getInt(path + "plots", 0));
            if (permission == null || permission.trim().isEmpty() || plots <= 0) {
                continue;
            }
            freePermissionLimits.put(permission.trim(), plots);
        }
    }

    private int freePlots(final Player player) {
        int plots = baseFreePlots;
        for (final Map.Entry<String, Integer> entry : freePermissionLimits.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                plots = Math.max(plots, entry.getValue());
            }
        }
        return plots;
    }

    private int purchasedPlots(final UUID uuid) {
        return Math.max(0, data.getInt(userPath(uuid) + ".purchased-plots", 0));
    }

    private void setPurchasedPlots(final UUID uuid, final String playerName, final int purchasedPlots) {
        final String path = userPath(uuid);
        data.set(path + ".name", playerName);
        data.set(path + ".purchased-plots", Math.max(0, purchasedPlots));
        data.set(path + ".updated", System.currentTimeMillis());
        save();
    }

    private void importLegacyPurchaseIfNeeded(final Player player) {
        final String path = userPath(player.getUniqueId());
        if (data.contains(path + ".purchased-plots")) {
            return;
        }
        final int currentLimit = highestPlotPermissionLimit(player);
        final int freePlots = freePlots(player);
        final int purchasedPlots = Math.max(0, currentLimit - freePlots);
        data.set(path + ".name", player.getName());
        data.set(path + ".purchased-plots", purchasedPlots);
        data.set(path + ".legacy-imported", true);
        data.set(path + ".legacy-permission-limit", currentLimit);
        data.set(path + ".updated", System.currentTimeMillis());
        save();
    }

    private boolean synchronizeLimit(final Player player) {
        final int targetLimit = freePlots(player) + purchasedPlots(player.getUniqueId());
        final int currentLimit = highestPlotPermissionLimit(player);
        if (currentLimit == targetLimit && player.hasPermission(plotPermissionPrefix + targetLimit)) {
            return true;
        }
        return applyPlotPermissionLimit(player, targetLimit);
    }

    private boolean applyPlotPermissionLimit(final Player player, final int targetLimit) {
        if (permissionProvider == null || permissionPlayerAddMethod == null || permissionPlayerRemoveMethod == null) {
            return false;
        }
        final String worldName = player.getWorld().getName();
        final List<Integer> limits = plotPermissionLimits(player);
        for (final int limit : limits) {
            if (limit == targetLimit) {
                continue;
            }
            invokePermission(permissionPlayerRemoveMethod, worldName, player, plotPermissionPrefix + limit);
        }
        if (targetLimit <= 0) {
            return true;
        }
        if (player.hasPermission(plotPermissionPrefix + targetLimit)) {
            return true;
        }
        return invokePermission(permissionPlayerAddMethod, worldName, player, plotPermissionPrefix + targetLimit);
    }

    private boolean invokePermission(
            final Method method,
            final String worldName,
            final OfflinePlayer player,
            final String permission
    ) {
        try {
            final Object result = method.invoke(permissionProvider, worldName, player, permission);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Plotlimit-Permission konnte nicht geaendert werden: " + permission, exception);
            return false;
        }
    }

    private int highestPlotPermissionLimit(final Player player) {
        int highest = 0;
        for (final int limit : plotPermissionLimits(player)) {
            highest = Math.max(highest, limit);
        }
        return highest;
    }

    private List<Integer> plotPermissionLimits(final Player player) {
        final List<Integer> limits = new ArrayList<>();
        for (final PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }
            final String permission = permissionInfo.getPermission();
            if (permission == null || !permission.toLowerCase(Locale.ROOT).startsWith(plotPermissionPrefix.toLowerCase(Locale.ROOT))) {
                continue;
            }
            final String rawValue = permission.substring(plotPermissionPrefix.length()).trim();
            try {
                final int value = Integer.parseInt(rawValue);
                if (value >= 0 && !limits.contains(value)) {
                    limits.add(value);
                }
            } catch (final NumberFormatException ignored) {
                // Wildcards and non-numeric permissions are intentionally ignored.
            }
        }
        return limits;
    }

    private boolean withdraw(final Player player, final double price, final Map<String, String> placeholders) {
        if (price <= 0.0D || !economyEnabled) {
            return true;
        }
        if (economy == null || economyHasMethod == null || economyWithdrawMethod == null) {
            plugin.getLanguageManager().send(player, "plotbuy-economy-missing", placeholders);
            return false;
        }
        try {
            final Object hasMoney = economyHasMethod.invoke(economy, player, price);
            if (hasMoney instanceof Boolean && !(Boolean) hasMoney) {
                plugin.getLanguageManager().send(player, "plotbuy-not-enough-money", placeholders);
                return false;
            }
            final Object response = economyWithdrawMethod.invoke(economy, player, price);
            final Method successMethod = response.getClass().getMethod("transactionSuccess");
            final Object success = successMethod.invoke(response);
            if (!(success instanceof Boolean) || !(Boolean) success) {
                plugin.getLanguageManager().send(player, "plotbuy-not-enough-money", placeholders);
                return false;
            }
            return true;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Plotkauf konnte kein Geld abbuchen.", exception);
            plugin.getLanguageManager().send(player, "plotbuy-economy-missing", placeholders);
            return false;
        }
    }

    private void refund(final Player player, final double price) {
        if (price <= 0.0D || !economyEnabled || economy == null || economyDepositMethod == null) {
            return;
        }
        try {
            economyDepositMethod.invoke(economy, player, price);
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Plotkauf-Rueckzahlung ist fehlgeschlagen.", exception);
        }
    }

    private double discountedPricePerPlot(final Player player) {
        double price = pricePerPlot;
        if (plugin.getConfig().getBoolean("plot-purchase.discounts.global.active", false)) {
            final double globalDiscount = plugin.getConfig().getDouble("plot-purchase.discounts.global.value-percent", 0.0D);
            price -= (globalDiscount / 100.0D) * price;
        }
        if (plugin.getConfig().getBoolean("plot-purchase.discounts.group.active", false)) {
            final int groups = Math.max(0, plugin.getConfig().getInt("plot-purchase.discounts.group.number-groups", 10));
            for (int index = 1; index <= groups; index++) {
                final String path = "plot-purchase.discounts.group.groups.group" + index + ".";
                final String groupName = plugin.getConfig().getString(path + "group-name", "");
                if (groupName == null || groupName.trim().isEmpty() || !playerInGroup(player, groupName.trim())) {
                    continue;
                }
                final double groupDiscount = plugin.getConfig().getDouble(path + "value-percent", 0.0D);
                price -= (groupDiscount / 100.0D) * price;
            }
        }
        return Math.max(0.0D, price);
    }

    private boolean playerInGroup(final Player player, final String groupName) {
        if (permissionProvider == null) {
            return false;
        }
        try {
            final Method method = permissionProvider.getClass().getMethod("playerInGroup", Player.class, String.class);
            final Object result = method.invoke(permissionProvider, player, groupName);
            return result instanceof Boolean && (Boolean) result;
        } catch (final ReflectiveOperationException ignored) {
            // Try the modern Vault signature below.
        }
        try {
            final Method method = permissionProvider.getClass().getMethod("playerInGroup", String.class, OfflinePlayer.class, String.class);
            final Object result = method.invoke(permissionProvider, player.getWorld().getName(), player, groupName);
            return result instanceof Boolean && (Boolean) result;
        } catch (final ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean hasPurchasePermission(final Player player) {
        return purchasePermission == null || purchasePermission.trim().isEmpty() || player.hasPermission(purchasePermission.trim());
    }

    private int clampSelection(final int amount) {
        return Math.max(1, Math.min(maximumSelection, amount));
    }

    private String formatMoney(final double amount) {
        if (economy != null && economyFormatMethod != null) {
            try {
                final Object result = economyFormatMethod.invoke(economy, amount);
                if (result != null && !result.toString().trim().isEmpty()) {
                    return result.toString();
                }
            } catch (final ReflectiveOperationException ignored) {
                // Fall back to a simple configured currency suffix.
            }
        }
        return String.format(Locale.US, "%.2f %s", amount, currencyName);
    }

    private void setupEconomy() {
        economy = null;
        economyHasMethod = null;
        economyWithdrawMethod = null;
        economyDepositMethod = null;
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
            economyDepositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            economyFormatMethod = economyClass.getMethod("format", double.class);
        } catch (final ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().fine("Vault-Economy ist fuer Plotkaeufe nicht verfuegbar: " + exception.getMessage());
        }
    }

    private void setupPermissions() {
        permissionProvider = null;
        permissionPlayerAddMethod = null;
        permissionPlayerRemoveMethod = null;
        try {
            final Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            @SuppressWarnings({"rawtypes", "unchecked"})
            final RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) permissionClass);
            if (registration == null) {
                return;
            }
            permissionProvider = registration.getProvider();
            permissionPlayerAddMethod = permissionClass.getMethod("playerAdd", String.class, OfflinePlayer.class, String.class);
            permissionPlayerRemoveMethod = permissionClass.getMethod("playerRemove", String.class, OfflinePlayer.class, String.class);
        } catch (final ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().fine("Vault-Permissions sind fuer Plotkaeufe nicht verfuegbar: " + exception.getMessage());
        }
    }

    private String userPath(final UUID uuid) {
        return "players." + uuid.toString();
    }

    private void save() {
        dirty = true;
        if (saveTask != null) {
            return;
        }
        saveTask = Bukkit.getScheduler().runTaskLater(plugin, this::flushSave, saveDelayTicks);
    }

    private void saveNow() {
        plugin.getStorageService().save(NAMESPACE, dataFile, data);
        dirty = false;
    }

    private void flushSave() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (dirty && data != null) {
            saveNow();
        }
    }

    public static final class PurchaseSnapshot {

        private final int freePlots;
        private final int purchasedPlots;
        private final int allowedPlots;
        private final int permissionLimit;
        private final int claimedPlots;
        private final int selectedPlots;
        private final int afterAllowedPlots;
        private final double pricePerPlot;
        private final double totalPrice;
        private final String formattedPricePerPlot;
        private final String formattedTotalPrice;

        private PurchaseSnapshot(
                final int freePlots,
                final int purchasedPlots,
                final int allowedPlots,
                final int permissionLimit,
                final int claimedPlots,
                final int selectedPlots,
                final int afterAllowedPlots,
                final double pricePerPlot,
                final double totalPrice,
                final String formattedPricePerPlot,
                final String formattedTotalPrice
        ) {
            this.freePlots = freePlots;
            this.purchasedPlots = purchasedPlots;
            this.allowedPlots = allowedPlots;
            this.permissionLimit = permissionLimit;
            this.claimedPlots = claimedPlots;
            this.selectedPlots = selectedPlots;
            this.afterAllowedPlots = afterAllowedPlots;
            this.pricePerPlot = pricePerPlot;
            this.totalPrice = totalPrice;
            this.formattedPricePerPlot = formattedPricePerPlot;
            this.formattedTotalPrice = formattedTotalPrice;
        }

        public int getFreePlots() {
            return freePlots;
        }

        public int getPurchasedPlots() {
            return purchasedPlots;
        }

        public int getAllowedPlots() {
            return allowedPlots;
        }

        public int getPermissionLimit() {
            return permissionLimit;
        }

        public int getClaimedPlots() {
            return claimedPlots;
        }

        public int getSelectedPlots() {
            return selectedPlots;
        }

        public int getAfterAllowedPlots() {
            return afterAllowedPlots;
        }

        public double getPricePerPlot() {
            return pricePerPlot;
        }

        public double getTotalPrice() {
            return totalPrice;
        }

        public String getFormattedPricePerPlot() {
            return formattedPricePerPlot;
        }

        public String getFormattedTotalPrice() {
            return formattedTotalPrice;
        }
    }
}
