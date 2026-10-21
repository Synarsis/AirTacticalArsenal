package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.LinearCurve;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.OffsetCenterOfMassShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.VehicleAntiRollBar;
import com.github.stephengold.joltjni.VehicleCollisionTester;
import com.github.stephengold.joltjni.VehicleCollisionTesterCastCylinder;
import com.github.stephengold.joltjni.VehicleCollisionTesterCastSphere;
import com.github.stephengold.joltjni.VehicleCollisionTesterRay;
import com.github.stephengold.joltjni.VehicleConstraint;
import com.github.stephengold.joltjni.VehicleConstraintSettings;
import com.github.stephengold.joltjni.VehicleDifferentialSettings;
import com.github.stephengold.joltjni.WheelSettingsWv;
import com.github.stephengold.joltjni.WheeledVehicleController;
import com.github.stephengold.joltjni.WheeledVehicleControllerSettings;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import com.github.stephengold.joltjni.enumerate.ETransmissionMode;

import java.util.ArrayList;
import java.util.List;

public final class JoltVehicle implements PhysicsWorld.Stepper, AutoCloseable {

    public enum Mode {
        DYNAMIC,
        KINEMATIC,
        STATIC
    }

    public record Controls(double throttle, double steer, double brake, double handbrake, boolean boost) {
        public static final Controls IDLE = new Controls(0.0, 0.0, 0.0, 0.0, false);

        public Controls(double throttle, double steer, double brake, double handbrake) {
            this(throttle, steer, brake, handbrake, false);
        }
    }

    public record Snapshot(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg,
            double vx, double vy, double vz, double wx, double wy, double wz) {
    }

    private final PhysicsWorld world;
    private VehicleSpec spec;
    private final int slot;
    private final List<AutoCloseable> owned = new ArrayList<>();
    private final BodyInterface bodies;
    private Body body;
    private int bodyId;
    private VehicleConstraint constraint;
    private WheeledVehicleController controller;
    private Mode mode = Mode.DYNAMIC;
    private boolean constraintInWorld;
    private boolean closed;

    private double inputThrottle;
    private double inputSteer;
    private double inputBrake;
    private double inputHandbrake;
    private boolean inputBoost;
    private boolean driven;
    private final boolean wheeled;
    private double impactSpeed;
    private double impactNormalY;
    private double impactApproachY;
    private boolean landed;
    private int previousWheelsOnGround;
    private double smoothThrottle;
    private double smoothSteer;
    private double inertiaRoll = 1.0;
    private double inertiaYaw = 1.0;
    private double wheelbase = 1.0;
    private double lastForwardSpeed;
    private boolean limiterCut;
    private final double[] wheelSuspension;
    private final double[] wheelAngle;
    private final double[] wheelSteer;
    private final boolean[] wheelContact;
    private final double[] wheelLoad;
    private final double[] wheelSlip;
    private double rollDegrees;
    private double lastVerticalSpeed;

    public JoltVehicle(PhysicsWorld world, VehicleSpec spec, double x, double y, double z, double yawDeg,
            double pitchDeg, double rollDeg) {
        this(world, spec, x, y, z, yawDeg, pitchDeg, rollDeg, true);
    }

    public JoltVehicle(PhysicsWorld world, VehicleSpec spec, double x, double y, double z, double yawDeg,
            double pitchDeg, double rollDeg, boolean wheeled) {
        this.world = world;
        this.wheeled = wheeled;
        this.spec = spec;
        this.slot = world.takeSlot();
        if (this.slot < 0) {
            throw new IllegalStateException("too many vehicles in one world");
        }
        this.bodies = world.bodies();
        int wheels = spec.wheelCount();
        this.wheelSuspension = new double[wheels];
        this.wheelAngle = new double[wheels];
        this.wheelSteer = new double[wheels];
        this.wheelContact = new boolean[wheels];
        this.wheelLoad = new double[wheels];
        this.wheelSlip = new double[wheels];
        try {
            this.build(x, y, z, yawDeg, pitchDeg, rollDeg);
        } catch (RuntimeException error) {
            this.close();
            throw error;
        }
        world.addStepper(this);
    }

    private void own(AutoCloseable thing) {
        this.owned.add(thing);
    }

