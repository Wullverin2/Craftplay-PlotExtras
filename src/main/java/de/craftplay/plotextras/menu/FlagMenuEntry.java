package de.craftplay.plotextras.menu;

import org.bukkit.Material;

import java.util.List;

public final class FlagMenuEntry {

    private final String flag;
    private final int page;
    private final int slot;
    private final Material enabledMaterial;
    private final Material disabledMaterial;
    private final String name;
    private final List<String> lore;
    private final String permission;

    public FlagMenuEntry(
            final String flag,
            final int page,
            final int slot,
            final Material enabledMaterial,
            final Material disabledMaterial,
            final String name,
            final List<String> lore,
            final String permission
    ) {
        this.flag = flag;
        this.page = page;
        this.slot = slot;
        this.enabledMaterial = enabledMaterial;
        this.disabledMaterial = disabledMaterial;
        this.name = name;
        this.lore = lore;
        this.permission = permission;
    }

    public String getFlag() {
        return flag;
    }

    public int getPage() {
        return page;
    }

    public int getSlot() {
        return slot;
    }

    public Material getEnabledMaterial() {
        return enabledMaterial;
    }

    public Material getDisabledMaterial() {
        return disabledMaterial;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public String getPermission() {
        return permission;
    }
}
