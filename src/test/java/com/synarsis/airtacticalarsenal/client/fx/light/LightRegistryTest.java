package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import com.synarsis.airtacticalarsenal.client.fx.light.LightSelect.Candidate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Lights: the list, life curves, flicker and the choice of which to draw")
class LightRegistryTest {

    @AfterEach
    void clean() {
        AtaLights.clear();
    }

    @Test
    @DisplayName("a light rises over its rise, holds, and falls over its fall, linearly or by an exponential that ends at zero")
    void envelope() {
        assertEquals(0.0, LightCurves.envelope(0.0, 10, 20, 40, Fall.LINEAR), 1.0E-12);
        assertEquals(0.5, LightCurves.envelope(5.0, 10, 20, 40, Fall.LINEAR), 1.0E-12);
        assertEquals(1.0, LightCurves.envelope(10.0, 10, 20, 40, Fall.LINEAR), 1.0E-12);
        assertEquals(1.0, LightCurves.envelope(29.0, 10, 20, 40, Fall.LINEAR), 1.0E-12);
        assertEquals(0.5, LightCurves.envelope(50.0, 10, 20, 40, Fall.LINEAR), 1.0E-12);
        assertEquals(0.0, LightCurves.envelope(70.0, 10, 20, 40, Fall.LINEAR), 1.0E-12);
        assertEquals(0.0, LightCurves.envelope(500.0, 10, 20, 40, Fall.LINEAR), 0.0);
        assertEquals(0.0, LightCurves.envelope(-3.0, 10, 20, 40, Fall.LINEAR), 0.0, "not begun");

        assertEquals(1.0, LightCurves.envelope(30.0, 10, 20, 40, Fall.EXPONENTIAL), 1.0E-12, "starts the fall at full");
        assertEquals(0.0, LightCurves.envelope(70.0, 10, 20, 40, Fall.EXPONENTIAL), 1.0E-12, "and ends at nothing");
        double last = 1.0;
        for (int age = 30; age <= 70; age++) {
            double e = LightCurves.envelope(age, 10, 20, 40, Fall.EXPONENTIAL);
            assertTrue(e <= last + 1.0E-12, "rose at " + age);
            last = e;
        }
        assertTrue(LightCurves.envelope(40.0, 10, 20, 40, Fall.EXPONENTIAL) < LightCurves.envelope(40.0, 10, 20, 40, Fall.LINEAR));
    }

    @Test
    @DisplayName("a negative hold lasts for ever, and the life of a light is rise plus hold plus fall")
    void endlessAndLifetime() {
        assertEquals(1.0, LightCurves.envelope(1.0E6, 0, -1, 0, Fall.LINEAR), 0.0);
        assertEquals(-1, LightCurves.lifetime(0, -1, 0));
        assertEquals(70, LightCurves.lifetime(10, 20, 40));
        assertEquals(1.0, LightCurves.envelope(1.0, 1, 0, 10, Fall.EXPONENTIAL), 1.0E-12);
        assertEquals(11, LightCurves.lifetime(1, 0, 10));
    }

    @Test
    @DisplayName("the flicker is a pure function of the seed and the time, stays within its amount, and differs by seed")
    void flicker() {
        for (int seed : new int[] {1, 7, 12345}) {
            for (double t = 0.0; t < 200.0; t += 0.37) {
                double f = LightCurves.flicker(seed, t, 11.0, 0.15);
                assertEquals(f, LightCurves.flicker(seed, t, 11.0, 0.15), 0.0, "the same twice");
                assertTrue(f >= 0.85 - 1.0E-9 && f <= 1.15 + 1.0E-9, "out of its amount: " + f);
            }
        }
        boolean differs = false;
        for (double t = 0.0; t < 50.0; t += 1.0) {
            differs |= LightCurves.flicker(1, t, 11.0, 0.15) != LightCurves.flicker(2, t, 11.0, 0.15);
        }
        assertTrue(differs, "another seed flickers otherwise");
        assertEquals(1.0, LightCurves.flicker(3, 10.0, 11.0, 0.0), 0.0, "no amount, no flicker");
        assertEquals(1.0, LightCurves.flicker(3, 10.0, 0.0, 0.2), 0.0, "no speed, no flicker");
        double min = 2.0;
        double max = 0.0;
        for (double t = 0.0; t < 40.0; t += 0.25) {
            double f = LightCurves.flicker(9, t, 10.0, 0.2);
            min = Math.min(min, f);
            max = Math.max(max, f);
        }
        assertTrue(max - min > 0.2, "range " + (max - min));
    }

    @Test
    @DisplayName("a flash comes up in a tick, falls over its fall, and the list drops it when it is over")
    void flashLife() {
        AtaLights.Light flash = AtaLights.flash(new Vec3(1, 2, 3), 1.0f, 0.9f, 0.7f, 10.0, 96.0, 6);
        assertEquals(0.0, flash.strength(0.0f), 1.0E-12, "not yet");
        AtaLights.tick();
        assertEquals(10.0, flash.strength(0.0f), 1.0E-9, "at full strength after a tick");
        assertEquals(96.0, flash.radius(0.0f), 1.0E-9);
        int ticks = 1;
        while (!AtaLights.isEmpty() && ticks < 100) {
            AtaLights.tick();
            ticks++;
        }
        assertEquals(7, ticks, "gone after rise 1 and fall 6");
    }

