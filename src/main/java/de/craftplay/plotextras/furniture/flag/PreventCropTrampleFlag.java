package de.craftplay.plotextras.furniture.flag;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class PreventCropTrampleFlag extends BooleanFlag<PreventCropTrampleFlag> {

    public static final PreventCropTrampleFlag PREVENT_CROP_TRAMPLE_TRUE = new PreventCropTrampleFlag(true);
    public static final PreventCropTrampleFlag PREVENT_CROP_TRAMPLE_FALSE = new PreventCropTrampleFlag(false);

    private PreventCropTrampleFlag(final boolean value) {
        super(value, StaticCaption.of("Verhindert, dass Spieler Ackerboden zertrampeln und Pflanzen dadurch abbrechen."));
    }

    @Override
    protected PreventCropTrampleFlag flagOf(final Boolean value) {
        return Boolean.TRUE.equals(value) ? PREVENT_CROP_TRAMPLE_TRUE : PREVENT_CROP_TRAMPLE_FALSE;
    }
}
