package de.craftplay.plotextras.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Text {

    private Text() {
    }

    public static String color(final String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> colored = new ArrayList<>();
        for (final String line : lines) {
            colored.add(color(line));
        }
        return colored;
    }
}
