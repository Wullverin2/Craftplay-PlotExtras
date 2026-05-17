package de.craftplay.plotextras.plot;

import java.util.Set;

public record PlotRole(
        String id,
        String displayName,
        Set<String> permissions,
        boolean protectedRole
) {
    public boolean hasPermission(final PlotRolePermission permission) {
        return permissions.contains("*") || permissions.contains(permission.key());
    }
}
