package de.craftplay.plotextras.plotpurchase;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.plotsquared.PlotSquaredPlotService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotPurchaseService {

    private static final int NO_WEIGHT = Integer.MIN_VALUE;

    private final CraftplayPlotExtrasPlugin plugin;
    private final PlotSquaredPlotService plotService;
    private final Map<UUID, Integer> selectedAmounts = new HashMap<>();

    private boolean enabled;
    private boolean logging;
    private boolean economyEnabled;
    private String purchasePermission;
    private String plotPermissionPrefix;
    private String currencyName;
    private int baseFreePlots;
    private int defaultSelection;
    private int maximumSelection;
    private double pricePerPlot;

    private Object economy;
    private Method economyHasMethod;
    private Method economyWithdrawMethod;
    private Method economyDepositMethod;
    private Method economyFormatMethod;

    private Object permissionProvider;
    private Method permissionPlayerAddMethod;
    private Method permissionPlayerRemoveMethod;
    private Method permissionGetPlayerGroupsMethod;

    private Object luckPermsUserManager;
    private Object luckPermsGroupManager;

    public PlotPurchaseService(final CraftplayPlotExtrasPlugin plugin, final PlotSquaredPlotService plotService) {
        this.plugin = plugin;
        this.plotService = plotService;
    }

    public void reload() {
        selectedAmounts.clear();

        enabled = plugin.getConfig().getBoolean("plot-purchase.enabled", true);
        logging = plugin.getConfig().getBoolean("plot-purchase.logging", true);
        economyEnabled = plugin.getConfig().getBoolean("plot-purchase.economy.enabled", true);
        purchasePermission = plugin.getConfig().getString("plot-purchase.purchase-permission", "craftplayplotextras.plotbuy");
        plotPermissionPrefix = plugin.getConfig().getString("plot-purchase.plot-permission-prefix", "plots.plot.");
        currencyName = plugin.getConfig().getString("plot-purchase.currency-name", "Taler");
        baseFreePlots = Math.max(0, plugin.getConfig().getInt("plot-purchase.base-free-plots", 0));
        defaultSelection = Math.max(1, plugin.getConfig().getInt("plot-purchase.default-selection", 1));
        maximumSelection = Math.max(defaultSelection, plugin.getConfig().getInt("plot-purchase.maximum-selection", 100));
        pricePerPlot = Math.max(0.0D, plugin.getConfig().getDouble("plot-purchase.plot-price", 50000.0D));

        setupEconomy();
        setupPermissions();
        setupLuckPerms();
    }

    public void shutdown() {
        selectedAmounts.clear();
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
        final int selected = selectedAmount(player);
        final int freePlots = freePlots(player);
        final int permissionLimit = highestDirectPlayerPlotPermissionLimit(player);
        final int claimedPlots = plotService.ownedPlots(player).size();
        final int allowedPlots = Math.max(Math.max(freePlots, permissionLimit), claimedPlots);
        final int purchasedPlots = Math.max(0, permissionLimit - freePlots);
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

        final int newAllowedPlots = before.getAllowedPlots() + amount;
        if (!applyPlotPermissionLimit(player, newAllowedPlots)) {
            refund(player, before.getTotalPrice());
            plugin.getLanguageManager().send(player, "plotbuy-permission-update-failed", placeholders);
            return false;
        }

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

    private int freePlots(final Player player) {
        int plots = baseFreePlots;
        final int weightedRankPlots = highestWeightedGroupPlotLimit(player);
        if (weightedRankPlots >= 0) {
            return Math.max(plots, weightedRankPlots);
        }
        return Math.max(plots, highestPlotPermissionLimit(player));
    }

    private boolean applyPlotPermissionLimit(final Player player, final int targetLimit) {
        if (permissionProvider == null || permissionPlayerAddMethod == null || permissionPlayerRemoveMethod == null) {
            return false;
        }
        final String worldName = player.getWorld().getName();
        final List<Integer> limits = directPlayerPlotPermissionLimits(player);
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

    private int highestDirectPlayerPlotPermissionLimit(final Player player) {
        int highest = 0;
        for (final int limit : directPlayerPlotPermissionLimits(player)) {
            highest = Math.max(highest, limit);
        }
        return highest;
    }

    private int highestWeightedGroupPlotLimit(final Player player) {
        int bestWeight = NO_WEIGHT;
        int bestLimit = -1;
        for (final String groupName : playerGroupNames(player)) {
            final int groupLimit = groupPlotPermissionLimit(groupName);
            if (groupLimit < 0) {
                continue;
            }
            final int groupWeight = luckPermsGroupWeight(groupName);
            if (groupWeight > bestWeight || (groupWeight == bestWeight && groupLimit > bestLimit)) {
                bestWeight = groupWeight;
                bestLimit = groupLimit;
            }
        }
        return bestLimit;
    }

    private List<Integer> directPlayerPlotPermissionLimits(final Player player) {
        final Object user = luckPermsUser(player);
        if (user == null) {
            return plotPermissionLimits(player);
        }
        return plotPermissionLimitsFromNodes(user);
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

    private int groupPlotPermissionLimit(final String groupName) {
        if (groupName == null || groupName.trim().isEmpty() || luckPermsGroupManager == null) {
            return -1;
        }
        try {
            final Method getGroupMethod = luckPermsGroupManager.getClass().getMethod("getGroup", String.class);
            final Object group = getGroupMethod.invoke(luckPermsGroupManager, groupName.trim());
            if (group == null) {
                return -1;
            }
            int highest = -1;
            for (final int limit : plotPermissionLimitsFromNodes(group)) {
                highest = Math.max(highest, limit);
            }
            return highest;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().fine("Plotlimit der LuckPerms-Gruppe '" + groupName + "' konnte nicht gelesen werden: "
                    + exception.getMessage());
            return -1;
        }
    }

    private List<Integer> plotPermissionLimitsFromNodes(final Object nodeHolder) {
        final List<Integer> limits = new ArrayList<>();
        if (nodeHolder == null) {
            return limits;
        }
        try {
            final Method getNodesMethod = nodeHolder.getClass().getMethod("getNodes");
            final Object rawNodes = getNodesMethod.invoke(nodeHolder);
            if (!(rawNodes instanceof Iterable<?>)) {
                return limits;
            }
            for (final Object node : (Iterable<?>) rawNodes) {
                final Integer limit = plotPermissionLimitFromNode(node);
                if (limit != null && !limits.contains(limit)) {
                    limits.add(limit);
                }
            }
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().fine("LuckPerms-Plotlimit-Nodes konnten nicht gelesen werden: " + exception.getMessage());
        }
        return limits;
    }

    private Integer plotPermissionLimitFromNode(final Object node) {
        if (node == null) {
            return null;
        }
        try {
            final Method getValueMethod = node.getClass().getMethod("getValue");
            final Object value = getValueMethod.invoke(node);
            if (value instanceof Boolean && !(Boolean) value) {
                return null;
            }
        } catch (final ReflectiveOperationException ignored) {
            // LuckPerms permission nodes are positive by default; older APIs may not expose getValue on the implementation class.
        }
        try {
            final Method getKeyMethod = node.getClass().getMethod("getKey");
            final Object key = getKeyMethod.invoke(node);
            if (key == null) {
                return null;
            }
            final String permission = key.toString();
            if (!permission.toLowerCase(Locale.ROOT).startsWith(plotPermissionPrefix.toLowerCase(Locale.ROOT))) {
                return null;
            }
            final String rawValue = permission.substring(plotPermissionPrefix.length()).trim();
            final int limit = Integer.parseInt(rawValue);
            return limit >= 0 ? limit : null;
        } catch (final ReflectiveOperationException | NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> playerGroupNames(final Player player) {
        final Set<String> groups = new HashSet<>();
        if (permissionProvider != null) {
            if (permissionGetPlayerGroupsMethod != null) {
                try {
                    final Object result = permissionGetPlayerGroupsMethod.invoke(permissionProvider, player.getWorld().getName(), player);
                    if (result instanceof String[]) {
                        groups.addAll(Arrays.asList((String[]) result));
                    }
                } catch (final ReflectiveOperationException exception) {
                    plugin.getLogger().fine("Vault-Spielergruppen konnten nicht gelesen werden: " + exception.getMessage());
                }
            }
            try {
                final Method method = permissionProvider.getClass().getMethod("getPlayerGroups", Player.class);
                final Object result = method.invoke(permissionProvider, player);
                if (result instanceof String[]) {
                    groups.addAll(Arrays.asList((String[]) result));
                }
            } catch (final ReflectiveOperationException ignored) {
                // The world-aware Vault signature above is preferred.
            }
        }
        final Object user = luckPermsUser(player);
        if (user != null) {
            try {
                final Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
                final Object primaryGroup = getPrimaryGroupMethod.invoke(user);
                if (primaryGroup != null && !primaryGroup.toString().trim().isEmpty()) {
                    groups.add(primaryGroup.toString());
                }
            } catch (final ReflectiveOperationException ignored) {
                // Vault normally provides all groups; this is only a fallback.
            }
        }
        final List<String> result = new ArrayList<>();
        for (final String group : groups) {
            if (group != null && !group.trim().isEmpty()) {
                result.add(group.trim());
            }
        }
        return result;
    }

    private Object luckPermsUser(final Player player) {
        if (luckPermsUserManager == null || player == null) {
            return null;
        }
        try {
            final Method getUserMethod = luckPermsUserManager.getClass().getMethod("getUser", UUID.class);
            return getUserMethod.invoke(luckPermsUserManager, player.getUniqueId());
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().fine("LuckPerms-User fuer Plotkauf konnte nicht gelesen werden: " + exception.getMessage());
            return null;
        }
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
        return Math.max(0.0D, price);
    }

    private int luckPermsGroupWeight(final String groupName) {
        if (groupName == null || groupName.trim().isEmpty() || luckPermsGroupManager == null) {
            return NO_WEIGHT;
        }
        try {
            final Method getGroupMethod = luckPermsGroupManager.getClass().getMethod("getGroup", String.class);
            final Object group = getGroupMethod.invoke(luckPermsGroupManager, groupName.trim());
            if (group == null) {
                return NO_WEIGHT;
            }
            final Method getWeightMethod = group.getClass().getMethod("getWeight");
            final Object optionalWeight = getWeightMethod.invoke(group);
            if (optionalWeight == null) {
                return NO_WEIGHT;
            }
            final Method isPresentMethod = optionalWeight.getClass().getMethod("isPresent");
            final Object present = isPresentMethod.invoke(optionalWeight);
            if (!(present instanceof Boolean) || !(Boolean) present) {
                return NO_WEIGHT;
            }
            final Method getAsIntMethod = optionalWeight.getClass().getMethod("getAsInt");
            final Object weight = getAsIntMethod.invoke(optionalWeight);
            return weight instanceof Number ? ((Number) weight).intValue() : NO_WEIGHT;
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().fine("LuckPerms-Gewichtung fuer Gruppe '" + groupName + "' konnte nicht gelesen werden: "
                    + exception.getMessage());
            return NO_WEIGHT;
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
        permissionGetPlayerGroupsMethod = null;
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
            try {
                permissionGetPlayerGroupsMethod = permissionClass.getMethod("getPlayerGroups", String.class, OfflinePlayer.class);
            } catch (final NoSuchMethodException ignored) {
                permissionGetPlayerGroupsMethod = null;
            }
        } catch (final ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().fine("Vault-Permissions sind fuer Plotkaeufe nicht verfuegbar: " + exception.getMessage());
        }
    }

    private void setupLuckPerms() {
        luckPermsUserManager = null;
        luckPermsGroupManager = null;
        try {
            final Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            @SuppressWarnings({"rawtypes", "unchecked"})
            final RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) luckPermsClass);
            if (registration == null) {
                return;
            }
            final Object luckPerms = registration.getProvider();
            final Method getUserManagerMethod = luckPermsClass.getMethod("getUserManager");
            final Method getGroupManagerMethod = luckPermsClass.getMethod("getGroupManager");
            luckPermsUserManager = getUserManagerMethod.invoke(luckPerms);
            luckPermsGroupManager = getGroupManagerMethod.invoke(luckPerms);
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().fine("LuckPerms-Gruppengewichtungen sind fuer Plotkaeufe nicht verfuegbar: "
                    + exception.getMessage());
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
