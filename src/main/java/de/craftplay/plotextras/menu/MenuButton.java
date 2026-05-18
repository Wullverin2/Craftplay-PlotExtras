package de.craftplay.plotextras.menu;

import org.bukkit.Material;

import java.util.List;

public final class MenuButton {

    private final String id;
    private final int slot;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final List<String> commands;
    private final boolean closeInventory;
    private final String permission;

    public MenuButton(
            final String id,
            final int slot,
            final Material material,
            final String name,
            final List<String> lore,
            final List<String> commands,
            final boolean closeInventory,
            final String permission
    ) {
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.commands = commands;
        this.closeInventory = closeInventory;
        this.permission = permission;
    }

    public String getId() {
        return id;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public List<String> getCommands() {
        return commands;
    }

    public boolean isCloseInventory() {
        return closeInventory;
    }

    public String getPermission() {
        return permission;
    }
}
