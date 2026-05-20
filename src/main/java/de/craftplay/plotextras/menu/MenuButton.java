package de.craftplay.plotextras.menu;

import org.bukkit.Material;

import java.util.List;

public final class MenuButton {

    private final String id;
    private final int slot;
    private final Material material;
    private final String headDatabaseId;
    private final String skullOwner;
    private final String name;
    private final List<String> lore;
    private final List<String> commands;
    private final boolean closeInventory;
    private final String permission;
    private final MenuSound clickSound;
    private final String bedrockLabel;

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
        this(id, slot, material, "", "", name, lore, commands, closeInventory, permission, null, "");
    }

    public MenuButton(
            final String id,
            final int slot,
            final Material material,
            final String headDatabaseId,
            final String name,
            final List<String> lore,
            final List<String> commands,
            final boolean closeInventory,
            final String permission
    ) {
        this(id, slot, material, headDatabaseId, "", name, lore, commands, closeInventory, permission, null, "");
    }

    public MenuButton(
            final String id,
            final int slot,
            final Material material,
            final String headDatabaseId,
            final String skullOwner,
            final String name,
            final List<String> lore,
            final List<String> commands,
            final boolean closeInventory,
            final String permission
    ) {
        this(id, slot, material, headDatabaseId, skullOwner, name, lore, commands, closeInventory, permission, null, "");
    }

    public MenuButton(
            final String id,
            final int slot,
            final Material material,
            final String headDatabaseId,
            final String skullOwner,
            final String name,
            final List<String> lore,
            final List<String> commands,
            final boolean closeInventory,
            final String permission,
            final MenuSound clickSound,
            final String bedrockLabel
    ) {
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.headDatabaseId = headDatabaseId;
        this.skullOwner = skullOwner;
        this.name = name;
        this.lore = lore;
        this.commands = commands;
        this.closeInventory = closeInventory;
        this.permission = permission;
        this.clickSound = clickSound;
        this.bedrockLabel = bedrockLabel == null ? "" : bedrockLabel;
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

    public String getHeadDatabaseId() {
        return headDatabaseId;
    }

    public String getSkullOwner() {
        return skullOwner;
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

    public MenuSound getClickSound() {
        return clickSound;
    }

    public String getBedrockLabel() {
        return bedrockLabel;
    }
}
