package de.craftplay.plotextras.language;

import de.craftplay.plotextras.player.PlayerDataManager;
import de.craftplay.plotextras.util.TextUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LanguageManager {

    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final Map<String, LanguageDefinition> languages = new LinkedHashMap<>();

    public LanguageManager(final JavaPlugin plugin, final PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public void load() {
        languages.clear();
        final File languageFolder = new File(plugin.getDataFolder(), "language");
        if (!languageFolder.exists() && !languageFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create language folder.");
        }

        final File[] files = languageFolder.listFiles((folder, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (final File file : files) {
            final String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase();
            final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            final String name = configuration.getString("meta.name", code);
            final String nativeName = configuration.getString("meta.native-name", name);
            languages.put(code, new LanguageDefinition(
                    code,
                    name,
                    nativeName,
                    configuration,
                    configuration.getConfigurationSection("meta.item")
            ));
        }
    }

    public Collection<LanguageDefinition> getLanguages() {
        return Collections.unmodifiableCollection(languages.values());
    }

    public boolean hasLanguage(final String language) {
        return language != null && languages.containsKey(language.toLowerCase());
    }

    public String getDefaultLanguage() {
        final String configured = plugin.getConfig().getString("language.default", "de").toLowerCase();
        if (languages.containsKey(configured)) {
            return configured;
        }
        return languages.isEmpty() ? configured : languages.keySet().iterator().next();
    }

    public String getPlayerLanguage(final Player player) {
        return playerDataManager.getLanguage(player.getUniqueId())
                .filter(languages::containsKey)
                .orElseGet(this::getDefaultLanguage);
    }

    public boolean setPlayerLanguage(final Player player, final String language) {
        if (!hasLanguage(language)) {
            return false;
        }
        playerDataManager.setLanguage(player.getUniqueId(), language);
        return true;
    }

    public LanguageDefinition getLanguage(final String code) {
        return languages.get(code.toLowerCase());
    }

    public String getRawMessage(final Player player, final String key) {
        return getRawMessage(getPlayerLanguage(player), key);
    }

    public String getString(final Player player, final String path, final String fallback) {
        return getString(getPlayerLanguage(player), path, fallback);
    }

    public String getString(final String language, final String path, final String fallback) {
        final LanguageDefinition selected = languages.getOrDefault(language.toLowerCase(), languages.get(getDefaultLanguage()));
        if (selected != null) {
            final String value = selected.configuration().getString(path);
            if (value != null) {
                return value;
            }
        }

        final LanguageDefinition defaultLanguage = languages.get(getDefaultLanguage());
        if (defaultLanguage != null) {
            return defaultLanguage.configuration().getString(path, fallback);
        }
        return fallback;
    }

    public String getRawMessage(final String language, final String key) {
        final LanguageDefinition selected = languages.getOrDefault(language.toLowerCase(), languages.get(getDefaultLanguage()));
        if (selected == null) {
            return key;
        }

        final String path = key.startsWith("messages.") ? key : "messages." + key;
        final String value = selected.configuration().getString(path);
        if (value != null) {
            return value;
        }

        final LanguageDefinition fallback = languages.get(getDefaultLanguage());
        if (fallback != null) {
            return fallback.configuration().getString(path, key);
        }
        return key;
    }

    public void send(final Player player, final String key) {
        send(player, key, Collections.emptyMap());
    }

    public void send(final Player player, final String key, final Map<String, String> placeholders) {
        player.sendMessage(TextUtil.component(format(getRawMessage(player, key), placeholders)));
    }

    public String format(final String text, final Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        final Map<String, String> safePlaceholders = new HashMap<>(placeholders);
        for (final Map.Entry<String, String> entry : safePlaceholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
