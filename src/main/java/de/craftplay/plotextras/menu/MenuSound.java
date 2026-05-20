package de.craftplay.plotextras.menu;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class MenuSound {

    private final boolean enabled;
    private final String soundName;
    private final float volume;
    private final float pitch;

    public MenuSound(final boolean enabled, final String soundName, final float volume, final float pitch) {
        this.enabled = enabled;
        this.soundName = soundName == null ? "" : soundName.trim();
        this.volume = volume;
        this.pitch = pitch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void play(final JavaPlugin plugin, final Player player) {
        if (!enabled || soundName.isEmpty()) {
            return;
        }
        try {
            final Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("Unbekannter Sound in einer GUI-Datei: " + soundName);
        }
    }
}