    private void build(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg) {
        ShapeRefC shape = this.buildShape();
        double[] origin = PoseMath.bodyOrigin(x, y, z, yawDeg, pitchDeg, rollDeg);
        double[] q = PoseMath.quaternion(yawDeg, pitchDeg, rollDeg);
        BodyCreationSettings settings = new BodyCreationSettings(shape, new RVec3(origin[0], origin[1], origin[2]),
                new Quat((float) q[0], (float) q[1], (float) q[2], (float) q[3]), EMotionType.Dynamic,
                PhysicsLayers.vehicle(this.slot));
        this.own(settings);
        settings.setOverrideMassProperties(EOverrideMassProperties.CalculateInertia);
        MassProperties mass = settings.getMassPropertiesOverride();
        mass.setMass((float) this.spec.body.massKg);
        settings.setLinearDamping((float) this.spec.body.linearDamping);
        settings.setAngularDamping((float) this.spec.body.angularDamping);
        settings.setFriction(0.6f);
        settings.setRestitution(0.0f);
        settings.setAllowSleeping(false);
        settings.setMaxLinearVelocity(60.0f);
        settings.setMaxAngularVelocity(6.0f);
        this.body = this.bodies.createBody(settings);
        this.bodyId = this.body.getId();
        this.bodies.addBody(this.bodyId, EActivation.Activate);
        this.measureInertia(shape);
        this.world.watchImpacts(this.bodyId, this::noteImpact);
        if (!this.wheeled) {
            this.own(shape);
            this.mode = Mode.KINEMATIC;
            this.bodies.setMotionType(this.bodyId, EMotionType.Kinematic, EActivation.Activate);
            return;
        }

        VehicleConstraintSettings constraintSettings = new VehicleConstraintSettings();
        this.own(constraintSettings);
        constraintSettings.setUp(new Vec3(0f, 1f, 0f));
        constraintSettings.setForward(new Vec3(0f, 0f, 1f));
        constraintSettings.setMaxPitchRollAngle((float) Math.toRadians(80.0));
        this.addWheels(constraintSettings);
        WheeledVehicleControllerSettings controllerSettings = this.controllerSettings();
        this.own(controllerSettings);
        constraintSettings.setController(controllerSettings);

        this.constraint = new VehicleConstraint(this.body, constraintSettings);
        this.own(this.constraint);
        this.controller = (WheeledVehicleController) this.constraint.getController();
        VehicleCollisionTester tester = this.collisionTester();
        this.own(tester);
        this.constraint.setVehicleCollisionTester(tester);
        this.addConstraint();
        this.own(shape);
    }

    private VehicleCollisionTester collisionTester() {
        int layer = PhysicsLayers.wheel(this.slot);
        String cast = this.spec.wheels.cast == null ? "cylinder" : this.spec.wheels.cast;
        return switch (cast) {
            case "sphere" -> new VehicleCollisionTesterCastSphere(layer, (float) this.spec.wheels.radius);
            case "ray" -> new VehicleCollisionTesterRay(layer);
            default -> new VehicleCollisionTesterCastCylinder(layer);
        };
    }

    private ShapeRefC buildShape() {
        StaticCompoundShapeSettings compound = new StaticCompoundShapeSettings();
        this.own(compound);
        Quat identity = new Quat(0f, 0f, 0f, 1f);
        for (VehicleSpec.Box box : this.spec.body.boxes) {
            BoxShapeSettings boxSettings = new BoxShapeSettings(new Vec3((float) box.half[0], (float) box.half[1],
                    (float) box.half[2]), 0.04f);
            this.own(boxSettings);
            compound.addShape(new Vec3((float) box.centre[0], (float) box.centre[1], (float) box.centre[2]),
                    identity, boxSettings);
        }
        ShapeResult natural = compound.create();
        if (natural.hasError()) {
            throw new IllegalStateException("vehicle shape: " + natural.getError());
        }
        ShapeRefC naturalShape = natural.get();
        Vec3 naturalCentre = naturalShape.getCenterOfMass();
        Vec3 offset = new Vec3((float) this.spec.body.centreOfMass[0] - naturalCentre.getX(),
                (float) this.spec.body.centreOfMass[1] - naturalCentre.getY(),
                (float) this.spec.body.centreOfMass[2] - naturalCentre.getZ());
        OffsetCenterOfMassShapeSettings shifted = new OffsetCenterOfMassShapeSettings(offset, naturalShape);
        this.own(shifted);
        ShapeResult result = shifted.create();
        if (result.hasError()) {
            throw new IllegalStateException("vehicle shape: " + result.getError());
        }
        this.own(naturalShape);
        return result.get();
    }

