package de.craftplay.plotextras.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {
    }

    public static Component component(final String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static List<Component> components(final List<String> lines) {
        final List<Component> components = new ArrayList<>();
        for (final String line : lines) {
            components.add(component(line));
        }
        return components;
    }

    public static String legacy(final String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
