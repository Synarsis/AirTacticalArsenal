package com.synarsis.airtacticalarsenal.entity.tel.physics;

import net.minecraft.world.phys.Vec3;

public final class TelHandover {

    private TelHandover() {
    }

    public static boolean acceptsServerMotion(boolean controlledByLocalInstance) {
        return !controlledByLocalInstance;
    }

    public static Vec3 coastVelocity(Vec3 lastObservedVelocity) {
        return new Vec3(lastObservedVelocity.x, 0.0, lastObservedVelocity.z);
    }
}
