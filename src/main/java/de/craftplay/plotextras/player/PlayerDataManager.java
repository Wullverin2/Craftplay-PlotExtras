package de.craftplay.plotextras.player;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public PlayerDataManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/players.yml");
    }

    public void load() {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Could not create data folder for player settings.");
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public Optional<String> getLanguage(final UUID playerId) {
        if (data == null) {
            load();
        }
        final String language = data.getString("players." + playerId + ".language");
        return language == null || language.isBlank() ? Optional.empty() : Optional.of(language.toLowerCase());
    }

    public void setLanguage(final UUID playerId, final String language) {
        if (data == null) {
            load();
        }
        data.set("players." + playerId + ".language", language.toLowerCase());
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save player language data.", exception);
        }
    }
}
