package com.synarsis.airtacticalarsenal.combat.hitbox;

import java.util.List;

public interface ObbProvider {

    List<Obb> getObbs();

    default List<Obb> getRenderObbs(float partialTick) {
        return getObbs();
    }

    default boolean useAabb() {
        return getObbs().isEmpty();
    }
}
