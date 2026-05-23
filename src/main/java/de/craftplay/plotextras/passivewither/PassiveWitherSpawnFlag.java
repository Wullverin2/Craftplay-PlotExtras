package de.craftplay.plotextras.passivewither;

import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.plot.flag.types.BooleanFlag;

public final class PassiveWitherSpawnFlag extends BooleanFlag<PassiveWitherSpawnFlag> {

    private final String description;

    public PassiveWitherSpawnFlag(final boolean value, final String description) {
        super(value, StaticCaption.of(description));
        this.description = description;
    }

    @Override
    protected PassiveWitherSpawnFlag flagOf(final Boolean value) {
        return new PassiveWitherSpawnFlag(value, description);
    }
}
