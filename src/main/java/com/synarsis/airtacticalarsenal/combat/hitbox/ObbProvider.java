package com.synarsis.airtacticalarsenal.combat.hitbox;

import java.util.List;

/** An entity whose hit geometry is a set of oriented boxes rather than one axis-aligned block. */
public interface ObbProvider {

    /**
     * Boxes in world space, valid for the current tick. Implementations are expected to cache and
     * rebuild lazily -- several bullets in one tick must not each pay for a rebuild.
     */
    List<Obb> getObbs();

    /** Boxes placed for a mid-tick pose, matching what the renderer draws. */
    default List<Obb> getRenderObbs(float partialTick) {
        return getObbs();
    }

    default boolean useAabb() {
        return getObbs().isEmpty();
    }
}
