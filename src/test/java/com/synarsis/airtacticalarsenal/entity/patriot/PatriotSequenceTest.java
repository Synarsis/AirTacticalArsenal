package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Gate;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Op;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot sequence")
class PatriotSequenceTest {

    private static PatriotSequence launcher() {
        return new PatriotSequence(PatriotKind.LAUNCHER);
    }

    private static PatriotSequence radar() {
        return new PatriotSequence(PatriotKind.RADAR);
    }

    private static int run(PatriotSequence s) {
        return run(s, Gate.OPEN);
    }

    private static int run(PatriotSequence s, Gate gate) {
        int ticks = 0;
        while (s.busy()) {
            s.tick(gate);
            ticks++;
            assertTrue(ticks < 5000, "the sequence does not finish");
        }
        return ticks;
    }

    private static void ok(PatriotSequence s, Command c) {
        assertEquals(PatriotRefusal.NONE, s.command(c, true), c.toString());
    }

    private static PatriotSequence deployed(PatriotKind kind) {
        PatriotSequence s = new PatriotSequence(kind);
        ok(s, Command.DEPLOY);
        run(s);
        return s;
    }

    @Test
    @DisplayName("DEPLOY puts the supports out and then raises the launcher, in the durations of the table")
    void deployLauncher() {
        PatriotSequence s = launcher();
        ok(s, Command.DEPLOY);
        int ticks = 0;
        while (!s.supports()) {
            s.tick(Gate.OPEN);
            ticks++;
        }
        assertEquals(PatriotTiming.SUPPORTS_TICKS, ticks);
        assertFalse(s.lift());
        assertEquals(Op.RAISE, s.current());
        ticks += run(s);
        assertEquals(PatriotTiming.SUPPORTS_TICKS + PatriotTiming.ERECTOR_TICKS, ticks);
        assertTrue(s.supports() && s.lift());
        assertFalse(s.busy());
    }

    @Test
    @DisplayName("DEPLOY of the radar raises the antenna in its own duration")
    void deployRadar() {
        PatriotSequence s = radar();
        ok(s, Command.DEPLOY);
        assertEquals(PatriotTiming.SUPPORTS_TICKS + PatriotTiming.ANTENNA_TICKS, run(s));
        assertTrue(s.supports() && s.lift());
    }

    @Test
    @DisplayName("STOW lowers, closes the hatches, brings the tractor back and retracts the supports, in that order")
    void stow() {
        PatriotSequence s = deployed(PatriotKind.LAUNCHER);
        ok(s, Command.STOW);
        assertEquals(PatriotTiming.ERECTOR_TICKS + PatriotTiming.SUPPORTS_TICKS, run(s));
        assertFalse(s.supports() || s.lift());
        assertTrue(s.canDrive());

        PatriotSequence r = deployed(PatriotKind.RADAR);
        ok(r, Command.SERVICE_OPEN);
        run(r);
        assertTrue(r.service());
        ok(r, Command.STOW);
        assertEquals(PatriotTiming.ANTENNA_TICKS + PatriotTiming.SERVICE_TICKS + PatriotTiming.SUPPORTS_TICKS, run(r));
        assertTrue(r.canDrive());
    }