    @Test
    @DisplayName("the reach of a light moves from its start to its end ratio over a finite life, and holds still for an endless one")
    void reachChanges() {
        AtaLights.Light ball = AtaLights.point(Vec3.ZERO).strength(5.0, 50.0).radiusEnd(0.6).life(0, 0, 70, Fall.LINEAR);
        assertEquals(50.0, ball.radius(0.0f), 1.0E-9);
        for (int i = 0; i < 35; i++) {
            AtaLights.tick();
        }
        assertEquals(40.0, ball.radius(0.0f), 1.0E-9, "half way: 50 to 30");
        AtaLights.Light flare = AtaLights.point(Vec3.ZERO).strength(4.0, 40.0).radiusEnd(0.1).life(0, -1, 0, Fall.LINEAR);
        for (int i = 0; i < 1000; i++) {
            AtaLights.tick();
        }
        assertEquals(40.0, flare.radius(0.0f), 1.0E-9);
    }

    @Test
    @DisplayName("a scale multiplies strength and reach together, as a rocket's flare weakens with height")
    void scale() {
        AtaLights.Light flare = AtaLights.point(Vec3.ZERO).strength(4.0, 40.0).life(0, -1, 0, Fall.LINEAR);
        flare.setScale(0.3);
        assertEquals(1.2, flare.strength(0.0f), 1.0E-9);
        assertEquals(12.0, flare.radius(0.0f), 1.0E-9);
        flare.setScale(-5.0);
        assertEquals(0.0, flare.strength(0.0f), 0.0, "never negative");
    }

    @Test
    @DisplayName("a light that is driven moves with its owner and is interpolated between ticks; one that follows is asked")
    void movement() {
        AtaLights.Light moved = AtaLights.point(new Vec3(0, 0, 0)).strength(1.0, 5.0).life(0, -1, 0, Fall.LINEAR);
        moved.moveTo(new Vec3(10, 0, 0));
        assertEquals(0.0, moved.position(0.0f).x, 1.0E-12);
        assertEquals(5.0, moved.position(0.5f).x, 1.0E-12);
        assertEquals(10.0, moved.position(1.0f).x, 1.0E-12);

        AtaLights.Light followed = AtaLights.attach(partial -> new Vec3(partial * 4.0, 0, 0), 1f, 1f, 1f, 2.0, 8.0);
        assertEquals(2.0, followed.position(0.5f).x, 1.0E-12);
        assertEquals(2.0, followed.strength(0.0f), 1.0E-12, "attached lights last until they are put out");

        AtaLights.Light rising = AtaLights.point(Vec3.ZERO).strength(1.0, 5.0).life(0, -1, 0, Fall.LINEAR)
                .drift(new Vec3(0.0, 0.05, 0.0));
        for (int i = 0; i < 20; i++) {
            AtaLights.tick();
        }
        assertEquals(1.0, rising.position(1.0f).y, 1.0E-9, "a fireball's light climbs 0.05 a tick");
    }

    @Test
    @DisplayName("a light put out goes at the next tick, and the list can be cleared and asked by id")
    void putOut() {
        AtaLights.Light a = AtaLights.point(Vec3.ZERO).strength(1.0, 5.0).life(0, -1, 0, Fall.LINEAR);
        AtaLights.Light b = AtaLights.point(Vec3.ZERO).strength(1.0, 5.0).life(0, -1, 0, Fall.LINEAR);
        assertEquals(2, AtaLights.all().size());
        assertNotNull(AtaLights.byId(a.id));
        AtaLights.remove(a);
        assertFalse(a.alive());
        assertEquals(0.0, a.strength(0.0f), 0.0);
        AtaLights.tick();
        assertEquals(1, AtaLights.all().size());
        assertTrue(b.alive());
        AtaLights.clear();
        assertTrue(AtaLights.isEmpty());
        assertNull(AtaLights.byId(99999));
    }

    @Test
    @DisplayName("a spot has its own cone, a point does not, and both can ask for shadows and take no tint")
    void kinds() {
        AtaLights.Light spot = AtaLights.spot(Vec3.ZERO).cone(new Vec3(0, 0, 3), 18.0, 32.0).strength(3.5, 36.0)
                .shadows(true).tintsParticles(false);
        assertEquals(AtaLights.Kind.SPOT, spot.kind());
        assertEquals(1.0, spot.direction().length(), 1.0E-12, "the axis is made a unit vector");
        assertEquals(Math.cos(Math.toRadians(18.0)), spot.cosInner(), 1.0E-12);
        assertEquals(Math.cos(Math.toRadians(32.0)), spot.cosOuter(), 1.0E-12);
        assertTrue(spot.shadows());
        assertFalse(spot.tintsParticles());
        AtaLights.Light point = AtaLights.point(Vec3.ZERO).wrap(0.9);
        assertEquals(AtaLights.Kind.POINT, point.kind());
        assertEquals(0.6, point.wrap(), 0.0, "the wrap is held to 0.6");
        assertTrue(point.tintsParticles());
    }

