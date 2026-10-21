package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;

import java.util.ArrayList;
import java.util.List;

public final class PatriotBones {

    public static final int SUPPORTS = 0;
    public static final int LIFT = 1;
    public static final int SERVICE = 2;
    public static final int DOORS = 3;
    public static final int COUNT = 4;

    private static final String[] OUTRIGGER_SIDES = {
            "left_front", "left_rear", "right_front", "right_rear"};

    private PatriotBones() {
    }

    public static boolean hasGroup(PatriotKind kind, int group) {
        return group != SERVICE || !kind.isLauncher();
    }

    public static List<String> bones(PatriotKind kind, int group) {
        List<String> out = new ArrayList<>();
        switch (group) {
            case SUPPORTS -> {
                for (String side : OUTRIGGER_SIDES) {
                    out.add("outrigger_" + side);
                    out.add("jack_rod_" + side);
                    out.add("jack_foot_" + side);
                }
                out.add("chassis");
            }
            case LIFT -> {
                if (kind.isLauncher()) {
                    out.addAll(List.of("elevation", "lift_sleeve_left", "lift_sleeve_right", "lift_rod_left",
                            "lift_rod_right"));
                } else {
                    out.addAll(List.of("antenna", "radar_lift_left", "radar_lift_right", "radar_rod_left",
                            "radar_rod_right"));
                }
            }
            case SERVICE -> {
                if (!kind.isLauncher()) {
                    out.addAll(List.of("service_left", "service_right"));
                }
            }
            case DOORS -> out.addAll(List.of("truck_door_left", "truck_door_right"));
            default -> {
            }
        }
        return out;
    }

    public static int groupOf(PatriotKind kind, String bone) {
        for (int group = 0; group < COUNT; group++) {
            if (bones(kind, group).contains(bone)) {
                return group;
            }
        }
        return -1;
    }

    public static boolean isDirect(PatriotKind kind, String bone) {
        return bone.equals("root") || bone.equals("tractor_carrier") || bone.equals("truck_coupling_latch")
                || bone.startsWith("truck_steer_") || bone.startsWith("truck_wheel_") || bone.startsWith("wheel_")
                || kind.isLauncher() && bone.equals("traverse");
    }

    public static int channelsOf(String bone) {
        return bone.equals("chassis") ? ModelHitbox.POSITION : ModelHitbox.ALL_CHANNELS;
    }

    public static String clipName(PatriotKind kind, int group, TelPose.Clip clip) {
        if (clip == TelPose.Clip.REST) {
            return null;
        }
        boolean opening = clip == TelPose.Clip.OPENING || clip == TelPose.Clip.HELD;
        String name = switch (group) {
            case SUPPORTS -> opening ? "supports_out" : "supports_in";
            case LIFT -> kind.isLauncher() ? (opening ? "raise" : "lower") : (opening ? "antenna_up" : "antenna_down");
            case SERVICE -> opening ? "service_open" : "service_close";
            default -> opening ? "doors_open" : "doors_close";
        };
        return kind.clipPrefix() + name;
    }

    public static int ticks(PatriotKind kind, int group) {
        return switch (group) {
            case SUPPORTS -> PatriotTiming.SUPPORTS_TICKS;
            case LIFT -> kind.isLauncher() ? PatriotTiming.ERECTOR_TICKS : PatriotTiming.ANTENNA_TICKS;
            case SERVICE -> PatriotTiming.SERVICE_TICKS;
            default -> PatriotTiming.DOORS_TICKS;
        };
    }
}