    private void measureInertia(ShapeRefC shape) {
        MassProperties properties = shape.getMassProperties();
        float volumeMass = properties.getMass();
        double scale = this.spec.body.massKg / Math.max(volumeMass, 1.0e-6);
        com.github.stephengold.joltjni.Mat44 inertia = properties.getInertia();
        this.inertiaRoll = Math.max(1.0, inertia.getElement(2, 2) * scale);
        this.inertiaYaw = Math.max(1.0, inertia.getElement(1, 1) * scale);
        double front = this.spec.wheels.axleZ[this.spec.wheels.axleZ.length - 1];
        double rear = this.spec.wheels.axleZ[0];
        this.wheelbase = Math.max(1.0, front - rear);
    }

    private void addWheels(VehicleConstraintSettings constraintSettings) {
        VehicleSpec.Wheels w = this.spec.wheels;
        VehicleSpec.Suspension s = this.spec.suspension;
        float maxLength = (float) s.travel;
        float restLength = (float) Math.max(0.0, s.travel - s.restCompression);
        float attachY = (float) (w.axleHeight + restLength);
        for (int axle = 0; axle < w.axleZ.length; axle++) {
            for (int side = 0; side < 2; side++) {
                WheelSettingsWv wheel = new WheelSettingsWv();
                this.own(wheel);
                float x = (float) (side == 0 ? w.trackHalf : -w.trackHalf);
                wheel.setPosition(new Vec3(x, attachY, (float) w.axleZ[axle]));
                wheel.setSuspensionDirection(new Vec3(0f, -1f, 0f));
                wheel.setSteeringAxis(new Vec3(0f, 1f, 0f));
                wheel.setWheelUp(new Vec3(0f, 1f, 0f));
                wheel.setWheelForward(new Vec3(0f, 0f, 1f));
                wheel.setSuspensionMinLength(0.0f);
                wheel.setSuspensionMaxLength(maxLength);
                wheel.getSuspensionSpring().setMode(ESpringMode.FrequencyAndDamping);
                wheel.getSuspensionSpring().setFrequency((float) s.frequencyHz);
                wheel.getSuspensionSpring().setDamping((float) s.damping);
                wheel.setRadius((float) w.radius);
                wheel.setWidth((float) w.width);
                wheel.setInertia((float) w.inertia);
                wheel.setAngularDamping((float) w.angularDamping);
                wheel.setMaxSteerAngle((float) this.spec.maxSteerRadians(axle));
                wheel.setMaxBrakeTorque((float) this.spec.brakes.serviceTorqueNm);
                wheel.setMaxHandBrakeTorque(w.handbrakeAxle[axle] ? (float) this.spec.brakes.handbrakeTorqueNm : 0f);
                wheel.setLongitudinalFriction(curve(this.spec.tyres.longitudinal));
                wheel.setLateralFriction(curve(this.spec.tyres.lateral));
                constraintSettings.addWheels(wheel);
            }
        }
        int bars = s.antiRollStiffness > 0.0 ? w.axleZ.length : 0;
        constraintSettings.setNumAntiRollBars(bars);
        for (int axle = 0; axle < bars; axle++) {
            VehicleAntiRollBar bar = constraintSettings.getAntiRollBar(axle);
            bar.setLeftWheel(axle * 2);
            bar.setRightWheel(axle * 2 + 1);
            bar.setStiffness((float) s.antiRollStiffness);
        }
    }

    private LinearCurve curve(double[][] points) {
        LinearCurve curve = new LinearCurve();
        this.own(curve);
        for (double[] point : points) {
            curve.addPoint((float) point[0], (float) point[1]);
        }
        return curve;
    }

