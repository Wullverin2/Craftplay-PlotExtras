package de.craftplay.plotextras.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GuiHolder implements InventoryHolder {

    private final String guiId;
    private final int page;
    private final Map<Integer, List<String>> actions = new HashMap<>();
    private Inventory inventory;

    GuiHolder(final String guiId, final int page) {
        this.guiId = guiId;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(final Inventory inventory) {
        this.inventory = inventory;
    }

    String guiId() {
        return guiId;
    }

    int page() {
        return page;
    }

    void setActions(final int slot, final List<String> slotActions) {
        if (!slotActions.isEmpty()) {
            actions.put(slot, slotActions);
        }
    }

    List<String> actions(final int slot) {
        return actions.getOrDefault(slot, Collections.emptyList());
    }
}
