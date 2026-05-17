package de.craftplay.plotextras.utility;

import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotId;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import de.craftplay.plotextras.feature.FeatureToggleService;
import de.craftplay.plotextras.plot.PlotService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlotUtilityService {

    private final JavaPlugin plugin;
    private final PlotService plotService;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private final Set<UUID> builderMode = ConcurrentHashMap.newKeySet();
    private YamlConfiguration data;

    public PlotUtilityService(
            final JavaPlugin plugin,
            final PlotService plotService,
            final FeatureToggleService featureToggleService
    ) {
        this.plugin = plugin;
        this.plotService = plotService;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-utilities.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner fuer Plot-Utilities konnte nicht erstellt werden.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public Map<String, String> placeholders(final Plot plot) {
        final Map<String, String> placeholders = new HashMap<>();
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            placeholders.put("plot_description", "-");
            placeholders.put("plot_category", "-");
            placeholders.put("plot_tags", "-");
            placeholders.put("plot_access_mode", "normal");
            placeholders.put("plot_guestbook_entries", "0");
            placeholders.put("plot_open_requests", "0");
            return placeholders;
        }
        final String path = profilePath(basePlot);
        placeholders.put("plot_description", data.getString(path + ".description", "-"));
        placeholders.put("plot_category", data.getString(path + ".category", "-"));
        placeholders.put("plot_tags", String.join(", ", data.getStringList(path + ".tags")));
        placeholders.put("plot_access_mode", data.getString(path + ".access-mode", "normal"));
        placeholders.put("plot_locked_message", data.getString(path + ".locked-message", "-"));
        placeholders.put("plot_guestbook_entries", String.valueOf(guestbook(basePlot, Integer.MAX_VALUE).size()));
        placeholders.put("plot_open_requests", String.valueOf(listOpenRequests().stream()
                .filter(entry -> entry.plotKey().equals(plotKey(basePlot)))
                .count()));
        return placeholders;
    }

    public boolean canManageProfile(final Player player, final Plot plot) {
        return featureToggleService.isEnabled("player.plot-profile")
                && plotService.canManageRoles(player, plot);
    }

    public boolean setDescription(final Player player, final Plot plot, final String description) {
        if (!canManageProfile(player, plot)) {
            return false;
        }
        final String path = ensureProfile(plot);
        data.set(path + ".description", blank(description, "-"));
        save();
        return true;
    }

    public boolean setCategory(final Player player, final Plot plot, final String category) {
        if (!canManageProfile(player, plot)) {
            return false;
        }
        final String path = ensureProfile(plot);
        data.set(path + ".category", normalizeFreeText(category, "allgemein"));
        save();
        return true;
    }

    public boolean setTags(final Player player, final Plot plot, final String rawTags) {
        if (!canManageProfile(player, plot)) {
            return false;
        }
        final List<String> tags = new ArrayList<>();
        for (final String tag : rawTags.split(",")) {
            final String normalized = normalizeFreeText(tag, "");
            if (!normalized.isBlank() && !tags.contains(normalized)) {
                tags.add(normalized);
            }
        }
        final String path = ensureProfile(plot);
        data.set(path + ".tags", tags);
        save();
        return true;
    }

    public boolean setAccessMode(final Player player, final Plot plot, final String mode) {
        if (!canManageProfile(player, plot) || !featureToggleService.isEnabled("player.visit-mode")) {
            return false;
        }
        final String normalized = switch (normalizeFreeText(mode, "normal")) {
            case "public", "oeffentlich", "offen" -> "public";
            case "members", "member", "mitglieder" -> "members";
            case "friends", "freunde" -> "friends";
            case "private", "privat" -> "private";
            case "locked", "gesperrt" -> "locked";
            default -> "normal";
        };
        final String path = ensureProfile(plot);
        data.set(path + ".access-mode", normalized);
        save();
        return true;
    }

    public boolean setLockedMessage(final Player player, final Plot plot, final String message) {
        if (!canManageProfile(player, plot)) {
            return false;
        }
        final String path = ensureProfile(plot);
        data.set(path + ".locked-message", blank(message, "-"));
        save();
        return true;
    }

    public String accessMode(final Plot plot) {
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return "normal";
        }
        return data.getString(profilePath(basePlot) + ".access-mode", "normal").toLowerCase(Locale.ROOT);
    }

    public String lockedMessage(final Plot plot) {
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return "Dieses Plot ist aktuell nicht öffentlich.";
        }
        return data.getString(profilePath(basePlot) + ".locked-message", "Dieses Plot ist aktuell nicht öffentlich.");
    }

    public boolean canVisit(final Player player, final Plot plot) {
        final Plot basePlot = base(plot);
        if (player == null || basePlot == null || !featureToggleService.isEnabled("player.visit-mode")) {
            return true;
        }
        if (player.hasPermission("craftplayplotextras.visit.bypass") || player.hasPermission("craftplayplotextras.admin")) {
            return true;
        }

        final UUID playerId = player.getUniqueId();
        if (basePlot.isOwner(playerId)) {
            return true;
        }
        if (basePlot.getDenied().contains(playerId)) {
            return false;
        }

        final boolean trusted = basePlot.getTrusted().contains(playerId) || basePlot.getMembers().contains(playerId);
        return switch (accessMode(basePlot)) {
            case "private", "members", "friends" -> trusted;
            case "locked" -> false;
            default -> true;
        };
    }

    public boolean toggleFavorite(final Player player, final Plot plot) {
        if (player == null || base(plot) == null || !featureToggleService.isEnabled("player.plot-favorites")) {
            return false;
        }
        final String key = plotKey(plot);
        final String path = "favorites." + player.getUniqueId();
        final List<String> favorites = new ArrayList<>(data.getStringList(path));
        final boolean added;
        if (favorites.remove(key)) {
            added = false;
        } else {
            favorites.add(key);
            added = true;
        }
        data.set(path, favorites.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        save();
        return added;
    }

    public List<String> favorites(final Player player) {
        if (player == null) {
            return List.of();
        }
        return data.getStringList("favorites." + player.getUniqueId()).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public GuestbookEntry signGuestbook(final Player player, final Plot plot, final String message) {
        final Plot basePlot = base(plot);
        if (player == null || basePlot == null || !featureToggleService.isEnabled("player.guestbook")) {
            return null;
        }
        final String id = "guest-" + Long.toString(System.currentTimeMillis(), 36);
        final GuestbookEntry entry = new GuestbookEntry(
                id,
                Instant.now(),
                player.getUniqueId(),
                player.getName(),
                plotKey(basePlot),
                trimMessage(message, 160)
        );
        final String path = "guestbook." + sanitize(entry.plotKey()) + "." + entry.id();
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".player-uuid", entry.playerUuid().toString());
        data.set(path + ".player-name", entry.playerName());
        data.set(path + ".message", entry.message());
        save();
        return entry;
    }

    public List<GuestbookEntry> guestbook(final Plot plot, final int limit) {
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return List.of();
        }
        final ConfigurationSection section = data.getConfigurationSection("guestbook." + sanitize(plotKey(basePlot)));
        if (section == null) {
            return List.of();
        }
        final List<GuestbookEntry> entries = new ArrayList<>();
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                entries.add(new GuestbookEntry(
                        id,
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        UUID.fromString(entrySection.getString("player-uuid", new UUID(0L, 0L).toString())),
                        entrySection.getString("player-name", "-"),
                        plotKey(basePlot),
                        entrySection.getString("message", "-")
                ));
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungueltiger Gaestebuch-Eintrag ignoriert: " + id);
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(GuestbookEntry::createdAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public boolean canManageGuestbook(final Player player, final Plot plot) {
        return player != null && base(plot) != null
                && (plotService.canManageRoles(player, plot)
                || player.hasPermission("craftplayplotextras.guestbook.manage")
                || player.hasPermission("craftplayplotextras.admin"));
    }

    public boolean deleteGuestbookEntry(final Player player, final Plot plot, final String id) {
        final Plot basePlot = base(plot);
        if (basePlot == null || id == null || id.isBlank() || !canManageGuestbook(player, basePlot)) {
            return false;
        }
        final String path = "guestbook." + sanitize(plotKey(basePlot)) + "." + id;
        if (!data.contains(path)) {
            return false;
        }
        data.set(path, null);
        save();
        return true;
    }

    public UtilityRequestEntry createRequest(final Player player, final Plot plot, final String type, final String note) {
        final Plot basePlot = base(plot);
        if (player == null || basePlot == null || !featureToggleService.isEnabled("player.requests")) {
            return null;
        }
        final String normalizedType = normalizeRequestType(type);
        final String id = normalizedType + "-" + Long.toString(System.currentTimeMillis(), 36);
        final UtilityRequestEntry entry = new UtilityRequestEntry(
                id,
                normalizedType,
                "open",
                Instant.now(),
                player.getUniqueId(),
                player.getName(),
                basePlot.getWorldName(),
                basePlot.getId().toString(),
                plotKey(basePlot),
                ownerName(basePlot.getOwnerAbs()),
                trimMessage(note, 240),
                "-",
                null,
                "-"
        );
        saveRequest(entry);
        return entry;
    }

    public List<UtilityRequestEntry> listOpenRequests() {
        return listRequests().stream()
                .filter(entry -> entry.status().equalsIgnoreCase("open"))
                .sorted(Comparator.comparing(UtilityRequestEntry::createdAt).reversed())
                .toList();
    }

    public List<UtilityRequestEntry> listOwnRequests(final Player player) {
        if (player == null) {
            return List.of();
        }
        return listRequests().stream()
                .filter(entry -> entry.requesterUuid().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(UtilityRequestEntry::createdAt).reversed())
                .toList();
    }

    public List<UtilityRequestEntry> listRequests() {
        final ConfigurationSection section = data.getConfigurationSection("requests");
        if (section == null) {
            return List.of();
        }
        final List<UtilityRequestEntry> entries = new ArrayList<>();
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                final String handledAt = entrySection.getString("handled-at", "");
                entries.add(new UtilityRequestEntry(
                        id,
                        entrySection.getString("type", "support"),
                        entrySection.getString("status", "open"),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        UUID.fromString(entrySection.getString("requester-uuid", new UUID(0L, 0L).toString())),
                        entrySection.getString("requester-name", "-"),
                        entrySection.getString("world", "-"),
                        entrySection.getString("plot-id", "-"),
                        entrySection.getString("plot-key", "-"),
                        entrySection.getString("owner-name", "-"),
                        entrySection.getString("note", "-"),
                        entrySection.getString("handled-by", "-"),
                        handledAt == null || handledAt.isBlank() ? null : Instant.parse(handledAt),
                        entrySection.getString("response", "-")
                ));
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungueltige Plot-Anfrage ignoriert: " + id);
            }
        }
        return entries;
    }

    public UtilityRequestEntry request(final String id) {
        return getRequest(id);
    }

    public boolean closeRequest(final Player actor, final String id, final String response) {
        if (!canHandleRequests(actor)) {
            return false;
        }
        final UtilityRequestEntry request = getRequest(id);
        if (request == null) {
            return false;
        }
        saveRequest(new UtilityRequestEntry(
                request.id(),
                request.type(),
                "closed",
                request.createdAt(),
                request.requesterUuid(),
                request.requesterName(),
                request.world(),
                request.plotId(),
                request.plotKey(),
                request.ownerName(),
                request.note(),
                actor.getName(),
                Instant.now(),
                blank(response, "Erledigt.")
        ));
        return true;
    }

    public boolean acceptTrustRequest(final Player actor, final String id) {
        final Plot plot = plotService.getCurrentPlot(actor);
        final UtilityRequestEntry request = getRequest(id);
        if (plot == null || request == null || !request.type().equals("trust") || !canHandleRequests(actor)) {
            return false;
        }
        if (!plotKey(plot).equalsIgnoreCase(request.plotKey()) || !plotService.canInviteMembers(actor, plot)) {
            return false;
        }
        for (final Plot connectedPlot : plot.getConnectedPlots()) {
            connectedPlot.removeDenied(request.requesterUuid());
            connectedPlot.addTrusted(request.requesterUuid());
        }
        return closeRequest(actor, id, "Trust-Anfrage angenommen.");
    }

    public TemporaryTrustEntry createTemporaryTrust(
            final Player actor,
            final Plot plot,
            final OfflinePlayer target,
            final Duration duration
    ) {
        final Plot basePlot = base(plot);
        if (actor == null || basePlot == null || target == null || duration == null
                || duration.isZero() || duration.isNegative()
                || !featureToggleService.isEnabled("player.temporary-trusts")
                || !plotService.canInviteMembers(actor, basePlot)) {
            return null;
        }
        if (basePlot.isOwner(target.getUniqueId())) {
            return null;
        }

        final boolean wasTrusted = basePlot.getTrusted().contains(target.getUniqueId());
        for (final Plot connectedPlot : basePlot.getConnectedPlots()) {
            connectedPlot.removeDenied(target.getUniqueId());
            connectedPlot.addTrusted(target.getUniqueId());
        }

        final Instant now = Instant.now();
        final Instant expiresAt = now.plus(duration);
        final TemporaryTrustEntry entry = new TemporaryTrustEntry(
                target.getUniqueId(),
                target.getName() == null ? target.getUniqueId().toString() : target.getName(),
                plotKey(basePlot),
                actor.getName(),
                now,
                expiresAt
        );
        final String path = "temporary-trusts." + sanitize(entry.plotKey()) + "." + entry.playerUuid();
        data.set(path + ".player-name", entry.playerName());
        data.set(path + ".plot-key", entry.plotKey());
        data.set(path + ".created-by", entry.createdBy());
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".expires-at", entry.expiresAt().toString());
        data.set(path + ".was-trusted", wasTrusted);
        save();
        return entry;
    }

    public boolean removeTemporaryTrust(final Player actor, final Plot plot, final UUID targetId) {
        final Plot basePlot = base(plot);
        if (actor == null || basePlot == null || targetId == null
                || !featureToggleService.isEnabled("player.temporary-trusts")
                || !plotService.canUntrustMembers(actor, basePlot)) {
            return false;
        }
        return removeTemporaryTrust(basePlot, targetId);
    }

    public List<TemporaryTrustEntry> temporaryTrusts(final Plot plot) {
        final Plot basePlot = base(plot);
        if (basePlot == null) {
            return List.of();
        }
        final ConfigurationSection section = data.getConfigurationSection("temporary-trusts." + sanitize(plotKey(basePlot)));
        if (section == null) {
            return List.of();
        }
        final List<TemporaryTrustEntry> entries = new ArrayList<>();
        for (final String uuidText : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(uuidText);
            if (entrySection == null) {
                continue;
            }
            try {
                entries.add(new TemporaryTrustEntry(
                        UUID.fromString(uuidText),
                        entrySection.getString("player-name", uuidText),
                        entrySection.getString("plot-key", plotKey(basePlot)),
                        entrySection.getString("created-by", "-"),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        Instant.parse(entrySection.getString("expires-at", Instant.EPOCH.toString()))
                ));
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungueltiger temporaerer Trust ignoriert: " + uuidText);
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(TemporaryTrustEntry::expiresAt))
                .toList();
    }

    public int revokeExpiredTemporaryTrusts() {
        final ConfigurationSection plots = data.getConfigurationSection("temporary-trusts");
        if (plots == null) {
            return 0;
        }
        int removed = 0;
        final Instant now = Instant.now();
        for (final String sanitizedPlotKey : new ArrayList<>(plots.getKeys(false))) {
            final ConfigurationSection entries = plots.getConfigurationSection(sanitizedPlotKey);
            if (entries == null) {
                continue;
            }
            for (final String uuidText : new ArrayList<>(entries.getKeys(false))) {
                final ConfigurationSection entry = entries.getConfigurationSection(uuidText);
                if (entry == null) {
                    continue;
                }
                try {
                    final Instant expiresAt = Instant.parse(entry.getString("expires-at", Instant.EPOCH.toString()));
                    if (expiresAt.isAfter(now)) {
                        continue;
                    }
                    final Plot plot = getPlotByKey(entry.getString("plot-key", ""));
                    if (plot != null) {
                        removeTemporaryTrust(plot, UUID.fromString(uuidText));
                        removed++;
                    } else {
                        data.set("temporary-trusts." + sanitizedPlotKey + "." + uuidText, null);
                        removed++;
                    }
                } catch (final RuntimeException exception) {
                    data.set("temporary-trusts." + sanitizedPlotKey + "." + uuidText, null);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public boolean canHandleRequests(final Player player) {
        return featureToggleService.isEnabled("team.requests")
                && (player.hasPermission("craftplayplotextras.requests.manage") || player.hasPermission("craftplayplotextras.admin"));
    }

    public List<PlotProfileEntry> searchProfiles(final String query) {
        final String normalizedQuery = normalizeFreeText(query, "");
        if (normalizedQuery.isBlank() || !featureToggleService.isEnabled("player.plot-search")) {
            return List.of();
        }
        final ConfigurationSection section = data.getConfigurationSection("profiles");
        if (section == null) {
            return List.of();
        }
        final List<PlotProfileEntry> entries = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(key);
            if (entrySection == null) {
                continue;
            }
            final PlotProfileEntry entry = new PlotProfileEntry(
                    entrySection.getString("plot-key", key),
                    entrySection.getString("world", "-"),
                    entrySection.getString("plot-id", "-"),
                    entrySection.getString("owner-name", "-"),
                    entrySection.getString("description", "-"),
                    entrySection.getString("category", "-"),
                    entrySection.getStringList("tags"),
                    entrySection.getString("access-mode", "normal")
            );
            final String haystack = (entry.plotKey() + " " + entry.ownerName() + " " + entry.description()
                    + " " + entry.category() + " " + String.join(" ", entry.tags())).toLowerCase(Locale.ROOT);
            if (haystack.contains(normalizedQuery)) {
                entries.add(entry);
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(PlotProfileEntry::category, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlotProfileEntry::ownerName, String.CASE_INSENSITIVE_ORDER))
                .limit(25)
                .toList();
    }

    public Plot getPlotByKey(final String key) {
        if (key == null || !key.contains(":")) {
            return null;
        }
        final String[] parts = key.split(":", 2);
        final PlotId plotId = PlotId.fromStringOrNull(parts[1].replace('-', ';'));
        if (plotId == null) {
            return null;
        }
        final PlotArea area = PlotSquared.get().getPlotAreaManager().getPlotArea(parts[0], null);
        return area == null ? null : area.getPlot(plotId);
    }

    public boolean teleportToPlot(final Player player, final String key) {
        final Plot plot = getPlotByKey(key);
        if (player == null || plot == null) {
            return false;
        }
        final com.plotsquared.core.location.Location home = plot.getHomeSynchronous();
        final World world = Bukkit.getWorld(home.getWorldName());
        if (world == null) {
            return false;
        }
        return player.teleport(new org.bukkit.Location(
                world,
                home.getX() + 0.5D,
                home.getY(),
                home.getZ() + 0.5D,
                home.getYaw(),
                home.getPitch()
        ));
    }

    public int cleanupOwnedPlot(final Player player, final Plot plot, final String rawMode) {
        if (plot == null || player == null || !featureToggleService.isEnabled("player.cleanup")) {
            return -1;
        }
        if (!plotService.canManageRoles(player, plot)) {
            return -1;
        }
        final World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) {
            return -1;
        }
        final String mode = rawMode == null ? "drops" : rawMode.toLowerCase(Locale.ROOT);
        int removed = 0;
        for (final CuboidRegion region : regions(plot)) {
            for (final Entity entity : world.getNearbyEntities(toBoundingBox(world, region))) {
                if (entity instanceof Player || !contains(region, entity) || !matchesCleanupMode(entity, mode)) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public BuildTaskEntry createBuildTask(final Player actor, final Plot plot, final String title, final String note) {
        if (actor == null || base(plot) == null || !canManageBuildTasks(actor)) {
            return null;
        }
        final Plot basePlot = base(plot);
        final String id = "build-" + Long.toString(System.currentTimeMillis(), 36);
        final BuildTaskEntry entry = new BuildTaskEntry(
                id,
                "open",
                Instant.now(),
                actor.getName(),
                plotKey(basePlot),
                trimMessage(title, 60),
                trimMessage(note, 240),
                "-",
                null
        );
        saveBuildTask(entry);
        return entry;
    }

    public boolean completeBuildTask(final Player actor, final String id) {
        if (!canManageBuildTasks(actor)) {
            return false;
        }
        final BuildTaskEntry task = getBuildTask(id);
        if (task == null) {
            return false;
        }
        saveBuildTask(new BuildTaskEntry(
                task.id(),
                "closed",
                task.createdAt(),
                task.createdBy(),
                task.plotKey(),
                task.title(),
                task.note(),
                actor.getName(),
                Instant.now()
        ));
        return true;
    }

    public List<BuildTaskEntry> listBuildTasks(final boolean all) {
        final ConfigurationSection section = data.getConfigurationSection("build-tasks");
        if (section == null) {
            return List.of();
        }
        final List<BuildTaskEntry> entries = new ArrayList<>();
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                final String completedAt = entrySection.getString("completed-at", "");
                final BuildTaskEntry entry = new BuildTaskEntry(
                        id,
                        entrySection.getString("status", "open"),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        entrySection.getString("created-by", "-"),
                        entrySection.getString("plot-key", "-"),
                        entrySection.getString("title", "-"),
                        entrySection.getString("note", "-"),
                        entrySection.getString("completed-by", "-"),
                        completedAt == null || completedAt.isBlank() ? null : Instant.parse(completedAt)
                );
                if (all || entry.status().equalsIgnoreCase("open")) {
                    entries.add(entry);
                }
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungueltige Builder-Aufgabe ignoriert: " + id);
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(BuildTaskEntry::createdAt).reversed())
                .toList();
    }

    public boolean canManageBuildTasks(final Player player) {
        return featureToggleService.isEnabled("team.builder.tasks")
                && (player.hasPermission("craftplayplotextras.builder.tasks") || player.hasPermission("craftplayplotextras.admin"));
    }

    public boolean setBuilderMode(final Player player, final boolean enabled) {
        if (player == null || !featureToggleService.isEnabled("team.builder.mode")
                || (!player.hasPermission("craftplayplotextras.builder.mode") && !player.hasPermission("craftplayplotextras.admin"))) {
            return false;
        }
        if (enabled) {
            builderMode.add(player.getUniqueId());
        } else {
            builderMode.remove(player.getUniqueId());
        }
        return true;
    }

    public boolean isBuilderMode(final Player player) {
        return player != null && builderMode.contains(player.getUniqueId());
    }

    public Map<String, Integer> statistics() {
        final Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("profiles", size("profiles"));
        stats.put("openRequests", listOpenRequests().size());
        stats.put("allRequests", listRequests().size());
        stats.put("guestbookPlots", size("guestbook"));
        stats.put("openBuildTasks", listBuildTasks(false).size());
        stats.put("allBuildTasks", listBuildTasks(true).size());
        return stats;
    }

    private UtilityRequestEntry getRequest(final String id) {
        if (id == null) {
            return null;
        }
        return listRequests().stream()
                .filter(entry -> entry.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    private BuildTaskEntry getBuildTask(final String id) {
        if (id == null) {
            return null;
        }
        return listBuildTasks(true).stream()
                .filter(entry -> entry.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    private boolean removeTemporaryTrust(final Plot plot, final UUID targetId) {
        final String path = "temporary-trusts." + sanitize(plotKey(plot)) + "." + targetId;
        final boolean wasTrusted = data.getBoolean(path + ".was-trusted", false);
        if (!wasTrusted) {
            for (final Plot connectedPlot : plot.getConnectedPlots()) {
                connectedPlot.removeTrusted(targetId);
            }
        }
        data.set(path, null);
        save();
        return true;
    }

    private void saveRequest(final UtilityRequestEntry entry) {
        final String path = "requests." + entry.id();
        data.set(path + ".type", entry.type());
        data.set(path + ".status", entry.status());
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".requester-uuid", entry.requesterUuid().toString());
        data.set(path + ".requester-name", entry.requesterName());
        data.set(path + ".world", entry.world());
        data.set(path + ".plot-id", entry.plotId());
        data.set(path + ".plot-key", entry.plotKey());
        data.set(path + ".owner-name", entry.ownerName());
        data.set(path + ".note", entry.note());
        data.set(path + ".handled-by", entry.handledBy());
        data.set(path + ".handled-at", entry.handledAt() == null ? null : entry.handledAt().toString());
        data.set(path + ".response", entry.response());
        save();
    }

    private void saveBuildTask(final BuildTaskEntry entry) {
        final String path = "build-tasks." + entry.id();
        data.set(path + ".status", entry.status());
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".created-by", entry.createdBy());
        data.set(path + ".plot-key", entry.plotKey());
        data.set(path + ".title", entry.title());
        data.set(path + ".note", entry.note());
        data.set(path + ".completed-by", entry.completedBy());
        data.set(path + ".completed-at", entry.completedAt() == null ? null : entry.completedAt().toString());
        save();
    }

    public String plotKey(final Plot plot) {
        final Plot basePlot = base(plot);
        return basePlot == null ? "-" : basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private String ensureProfile(final Plot plot) {
        final Plot basePlot = base(plot);
        final String path = profilePath(basePlot);
        data.set(path + ".plot-key", plotKey(basePlot));
        data.set(path + ".world", basePlot.getWorldName());
        data.set(path + ".plot-id", basePlot.getId().toString());
        data.set(path + ".owner-name", ownerName(basePlot.getOwnerAbs()));
        return path;
    }

    private String profilePath(final Plot plot) {
        return "profiles." + sanitize(plotKey(plot));
    }

    private Plot base(final Plot plot) {
        return plot == null ? null : plot.getBasePlot(false);
    }

    private String ownerName(final UUID ownerUuid) {
        if (ownerUuid == null) {
            return "-";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        return offlinePlayer.getName() == null ? ownerUuid.toString() : offlinePlayer.getName();
    }

    private List<CuboidRegion> regions(final Plot plot) {
        final List<CuboidRegion> regions = new ArrayList<>();
        for (final Plot connectedPlot : plot.getBasePlot(false).getConnectedPlots()) {
            try {
                regions.add(connectedPlot.getLargestRegion());
            } catch (final RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Plot-Region konnte nicht gelesen werden.", exception);
            }
        }
        return regions;
    }

    private BoundingBox toBoundingBox(final World world, final CuboidRegion region) {
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        return new BoundingBox(min.getX(), world.getMinHeight(), min.getZ(), max.getX() + 1D, world.getMaxHeight(), max.getZ() + 1D);
    }

    private boolean contains(final CuboidRegion region, final Entity entity) {
        return region.contains(BlockVector3.at(entity.getLocation().getBlockX(), entity.getLocation().getBlockY(), entity.getLocation().getBlockZ()));
    }

    private boolean matchesCleanupMode(final Entity entity, final String mode) {
        return switch (mode) {
            case "drops", "items" -> entity instanceof Item || entity instanceof ExperienceOrb;
            case "projectiles" -> entity instanceof Projectile;
            case "monsters", "mobs" -> entity instanceof Monster;
            case "animals" -> entity instanceof Animals;
            case "vehicles" -> entity instanceof Vehicle;
            case "all" -> true;
            default -> false;
        };
    }

    private String normalizeRequestType(final String type) {
        return switch (normalizeFreeText(type, "support")) {
            case "trust", "trusted", "member", "mitglied" -> "trust";
            case "move", "umzug", "verschieben" -> "move";
            case "backup", "sicherung" -> "backup";
            case "restore", "wiederherstellen" -> "restore";
            case "design", "builder", "bau" -> "design";
            default -> "support";
        };
    }

    private int size(final String path) {
        final ConfigurationSection section = data.getConfigurationSection(path);
        return section == null ? 0 : section.getKeys(false).size();
    }

    private String sanitize(final String value) {
        return value == null ? "-" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private String normalizeFreeText(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_.-]", "-");
    }

    private String trimMessage(final String value, final int maxLength) {
        final String message = blank(value, "-");
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

    private String blank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plot-Utilities konnten nicht gespeichert werden.", exception);
        }
    }

    public record GuestbookEntry(
            String id,
            Instant createdAt,
            UUID playerUuid,
            String playerName,
            String plotKey,
            String message
    ) {
    }

    public record UtilityRequestEntry(
            String id,
            String type,
            String status,
            Instant createdAt,
            UUID requesterUuid,
            String requesterName,
            String world,
            String plotId,
            String plotKey,
            String ownerName,
            String note,
            String handledBy,
            Instant handledAt,
            String response
    ) {
    }

    public record PlotProfileEntry(
            String plotKey,
            String world,
            String plotId,
            String ownerName,
            String description,
            String category,
            List<String> tags,
            String accessMode
    ) {
    }

    public record BuildTaskEntry(
            String id,
            String status,
            Instant createdAt,
            String createdBy,
            String plotKey,
            String title,
            String note,
            String completedBy,
            Instant completedAt
    ) {
    }

    public record TemporaryTrustEntry(
            UUID playerUuid,
            String playerName,
            String plotKey,
            String createdBy,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