    private WheeledVehicleControllerSettings controllerSettings() {
        VehicleSpec.Engine e = this.spec.engine;
        VehicleSpec.Gearbox g = this.spec.gearbox;
        VehicleSpec.Drivetrain d = this.spec.drivetrain;
        WheeledVehicleControllerSettings settings = new WheeledVehicleControllerSettings();
        settings.getEngine().setMaxTorque((float) e.maxTorqueNm);
        settings.getEngine().setMinRpm((float) e.minRpm);
        settings.getEngine().setMaxRpm((float) e.maxRpm);
        settings.getEngine().setInertia((float) e.inertia);
        settings.getEngine().setAngularDamping((float) e.angularDamping);
        LinearCurve torque = new LinearCurve();
        this.own(torque);
        for (double[] point : e.torqueCurve) {
            torque.addPoint((float) point[0], (float) point[1]);
        }
        settings.getEngine().setNormalizedTorque(torque);

        settings.getTransmission().setMode(ETransmissionMode.Auto);
        float[] forward = new float[g.forward.length];
        for (int i = 0; i < forward.length; i++) {
            forward[i] = (float) g.forward[i];
        }
        float[] reverse = new float[g.reverse.length];
        for (int i = 0; i < reverse.length; i++) {
            reverse[i] = (float) g.reverse[i];
        }
        settings.getTransmission().setGearRatios(forward);
        settings.getTransmission().setReverseGearRatios(reverse);
        settings.getTransmission().setSwitchTime((float) g.switchSeconds);
        settings.getTransmission().setClutchReleaseTime((float) g.clutchReleaseSeconds);
        settings.getTransmission().setClutchStrength((float) g.clutchStrength);
        settings.getTransmission().setShiftUpRpm((float) g.shiftUpRpm);
        settings.getTransmission().setShiftDownRpm((float) g.shiftDownRpm);

        int driven = 0;
        for (boolean axle : d.drivenAxle) {
            if (axle) {
                driven++;
            }
        }
        settings.setNumDifferentials(driven);
        settings.setDifferentialLimitedSlipRatio((float) d.slipLimitRatio);
        int next = 0;
        for (int axle = 0; axle < d.drivenAxle.length; axle++) {
            if (!d.drivenAxle[axle]) {
                continue;
            }
            VehicleDifferentialSettings differential = settings.getDifferential(next++);
            differential.setLeftWheel(axle * 2);
            differential.setRightWheel(axle * 2 + 1);
            differential.setDifferentialRatio((float) d.finalDrive);
            differential.setLeftRightSplit(0.5f);
            differential.setLimitedSlipRatio((float) d.slipLimitRatio);
            differential.setEngineTorqueRatio(1.0f / driven);
        }
        return settings;
    }

    public PhysicsWorld world() {
        return this.world;
    }

    public VehicleSpec spec() {
        return this.spec;
    }

    public int slot() {
        return this.slot;
    }

    public int bodyId() {
        return this.bodyId;
    }

    public boolean wheeled() {
        return this.wheeled;
    }

    public Mode mode() {
        return this.mode;
    }

    public void setControls(Controls controls) {
        this.inputThrottle = clamp(controls.throttle(), -1.0, 1.0);
        this.inputSteer = clamp(controls.steer(), -1.0, 1.0);
        this.inputBrake = clamp(controls.brake(), 0.0, 1.0);
        this.inputHandbrake = clamp(controls.handbrake(), 0.0, 1.0);
        this.inputBoost = controls.boost();
    }

    private void noteImpact(int otherBodyId, double approachSpeed, double normalY) {
        if (approachSpeed > this.impactSpeed) {
            this.impactSpeed = approachSpeed;
            this.impactNormalY = normalY;
        }
    }

    public double takeImpactSpeed() {
        double speed = this.impactSpeed;
        this.impactSpeed = 0.0;
        return speed;
    }

    public double lastImpactNormalY() {
        return this.impactNormalY;
    }

    public double takeLanding() {
        double speed = this.landed ? this.impactApproachY : 0.0;
        this.landed = false;
        this.impactApproachY = 0.0;
        return speed;
    }

    public void setDriven(boolean driven) {
        this.driven = driven;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public void setMode(Mode next) {
        if (next == this.mode || (!this.wheeled && next == Mode.DYNAMIC)) {
            return;
        }
        this.mode = next;
        switch (next) {
            case DYNAMIC -> {
                this.bodies.setMotionType(this.bodyId, EMotionType.Dynamic, EActivation.Activate);
                this.addConstraint();
            }
            case KINEMATIC -> {
                this.removeConstraint();
                this.bodies.setMotionType(this.bodyId, EMotionType.Kinematic, EActivation.Activate);
            }
            case STATIC -> {
                this.removeConstraint();
                this.bodies.setLinearAndAngularVelocity(this.bodyId, new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 0f));
                this.bodies.setMotionType(this.bodyId, EMotionType.Static, EActivation.DontActivate);
            }
        }
    }

    private void addConstraint() {
        if (!this.constraintInWorld) {
            this.world.system().addConstraint(this.constraint);
            this.world.system().addStepListener(this.constraint.getStepListener());
            this.constraintInWorld = true;
        }
    }

