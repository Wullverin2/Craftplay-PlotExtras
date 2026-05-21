package de.craftplay.plotextras.roles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlotRole {

    private final String name;
    private final List<String> permissions;
    private final boolean protectedRole;

    public PlotRole(final String name, final List<String> permissions, final boolean protectedRole) {
        this.name = name == null ? "" : name;
        this.permissions = permissions == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(permissions));
        this.protectedRole = protectedRole;
    }

    public String getName() {
        return name;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public boolean isProtectedRole() {
        return protectedRole;
    }

    public PlotRole withName(final String newName) {
        return new PlotRole(newName, permissions, protectedRole);
    }

    public PlotRole withPermissions(final List<String> newPermissions) {
        return new PlotRole(name, newPermissions, protectedRole);
    }
}
