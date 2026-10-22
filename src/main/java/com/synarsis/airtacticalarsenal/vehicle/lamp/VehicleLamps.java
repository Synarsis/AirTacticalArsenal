package com.synarsis.airtacticalarsenal.vehicle.lamp;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VehicleLamps {

    public enum Kind {
        HEAD,
        TAIL,
        TURN,
        BEAM,
        GLOW
    }

    public record Lamp(Vec3 local, Kind kind, float size, float red, float green, float blue) {
    }

    private final List<Lamp> lamps;

    public VehicleLamps(List<Lamp> lamps) {
        this.lamps = Collections.unmodifiableList(new ArrayList<>(lamps));
    }

    public List<Lamp> lamps() {
        return this.lamps;
    }

    public List<Lamp> of(Kind kind) {
        List<Lamp> out = new ArrayList<>();
        for (Lamp lamp : this.lamps) {
            if (lamp.kind() == kind) {
                out.add(lamp);
            }
        }
        return out;
    }
}
