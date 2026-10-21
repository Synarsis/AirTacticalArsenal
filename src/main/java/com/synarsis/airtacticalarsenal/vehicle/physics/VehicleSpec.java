package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

public final class VehicleSpec {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int DEFAULTS_VERSION = 1;

    public String name = "iskander_tel";
    public int defaultsVersion;

    public Body body = new Body();
    public Wheels wheels = new Wheels();
    public Suspension suspension = new Suspension();
    public Engine engine = new Engine();
    public Gearbox gearbox = new Gearbox();
    public Drivetrain drivetrain = new Drivetrain();
    public Brakes brakes = new Brakes();
    public Tyres tyres = new Tyres();
    public Resistance resistance = new Resistance();
    public Assist assist = new Assist();
    public Limits limits = new Limits();

    public static final class Box {
        public String name;
        public double[] centre;
        public double[] half;

        public Box() {
        }

        public Box(String name, double cx, double cy, double cz, double hx, double hy, double hz) {
            this.name = name;
            this.centre = new double[] {cx, cy, cz};
            this.half = new double[] {hx, hy, hz};
        }
    }

    public static final class Body {
        public double massKg = 42000.0;
        public double[] centreOfMass = {0.0, 1.8, 0.3};
        public double linearDamping = 0.0;
        public double angularDamping = 0.05;
        public List<Box> boxes = new ArrayList<>(List.of(
                new Box("frame", 0.0, 0.95, 0.0, 1.7, 0.4, 4.2),
                new Box("frameRear", 0.0, 1.7, -6.0, 1.7, 0.3, 0.94),
                new Box("frameFront", 0.0, 1.7, 6.0, 1.7, 0.3, 1.66),
                new Box("bay", 0.0, 2.9, -2.44, 1.7, 1.1, 4.5),
                new Box("cab", 0.0, 2.45, 5.96, 1.8, 1.1, 1.7)));
    }

    public static final class Wheels {
        public double[] axleZ = {-5.125, -3.125, 3.0, 5.125};
        public double trackHalf = 1.69;
        public double axleHeight = 0.75;
        public double radius = 0.72;
        public double width = 0.55;
        public double inertia = 150.0;
        public double angularDamping = 0.2;
        public String cast = "cylinder";
        public double[] steerDegrees = {0.0, 0.0, 17.0, 28.0};
        public boolean[] handbrakeAxle = {true, true, false, false};
    }

    public static final class Suspension {
        public double travel = 0.45;
        public double restCompression = 0.25;
        public double frequencyHz = 0.85;
        public double damping = 0.5;
        public double antiRollStiffness = 0.0;
    }

    public static final class Engine {
        public double maxTorqueNm = 8500.0;
        public double minRpm = 600.0;
        public double maxRpm = 2100.0;
        public double inertia = 1.2;
        public double angularDamping = 0.3;
        public double[][] torqueCurve = {{0.0, 0.6}, {0.2, 0.85}, {0.4, 1.0}, {0.7, 0.9}, {1.0, 0.7}};
    }

    public static final class Gearbox {
        public double[] forward = {7.0, 4.6, 3.1, 2.1, 1.45, 1.0};
        public double[] reverse = {-5.0};
        public double switchSeconds = 0.2;
        public double clutchReleaseSeconds = 0.2;
        public double clutchStrength = 500.0;
        public double shiftUpRpm = 1900.0;
        public double shiftDownRpm = 1000.0;
    }

    public static final class Drivetrain {
        public double finalDrive = 5.0;
        public double slipLimitRatio = 1.4;
        public boolean[] drivenAxle = {true, true, true, true};
    }

    public static final class Brakes {
        public double serviceTorqueNm = 24000.0;
        public double handbrakeTorqueNm = 30000.0;
    }

    public static final class Tyres {
        public double[][] longitudinal = {{0.0, 0.0}, {0.06, 1.0}, {1.0, 0.8}};
        public double[][] lateral = {{0.0, 0.0}, {4.0, 1.0}, {30.0, 0.8}};
    }

    public static final class Resistance {
        public double airDensity = 1.2;
        public double dragArea = 8.0;
        public double rollingCoefficient = 0.015;
    }

    public static final class Assist {
        public double lowSpeedSteerBoost = 1.6;
        public double lowSpeedSteerFadeKmh = 25.0;
        public double rollHoldStartDegrees = 6.0;
        public double rollHoldGain = 0.6;
        public double yawStabilisation = 0.25;
        public double steerRatePerSecond = 2.5;
        public double steerReturnPerSecond = 4.0;
        public double throttleRatePerSecond = 2.0;
        public double slopeRelief = 0.5;
        public double parkingBrake = 1.0;
    }

