package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;

final class TrainLink {

    private TrainLink() {
    }

    static TwoBodyConstraint create(PhysicsWorld world, JoltVehicle tractor, JoltVehicle trailer,
            TrainSpec.Coupling c, double[] tractorAnchor, double[] trailerAnchor) {
        double[][] rt = tractor.rotation();
        double[][] rr = trailer.rotation();
        double[] p1 = PoseMath.toWorld(tractor.origin(), rt, tractorAnchor[0], tractorAnchor[1], tractorAnchor[2]);
        double[] p2 = PoseMath.toWorld(trailer.origin(), rr, trailerAnchor[0], trailerAnchor[1], trailerAnchor[2]);
        TwoBodyConstraintSettings settings;
        if (TrainSpec.SWING_TWIST.equals(c.type)) {
            SwingTwistConstraintSettings s = new SwingTwistConstraintSettings();
            s.setSpace(EConstraintSpace.WorldSpace);
            s.setPosition1(p1[0], p1[1], p1[2]);
            s.setPosition2(p2[0], p2[1], p2[2]);
            s.setTwistAxis1(column(rt, 2));
            s.setTwistAxis2(column(rr, 2));
            s.setPlaneAxis1(column(rt, 0));
            s.setPlaneAxis2(column(rr, 0));
            s.setSwingType(ESwingType.Cone);
            s.setTwistMinAngle((float) -Math.toRadians(c.rollLimitDegrees));
            s.setTwistMaxAngle((float) Math.toRadians(c.rollLimitDegrees));
            s.setPlaneHalfConeAngle((float) Math.toRadians(c.yawLimitDegrees));
            s.setNormalHalfConeAngle((float) Math.toRadians(c.pitchLimitDegrees));
            s.setMaxFrictionTorque((float) c.yawFrictionNm);
            settings = s;
        } else {
            SixDofConstraintSettings s = new SixDofConstraintSettings();
            s.setSpace(EConstraintSpace.WorldSpace);
            s.setPosition1(p1[0], p1[1], p1[2]);
            s.setPosition2(p2[0], p2[1], p2[2]);
            s.setAxisX1(column(rt, 0));
            s.setAxisY1(column(rt, 1));
            s.setAxisX2(column(rr, 0));
            s.setAxisY2(column(rr, 1));
            s.setSwingType(ESwingType.Pyramid);
            s.makeFixedAxis(EAxis.TranslationX);
            s.makeFixedAxis(EAxis.TranslationY);
            s.makeFixedAxis(EAxis.TranslationZ);
            s.setLimitedAxis(EAxis.RotationX, (float) -Math.toRadians(c.pitchLimitDegrees),
                    (float) Math.toRadians(c.pitchLimitDegrees));
            s.setLimitedAxis(EAxis.RotationY, (float) -Math.toRadians(c.yawLimitDegrees),
                    (float) Math.toRadians(c.yawLimitDegrees));
            s.setLimitedAxis(EAxis.RotationZ, (float) -Math.toRadians(c.rollLimitDegrees),
                    (float) Math.toRadians(c.rollLimitDegrees));
            if (c.yawFrictionNm > 0.0) {
                s.setMaxFriction(EAxis.RotationY, (float) c.yawFrictionNm);
            }
            settings = s;
        }
        TwoBodyConstraint link;
        try {
            link = settings.create(tractor.body(), trailer.body());
        } finally {
            settings.close();
        }
        world.system().addConstraint(link);
        return link;
    }

    static void remove(PhysicsWorld world, TwoBodyConstraint link) {
        if (!world.isClosed()) {
            world.system().removeConstraint(link);
        }
        link.close();
    }

    private static Vec3 column(double[][] m, int c) {
        return new Vec3((float) m[0][c], (float) m[1][c], (float) m[2][c]);
    }
}
