package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import com.synarsis.airtacticalarsenal.vehicle.physics.TrainSpec;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Where the trailer frame touches the tractor")
class PatriotContactAngleTest {

    private static final double SHRINK = 0.02;

    private static boolean overlap(ModelHitbox.LocalBox a, ModelHitbox.LocalBox b) {
        Vec3[] ua = {a.ax(), a.ay(), a.az()};
        Vec3[] ub = {b.ax(), b.ay(), b.az()};
        double[] ha = {Math.max(0.0, a.hx() - SHRINK), Math.max(0.0, a.hy() - SHRINK), Math.max(0.0, a.hz() - SHRINK)};
        double[] hb = {Math.max(0.0, b.hx() - SHRINK), Math.max(0.0, b.hy() - SHRINK), Math.max(0.0, b.hz() - SHRINK)};
        Vec3 d = b.centre().subtract(a.centre());
        List<Vec3> axes = new ArrayList<>(List.of(ua));
        axes.addAll(List.of(ub));
        for (Vec3 x : ua) {
            for (Vec3 y : ub) {
                Vec3 c = x.cross(y);
                if (c.lengthSqr() > 1.0E-9) {
                    axes.add(c.normalize());
                }
            }
        }
        for (Vec3 axis : axes) {
            double ra = 0.0;
            double rb = 0.0;
            for (int i = 0; i < 3; i++) {
                ra += ha[i] * Math.abs(ua[i].dot(axis));
                rb += hb[i] * Math.abs(ub[i].dot(axis));
            }
            if (Math.abs(d.dot(axis)) > ra + rb) {
                return false;
            }
        }
        return true;
    }

    private static boolean touches(PatriotHitbox model, double yaw, Predicate<String> trailer, Predicate<String> tractor,
            boolean onlySolid) {
        List<ModelHitbox.LocalBox> boxes = model.localBoxes(PatriotPose.REST.withArticulation(yaw, 0.0, 0.0));
        List<ModelHitbox.LocalBox> frame = new ArrayList<>();
        List<ModelHitbox.LocalBox> truck = new ArrayList<>();
        for (ModelHitbox.LocalBox box : boxes) {
            String bone = model.boneName(box.bone());
            if (onlySolid && !box.solid()) {
                continue;
            }
            if (trailer.test(bone)) {
                frame.add(box);
            } else if (tractor.test(bone)) {
                truck.add(box);
            }
        }
        for (ModelHitbox.LocalBox f : frame) {
            for (ModelHitbox.LocalBox t : truck) {
                if (overlap(f, t)) {
                    {
                        note(String.format(Locale.ROOT, "PAIR yaw %.1f chassis(%.2f %.2f %.2f h %.2f %.2f %.2f) vs %s(%.2f %.2f %.2f h %.2f %.2f %.2f)", yaw,
                                f.centre().x, f.centre().y, f.centre().z, f.hx(), f.hy(), f.hz(), model.boneName(t.bone()),
                                t.centre().x, t.centre().y, t.centre().z, t.hx(), t.hy(), t.hz()));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static void note(String line) {
        try {
            Files.writeString(Path.of("build", "contact-debug.txt"), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static double firstContact(PatriotHitbox model, double sign, boolean onlySolid) {
        Predicate<String> frame = b -> b.equals("chassis");
        Predicate<String> truck = b -> b.startsWith("truck_") && !b.startsWith("truck_coupling");
        for (double yaw = 0.0; yaw <= 90.0; yaw += 0.5) {
            if (touches(model, sign * yaw, frame, truck, onlySolid)) {
                return yaw;
            }
        }
        return 90.0;
    }

    @Test
    @DisplayName("the trailer frame meets the tractor wheels and mudguards only past the yaw limit of the hitch")
    void contactAngle() throws IOException {
        for (PatriotKind kind : PatriotKind.values()) {
            PatriotHitbox exact = PatriotHitbox.exact(kind);
            double solidRight = firstContact(exact, 1.0, true);
            double solidLeft = firstContact(exact, -1.0, true);
            double anyRight = firstContact(exact, 1.0, false);
            double anyLeft = firstContact(exact, -1.0, false);
            double toTheRight = solidRight;
            double toTheLeft = solidLeft;
            String line = String.format(Locale.ROOT,
                    "CONTACT %s: frame meets the tractor wheels, mudguards and cab at +%.1f / -%.1f degrees (90 means never); the first touch of a thin detail is at +%.1f / -%.1f; limit %.1f",
                    kind, solidRight, solidLeft, anyRight, anyLeft, TrainSpec.launcherDefaults().coupling.yawLimitDegrees);
            Files.writeString(Path.of("build", "jolt-report.txt"), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            double limit = TrainSpec.launcherDefaults().coupling.yawLimitDegrees;
            assertTrue(limit >= 55.0);
            assertTrue(limit < Math.min(toTheRight, toTheLeft), line);
        }
    }
}
