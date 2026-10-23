package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TrainSpec {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int DEFAULTS_VERSION = 2;
    public static final int M983_DEFAULTS_VERSION = 3;

    public static final String SIX_DOF = "sixdof";
    public static final String SWING_TWIST = "swingtwist";

    public String name = VehicleSpecs.PATRIOT_LAUNCHER_TRAILER;
    public int defaultsVersion;

    public VehicleSpec trailer = launcherTrailerDefaults();
    public Coupling coupling = new Coupling();

    public static final class Coupling {
        public String type = SIX_DOF;
        public double[] kingpin = {0.0, 1.341, 5.875};
        public double yawLimitDegrees = 70.0;
        public double pitchLimitDegrees = 15.0;
        public double rollLimitDegrees = 4.0;
        public double yawFrictionNm = 0.0;
        public double trainLimitKmh = 60.0;
    }

    public static TrainSpec launcherDefaults() {
        TrainSpec spec = new TrainSpec();
        spec.name = VehicleSpecs.PATRIOT_LAUNCHER_TRAILER;
        spec.defaultsVersion = DEFAULTS_VERSION;
        spec.trailer = launcherTrailerDefaults();
        return spec;
    }

    public static TrainSpec radarDefaults() {
        TrainSpec spec = new TrainSpec();
        spec.name = VehicleSpecs.PATRIOT_RADAR_TRAILER;
        spec.defaultsVersion = DEFAULTS_VERSION;
        spec.trailer = radarTrailerDefaults();
        return spec;
    }

    public static VehicleSpec m983TractorDefaults() {
        VehicleSpec spec = new VehicleSpec();
        spec.name = VehicleSpecs.M983_TRACTOR;
        spec.defaultsVersion = M983_DEFAULTS_VERSION;
        spec.body.massKg = 15000.0;
        spec.body.centreOfMass = new double[] {0.0, 1.3, 4.325};
        spec.body.boxes = new ArrayList<>(List.of(
                new VehicleSpec.Box("frame", 0.0, 0.5 * (0.4 + SADDLE_TOP_Y), 5.0, 1.2, 0.5 * (SADDLE_TOP_Y - 0.4),
                        4.35),
                new VehicleSpec.Box("saddle", 0.0, 0.5 * (0.4 + SADDLE_TOP_Y), -0.6, 1.0, 0.5 * (SADDLE_TOP_Y - 0.4),
                        1.15),
                new VehicleSpec.Box("cab", 0.0, 2.8, 8.5, 1.35, 1.2, 1.6)));
        spec.wheels.axleZ = new double[] {0.0, 1.5, 7.25, 8.75};
        spec.wheels.trackHalf = 1.666;
        spec.wheels.axleHeight = 0.6375;
        spec.wheels.radius = 0.5875;
        spec.wheels.width = 0.52;
        spec.wheels.steerDegrees = new double[] {0.0, 0.0, 21.0, 30.0};
        spec.wheels.handbrakeAxle = new boolean[] {true, true, false, false};
        spec.suspension.travel = 0.55;
        spec.suspension.restCompression = 0.25;
        spec.suspension.frequencyHz = 1.6;
        spec.drivetrain.drivenAxle = new boolean[] {true, true, true, true};
        spec.brakes.serviceTorqueNm = 22000.0;
        spec.brakes.handbrakeTorqueNm = 30000.0;
        spec.limits.forwardKmh = 80.0;
        spec.limits.reverseKmh = 15.0;
        spec.assist.yawStabilisation = 0.1;
        return spec;
    }

    private static VehicleSpec trailerBase(String name, double mass, double[] centreOfMass) {
        VehicleSpec spec = new VehicleSpec();
        spec.name = name;
        spec.defaultsVersion = DEFAULTS_VERSION;
        spec.body.massKg = mass;
        spec.body.centreOfMass = centreOfMass;
        spec.wheels.axleZ = new double[] {-4.5, -3.0625};
        spec.wheels.trackHalf = 1.5375;
        spec.wheels.axleHeight = 0.581;
        spec.wheels.radius = 0.531;
        spec.wheels.width = 0.475;
        spec.wheels.steerDegrees = new double[] {0.0, 0.0};
        spec.wheels.handbrakeAxle = new boolean[] {true, true};
        spec.suspension.travel = 0.50;
        spec.suspension.restCompression = 0.24;
        spec.suspension.frequencyHz = 0.9;
        spec.drivetrain.drivenAxle = new boolean[] {false, false};
        spec.brakes.serviceTorqueNm = 20000.0;
        spec.brakes.handbrakeTorqueNm = 20000.0;
        spec.resistance.dragArea = 4.0;
        spec.assist.yawStabilisation = 0.0;
        spec.assist.rollHoldGain = 0.0;
        spec.assist.slopeRelief = 0.0;
        return spec;
    }

    public static final double SADDLE_CLEARANCE_Z = 3.8;
    public static final double KINGPIN_UNDERSIDE_Y = 1.40;
    public static final double SADDLE_TOP_Y = 1.05;

    public static VehicleSpec launcherTrailerDefaults() {
        VehicleSpec spec = trailerBase("patriot_launcher_trailer", 24000.0, new double[] {0.0, 1.5, -0.5});
        spec.body.boxes = new ArrayList<>(List.of(
                rearBox("chassis", 1.8, 0.55, 3.126),
                frontBox("chassisNeck", 1.8, 3.126),
                new VehicleSpec.Box("pack", 0.0, 3.73, -2.06, 1.563, 0.61, 4.31)));
        return spec;
    }

    public static VehicleSpec radarTrailerDefaults() {
        VehicleSpec spec = trailerBase("patriot_radar_trailer", 20000.0, new double[] {0.0, 1.6, -1.0});
        spec.body.boxes = new ArrayList<>(List.of(
                rearBox("shelter", 2.013, 0.55, 4.577),
                frontBox("shelterNeck", 2.013, 4.577)));
        return spec;
    }

    private static VehicleSpec.Box rearBox(String name, double halfX, double bottom, double top) {
        double from = -6.281;
        double to = SADDLE_CLEARANCE_Z;
        return new VehicleSpec.Box(name, 0.0, 0.5 * (bottom + top), 0.5 * (from + to), halfX, 0.5 * (top - bottom),
                0.5 * (to - from));
    }

    private static VehicleSpec.Box frontBox(String name, double halfX, double top) {
        double to = 6.219;
        return new VehicleSpec.Box(name, 0.0, 0.5 * (KINGPIN_UNDERSIDE_Y + top), 0.5 * (SADDLE_CLEARANCE_Z + to),
                halfX, 0.5 * (top - KINGPIN_UNDERSIDE_Y), 0.5 * (to - SADDLE_CLEARANCE_Z));
    }

    public static TrainSpec defaultsFor(String name) {
        return switch (name) {
            case VehicleSpecs.PATRIOT_RADAR_TRAILER -> radarDefaults();
            default -> launcherDefaults();
        };
    }

    public static TrainSpec parse(String name, String json) {
        JsonElement user;
        try {
            user = JsonParser.parseString(json);
        } catch (RuntimeException error) {
            throw new JsonParseException("train spec: " + error.getMessage(), error);
        }
        if (user == null || !user.isJsonObject()) {
            throw new JsonParseException("train spec: empty file");
        }
        JsonObject merged = GSON.toJsonTree(defaultsFor(name)).getAsJsonObject();
        merge(merged, user.getAsJsonObject());
        TrainSpec spec;
        try {
            spec = GSON.fromJson(merged, TrainSpec.class);
        } catch (RuntimeException error) {
            throw new JsonParseException("train spec: " + error.getMessage(), error);
        }
        spec.name = name;
        if (!user.getAsJsonObject().has("defaultsVersion")) {
            spec.defaultsVersion = 0;
        }
        List<String> problems = spec.problems();
        if (!problems.isEmpty()) {
            throw new JsonParseException(String.join("; ", problems));
        }
        return spec;
    }

    private static void merge(JsonObject base, JsonObject over) {
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            JsonElement existing = base.get(entry.getKey());
            if (existing != null && existing.isJsonObject() && entry.getValue().isJsonObject()) {
                merge(existing.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                base.add(entry.getKey(), entry.getValue());
            }
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public List<String> problems() {
        List<String> found = new ArrayList<>();
        if (this.trailer == null || this.coupling == null) {
            found.add("trailer and coupling are both needed");
            return found;
        }
        for (String problem : this.trailer.problems()) {
            found.add("trailer." + problem);
        }
        if (this.coupling.kingpin == null || this.coupling.kingpin.length != 3) {
            found.add("coupling.kingpin needs three numbers");
        }
        if (!SIX_DOF.equals(this.coupling.type) && !SWING_TWIST.equals(this.coupling.type)) {
            found.add("coupling.type must be sixdof or swingtwist");
        }
        if (this.coupling.trainLimitKmh < 5.0 || this.coupling.trainLimitKmh > 200.0) {
            found.add("coupling.trainLimitKmh must be between 5 and 200");
        }
        if (this.coupling.yawLimitDegrees < 5.0 || this.coupling.yawLimitDegrees > 120.0) {
            found.add("coupling.yawLimitDegrees must be between 5 and 120");
        }
        if (this.coupling.pitchLimitDegrees < 1.0 || this.coupling.pitchLimitDegrees > 60.0) {
            found.add("coupling.pitchLimitDegrees must be between 1 and 60");
        }
        if (this.coupling.rollLimitDegrees < 0.5 || this.coupling.rollLimitDegrees > 30.0) {
            found.add("coupling.rollLimitDegrees must be between 0.5 and 30");
        }
        return found;
    }
}
