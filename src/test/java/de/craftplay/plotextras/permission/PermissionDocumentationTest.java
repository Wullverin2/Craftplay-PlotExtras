package de.craftplay.plotextras.permission;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionDocumentationTest {

    @Test
    void pluginDescriptorDocumentsDynamicPermissionFamilies() throws Exception {
        final String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(pluginYml.contains("craftplayplotextras.flags.*"));
        assertTrue(pluginYml.contains("craftplayplotextras.entitylimit.*"));
        assertTrue(pluginYml.contains("craftplayplotextras.decor.wall.*"));
        assertTrue(pluginYml.contains("craftplayplotextras.decor.border.*"));
    }

    @Test
    void permissionGuideDocumentsRuntimePermissions() throws Exception {
        final String guide = Files.readString(Path.of("docs/PERMISSIONS.md"));
        assertTrue(guide.contains("craftplayplotextras.entitylimit.<entity>.<limit>"));
        assertTrue(guide.contains("craftplayplotextras.flags.<flag>"));
        assertTrue(guide.contains("craftplayplotextras.decor.wall.1"));
        assertTrue(guide.contains("plots.plot.<anzahl>"));
    }
}
