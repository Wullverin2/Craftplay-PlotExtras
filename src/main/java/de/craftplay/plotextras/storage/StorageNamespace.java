package de.craftplay.plotextras.storage;

public final class StorageNamespace {

    private final String id;
    private final String yamlFile;

    public StorageNamespace(final String id, final String yamlFile) {
        this.id = id;
        this.yamlFile = yamlFile;
    }

    public String getId() {
        return id;
    }

    public String getYamlFile() {
        return yamlFile;
    }
}