    private static final LightSelect.SphereTest EVERYTHING = (x, y, z, r) -> true;

    @Test
    @DisplayName("from fifty lights the N with the greatest contribution are taken, the greatest first")
    void picksTheMostValuable() {
        Random rnd = new Random(5);
        List<Candidate> all = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            all.add(new Candidate(i, rnd.nextDouble() * 100.0 - 50.0, rnd.nextDouble() * 20.0, rnd.nextDouble() * 100.0 - 50.0,
                    5.0 + rnd.nextDouble() * 60.0, 0.5 + rnd.nextDouble() * 8.0));
        }
        List<Candidate> picked = LightSelect.pick(all, 0.0, 0.0, 0.0, EVERYTHING, 8);
        assertEquals(8, picked.size());
        for (int i = 1; i < picked.size(); i++) {
            assertTrue(value(picked.get(i - 1)) >= value(picked.get(i)), "not in order at " + i);
        }
        double weakestPicked = value(picked.get(picked.size() - 1));
        for (Candidate c : all) {
            if (!picked.contains(c)) {
                assertTrue(value(c) <= weakestPicked + 1.0E-12, "left out a stronger one: " + c);
            }
        }
        assertEquals(50, LightSelect.pick(all, 0.0, 0.0, 0.0, EVERYTHING, 100).size(), "all of them when there is room");
    }

    private static double value(Candidate c) {
        return LightSelect.contribution(c.intensity(), c.radius(), c.x() * c.x() + c.y() * c.y() + c.z() * c.z());
    }

    @Test
    @DisplayName("a light's worth is I R squared over the squared distance, with the distance never under a block")
    void contribution() {
        assertEquals(10.0 * 100.0 / 4.0, LightSelect.contribution(10.0, 10.0, 4.0), 1.0E-12);
        assertEquals(10.0 * 100.0, LightSelect.contribution(10.0, 10.0, 0.0), 1.0E-12, "at the light");
        assertEquals(10.0 * 100.0, LightSelect.contribution(10.0, 10.0, 0.25), 1.0E-12, "closer than a block counts as a block");
    }

    @Test
    @DisplayName("lights beyond 192 blocks, outside the view, or with nothing to give are dropped")
    void dropsWhatDoesNotCount() {
        List<Candidate> all = List.of(
                new Candidate(1, 10, 0, 0, 20, 5.0),
                new Candidate(2, 191, 0, 0, 20, 5.0),
                new Candidate(3, 193, 0, 0, 20, 5.0),
                new Candidate(4, 0, 0, -300, 90, 50.0),
                new Candidate(5, 5, 0, 5, 20, 0.0),
                new Candidate(6, 5, 0, 5, 0.0, 5.0),
                new Candidate(7, -20, 0, 0, 20, 5.0));
        LightSelect.SphereTest ahead = (x, y, z, r) -> x + r > 0.0;
        List<Candidate> picked = LightSelect.pick(all, 0.0, 0.0, 0.0, ahead, 32);
        List<Integer> ids = picked.stream().map(Candidate::id).toList();
        assertTrue(ids.contains(1) && ids.contains(2));
        assertFalse(ids.contains(3), "too far");
        assertFalse(ids.contains(4), "farther than 192 however strong");
        assertFalse(ids.contains(5), "no strength");
        assertFalse(ids.contains(6), "no reach");
        assertFalse(ids.contains(7), "behind the view");
        assertEquals(2, picked.size());
    }

    @Test
    @DisplayName("ties are broken by id, so the choice does not flicker from frame to frame")
    void stableChoice() {
        List<Candidate> all = new ArrayList<>();
        for (int i = 10; i > 0; i--) {
            all.add(new Candidate(i, 10.0, 0.0, 0.0, 20.0, 3.0));
        }
        List<Candidate> picked = LightSelect.pick(all, 0.0, 0.0, 0.0, EVERYTHING, 4);
        assertEquals(List.of(1, 2, 3, 4), picked.stream().map(Candidate::id).toList());
    }

    @Test
    @DisplayName("only lights that are lit at all become candidates")
    void candidatesAreLit() {
        AtaLights.point(Vec3.ZERO).strength(0.0, 10.0).life(0, -1, 0, Fall.LINEAR);
        AtaLights.point(Vec3.ZERO).strength(2.0, 10.0).life(0, -1, 0, Fall.LINEAR);
        AtaLights.point(Vec3.ZERO).strength(2.0, 10.0).life(5, 5, 5, Fall.LINEAR);
        assertEquals(1, AtaLights.candidates(0.0f).size(), "the one that is lit; the others have nothing to give yet");
        assertEquals(2, AtaLights.candidates(1.0f).size(), "a moment on, the one that was rising is in as well");
    }
}
