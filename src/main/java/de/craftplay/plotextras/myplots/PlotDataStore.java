package de.craftplay.plotextras.myplots;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class PlotDataStore {

    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration configuration;

    public PlotDataStore(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), plugin.getConfig().getString("my-plots.data-file", "plotdata.yml"));
        if (!file.exists()) {
            createDefaultFile();
        }
        configuration = YamlConfiguration.loadConfiguration(file);
        if (configuration.getInt("file-version", 0) < 1) {
            configuration.set("file-version", 1);
            save();
        }
    }

    public PlotMetadata metadata(final String plotKey) {
        final String path = plotPath(plotKey);
        return new PlotMetadata(
                configuration.getString(path + ".category", ""),
                configuration.getStringList(path + ".tags"),
                configuration.getString(path + ".visibility", "auto"),
                configuration.getDouble(path + ".rating", 0.0D),
                configuration.getInt(path + ".visits", 0),
                configuration.getLong(path + ".last-visit", 0L)
        );
    }

    public boolean isFavorite(final UUID playerUuid, final String plotKey) {
        return configuration.getStringList(plotPath(plotKey) + ".favorites").contains(playerUuid.toString());
    }

    public boolean toggleFavorite(final UUID playerUuid, final String plotKey) {
        final String path = plotPath(plotKey) + ".favorites";
        final List<String> favorites = new ArrayList<>(configuration.getStringList(path));
        final String uuid = playerUuid.toString();
        final boolean favorite;
        if (favorites.contains(uuid)) {
            favorites.remove(uuid);
            favorite = false;
        } else {
            favorites.add(uuid);
            favorite = true;
        }
        configuration.set(path, favorites);
        save();
        return favorite;
    }

    public String cycleVisibility(final String plotKey) {
        final String path = plotPath(plotKey) + ".visibility";
        final String current = configuration.getString(path, "auto").toLowerCase(Locale.ROOT);
        final String next;
        if ("auto".equals(current)) {
            next = "public";
        } else if ("public".equals(current)) {
            next = "private";
        } else {
            next = "auto";
        }
        configuration.set(path, next);
        save();
        return next;
    }

    public String setNextCategory(final String plotKey, final List<String> categories, final String fallback) {
        final List<String> usable = categories == null || categories.isEmpty() ? new ArrayList<>() : categories;
        if (usable.isEmpty()) {
            configuration.set(plotPath(plotKey) + ".category", fallback);
            save();
            return fallback;
        }
        final String current = configuration.getString(plotPath(plotKey) + ".category", fallback);
        final int index = usable.indexOf(current);
        final String next = usable.get(index < 0 || index >= usable.size() - 1 ? 0 : index + 1);
        configuration.set(plotPath(plotKey) + ".category", next);
        save();
        return next;
    }

    public boolean toggleTag(final String plotKey, final String tag) {
        final String path = plotPath(plotKey) + ".tags";
        final List<String> tags = new ArrayList<>(configuration.getStringList(path));
        final boolean enabled;
        if (tags.contains(tag)) {
            tags.remove(tag);
            enabled = false;
        } else {
            tags.add(tag);
            enabled = true;
        }
        configuration.set(path, tags);
        save();
        return enabled;
    }

    public void recordVisit(final String plotKey) {
        final String path = plotPath(plotKey);
        configuration.set(path + ".visits", configuration.getInt(path + ".visits", 0) + 1);
        configuration.set(path + ".last-visit", System.currentTimeMillis());
        save();
    }

    private String plotPath(final String plotKey) {
        return "plots." + safeKey(plotKey);
    }

    private String safeKey(final String plotKey) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(plotKey.getBytes(StandardCharsets.UTF_8));
    }

    private void createDefaultFile() {
        final File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Plotdaten-Ordner konnte nicht erstellt werden: " + parent.getPath());
        }
        configuration = new YamlConfiguration();
        configuration.options().header("Daten für das Meine-Plots-Menü.\n"
                + "Hier speichert das Plugin Favoriten, Tags, Kategorien, Sichtbarkeit und Aktivität.\n"
                + "Diese Datei ist eine Datendatei und wird automatisch gepflegt.");
        configuration.set("file-version", 1);
        save();
    }

    private void save() {
        if (configuration == null || file == null) {
            return;
        }
        try {
            configuration.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plotdaten konnten nicht gespeichert werden: " + file.getPath(), exception);
        }
    }
}