    public static final class Limits {
        public double forwardKmh = 65.0;
        public double reverseKmh = 20.0;
        public double boostFactor = 1.2;
        public double cheatSpeedFactor = 1.5;
        public double groundDepthMetres = 0.5;
        public double explosionImpulse = 1000000.0;
    }

    public static VehicleSpec defaults() {
        VehicleSpec spec = new VehicleSpec();
        spec.defaultsVersion = DEFAULTS_VERSION;
        return spec;
    }

    public static VehicleSpec parse(String json) {
        VehicleSpec spec;
        try {
            spec = GSON.fromJson(json, VehicleSpec.class);
        } catch (RuntimeException error) {
            throw new JsonParseException("vehicle spec: " + error.getMessage(), error);
        }
        if (spec == null) {
            throw new JsonParseException("vehicle spec: empty file");
        }
        spec.repair();
        return spec;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public int wheelCount() {
        return this.wheels.axleZ.length * 2;
    }

    public double maxSteerRadians(int axle) {
        return Math.toRadians(this.wheels.steerDegrees[axle]);
    }

    public double forwardLimitMs() {
        return Units.kmhToMs(this.limits.forwardKmh);
    }

    public double reverseLimitMs() {
        return Units.kmhToMs(this.limits.reverseKmh);
    }

    public double dragForce(double speedMs) {
        return 0.5 * this.resistance.airDensity * this.resistance.dragArea * speedMs * Math.abs(speedMs);
    }

    public double rollingForce(double gravity) {
        return this.resistance.rollingCoefficient * this.body.massKg * gravity;
    }

    public List<String> problems() {
        List<String> found = new ArrayList<>();
        if (this.body.massKg < 1000.0) {
            found.add("body.massKg is too small");
        }
        if (this.body.boxes == null || this.body.boxes.isEmpty()) {
            found.add("body.boxes is empty");
        } else {
            for (Box box : this.body.boxes) {
                if (box == null || box.centre == null || box.centre.length != 3 || box.half == null || box.half.length != 3) {
                    found.add("every body box needs centre and half with three numbers");
                    break;
                }
            }
        }
        if (this.body.centreOfMass == null || this.body.centreOfMass.length != 3) {
            found.add("body.centreOfMass needs three numbers");
        }
        int axles = this.wheels.axleZ == null ? 0 : this.wheels.axleZ.length;
        if (axles < 1) {
            found.add("wheels.axleZ is empty");
        } else {
            if (this.wheels.steerDegrees == null || this.wheels.steerDegrees.length != axles) {
                found.add("wheels.steerDegrees must have one entry per axle");
            }
            if (this.wheels.handbrakeAxle == null || this.wheels.handbrakeAxle.length != axles) {
                found.add("wheels.handbrakeAxle must have one entry per axle");
            }
            if (this.drivetrain.drivenAxle == null || this.drivetrain.drivenAxle.length != axles) {
                found.add("drivetrain.drivenAxle must have one entry per axle");
            }
        }
        if (this.gearbox.forward == null || this.gearbox.forward.length < 1) {
            found.add("gearbox.forward is empty");
        }
        if (this.gearbox.reverse == null || this.gearbox.reverse.length < 1) {
            found.add("gearbox.reverse is empty");
        }
        if (this.engine.maxRpm <= this.engine.minRpm) {
            found.add("engine.maxRpm must be above engine.minRpm");
        }
        if (this.engine.torqueCurve == null || this.engine.torqueCurve.length < 2) {
            found.add("engine.torqueCurve needs at least two points");
        }
        if (this.tyres.longitudinal == null || this.tyres.longitudinal.length < 2
                || this.tyres.lateral == null || this.tyres.lateral.length < 2) {
            found.add("tyre curves need at least two points");
        }
        return found;
    }

    private void repair() {
        VehicleSpec fallback = new VehicleSpec();
        if (this.body == null) {
            this.body = fallback.body;
        }
        if (this.wheels == null) {
            this.wheels = fallback.wheels;
        }
        if (this.suspension == null) {
            this.suspension = fallback.suspension;
        }
        if (this.engine == null) {
            this.engine = fallback.engine;
        }
        if (this.gearbox == null) {
            this.gearbox = fallback.gearbox;
        }
        if (this.drivetrain == null) {
            this.drivetrain = fallback.drivetrain;
        }
        if (this.brakes == null) {
            this.brakes = fallback.brakes;
        }
        if (this.tyres == null) {
            this.tyres = fallback.tyres;
        }
        if (this.resistance == null) {
            this.resistance = fallback.resistance;
        }
        if (this.assist == null) {
            this.assist = fallback.assist;
        }
        if (this.limits == null) {
            this.limits = fallback.limits;
        }
        List<String> problems = this.problems();
        if (!problems.isEmpty()) {
            throw new JsonParseException(String.join("; ", problems));
        }
    }
}
