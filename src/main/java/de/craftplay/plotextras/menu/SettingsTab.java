package de.craftplay.plotextras.menu;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SettingsTab {

    private final String id;
    private final List<MenuButton> selectors;
    private final Map<Integer, MenuButton> buttonsBySlot;

    public SettingsTab(final String id, final List<MenuButton> selectors, final Map<Integer, MenuButton> buttonsBySlot) {
        this.id = id;
        this.selectors = selectors == null ? Collections.emptyList() : Collections.unmodifiableList(selectors);
        this.buttonsBySlot = buttonsBySlot;
    }

    public String getId() {
        return id;
    }

    public MenuButton getSelector() {
        return selectors.isEmpty() ? null : selectors.get(0);
    }

    public List<MenuButton> getSelectors() {
        return selectors;
    }

    public Map<Integer, MenuButton> getButtonsBySlot() {
        return buttonsBySlot;
    }
}
