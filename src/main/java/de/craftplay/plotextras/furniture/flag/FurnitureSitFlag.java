package de.craftplay.plotextras.furniture.flag;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class FurnitureSitFlag extends BooleanFlag<FurnitureSitFlag> {

    public static final FurnitureSitFlag FURNITURE_SIT_TRUE = new FurnitureSitFlag(true);
    public static final FurnitureSitFlag FURNITURE_SIT_FALSE = new FurnitureSitFlag(false);

    private FurnitureSitFlag(final boolean value) {
        super(value, StaticCaption.of("Vorbereitete Flag für getrennte Möbel-Sitzrechte."));
    }

    @Override
    protected FurnitureSitFlag flagOf(final Boolean value) {
        return Boolean.TRUE.equals(value) ? FURNITURE_SIT_TRUE : FURNITURE_SIT_FALSE;
    }
}
