package de.craftplay.plotextras.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SlotParser {

    private SlotParser() {
    }

    public static List<Integer> itemSlots(final ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyList();
        }
        if (section.contains("slots")) {
            return parse(section.get("slots"));
        }
        if (section.contains("slot")) {
            return parse(section.get("slot"));
        }
        return Collections.emptyList();
    }

    public static List<Integer> slots(final ConfigurationSection section, final String path) {
        if (section == null || !section.contains(path)) {
            return Collections.emptyList();
        }
        return parse(section.get(path));
    }

    private static List<Integer> parse(final Object value) {
        final List<Integer> slots = new ArrayList<>();
        if (value instanceof Number number) {
            slots.add(number.intValue());
            return slots;
        }
        if (value instanceof String string) {
            parseString(string, slots);
            return slots;
        }
        if (value instanceof List<?> list) {
            for (final Object entry : list) {
                if (entry instanceof Number number) {
                    slots.add(number.intValue());
                } else if (entry instanceof String string) {
                    parseString(string, slots);
                }
            }
        }
        return slots;
    }

    private static void parseString(final String input, final List<Integer> slots) {
        for (final String part : input.split(",")) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("-")) {
                final String[] range = trimmed.split("-", 2);
                try {
                    final int start = Integer.parseInt(range[0].trim());
                    final int end = Integer.parseInt(range[1].trim());
                    if (start <= end) {
                        for (int slot = start; slot <= end; slot++) {
                            slots.add(slot);
                        }
                    } else {
                        for (int slot = start; slot >= end; slot--) {
                            slots.add(slot);
                        }
                    }
                } catch (final NumberFormatException ignored) {
                    // Invalid slot ranges are ignored so one typo does not disable the full GUI.
                }
                continue;
            }
            try {
                slots.add(Integer.parseInt(trimmed));
            } catch (final NumberFormatException ignored) {
                // Invalid slot entries are ignored.
            }
        }
    }
}