    private void removeConstraint() {
        if (this.constraintInWorld) {
            this.world.system().removeStepListener(this.constraint.getStepListener());
            this.world.system().removeConstraint(this.constraint);
            this.constraintInWorld = false;
        }
    }

    public void teleport(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg,
            Vec3 linear, Vec3 angular) {
        double[] origin = PoseMath.bodyOrigin(x, y, z, yawDeg, pitchDeg, rollDeg);
        double[] q = PoseMath.quaternion(yawDeg, pitchDeg, rollDeg);
        this.bodies.setPositionAndRotation(this.bodyId, new RVec3(origin[0], origin[1], origin[2]),
                new Quat((float) q[0], (float) q[1], (float) q[2], (float) q[3]), EActivation.Activate);
        if (linear != null) {
            this.bodies.setLinearAndAngularVelocity(this.bodyId, linear, angular == null ? new Vec3(0f, 0f, 0f) : angular);
        }
    }

    public void moveKinematic(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg, double dt) {
        double[] origin = PoseMath.bodyOrigin(x, y, z, yawDeg, pitchDeg, rollDeg);
        double[] q = PoseMath.quaternion(yawDeg, pitchDeg, rollDeg);
        this.bodies.moveKinematic(this.bodyId, new RVec3(origin[0], origin[1], origin[2]),
                new Quat((float) q[0], (float) q[1], (float) q[2], (float) q[3]), (float) dt);
    }

    public void setVelocity(double vx, double vy, double vz, double wx, double wy, double wz) {
        this.bodies.setLinearAndAngularVelocity(this.bodyId, new Vec3((float) vx, (float) vy, (float) vz),
                new Vec3((float) wx, (float) wy, (float) wz));
    }

    public void addImpulse(double ix, double iy, double iz) {
        this.bodies.activateBody(this.bodyId);
        this.bodies.addImpulse(this.bodyId, new Vec3((float) ix, (float) iy, (float) iz));
    }

    public Snapshot snapshot() {
        RVec3 p = this.bodies.getPosition(this.bodyId);
        Quat q = this.bodies.getRotation(this.bodyId);
        PoseMath.Pose pose = PoseMath.entityPose(p.xx(), p.yy(), p.zz(), q.getX(), q.getY(), q.getZ(), q.getW());
        Vec3 v = this.bodies.getLinearVelocity(this.bodyId);
        Vec3 w = this.bodies.getAngularVelocity(this.bodyId);
        return new Snapshot(pose.x(), pose.y(), pose.z(), pose.yawDeg(), pose.pitchDeg(), pose.rollDeg(),
                v.getX(), v.getY(), v.getZ(), w.getX(), w.getY(), w.getZ());
    }

    public double forwardSpeed() {
        Snapshot s = this.snapshot();
        double yaw = Math.toRadians(s.yawDeg());
        return -Math.sin(yaw) * s.vx() + Math.cos(yaw) * s.vz();
    }

    public double speed() {
        Snapshot s = this.snapshot();
        return Math.sqrt(s.vx() * s.vx() + s.vz() * s.vz());
    }

    public double rpm() {
        return this.controller.getEngine().getCurrentRpm();
    }

    public int gear() {
        return this.controller.getTransmission().getCurrentGear();
    }

    public double wheelSpin(int i) {
        return this.constraint.getWheel(i).getAngularVelocity();
    }

    public double wheelLongitudinalForce(int i) {
        return this.constraint.getWheel(i).getLongitudinalLambda() * 60.0;
    }

    public double wheelSuspensionForce(int i) {
        return this.constraint.getWheel(i).getSuspensionLambda() * 60.0;
    }

    public double clutchFriction() {
        return this.controller.getTransmission().getClutchFriction();
    }

    public int wheelCount() {
        return this.constraint.countWheels();
    }

    public double wheelSuspension(int i) {
        return this.wheelSuspension[i];
    }

    public double wheelAngle(int i) {
        return this.wheelAngle[i];
    }

    public double wheelOffset(int i) {
        double rest = Math.max(0.0, this.spec.suspension.travel - this.spec.suspension.restCompression);
        return rest - this.wheelSuspension[i];
    }

    public double wheelSteer(int i) {
        return this.wheelSteer[i];
    }

    public boolean wheelContact(int i) {
        return this.wheelContact[i];
    }

    public double wheelLoad(int i) {
        return this.wheelLoad[i];
    }

