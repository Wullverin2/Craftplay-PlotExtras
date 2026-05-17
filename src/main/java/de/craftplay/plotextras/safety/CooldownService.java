package de.craftplay.plotextras.safety;

import de.craftplay.plotextras.feature.FeatureToggleService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {

    private final JavaPlugin plugin;
    private final FeatureToggleService featureToggleService;
    private final Map<String, Integer> cooldownSeconds = new HashMap<>();
    private final Map<String, Instant> lastUse = new ConcurrentHashMap<>();
    private boolean enabled;

    public CooldownService(final JavaPlugin plugin, final FeatureToggleService featureToggleService) {
        this.plugin = plugin;
        this.featureToggleService = featureToggleService;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("cooldowns.enabled", true)
                && featureToggleService.isEnabled("protection.cooldowns");
        cooldownSeconds.clear();
        final var section = plugin.getConfig().getConfigurationSection("cooldowns.commands");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            cooldownSeconds.put(normalize(key), Math.max(0, section.getInt(key, 0)));
        }
    }

    public boolean tryUse(final Player player, final String key) {
        if (remainingSeconds(player, key) > 0) {
            return false;
        }
        if (enabled && player != null && !bypass(player)) {
            lastUse.put(mapKey(player.getUniqueId(), key), Instant.now());
        }
        return true;
    }

    public long remainingSeconds(final Player player, final String key) {
        if (!enabled || player == null || bypass(player)) {
            return 0L;
        }
        final int seconds = cooldownSeconds.getOrDefault(normalize(key), 0);
        if (seconds <= 0) {
            return 0L;
        }
        final Instant last = lastUse.get(mapKey(player.getUniqueId(), key));
        if (last == null) {
            return 0L;
        }
        final long elapsed = Duration.between(last, Instant.now()).toSeconds();
        return Math.max(0L, seconds - elapsed);
    }

    private boolean bypass(final Player player) {
        return player.hasPermission("craftplayplotextras.cooldown.bypass")
                || player.hasPermission("craftplayplotextras.admin");
    }

    private String mapKey(final UUID uuid, final String key) {
        return uuid + ":" + normalize(key);
    }

    private String normalize(final String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).trim();
    }
}
