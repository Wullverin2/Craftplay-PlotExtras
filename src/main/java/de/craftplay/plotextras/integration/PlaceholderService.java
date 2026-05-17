package de.craftplay.plotextras.integration;

import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.language.LanguageManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class PlaceholderService {

    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final FeatureToggleService featureToggleService;
    private boolean placeholderApiAvailable;
    private Method setPlaceholdersMethod;

    public PlaceholderService(final JavaPlugin plugin, final LanguageManager languageManager, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.featureToggleService = featureToggleService;
    }

    public void reload() {
        placeholderApiAvailable = featureToggleService.isEnabled("integrations.placeholder-api")
                && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        setPlaceholdersMethod = null;
        if (!placeholderApiAvailable) {
            return;
        }

        try {
            final Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = placeholderApiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (final ReflectiveOperationException exception) {
            placeholderApiAvailable = false;
            plugin.getLogger().log(Level.WARNING, "PlaceholderAPI was found but could not be hooked.", exception);
        }
    }

    public String apply(final Player player, final String text, final Map<String, String> placeholders) {
        String result = replaceBracedPlaceholders(text, placeholders);
        result = replaceIntegrationAliases(player, result);
        return applyPlaceholderApi(player, result);
    }

    public Map<String, String> getIntegrationPlaceholders(final Player player) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("jobs", featureToggleService.isEnabled("integrations.jobs") ? resolveConfiguredPlaceholder(player, "jobs") : fallback("jobs"));
        placeholders.put("cmi_money", featureToggleService.isEnabled("integrations.cmi") ? resolveConfiguredPlaceholder(player, "cmi-money") : fallback("cmi-money"));
        placeholders.put("quests_completed", featureToggleService.isEnabled("integrations.quests") ? resolveConfiguredPlaceholder(player, "quests-completed") : fallback("quests-completed"));
        placeholders.put("quests_total", featureToggleService.isEnabled("integrations.quests") ? resolveConfiguredPlaceholder(player, "quests-total") : fallback("quests-total"));
        return placeholders;
    }

    private String replaceBracedPlaceholders(final String text, final Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private String replaceIntegrationAliases(final Player player, final String text) {
        String result = text;
        for (final Map.Entry<String, String> entry : getIntegrationPlaceholders(player).entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String resolveConfiguredPlaceholder(final Player player, final String key) {
        final String placeholder = plugin.getConfig().getString("integrations.placeholder-values." + key, "");
        final String fallback = fallback(key);
        if (placeholder == null || placeholder.isBlank()) {
            return fallback;
        }
        final String resolved = applyPlaceholderApi(player, placeholder);
        if (!placeholderApiAvailable || resolved.equals(placeholder)) {
            return fallback;
        }
        return resolved;
    }

    private String fallback(final String key) {
        return plugin.getConfig().getString("integrations.placeholder-fallbacks." + key, "-");
    }

    private String applyPlaceholderApi(final Player player, final String text) {
        if (!placeholderApiAvailable || setPlaceholdersMethod == null || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        try {
            return String.valueOf(setPlaceholdersMethod.invoke(null, player, text));
        } catch (final ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not resolve PlaceholderAPI placeholders.", exception);
            return text;
        }
    }

    public String message(final Player player, final String key, final Map<String, String> placeholders) {
        return apply(player, languageManager.getRawMessage(player, key), placeholders);
    }
}