    public double wheelSlip(int i) {
        return this.wheelSlip[i];
    }

    public int wheelsOnGround() {
        int n = 0;
        for (boolean contact : this.wheelContact) {
            if (contact) {
                n++;
            }
        }
        return n;
    }

    public double rollDegrees() {
        return this.rollDegrees;
    }

    public boolean limiterCut() {
        return this.limiterCut;
    }

    public double engineLoad() {
        return clamp(Math.abs(this.smoothThrottle), 0.0, 1.0);
    }

    @Override
    public void beforeStep(float dt) {
        if (this.mode != Mode.DYNAMIC) {
            return;
        }
        VehicleSpec.Assist a = this.spec.assist;
        Snapshot s = this.snapshot();
        double yaw = Math.toRadians(s.yawDeg());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double forward = fx * s.vx() + fz * s.vz();
        this.lastForwardSpeed = forward;

        double throttleTarget = this.driven ? this.inputThrottle : 0.0;
        double brake = this.driven ? this.inputBrake : 0.0;
        double handbrake = this.driven ? this.inputHandbrake : a.parkingBrake;
        if (throttleTarget < 0.0 && forward > 1.0) {
            brake = Math.max(brake, 1.0);
            throttleTarget = 0.0;
        } else if (throttleTarget > 0.0 && forward < -1.0) {
            brake = Math.max(brake, 1.0);
            throttleTarget = 0.0;
        }
        this.limiterCut = false;
        double forwardLimit = this.spec.forwardLimitMs() * (this.inputBoost && this.driven ? this.spec.limits.boostFactor : 1.0);
        if (throttleTarget > 0.0 && forward > forwardLimit - 1.0) {
            throttleTarget *= clamp((forwardLimit - forward) / 1.0, 0.0, 1.0);
            this.limiterCut = true;
        } else if (throttleTarget < 0.0 && -forward > this.spec.reverseLimitMs() - 1.0) {
            throttleTarget *= clamp((this.spec.reverseLimitMs() + forward) / 1.0, 0.0, 1.0);
            this.limiterCut = true;
        }
        this.smoothThrottle = approach(this.smoothThrottle, throttleTarget,
                (throttleTarget == 0.0 ? 4.0 : a.throttleRatePerSecond) * dt);

        this.applySlopeRelief();
        double speedKmh = Units.msToKmh(Math.abs(forward));
        double fade = clamp(speedKmh / Math.max(a.lowSpeedSteerFadeKmh, 1.0), 0.0, 1.0);
        double lock = 1.0 + (1.0 / Math.max(a.lowSpeedSteerBoost, 1.0) - 1.0) * fade;
        double steerTarget = this.inputSteer * lock;
        double steerRate = (steerTarget == 0.0 || Math.signum(steerTarget) != Math.signum(this.smoothSteer)
                ? a.steerReturnPerSecond : a.steerRatePerSecond) * dt;
        this.smoothSteer = approach(this.smoothSteer, steerTarget, steerRate);

        this.controller.setDriverInput((float) this.smoothThrottle, (float) -this.smoothSteer, (float) brake,
                (float) handbrake);

        this.applyResistance(s, forward);
        this.applyStabilisers(s, forward, dt);
    }

    private static double approach(double value, double target, double step) {
        if (value < target) {
            return Math.min(value + step, target);
        }
        return Math.max(value - step, target);
    }

    private double gravity() {
        return Math.abs(this.world.system().getGravity().getY());
    }

    private void applySlopeRelief() {
        if (!this.driven) {
            return;
        }
        double relief = this.spec.assist.slopeRelief;
        if (relief <= 0.0 || this.wheelsOnGround() < 2) {
            return;
        }
        Quat q = this.bodies.getRotation(this.bodyId);
        double[][] r = PoseMath.rotation(q.getX(), q.getY(), q.getZ(), q.getW());
        double along = relief * this.spec.body.massKg * this.gravity() * r[1][2];
        this.bodies.addForce(this.bodyId, new Vec3((float) (r[0][2] * along), (float) (r[1][2] * along),
                (float) (r[2][2] * along)));
    }

