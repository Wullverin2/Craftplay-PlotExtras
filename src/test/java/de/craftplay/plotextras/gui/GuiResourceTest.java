package de.craftplay.plotextras.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GuiResourceTest {

    @Test
    void bundledGuisDoNotDelegatePlotInfoOrMembersToPlotSquaredCommands() throws Exception {
        final Path guiFolder = Path.of("src/main/resources/gui");
        try (var paths = Files.walk(guiFolder)) {
            for (final Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".yml")).toList()) {
                final String text = Files.readString(path).toLowerCase();
                assertFalse(text.contains("player:plot info"), path + " still runs /plot info");
                assertFalse(text.contains("player:plot members"), path + " still runs /plot members");
                assertFalse(text.contains("führt /plot info"), path + " still advertises /plot info");
                assertFalse(text.contains("führt /plot members"), path + " still advertises /plot members");
                assertFalse(text.contains("runs /plot info"), path + " still advertises /plot info");
                assertFalse(text.contains("runs /plot members"), path + " still advertises /plot members");
            }
        }
    }
}
