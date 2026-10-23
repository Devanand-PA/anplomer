package com.example.anplomer;

/**
 * The four sword arcs. {@link Player} picks one from the body's state at the
 * moment a swing starts, so the same button reads differently depending on how
 * the player is moving. {@link Renderer} draws the arc for the active style.
 */
public enum SwordStyle {
    /** Grounded and slow: a tall overhead chop right in front. */
    GROUND_IDLE,
    /** Grounded and running: a longer, lower forward slash. */
    GROUND_RUN,
    /** Airborne while rising: an upward arc above the head. */
    AIR_RISE,
    /** Airborne while falling: a plunge arc below the feet. */
    AIR_FALL;

    public boolean grounded() {
        return this == GROUND_IDLE || this == GROUND_RUN;
    }

    public boolean upward() {
        return this == AIR_RISE;
    }

    public boolean downward() {
        return this == AIR_FALL;
    }
}