    private void applyResistance(Snapshot s, double forward) {
        double speed = Math.sqrt(s.vx() * s.vx() + s.vy() * s.vy() + s.vz() * s.vz());
        if (speed < 1.0e-3) {
            return;
        }
        double drag = this.spec.dragForce(speed);
        double rolling = this.wheelsOnGround() > 0 ? this.spec.rollingForce(this.gravity()) * Math.min(1.0, speed / 0.5) : 0.0;
        double total = drag + rolling;
        this.bodies.addForce(this.bodyId, new Vec3((float) (-s.vx() / speed * total), (float) (-s.vy() / speed * total),
                (float) (-s.vz() / speed * total)));
    }

    private void applyStabilisers(Snapshot s, double forward, float dt) {
        VehicleSpec.Assist a = this.spec.assist;
        Quat q = this.bodies.getRotation(this.bodyId);
        double[][] r = PoseMath.rotation(q.getX(), q.getY(), q.getZ(), q.getW());
        double fx = r[0][2];
        double fy = r[1][2];
        double fz = r[2][2];
        double sideX = r[0][0];
        double sideY = r[1][0];
        double sideZ = r[2][0];
        double upX = r[0][1];
        double upY = r[1][1];
        double upZ = r[2][1];
        double rollSine = sideY;
        double roll = Math.toDegrees(Math.asin(clamp(rollSine, -1.0, 1.0)));
        this.rollDegrees = roll;
        double excess = Math.abs(roll) - a.rollHoldStartDegrees;
        if (excess > 0.0 && upY > 0.2) {
            double torque = -Math.signum(roll) * a.rollHoldGain * this.inertiaRoll * Math.toRadians(excess) * 4.0;
            this.bodies.addTorque(this.bodyId, new Vec3((float) (fx * torque), (float) (fy * torque), (float) (fz * torque)));
        }
        if (a.yawStabilisation > 0.0 && Math.abs(forward) > 4.0 && upY > 0.5) {
            double yawRate = s.wy();
            double steerAngle = this.smoothSteer * this.maxFrontSteerRadians();
            double target = -forward * Math.tan(steerAngle) / this.wheelbase * 0.6;
            double error = target - yawRate;
            double torque = a.yawStabilisation * this.inertiaYaw * error * 2.0;
            this.bodies.addTorque(this.bodyId, new Vec3((float) (upX * torque), (float) (upY * torque), (float) (upZ * torque)));
        }
    }

    private double maxFrontSteerRadians() {
        double max = 0.0;
        for (int axle = 0; axle < this.spec.wheels.axleZ.length; axle++) {
            max = Math.max(max, this.spec.maxSteerRadians(axle));
        }
        return max;
    }

    @Override
    public void afterStep(float dt) {
        if (this.closed || this.constraint == null || this.mode != Mode.DYNAMIC) {
            return;
        }
        double vyBefore = this.lastVerticalSpeed;
        this.lastVerticalSpeed = this.bodies.getLinearVelocity(this.bodyId).getY();
        int wheels = this.constraint.countWheels();
        for (int i = 0; i < wheels; i++) {
            com.github.stephengold.joltjni.Wheel wheel = this.constraint.getWheel(i);
            this.wheelContact[i] = wheel.hasContact();
            this.wheelSuspension[i] = wheel.getSuspensionLength();
            this.wheelAngle[i] = wheel.getRotationAngle();
            this.wheelSteer[i] = wheel.getSteerAngle();
            this.wheelLoad[i] = wheel.getSuspensionLambda() / dt;
            this.wheelSlip[i] = Math.abs(wheel.getLateralLambda()) / Math.max(wheel.getSuspensionLambda(), 1.0e-3);
        }
        int onGround = this.wheelsOnGround();
        if (this.previousWheelsOnGround == 0 && onGround > 0 && vyBefore < -1.0) {
            this.landed = true;
            this.impactApproachY = Math.max(this.impactApproachY, -vyBefore);
        }
        this.previousWheelsOnGround = onGround;
    }

    public void useSpec(VehicleSpec next) {
        this.spec = next;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        boolean worldAlive = !this.world.isClosed();
        if (worldAlive) {
            this.world.removeStepper(this);
            this.world.unwatchImpacts(this.bodyId);
            if (this.constraint != null) {
                this.removeConstraint();
            }
            if (this.body != null) {
                this.bodies.removeBody(this.bodyId);
                this.bodies.destroyBody(this.bodyId);
            }
        }
        for (int i = this.owned.size() - 1; i >= 0; i--) {
            try {
                this.owned.get(i).close();
            } catch (Exception ignored) {
            }
        }
        this.owned.clear();
        this.world.releaseSlot(this.slot);
    }
}
