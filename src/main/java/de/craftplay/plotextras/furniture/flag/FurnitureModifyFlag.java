package de.craftplay.plotextras.furniture.flag;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class FurnitureModifyFlag extends BooleanFlag<FurnitureModifyFlag> {

    public static final FurnitureModifyFlag FURNITURE_MODIFY_TRUE = new FurnitureModifyFlag(true);
    public static final FurnitureModifyFlag FURNITURE_MODIFY_FALSE = new FurnitureModifyFlag(false);

    private FurnitureModifyFlag(final boolean value) {
        super(value, StaticCaption.of("Vorbereitete Flag für getrennte Möbel-Änderungsrechte."));
    }

    @Override
    protected FurnitureModifyFlag flagOf(final Boolean value) {
        return Boolean.TRUE.equals(value) ? FURNITURE_MODIFY_TRUE : FURNITURE_MODIFY_FALSE;
    }
}
