package de.craftplay.plotextras.myplots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlotDataStore {

    private final JavaPlugin plugin;
    private YamlConfiguration configuration;
    private String dataFile;
    private BukkitTask saveTask;
    private boolean dirty;

    public PlotDataStore(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        flushSave();
        dataFile = plugin.getConfig().getString("my-plots.data-file", "plotdata.yml");
        configuration = ((de.craftplay.plotextras.CraftplayPlotExtrasPlugin) plugin)
                .getStorageService()
                .load("plotdata", dataFile);
        dirty = false;
        if (configuration.getInt("file-version", 0) < 1) {
            configuration.set("file-version", 1);
            dirty = true;
            saveNow();
        }
    }

    public void shutdown() {
        flushSave();
    }

    public PlotMetadata metadata(final String plotKey) {
        final String path = plotPath(plotKey);
        return new PlotMetadata(
                configuration.getString(path + ".category", ""),
                configuration.getStringList(path + ".tags"),
                configuration.getString(path + ".visibility", "auto"),
                configuration.getString(path + ".note", ""),
                configuration.getDouble(path + ".rating", 0.0D),
                configuration.getInt(path + ".rating-count", ratingCount(plotKey)),
                likeCount(plotKey),
                commentCount(plotKey),
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

    public void addTags(final String plotKey, final Collection<String> newTags) {
        if (newTags == null || newTags.isEmpty()) {
            return;
        }
        final String path = plotPath(plotKey) + ".tags";
        final Set<String> tags = new LinkedHashSet<>(configuration.getStringList(path));
        for (final String tag : newTags) {
            if (tag != null && !tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        configuration.set(path, new ArrayList<>(tags));
        save();
    }

    public void setNote(final String plotKey, final String note) {
        configuration.set(plotPath(plotKey) + ".note", note == null ? "" : note);
        save();
    }

    public void recordVisit(final String plotKey) {
        final String path = plotPath(plotKey);
        configuration.set(path + ".visits", configuration.getInt(path + ".visits", 0) + 1);
        configuration.set(path + ".last-visit", System.currentTimeMillis());
        save();
    }

    public void clearActivity(final String plotKey) {
        final String path = plotPath(plotKey);
        configuration.set(path + ".visits", 0);
        configuration.set(path + ".last-visit", 0L);
        save();
    }

    public boolean hasLike(final UUID playerUuid, final String plotKey) {
        if (playerUuid == null) {
            return false;
        }
        return configuration.getStringList(plotPath(plotKey) + ".likes").contains(playerUuid.toString());
    }

    public boolean toggleLike(final UUID playerUuid, final String playerName, final String plotKey) {
        if (playerUuid == null) {
            return false;
        }
        final String path = plotPath(plotKey);
        final List<String> likes = new ArrayList<>(configuration.getStringList(path + ".likes"));
        final String uuid = playerUuid.toString();
        final boolean liked;
        if (likes.contains(uuid)) {
            likes.remove(uuid);
            configuration.set(path + ".like-names." + uuid, null);
            liked = false;
        } else {
            likes.add(uuid);
            configuration.set(path + ".like-names." + uuid, cleanName(playerName));
            liked = true;
        }
        configuration.set(path + ".likes", likes);
        save();
        return liked;
    }

    public int likeCount(final String plotKey) {
        return configuration.getStringList(plotPath(plotKey) + ".likes").size();
    }

    public void setRating(final UUID playerUuid, final String playerName, final String plotKey, final int rating) {
        if (playerUuid == null) {
            return;
        }
        final int value = Math.max(1, Math.min(5, rating));
        final String path = plotPath(plotKey) + ".ratings." + playerUuid;
        configuration.set(path + ".name", cleanName(playerName));
        configuration.set(path + ".value", value);
        configuration.set(path + ".created", System.currentTimeMillis());
        updateRatingSummary(plotKey);
        save();
    }

    public double averageRating(final String plotKey) {
        return calculateRatingSummary(plotKey)[0] / 100.0D;
    }

    public int ratingCount(final String plotKey) {
        return (int) calculateRatingSummary(plotKey)[1];
    }

    public void addComment(final UUID playerUuid, final String playerName, final String plotKey, final String message) {
        final String cleanedMessage = message == null ? "" : message.trim();
        if (cleanedMessage.isEmpty()) {
            return;
        }
        final String commentsPath = plotPath(plotKey) + ".comments";
        long id = System.currentTimeMillis();
        while (configuration.contains(commentsPath + "." + id)) {
            id++;
        }
        final String path = commentsPath + "." + id;
        configuration.set(path + ".author-uuid", playerUuid == null ? "" : playerUuid.toString());
        configuration.set(path + ".author-name", cleanName(playerName));
        configuration.set(path + ".message", cleanedMessage);
        configuration.set(path + ".created", System.currentTimeMillis());
        save();
    }

    public List<PlotComment> comments(final String plotKey) {
        final ConfigurationSection section = configuration.getConfigurationSection(plotPath(plotKey) + ".comments");
        if (section == null) {
            return Collections.emptyList();
        }
        final List<PlotComment> comments = new ArrayList<>();
        for (final String id : section.getKeys(false)) {
            final String path = section.getCurrentPath() + "." + id;
            UUID authorUuid = null;
            final String rawUuid = configuration.getString(path + ".author-uuid", "");
            if (rawUuid != null && !rawUuid.trim().isEmpty()) {
                try {
                    authorUuid = UUID.fromString(rawUuid);
                } catch (final IllegalArgumentException ignored) {
                    authorUuid = null;
                }
            }
            comments.add(new PlotComment(
                    id,
                    authorUuid,
                    configuration.getString(path + ".author-name", ""),
                    configuration.getString(path + ".message", ""),
                    configuration.getLong(path + ".created", 0L)
            ));
        }
        comments.sort(Comparator.comparingLong(PlotComment::getCreatedAt).reversed());
        return comments;
    }

    public int commentCount(final String plotKey) {
        final ConfigurationSection section = configuration.getConfigurationSection(plotPath(plotKey) + ".comments");
        return section == null ? 0 : section.getKeys(false).size();
    }

    private void updateRatingSummary(final String plotKey) {
        final long[] summary = calculateRatingSummary(plotKey);
        configuration.set(plotPath(plotKey) + ".rating", summary[0] / 100.0D);
        configuration.set(plotPath(plotKey) + ".rating-count", (int) summary[1]);
    }

    private long[] calculateRatingSummary(final String plotKey) {
        final ConfigurationSection section = configuration.getConfigurationSection(plotPath(plotKey) + ".ratings");
        if (section == null) {
            return new long[]{0L, 0L};
        }
        int total = 0;
        int count = 0;
        for (final String uuid : section.getKeys(false)) {
            final int rating = configuration.getInt(section.getCurrentPath() + "." + uuid + ".value", 0);
            if (rating < 1 || rating > 5) {
                continue;
            }
            total += rating;
            count++;
        }
        if (count <= 0) {
            return new long[]{0L, 0L};
        }
        return new long[]{Math.round((total / (double) count) * 100.0D), count};
    }

    private String cleanName(final String playerName) {
        return playerName == null || playerName.trim().isEmpty() ? "Unbekannt" : playerName.trim();
    }

    private String plotPath(final String plotKey) {
        return "plots." + safeKey(plotKey);
    }

    private String safeKey(final String plotKey) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(plotKey.getBytes(StandardCharsets.UTF_8));
    }

    private void save() {
        dirty = true;
        if (saveTask != null) {
            return;
        }
        final long delay = Math.max(1L, plugin.getConfig().getLong("my-plots.save-delay-ticks", 40L));
        saveTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::saveNow, delay);
    }

    private void flushSave() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveNow();
    }

    private void saveNow() {
        if (configuration == null || dataFile == null) {
            return;
        }
        if (!dirty && saveTask == null) {
            return;
        }
        ((de.craftplay.plotextras.CraftplayPlotExtrasPlugin) plugin)
                .getStorageService()
                .save("plotdata", dataFile, configuration);
        dirty = false;
        saveTask = null;
    }
}
