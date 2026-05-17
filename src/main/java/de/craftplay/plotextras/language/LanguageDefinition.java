package de.craftplay.plotextras.language;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record LanguageDefinition(
        String code,
        String name,
        String nativeName,
        FileConfiguration configuration,
        ConfigurationSection itemSection
) {
}
