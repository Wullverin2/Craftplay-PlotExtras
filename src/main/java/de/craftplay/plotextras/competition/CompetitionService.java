package de.craftplay.plotextras.competition;

import com.plotsquared.core.plot.Plot;
import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class CompetitionService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final File dataFile;
    private final Map<String, CompetitionEntry> entries = new LinkedHashMap<>();
    private YamlConfiguration data;

    public CompetitionService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
        this.dataFile = new File(plugin.getDataFolder(), "data/plot-competitions.yml");
    }

    public void load() {
        final File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Der Datenordner für Plot-Wettbewerbe konnte nicht erstellt werden.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        reloadEntries();
    }

    public boolean canJoin(final Player player) {
        return featureToggleService.isEnabled("player.competitions")
                && player.hasPermission("craftplayplotextras.competition.join");
    }

    public boolean canJudge(final CommandSender sender) {
        return featureToggleService.isEnabled("team.competitions.judge")
                && (sender.hasPermission("craftplayplotextras.competition.judge") || sender.hasPermission("craftplayplotextras.admin"));
    }

    public CompetitionEntry join(final Player player, final Plot plot, final String competition, final String note) {
        final Plot basePlot = base(plot);
        if (player == null || basePlot == null || !canJoin(player)) {
            return null;
        }
        final String normalizedCompetition = normalize(competition);
        final String id = normalizedCompetition + "-" + basePlot.getWorldName().toLowerCase(Locale.ROOT) + "-" + basePlot.getId().toDashSeparatedString();
        final CompetitionEntry entry = new CompetitionEntry(
                id,
                normalizedCompetition,
                Instant.now(),
                basePlot.getOwnerAbs() == null ? player.getUniqueId() : basePlot.getOwnerAbs(),
                ownerName(basePlot.getOwnerAbs()),
                basePlot.getWorldName(),
                basePlot.getId().toString(),
                plotKey(basePlot),
                blank(note, "-"),
                0,
                "-",
                "-"
        );
        entries.put(entry.id(), entry);
        saveEntry(entry);
        return entry;
    }

    public boolean score(final CommandSender sender, final String id, final int score, final String note) {
        if (!canJudge(sender)) {
            return false;
        }
        final Optional<CompetitionEntry> existing = get(id);
        if (existing.isEmpty()) {
            return false;
        }
        final CompetitionEntry old = existing.get();
        final CompetitionEntry entry = new CompetitionEntry(
                old.id(),
                old.competition(),
                old.createdAt(),
                old.ownerUuid(),
                old.ownerName(),
                old.world(),
                old.plotId(),
                old.plotKey(),
                old.note(),
                Math.max(0, Math.min(100, score)),
                sender.getName(),
                blank(note, "-")
        );
        entries.put(entry.id(), entry);
        saveEntry(entry);
        return true;
    }

    public List<CompetitionEntry> list(final String competition) {
        final String normalizedCompetition = normalize(competition);
        return entries.values().stream()
                .filter(entry -> normalizedCompetition.isBlank() || entry.competition().equalsIgnoreCase(normalizedCompetition))
                .sorted(Comparator.comparingInt(CompetitionEntry::score).reversed()
                        .thenComparing(CompetitionEntry::createdAt))
                .toList();
    }

    public Optional<CompetitionEntry> get(final String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(id.toLowerCase(Locale.ROOT)));
    }

    private void reloadEntries() {
        entries.clear();
        final ConfigurationSection section = data.getConfigurationSection("entries");
        if (section == null) {
            return;
        }
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection entrySection = section.getConfigurationSection(id);
            if (entrySection == null) {
                continue;
            }
            try {
                final CompetitionEntry entry = new CompetitionEntry(
                        id.toLowerCase(Locale.ROOT),
                        entrySection.getString("competition", "default"),
                        Instant.parse(entrySection.getString("created-at", Instant.EPOCH.toString())),
                        UUID.fromString(entrySection.getString("owner-uuid", new UUID(0L, 0L).toString())),
                        entrySection.getString("owner-name", "-"),
                        entrySection.getString("world", "-"),
                        entrySection.getString("plot-id", "-"),
                        entrySection.getString("plot-key", "-"),
                        entrySection.getString("note", "-"),
                        entrySection.getInt("score", 0),
                        entrySection.getString("scored-by", "-"),
                        entrySection.getString("score-note", "-")
                );
                entries.put(entry.id(), entry);
            } catch (final RuntimeException exception) {
                plugin.getLogger().warning("Ungültiger Wettbewerbseintrag ignoriert: " + id);
            }
        }
    }

    private void saveEntry(final CompetitionEntry entry) {
        final String path = "entries." + entry.id();
        data.set(path + ".competition", entry.competition());
        data.set(path + ".created-at", entry.createdAt().toString());
        data.set(path + ".owner-uuid", entry.ownerUuid().toString());
        data.set(path + ".owner-name", entry.ownerName());
        data.set(path + ".world", entry.world());
        data.set(path + ".plot-id", entry.plotId());
        data.set(path + ".plot-key", entry.plotKey());
        data.set(path + ".note", entry.note());
        data.set(path + ".score", entry.score());
        data.set(path + ".scored-by", entry.scoredBy());
        data.set(path + ".score-note", entry.scoreNote());
        save();
    }

    private Plot base(final Plot plot) {
        return plot == null ? null : plot.getBasePlot(false);
    }

    private String plotKey(final Plot plot) {
        final Plot basePlot = base(plot);
        return basePlot.getWorldName() + ":" + basePlot.getId().toDashSeparatedString();
    }

    private String ownerName(final UUID ownerUuid) {
        if (ownerUuid == null) {
            return "-";
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerUuid);
        return offlinePlayer.getName() == null ? ownerUuid.toString() : offlinePlayer.getName();
    }

    private String normalize(final String input) {
        return input == null ? "default" : input.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]", "-");
    }

    private String blank(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Plot-Wettbewerbe konnten nicht gespeichert werden.", exception);
        }
    }
}
