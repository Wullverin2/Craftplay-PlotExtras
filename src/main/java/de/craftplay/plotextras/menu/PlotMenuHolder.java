package de.craftplay.plotextras.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PlotMenuHolder implements InventoryHolder {

    private final String menuId;
    private final int page;

    public PlotMenuHolder(final String menuId) {
        this(menuId, 1);
    }

    public PlotMenuHolder(final String menuId, final int page) {
        this.menuId = menuId;
        this.page = page;
    }

    public String getMenuId() {
        return menuId;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