    @Test
    @DisplayName("STOW of a detached, raised launcher with the turret turned centres, lowers, attaches and retracts")
    void stowFromEverything() {
        PatriotSequence s = deployed(PatriotKind.LAUNCHER);
        ok(s, Command.TRAVERSE_RIGHT);
        run(s);
        for (int i = 0; i < 30; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(PatriotSequence.TRAVERSE_STEP, s.traverse(), 1.0E-9);
        ok(s, Command.DETACH);
        run(s);
        assertTrue(s.tractorOut() && s.latchOpen());
        ok(s, Command.STOW);
        int ticks = run(s);
        int expected = (int) Math.ceil(PatriotSequence.TRAVERSE_STEP * 2.0) + PatriotTiming.ERECTOR_TICKS
                + PatriotTiming.TRACTOR_TICKS + PatriotTiming.LATCH_TICKS + PatriotTiming.SUPPORTS_TICKS;
        assertEquals(expected, ticks, 2);
        assertEquals(0.0, s.traverse(), 1.0E-9);
        assertTrue(s.canDrive());
        assertTrue(s.attached());
    }

    @Test
    @DisplayName("every command checks its conditions against the state it would find")
    void conditions() {
        PatriotSequence s = launcher();
        assertEquals(PatriotRefusal.NEED_SUPPORTS, s.can(Command.LIFT_UP, true));
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.SUPPORTS_IN, true));
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.LIFT_DOWN, true));
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.ATTACH, true));
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.STOW, true));
        assertEquals(PatriotRefusal.MOVING, s.can(Command.DEPLOY, false));
        assertEquals(PatriotRefusal.MOVING, s.can(Command.DETACH, false));
        assertEquals(PatriotRefusal.MOVING, s.can(Command.SUPPORTS_OUT, false));
        assertEquals(PatriotRefusal.NEED_LIFT_UP, s.can(Command.TRAVERSE_LEFT, true));
        assertEquals(PatriotRefusal.WRONG_VEHICLE, s.can(Command.SERVICE_OPEN, true));
        assertEquals(PatriotRefusal.WRONG_VEHICLE, radar().can(Command.TRAVERSE_LEFT, true));

        PatriotSequence up = deployed(PatriotKind.LAUNCHER);
        assertEquals(PatriotRefusal.ALREADY, up.can(Command.DEPLOY, true));
        assertEquals(PatriotRefusal.LIFT_NOT_DOWN, up.can(Command.SUPPORTS_IN, true));
        assertEquals(PatriotRefusal.ALREADY, up.can(Command.LIFT_UP, true));
        assertEquals(PatriotRefusal.NONE, up.can(Command.LIFT_DOWN, false));

        PatriotSequence r = deployed(PatriotKind.RADAR);
        ok(r, Command.SERVICE_OPEN);
        run(r);
        ok(r, Command.LIFT_DOWN);
        run(r);
        assertEquals(PatriotRefusal.SERVICE_OPEN, r.can(Command.SUPPORTS_IN, true));
        assertEquals(PatriotRefusal.ALREADY, r.can(Command.SERVICE_OPEN, true));
        assertEquals(PatriotRefusal.NONE, r.can(Command.SERVICE_CLOSE, true));
    }

    @Test
    @DisplayName("a command during an operation joins the queue and is judged by the state the queue leads to")
    void queue() {
        PatriotSequence s = launcher();
        ok(s, Command.SUPPORTS_OUT);
        s.tick(Gate.OPEN);
        assertEquals(PatriotRefusal.NONE, s.can(Command.LIFT_UP, true));
        ok(s, Command.LIFT_UP);
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.DEPLOY, true));
        assertEquals(PatriotRefusal.LIFT_NOT_DOWN, s.can(Command.SUPPORTS_IN, true));
        ok(s, Command.LIFT_DOWN);
        ok(s, Command.SUPPORTS_IN);
        int ticks = 1 + run(s);
        assertEquals(PatriotTiming.SUPPORTS_TICKS * 2 + PatriotTiming.ERECTOR_TICKS * 2, ticks);
        assertTrue(s.canDrive());
    }

    @Test
    @DisplayName("the queue has a limit")
    void queueLimit() {
        PatriotSequence s = deployed(PatriotKind.RADAR);
        int accepted = 0;
        for (int i = 0; i < 20; i++) {
            Command next = i % 2 == 0 ? Command.SERVICE_OPEN : Command.SERVICE_CLOSE;
            if (s.command(next, true).ok()) {
                accepted++;
            }
        }
        assertEquals(PatriotSequence.QUEUE_LIMIT, accepted);
        assertEquals(PatriotRefusal.BUSY, s.command(Command.SERVICE_OPEN, true));
    }

    @Test
    @DisplayName("DETACH goes supports, latch, tractor; ATTACH goes tractor, latch and leaves the supports out")
    void detachAndAttach() {
        PatriotSequence s = launcher();
        ok(s, Command.DETACH);
        int unlatched = 0;
        int ticks = 0;
        while (s.busy()) {
            s.tick(Gate.OPEN);
            ticks++;
            if (s.finished() == Op.UNLATCH) {
                unlatched = ticks;
                assertFalse(s.linked(), "the link goes when the latch opens");
            }
        }
        assertEquals(PatriotTiming.SUPPORTS_TICKS + PatriotTiming.LATCH_TICKS, unlatched);
        assertEquals(PatriotTiming.SUPPORTS_TICKS + PatriotTiming.LATCH_TICKS + PatriotTiming.TRACTOR_TICKS, ticks);
        assertTrue(s.supports() && s.tractorOut() && s.latchOpen() && s.detached());
        assertFalse(s.canDrive());
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.DETACH, true));
        assertEquals(PatriotRefusal.NEED_COUPLED, s.can(Command.SUPPORTS_IN, true));

        ok(s, Command.ATTACH);
        int back = run(s);
        assertEquals(PatriotTiming.TRACTOR_TICKS + PatriotTiming.LATCH_TICKS, back);
        assertTrue(s.attached() && s.linked() && s.supports());
        assertFalse(s.canDrive());
        ok(s, Command.SUPPORTS_IN);
        run(s);
        assertTrue(s.canDrive());
    }

    @Test
    @DisplayName("when the place check refuses an operation the queue is dropped and the reason is kept")
    void gateDropsTheQueue() {
        PatriotSequence s = launcher();
        ok(s, Command.DETACH);
        Gate noRoom = op -> op == Op.TRACTOR_OUT
                ? new PatriotRefusal.Denial(PatriotRefusal.TRACTOR_NO_ROOM, "1 2 3") : PatriotRefusal.Denial.NONE;
        run(s, noRoom);
        PatriotRefusal.Denial denial = s.takeDropped();
        assertEquals(PatriotRefusal.TRACTOR_NO_ROOM, denial.reason());
        assertEquals("1 2 3", denial.args()[0]);
        assertTrue(s.takeDropped().ok());
        assertTrue(s.supports() && s.latchOpen() && !s.tractorOut());
        assertFalse(s.linked());
        assertEquals(PatriotRefusal.NONE, s.can(Command.ATTACH, true));
        ok(s, Command.ATTACH);
        assertEquals(PatriotTiming.LATCH_TICKS, run(s));
        assertTrue(s.attached());

        ok(s, Command.DETACH);
        assertEquals(PatriotTiming.LATCH_TICKS + PatriotTiming.TRACTOR_TICKS, run(s));
    }

    @Test
    @DisplayName("the turret turns by steps at 10 degrees a second, stops at the limit, and comes home before lowering")
    void traverse() {
        PatriotSequence s = deployed(PatriotKind.LAUNCHER);
        for (int i = 0; i < 4; i++) {
            assertEquals(PatriotRefusal.NONE, s.command(Command.TRAVERSE_RIGHT, true));
        }
        assertEquals(PatriotRefusal.TRAVERSE_LIMIT, s.command(Command.TRAVERSE_RIGHT, true));
        assertEquals(24.0, s.traverseTarget(), 1.0E-9);
        for (int i = 0; i < 12; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(6.0, s.traverse(), 1.0E-9);
        for (int i = 0; i < 200; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(24.0, s.traverse(), 1.0E-9);
        for (int i = 0; i < 9; i++) {
            s.command(Command.TRAVERSE_LEFT, true);
        }
        assertEquals(-24.0, s.traverseTarget(), 1.0E-9);
        assertEquals(PatriotRefusal.TRAVERSE_LIMIT, s.command(Command.TRAVERSE_LEFT, true));

        assertEquals(PatriotRefusal.NONE, s.command(Command.LIFT_DOWN, true));
        int ticks = run(s);
        assertTrue(ticks >= 48 + PatriotTiming.ERECTOR_TICKS - 2, "ticks " + ticks);
        assertEquals(0.0, s.traverse(), 1.0E-9);
        assertFalse(s.lift());
    }

    @Test
    @DisplayName("the turret only turns while raised and idle")
    void traverseNeedsLiftAndIdle() {
        PatriotSequence s = launcher();
        assertEquals(PatriotRefusal.NEED_LIFT_UP, s.can(Command.TRAVERSE_LEFT, true));
        ok(s, Command.DEPLOY);
        assertEquals(PatriotRefusal.BUSY, s.can(Command.TRAVERSE_LEFT, true));
        run(s);
        assertEquals(PatriotRefusal.ALREADY, s.can(Command.TRAVERSE_CENTER, true));
        assertEquals(PatriotRefusal.NONE, s.can(Command.TRAVERSE_LEFT, true));
    }

    @Test
    @DisplayName("each group reports the clip it stands in, and the age it has been in it")
    void groupClips() {
        PatriotSequence s = launcher();
        assertEquals(TelPose.Clip.REST, s.groupClip(PatriotBones.SUPPORTS));
        ok(s, Command.DEPLOY);
        for (int i = 0; i < 10; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(TelPose.Clip.OPENING, s.groupClip(PatriotBones.SUPPORTS));
        assertEquals(10, s.groupAge(PatriotBones.SUPPORTS));
        assertEquals(TelPose.Clip.REST, s.groupClip(PatriotBones.LIFT));
        run(s);
        assertEquals(TelPose.Clip.HELD, s.groupClip(PatriotBones.SUPPORTS));
        assertEquals(TelPose.Clip.HELD, s.groupClip(PatriotBones.LIFT));
        ok(s, Command.LIFT_DOWN);
        s.tick(Gate.OPEN);
        assertEquals(TelPose.Clip.CLOSING, s.groupClip(PatriotBones.LIFT));
        assertEquals(TelPose.Clip.HELD, s.groupClip(PatriotBones.SUPPORTS));
        run(s);
        assertEquals(TelPose.Clip.REST, s.groupClip(PatriotBones.LIFT));
    }

    @Test
    @DisplayName("the doors open for 36 ticks, hold, and close for 36; a new pulse while they are open holds them longer")
    void doors() {
        PatriotSequence s = launcher();
        s.pulseDoors();
        assertEquals(TelPose.Clip.OPENING, s.groupClip(PatriotBones.DOORS));
        for (int i = 0; i < PatriotTiming.DOORS_TICKS; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(TelPose.Clip.HELD, s.groupClip(PatriotBones.DOORS));
        for (int i = 0; i < 20; i++) {
            s.tick(Gate.OPEN);
        }
        s.pulseDoors();
        for (int i = 0; i < PatriotSequence.DOOR_HOLD_TICKS - 1; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(TelPose.Clip.HELD, s.groupClip(PatriotBones.DOORS));
        s.tick(Gate.OPEN);
        assertEquals(TelPose.Clip.CLOSING, s.groupClip(PatriotBones.DOORS));
        for (int i = 0; i < PatriotTiming.DOORS_TICKS; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(TelPose.Clip.REST, s.groupClip(PatriotBones.DOORS));
        assertFalse(s.busy(), "the doors are only a pose");
    }

    @Test
    @DisplayName("a pulse while the doors are closing reopens them once they are shut, never mid-swing")
    void doorsReopen() {
        PatriotSequence s = launcher();
        s.pulseDoors();
        for (int i = 0; i < PatriotTiming.DOORS_TICKS + PatriotSequence.DOOR_HOLD_TICKS + 5; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(TelPose.Clip.CLOSING, s.groupClip(PatriotBones.DOORS));
        s.pulseDoors();
        assertEquals(TelPose.Clip.CLOSING, s.groupClip(PatriotBones.DOORS));
        for (int i = 0; i < PatriotTiming.DOORS_TICKS - 5; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(TelPose.Clip.OPENING, s.groupClip(PatriotBones.DOORS));
        assertEquals(0, s.groupAge(PatriotBones.DOORS));
    }

    @Test
    @DisplayName("the tractor and latch progress follow the operation, smoothly and in the right direction")
    void progress() {
        PatriotSequence s = launcher();
        ok(s, Command.DETACH);
        while (s.current() != Op.TRACTOR_OUT) {
            s.tick(Gate.OPEN);
        }
        assertEquals(0.0, s.tractorProgress(0.0f), 1.0E-9);
        assertEquals(1.0, s.latchProgress(0.0f), 1.0E-9);
        for (int i = 0; i < PatriotTiming.TRACTOR_TICKS / 2; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(0.5, s.tractorProgress(0.0f), 1.0E-9);
        run(s);
        assertEquals(1.0, s.tractorProgress(0.0f), 1.0E-9);
        ok(s, Command.ATTACH);
        s.tick(Gate.OPEN);
        assertEquals(Op.TRACTOR_IN, s.current());
        assertTrue(s.tractorProgress(0.0f) > 0.99);
        run(s);
        assertEquals(0.0, s.tractorProgress(0.0f), 1.0E-9);
        assertEquals(0.0, s.latchProgress(0.0f), 1.0E-9);
    }

    @Test
    @DisplayName("loading from a save in the middle of an operation finishes it at once, queue included")
    void restoreFinishesInstantly() {
        PatriotSequence s = launcher();
        ok(s, Command.DETACH);
        for (int i = 0; i < PatriotTiming.SUPPORTS_TICKS + 5; i++) {
            s.tick(Gate.OPEN);
        }
        assertEquals(Op.UNLATCH, s.current());
        PatriotSequence.Snapshot saved = s.snapshot();
        assertEquals(1, saved.queue().length);

        PatriotSequence loaded = launcher();
        loaded.restore(saved);
        assertFalse(loaded.busy());
        assertNull(loaded.current());
        assertTrue(loaded.supports() && loaded.latchOpen() && loaded.tractorOut());
        assertEquals(1.0, loaded.tractorProgress(0.0f), 1.0E-9);

        PatriotSequence mid = launcher();
        ok(mid, Command.DEPLOY);
        for (int i = 0; i < PatriotTiming.SUPPORTS_TICKS + 10; i++) {
            mid.tick(Gate.OPEN);
        }
        PatriotSequence copy = launcher();
        copy.restore(mid.snapshot());
        assertTrue(copy.supports() && copy.lift() && !copy.busy());
    }

    @Test
    @DisplayName("a turret that was turning keeps its target when loaded")
    void restoreKeepsTraverse() {
        PatriotSequence s = deployed(PatriotKind.LAUNCHER);
        ok(s, Command.TRAVERSE_LEFT);
        ok(s, Command.TRAVERSE_LEFT);
        PatriotSequence loaded = launcher();
        loaded.restore(s.snapshot());
        assertEquals(-12.0, loaded.traverse(), 1.0E-9);
        assertEquals(-12.0, loaded.traverseTarget(), 1.0E-9);
    }

    @Test
    @DisplayName("skip finishes the current operation at once and leaves the queue")
    void skip() {
        PatriotSequence s = launcher();
        ok(s, Command.DEPLOY);
        s.tick(Gate.OPEN);
        assertTrue(s.skip(Gate.OPEN));
        assertTrue(s.supports());
        assertNull(s.current());
        assertEquals(1, s.queued());
        assertTrue(s.skip(Gate.OPEN));
        assertTrue(s.lift());
        assertFalse(s.skip(Gate.OPEN));
    }

    @Test
    @DisplayName("a view built from the synchronised numbers judges buttons like the real sequence")
    void view() {
        PatriotSequence s = deployed(PatriotKind.LAUNCHER);
        PatriotSequence copy = PatriotSequence.view(PatriotKind.LAUNCHER, s.mask(), -1, s.traverse(),
                s.traverseTarget());
        for (Command c : Command.values()) {
            assertEquals(s.can(c, true), copy.can(c, true), c.toString());
        }
        assertEquals(s.mask(), copy.mask());
        PatriotSequence busy = PatriotSequence.view(PatriotKind.LAUNCHER, 0, Op.SUPPORTS_OUT.ordinal(), 0, 0);
        assertTrue(busy.busy());
        assertEquals(PatriotRefusal.ALREADY, busy.can(Command.SUPPORTS_OUT, true));
        assertEquals(PatriotRefusal.NONE, busy.can(Command.LIFT_UP, true));
    }

    @Test
    @DisplayName("driving is allowed only when coupled, supports in, nothing raised, hatches shut and no operation")
    void driveConditions() {
        PatriotSequence s = launcher();
        assertTrue(s.canDrive());
        assertEquals(PatriotRefusal.NONE, s.whyNotDrive());
        ok(s, Command.SUPPORTS_OUT);
        assertEquals(PatriotRefusal.BUSY, s.whyNotDrive());
        run(s);
        assertEquals(PatriotRefusal.NEED_SUPPORTS, s.whyNotDrive());
        PatriotSequence r = deployed(PatriotKind.RADAR);
        assertEquals(PatriotRefusal.NEED_SUPPORTS, r.whyNotDrive());
        PatriotSequence snapshot = PatriotSequence.view(PatriotKind.RADAR, PatriotSequence.SERVICE_BIT, -1, 0, 0);
        assertEquals(PatriotRefusal.SERVICE_OPEN, snapshot.whyNotDrive());
        PatriotSequence lifted = PatriotSequence.view(PatriotKind.RADAR, PatriotSequence.LIFT_BIT, -1, 0, 0);
        assertEquals(PatriotRefusal.LIFT_NOT_DOWN, lifted.whyNotDrive());
        PatriotSequence detached = PatriotSequence.view(PatriotKind.RADAR, PatriotSequence.TRACTOR_OUT_BIT, -1, 0, 0);
        assertEquals(PatriotRefusal.NEED_COUPLED, detached.whyNotDrive());
    }
}
