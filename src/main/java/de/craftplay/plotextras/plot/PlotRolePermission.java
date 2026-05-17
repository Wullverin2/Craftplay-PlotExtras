package de.craftplay.plotextras.plot;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum PlotRolePermission {
    FLAGS("flags", "Flags"),
    DECOR_WALL("decor.wall", "Wand"),
    DECOR_BORDER("decor.border", "Rand"),
    SETTINGS_HOME("settings.home", "Plot-Home"),
    SETTINGS_WEATHER("settings.weather", "Wetter"),
    SETTINGS_TIME("settings.time", "Zeit"),
    SETTINGS_BIOME("settings.biome", "Biom"),
    MEMBERS_INVITE("members.invite", "Mitglieder einladen"),
    MEMBERS_UNTRUST("members.untrust", "Mitglieder entfernen"),
    MEMBERS_PROMOTE("members.promote", "Mitglieder befördern"),
    MEMBERS_DEMOTE("members.demote", "Mitglieder degradieren");

    private final String key;
    private final String displayName;

    PlotRolePermission(final String key, final String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<PlotRolePermission> fromKey(final String key) {
        final String normalized = normalizeKey(key);
        return Arrays.stream(values())
                .filter(permission -> permission.key.equals(normalized))
                .findFirst();
    }

    public static String normalizeKey(final String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(PlotRolePermission::key).toList();
    }
}
