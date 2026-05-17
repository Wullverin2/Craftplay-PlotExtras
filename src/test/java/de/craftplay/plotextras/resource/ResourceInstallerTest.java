package de.craftplay.plotextras.resource;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceInstallerTest {

    @Test
    void installsEveryBundledYamlDefaultExceptPluginDescriptor() throws Exception {
        final Field field = ResourceInstaller.class.getDeclaredField("DEFAULT_RESOURCES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Set<String> installed = new TreeSet<>((List<String>) field.get(null));

        final Path resources = Path.of("src/main/resources");
        final Set<String> bundledYaml = new TreeSet<>();
        try (var paths = Files.walk(resources)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .map(resources::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !path.equals("plugin.yml"))
                    .forEach(bundledYaml::add);
        }

        bundledYaml.removeAll(installed);
        assertTrue(bundledYaml.isEmpty(), "Missing ResourceInstaller entries: " + bundledYaml);
    }
}
