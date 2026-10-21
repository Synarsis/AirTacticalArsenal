package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import java.util.List;

public final class TelBoneGroups {

    public static final int COVERS = 0;
    public static final int LIFT_1 = 1;
    public static final int LIFT_2 = 2;
    public static final int COUNT = 3;

    private static final List<List<String>> BONES = List.of(
            List.of("cover_left", "cover_right"),
            List.of("erector_left", "lift_sleeve6_left", "lift_rod6_left", "carrier_shoe6_left"),
            List.of("erector_right", "lift_sleeve6_right", "lift_rod6_right", "carrier_shoe6_right"));

    private TelBoneGroups() {
    }

    public static List<String> bones(int group) {
        return BONES.get(group);
    }

    public static int of(String bone) {
        for (int g = 0; g < COUNT; g++) {
            for (String name : BONES.get(g)) {
                if (name.equals(bone)) {
                    return g;
                }
            }
        }
        return -1;
    }

    public static int liftOf(int slot) {
        return slot == 0 ? LIFT_1 : LIFT_2;
    }
}
