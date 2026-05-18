package de.craftplay.plotextras.language;

import de.craftplay.plotextras.CraftplayPlotExtrasPlugin;
import de.craftplay.plotextras.util.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LanguageManager {

    private static final String FALLBACK_LANGUAGE = "de";

    private final CraftplayPlotExtrasPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();

    private String defaultLanguage = FALLBACK_LANGUAGE;

    public LanguageManager(final CraftplayPlotExtrasPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        defaultLanguage = normalize(plugin.getConfig().getString("language.default", FALLBACK_LANGUAGE));
        languages.clear();

        final File languageFolder = new File(plugin.getDataFolder(), "language");
        if (!languageFolder.exists() && !languageFolder.mkdirs()) {
            plugin.getLogger().warning("Der Sprachordner konnte nicht erstellt werden: " + languageFolder.getPath());
        }

        loadLanguage(FALLBACK_LANGUAGE);
        loadLanguage(defaultLanguage);
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String getMessage(final String key) {
        return Text.color(raw(defaultLanguage, key));
    }

    public void send(final CommandSender sender, final String key) {
        sender.sendMessage(getMessage(key));
    }

    private String raw(final String language, final String key) {
        final YamlConfiguration configuration = languages.computeIfAbsent(normalize(language), this::loadLanguage);
        if (configuration.contains(key)) {
            return configuration.getString(key, key);
        }

        final YamlConfiguration fallback = languages.computeIfAbsent(FALLBACK_LANGUAGE, this::loadLanguage);
        return fallback.getString(key, key);
    }

    private YamlConfiguration loadLanguage(final String language) {
        final File file = new File(plugin.getDataFolder(), "language/" + normalize(language) + ".yml");
        if (!file.exists()) {
            final String resourcePath = "language/" + normalize(language) + ".yml";
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private String normalize(final String language) {
        if (language == null || language.trim().isEmpty()) {
            return FALLBACK_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
