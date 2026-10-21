package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Refusal;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose.Clip;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelBoneGroups;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Launcher cycle")
class TelSequenceTest {

    private static final class Rig {
        final TelSequence seq = new TelSequence();
        int pockets;
        int fired;
        final List<Integer> launchTicks = new ArrayList<>();
        final List<Integer> launchSlots = new ArrayList<>();
        int totalTicks;
        int launchedAtState;

        Rig(int pockets, boolean load0, boolean load1) {
            this.pockets = pockets;
            this.seq.setLoaded(0, load0);
            this.seq.setLoaded(1, load1);
        }

        int total() {
            int bay = (this.seq.isLoaded(0) ? 1 : 0) + (this.seq.isLoaded(1) ? 1 : 0);
            return this.pockets + bay + (this.seq.roundHeld() ? 1 : 0) + this.fired;
        }

        void tick() {
            this.totalTicks++;
            TelSequence.Event e = this.seq.tick();
            int slot = this.seq.pendingSlot();
            switch (e) {
                case LOAD_COMMIT -> this.seq.setLoaded(slot, true);
                case UNLOAD_COMMIT -> {
                    this.seq.setLoaded(slot, false);
                    this.pockets++;
                }
                case LAUNCH_0 -> this.launch(0);
                case LAUNCH_1 -> this.launch(1);
                default -> {
                }
            }
        }

        void launch(int slot) {
            this.seq.setLoaded(slot, false);
            this.fired++;
            this.launchTicks.add(this.totalTicks);
            this.launchSlots.add(slot);
        }

        void run(int ticks) {
            for (int i = 0; i < ticks; i++) {
                this.tick();
            }
        }

        void settle() {
            for (int i = 0; i < 2000 && (this.seq.state().isTransition()); i++) {
                this.tick();
            }
            assertFalse(this.seq.state().isTransition(), "never settled from " + this.seq.state());
        }

        Refusal load(int slot) {
            if (this.pockets <= 0) {
                return Refusal.NO_ROUND_IN_INVENTORY;
            }
            Refusal r = this.seq.tryLoad(slot);
            if (r.ok()) {
                this.pockets--;
            }
            return r;
        }
    }

    private static Rig rig(boolean a, boolean b) {
        return new Rig(0, a, b);
    }

    private static void assertPoseMatchesFlags(TelSequence s) {
        if (s.state().isTransition()) {
            return;
        }
        for (int g = 0; g < TelBoneGroups.COUNT; g++) {
            assertFalse(s.groupClip(g).moving(), "group " + g + " moves in the holding state " + s.state());
        }
        assertEquals(s.coversOpen() ? Clip.HELD : Clip.REST, s.groupClip(TelBoneGroups.COVERS),
                "the covers flag and the covers' pose disagree in " + s.state());
        for (int slot = 0; slot < 2; slot++) {
            boolean up = (s.raisedMask() & (1 << slot)) != 0;
            assertEquals(up ? Clip.HELD : Clip.REST, s.groupClip(TelBoneGroups.liftOf(slot)),
                    "erector " + slot + " and the raised mask disagree in " + s.state());
        }
    }

    private static final class Watch {
        final Clip[] clip = new Clip[TelBoneGroups.COUNT];
        final int[] lastAge = new int[TelBoneGroups.COUNT];
        final int[] level = new int[TelBoneGroups.COUNT];
        int changes;

        void observe(TelSequence s) {
            for (int g = 0; g < TelBoneGroups.COUNT; g++) {
                Clip now = s.groupClip(g);
                if (this.clip[g] != null && now != this.clip[g]) {
                    this.changes++;
                    Clip was = this.clip[g];
                    if (was.moving()) {
                        assertTrue(this.lastAge[g] >= TelPose.ticks(g) - 1,
                                "group " + g + " " + was + " was cut short at " + this.lastAge[g] + " in " + s.state());
                    }
                    assertEquals(this.level[g], now.startLevel(),
                            "group " + g + " jumped from the end of " + was + " to the start of " + now + " in " + s.state());
                }
                this.clip[g] = now;
                this.lastAge[g] = s.groupAge(g);
                this.level[g] = now.endLevel();
            }
            assertPoseMatchesFlags(s);
        }
    }

    private static boolean command(Rig r, int which, Random rnd) {
        TelSequence s = r.seq;
        return switch (which) {
            case 0 -> s.tryCovers().ok();
            case 1 -> r.load(rnd.nextInt(2)).ok();
            case 2 -> s.tryUnload(rnd.nextInt(2)).ok();
            case 3 -> s.tryPrepare(1 + rnd.nextInt(3)).ok();
            case 4 -> s.tryLaunch().ok();
            case 5 -> s.abort();
            case 6 -> s.tryLower(1 + rnd.nextInt(3)).ok();
            case 7 -> s.tryStow().ok();
            default -> {
                s.setIntervalSeconds(1 + rnd.nextInt(60));
                yield true;
            }
        };
    }

    @Test
    @DisplayName("a long random run of orders never jumps between poses and never makes a round from nothing")
    void randomRun() {
        for (long seed = 1; seed <= 40; seed++) {
            Random rnd = new Random(seed);
            Rig r = new Rig(rnd.nextInt(4), rnd.nextBoolean(), rnd.nextBoolean());
            int total = r.total();
            Watch watch = new Watch();
            int accepted = 0;
            for (int step = 0; step < 400; step++) {
                if (command(r, rnd.nextInt(9), rnd)) {
                    accepted++;
                }
                int ticks = 1 + rnd.nextInt(rnd.nextBoolean() ? 12 : 220);
                for (int i = 0; i < ticks; i++) {
                    r.tick();
                    watch.observe(r.seq);
                    assertEquals(total, r.total(), "rounds appeared or vanished, seed " + seed);
                }
            }
            assertTrue(accepted > 40, "the run should have actually done things, seed " + seed);
        }
    }

    @Test
    @DisplayName("loading is refused with the covers shut: the crew opens them first")
    void loadNeedsOpenCovers() {
        Rig r = new Rig(1, false, false);
        assertEquals(Refusal.COVERS_CLOSED, r.seq.canLoad(0));
        assertEquals(Refusal.COVERS_CLOSED, r.load(0));
        assertEquals(TelState.STOWED, r.seq.state());
        assertFalse(r.seq.roundHeld());
        assertEquals(1, r.pockets, "nothing was taken");
        Rig full = new Rig(0, true, false);
        assertEquals(Refusal.COVERS_CLOSED, full.seq.canUnload(0));
    }

    @Test
    @DisplayName("a round goes in over the loading time with the covers standing still, and comes out the same way")
    void loadWithOpenCovers() {
        Rig r = new Rig(2, false, true);
        assertTrue(r.seq.tryCovers().ok());
        r.settle();
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertEquals(160, r.seq.loadTicks(), "eight seconds by default");

        assertEquals(Refusal.NONE, r.load(0));
        assertEquals(TelState.COVER_LOAD, r.seq.state());
        assertTrue(r.seq.roundHeld());
        r.run(159);
        assertFalse(r.seq.isLoaded(0));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS), "the covers do not move");
        r.run(1);
        assertTrue(r.seq.isLoaded(0));
        assertEquals(TelState.COVERS_OPEN, r.seq.state());

        assertEquals(Refusal.NONE, r.seq.tryUnload(1));
        r.settle();
        assertFalse(r.seq.isLoaded(1));
        assertEquals(2, r.pockets, "the unloaded round went back to the crew");
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
    }

    @Test
    @DisplayName("the loading time is a setting")
    void loadTimeIsConfigurable() {
        Rig r = new Rig(1, false, false);
        r.seq.setLoadTicks(60);
        r.seq.tryCovers();
        r.settle();
        r.load(1);
        assertEquals(60, r.seq.durationOf(TelState.COVER_LOAD));
        r.run(59);
        assertFalse(r.seq.isLoaded(1));
        r.run(1);
        assertTrue(r.seq.isLoaded(1));
    }

    @Test
    @DisplayName("a full slot cannot be loaded and an empty one cannot be unloaded")
    void slotChecks() {
        Rig r = new Rig(2, true, false);
        r.seq.tryCovers();
        r.settle();
        assertEquals(Refusal.SLOT_FULL, r.seq.canLoad(0));
        assertEquals(Refusal.SLOT_EMPTY, r.seq.canUnload(1));
        assertEquals(Refusal.NONE, r.seq.canLoad(1));
        assertEquals(Refusal.NONE, r.seq.canUnload(0));
    }

    @Test
    @DisplayName("a round taken for loading is given back when the loading comes to nothing")
    void heldRoundComesBack() {
        Rig r = new Rig(1, false, false);
        r.seq.tryCovers();
        r.settle();
        assertEquals(Refusal.NONE, r.load(1));
        r.run(10);
        assertTrue(r.seq.roundHeld());
        assertTrue(r.seq.dropHeldRound(), "there was one to give back");
        assertFalse(r.seq.roundHeld());
        assertFalse(r.seq.dropHeldRound(), "and only once");
        r.seq.setLoaded(0, false);
        r.pockets++;
        assertEquals(1, r.pockets);
        r.run(300);
        assertFalse(r.seq.isLoaded(1), "a cancelled loading puts nothing in the bay");
    }

    @Test
    @DisplayName("loading is refused while anything else is under way")
    void loadWhileBusy() {
        Rig r = new Rig(2, true, true);
        assertTrue(r.seq.tryPrepare(1).ok());
        assertEquals(Refusal.BUSY, r.seq.canLoad(0));
        r.settle();
        r.settle();
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(Refusal.BUSY, r.seq.canLoad(0));
        assertEquals(Refusal.BUSY, r.seq.canUnload(0));
    }

    @Test
    @DisplayName("covers open and close by themselves, and the jacks come up if they were down")
    void covers() {
        Rig r = rig(true, true);
        assertEquals(Refusal.NONE, r.seq.tryCovers());
        assertEquals(TelState.COVERS_OPENING, r.seq.state());
        r.settle();
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS));
        assertEquals(0.0f, r.seq.jackProgress(0), 0.0);

        assertEquals(Refusal.NONE, r.seq.tryCovers());
        r.settle();
        assertEquals(TelState.STOWED, r.seq.state());

        assertTrue(r.seq.tryPrepare(1).ok());
        r.settle();
        assertEquals(TelState.RAISED, r.seq.state());
        r.settle();
        assertTrue(r.seq.tryLower(1).ok());
        r.settle();
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertTrue(r.seq.jacksDown());
        assertEquals(1.0f, r.seq.jackProgress(0), 0.0);
        assertTrue(r.seq.tryCovers().ok());
        r.run(56);
        assertEquals(TelState.JACKS_UP, r.seq.state());
        assertEquals(1.0f, r.seq.jackProgress(0), 0.0, "the jacks have not moved yet");
        r.run(15);
        assertEquals(0.5f, r.seq.jackProgress(0), 0.001);
        r.settle();
        assertEquals(TelState.STOWED, r.seq.state());
        assertFalse(r.seq.jacksDown());
    }

    @Test
    @DisplayName("one round from the travelling position: jacks, then the covers, then the lift, then it stands")
    void prepareOneFromStowed() {
        Rig r = rig(true, true);
        assertEquals(Refusal.NONE, r.seq.tryPrepare(1));
        assertEquals(TelState.JACKS_DOWN, r.seq.state());
        r.run(30);
        assertEquals(TelState.DEPLOYING, r.seq.state());
        assertEquals(Clip.OPENING, r.seq.groupClip(TelBoneGroups.COVERS));
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_1), "the lift waits for the covers");
        r.run(55);
        assertEquals(Clip.OPENING, r.seq.groupClip(TelBoneGroups.COVERS));
        r.run(1);
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS));
        assertEquals(Clip.OPENING, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(0, r.seq.groupAge(TelBoneGroups.LIFT_1));
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_2), "the other one stays down");
        r.run(96);
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(1, r.seq.raisedMask());
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertTrue(r.seq.isRaised(0));
        assertFalse(r.seq.isRaised(1));
        assertEquals(1.0f, r.seq.jackProgress(0), 0.0);
        assertTrue(r.seq.coversOpen());
    }

    @Test
    @DisplayName("both rounds rise together from the travelling position")
    void prepareBothFromStowed() {
        Rig r = rig(true, true);
        assertEquals(Refusal.NONE, r.seq.tryPrepare(3));
        r.settle();
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(3, r.seq.raisedMask());
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Refusal.ALREADY_RAISED, r.seq.canPrepare(3));
    }

    @Test
    @DisplayName("both rounds rise together from open covers, and the two erectors move in step")
    void prepareBothFromOpenCovers() {
        Rig r = rig(true, true);
        r.seq.tryCovers();
        r.settle();
        assertEquals(Refusal.NONE, r.seq.canPrepare(3));
        assertEquals(Refusal.NONE, r.seq.tryPrepare(3));
        r.run(30);
        assertEquals(TelState.RAISING, r.seq.state());
        assertEquals(Clip.OPENING, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.OPENING, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(r.seq.groupAge(TelBoneGroups.LIFT_1), r.seq.groupAge(TelBoneGroups.LIFT_2));
        r.run(96);
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(3, r.seq.raisedMask());
    }

    @Test
    @DisplayName("an empty slot cannot be raised")
    void prepareNeedsRound() {
        Rig r = rig(false, true);
        assertEquals(Refusal.NO_MISSILE, r.seq.canPrepare(1));
        assertEquals(Refusal.NO_MISSILE, r.seq.canPrepare(3));
        assertEquals(Refusal.NONE, r.seq.canPrepare(2));
    }

    @Test
    @DisplayName("from open covers one round goes up on its lift, and the jacks come first only if they were up")
    void prepareFromOpenCovers() {
        Rig r = rig(true, true);
        r.seq.tryCovers();
        r.settle();
        assertEquals(Refusal.NONE, r.seq.tryPrepare(2));
        assertEquals(TelState.JACKS_DOWN, r.seq.state());
        r.run(30);
        assertEquals(TelState.RAISING, r.seq.state());
        assertEquals(Clip.OPENING, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_1));
        r.run(96);
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(2, r.seq.raisedMask());

        assertTrue(r.seq.tryLower(2).ok());
        r.settle();
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Refusal.NONE, r.seq.tryPrepare(1));
        assertEquals(TelState.RAISING, r.seq.state());
    }

    @Test
    @DisplayName("round 2 asked for while round 1 stands: round 1 stays up and round 2 rises beside it")
    void secondBesideTheFirst() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(1);
        r.settle();
        assertEquals(TelState.RAISED, r.seq.state());

        assertEquals(Refusal.NONE, r.seq.canPrepare(2));
        assertEquals(Refusal.NONE, r.seq.tryPrepare(2));
        assertEquals(TelState.RAISING, r.seq.state());
        for (int i = 0; i < 96; i++) {
            r.tick();
            assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_1), "round 1 never moves");
            assertEquals(1, r.seq.raisedMask() & 1);
        }
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(3, r.seq.raisedMask());
        assertEquals(Refusal.ALREADY_RAISED, r.seq.canPrepare(2));
        assertEquals(Refusal.ALREADY_RAISED, r.seq.canPrepare(3));
    }

    @Test
    @DisplayName("asking for both while one stands raises only the other")
    void bothWhileOneStands() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(2);
        r.settle();
        assertEquals(Refusal.NONE, r.seq.tryPrepare(3));
        assertEquals(1, r.seq.risingMask(), "only round 1 moves");
        r.settle();
        assertEquals(3, r.seq.raisedMask());
    }

    @Test
    @DisplayName("an emptied erector that still stands does not stop the other round from being raised")
    void emptyStandingLift() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(1);
        r.settle();
        r.seq.tryLaunch();
        r.run(140);
        assertFalse(r.seq.isLoaded(0));
        assertEquals(Refusal.NONE, r.seq.canPrepare(3));
        assertEquals(Refusal.NONE, r.seq.tryPrepare(3));
        r.settle();
        assertEquals(3, r.seq.raisedMask());
    }

    @Test
    @DisplayName("with two standing, either can be lowered alone, or both together")
    void lowerOneOrBoth() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(3);
        r.settle();
        assertEquals(Refusal.NONE, r.seq.canLower(1));
        assertEquals(Refusal.NONE, r.seq.canLower(2));
        assertEquals(Refusal.NONE, r.seq.canLower(3));

        assertEquals(Refusal.NONE, r.seq.tryLower(1));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_2), "the other stays up");
        r.settle();
        assertEquals(TelState.RAISED, r.seq.state(), "one still stands");
        assertEquals(2, r.seq.raisedMask());
        assertEquals(Refusal.NOT_RAISED, r.seq.canLower(1), "that one is already down");

        assertEquals(Refusal.NONE, r.seq.tryLower(3), "asking for both lowers the one that stands");
        r.settle();
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertEquals(0, r.seq.raisedMask());
        assertTrue(r.seq.coversOpen());
    }

    @Test
    @DisplayName("both erectors come down together and leave the covers open")
    void lowerBoth() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(3);
        r.settle();
        assertEquals(Refusal.NONE, r.seq.tryLower(3));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS));
        r.run(96);
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS));
    }

    @Test
    @DisplayName("lowering something that is not up is refused, and so is lowering mid-countdown")
    void lowerRefusals() {
        Rig r = rig(true, true);
        assertEquals(Refusal.NOT_RAISED, r.seq.canLower(1));
        r.seq.tryPrepare(1);
        r.settle();
        assertEquals(Refusal.NOT_RAISED, r.seq.canLower(2), "round 2 is not up");
        r.seq.tryLaunch();
        assertEquals(Refusal.BUSY, r.seq.canLower(1));
    }

    @Test
    @DisplayName("one round leaves after 140 ticks and the machine stays up, empty: nothing folds by itself")
    void noAutoStow() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(1);
        r.settle();
        r.launchTicks.clear();
        r.totalTicks = 0;
        assertEquals(Refusal.NONE, r.seq.tryLaunch());
        assertEquals(TelState.COUNTDOWN, r.seq.state());
        assertEquals(140, r.seq.launchIn(0));
        assertEquals(-1, r.seq.launchIn(1));
        r.run(139);
        assertTrue(r.launchTicks.isEmpty());
        r.run(1);
        assertEquals(List.of(140), r.launchTicks);
        assertEquals(List.of(0), r.launchSlots);
        assertEquals(TelState.RAISED, r.seq.state());

        r.run(5000);
        assertEquals(TelState.RAISED, r.seq.state(), "no folding by itself, however long it waits");
        assertEquals(1, r.seq.raisedMask());
        assertFalse(r.seq.isLoaded(0));
        assertTrue(r.seq.isLoaded(1));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_1), "the erector stays as it stood");
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS));
    }

    @Test
    @DisplayName("a salvo at five seconds goes at 140 and 240")
    void salvo() {
        Rig r = rig(true, true);
        r.seq.setIntervalSeconds(5);
        r.seq.tryPrepare(3);
        r.settle();
        r.totalTicks = 0;
        assertEquals(Refusal.NONE, r.seq.tryLaunch());
        assertEquals(140, r.seq.launchIn(0));
        assertEquals(240, r.seq.launchIn(1));
        r.run(400);
        assertEquals(List.of(140, 240), r.launchTicks);
        assertEquals(List.of(0, 1), r.launchSlots);
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(3, r.seq.raisedMask(), "both erectors stay up");
        assertFalse(r.seq.isLoaded(0));
        assertFalse(r.seq.isLoaded(1));
        assertEquals(Refusal.NO_MISSILE, r.seq.canLaunch());
    }

    @Test
    @DisplayName("the interval is clamped to five to thirty seconds and starts at five")
    void interval() {
        TelSequence s = new TelSequence();
        s.setIntervalSeconds(0);
        assertEquals(5, s.intervalSeconds());
        s.setIntervalSeconds(1);
        assertEquals(5, s.intervalSeconds());
        s.setIntervalSeconds(900);
        assertEquals(30, s.intervalSeconds());
        s.setIntervalSeconds(17);
        assertEquals(17, s.intervalSeconds());
        assertEquals(5, new TelSequence().intervalSeconds());
        assertEquals(5, TelSequence.MIN_INTERVAL_SECONDS);
        assertEquals(30, TelSequence.MAX_INTERVAL_SECONDS);
    }

    @Test
    @DisplayName("a salvo at thirty seconds goes at 140 and 740")
    void longSalvo() {
        Rig r = rig(true, true);
        r.seq.setIntervalSeconds(30);
        r.seq.tryPrepare(3);
        r.settle();
        r.totalTicks = 0;
        r.seq.tryLaunch();
        r.run(800);
        assertEquals(List.of(140, 740), r.launchTicks);
    }

    @Test
    @DisplayName("abort between the two rounds: the first has gone, the second stays loaded and raised")
    void abortMidSalvo() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(3);
        r.settle();
        r.seq.tryLaunch();
        r.run(150);
        assertEquals(1, r.launchTicks.size());
        assertEquals(TelState.COUNTDOWN, r.seq.state());
        assertTrue(r.seq.abort());
        assertEquals(TelState.RAISED, r.seq.state());
        assertFalse(r.seq.isLoaded(0));
        assertTrue(r.seq.isLoaded(1));
        assertEquals(3, r.seq.raisedMask(), "the lifts stay up");
        r.run(1000);
        assertEquals(1, r.launchTicks.size(), "the second never goes");
        assertEquals(Refusal.NONE, r.seq.canLaunch(), "and it can be fired again");
        assertEquals(1, r.seq.launchMask() >> 1);
    }

    @Test
    @DisplayName("abort only means something in a countdown")
    void abortOutsideCountdown() {
        Rig r = rig(true, true);
        assertFalse(r.seq.abort());
        r.seq.tryPrepare(1);
        r.settle();
        assertFalse(r.seq.abort());
        assertEquals(TelState.RAISED, r.seq.state());
    }

    @Test
    @DisplayName("a second launch can follow an abort with a fresh countdown")
    void relaunch() {
        Rig r = rig(true, false);
        r.seq.tryPrepare(1);
        r.settle();
        r.seq.tryLaunch();
        r.run(100);
        r.seq.abort();
        r.totalTicks = 0;
        r.seq.tryLaunch();
        assertEquals(140, r.seq.launchIn(0));
        r.run(140);
        assertEquals(List.of(140), r.launchTicks);
    }

    @Test
    @DisplayName("lowering the empty lift keeps the covers open, then the crew may stow")
    void lowerAfterLaunch() {
        Rig r = rig(true, false);
        r.seq.tryPrepare(1);
        r.settle();
        r.seq.tryLaunch();
        r.run(140);
        assertEquals(Refusal.NONE, r.seq.tryLower(1));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_1));
        r.settle();
        assertEquals(TelState.COVERS_OPEN, r.seq.state());
        assertEquals(0, r.seq.raisedMask());
        assertTrue(r.seq.coversOpen());

        assertEquals(Refusal.NONE, r.seq.tryStow());
        assertEquals(TelState.COVERS_CLOSING, r.seq.state());
        r.settle();
        assertEquals(TelState.STOWED, r.seq.state());
    }

    @Test
    @DisplayName("stowing from raised lowers the lifts, then closes the covers, then lifts the jacks")
    void stowFromRaised() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(3);
        r.settle();
        assertEquals(Refusal.NONE, r.seq.tryStow());
        assertEquals(TelState.STOWING, r.seq.state());
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS), "the covers wait for the lifts");
        r.run(96);
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.COVERS));
        assertEquals(0, r.seq.groupAge(TelBoneGroups.COVERS));
        r.run(56);
        assertEquals(TelState.JACKS_UP, r.seq.state());
        r.run(30);
        assertEquals(TelState.STOWED, r.seq.state());
        assertEquals(0, r.seq.raisedMask());
        assertFalse(r.seq.coversOpen());
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.COVERS));
    }

    @Test
    @DisplayName("stowing with only one lift up lowers just that one")
    void stowWithOneUp() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(2);
        r.settle();
        r.seq.tryStow();
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.CLOSING, r.seq.groupClip(TelBoneGroups.LIFT_2));
        r.settle();
        assertEquals(TelState.STOWED, r.seq.state());
    }

    @Test
    @DisplayName("a stow asked for during the raising waits for the round to stand, then folds at once")
    void deferredStow() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(1);
        r.run(40);
        assertEquals(TelState.DEPLOYING, r.seq.state());
        assertEquals(Refusal.NONE, r.seq.tryStow());
        assertTrue(r.seq.stowQueued());
        assertEquals(TelState.DEPLOYING, r.seq.state(), "it does not cut the movement short");
        assertEquals(Refusal.BUSY, r.seq.canStow(), "and cannot be queued twice");
        r.run(200);
        assertEquals(TelState.STOWING, r.seq.state());
        r.settle();
        assertEquals(TelState.STOWED, r.seq.state());
        assertFalse(r.seq.stowQueued());
        assertEquals(0, r.launchTicks.size(), "nothing was fired on the way");
    }

    @Test
    @DisplayName("a stow asked for during the jacks also waits")
    void deferredStowDuringJacks() {
        Rig r = rig(true, false);
        r.seq.tryPrepare(1);
        r.run(5);
        assertEquals(TelState.JACKS_DOWN, r.seq.state());
        assertEquals(Refusal.NONE, r.seq.tryStow());
        r.run(30 + 152 - 5);
        assertEquals(TelState.STOWING, r.seq.state());
    }

    private static Rig inState(TelState want) {
        Rig r = rig(true, true);
        switch (want) {
            case STOWED -> {
            }
            case COVERS_OPENING -> r.seq.tryCovers();
            case COVERS_OPEN -> {
                r.seq.tryCovers();
                r.settle();
            }
            case COVERS_CLOSING -> {
                r.seq.tryCovers();
                r.settle();
                r.seq.tryCovers();
            }
            case COVER_LOAD -> {
                r.seq.tryCovers();
                r.settle();
                r.seq.tryUnload(0);
            }
            case JACKS_DOWN -> r.seq.tryPrepare(1);
            case JACKS_UP -> {
                r.seq.tryPrepare(1);
                r.settle();
                r.seq.tryLower(1);
                r.settle();
                r.seq.tryCovers();
                r.run(56);
            }
            case DEPLOYING -> {
                r.seq.tryPrepare(1);
                r.run(30);
            }
            case RAISING -> {
                r.seq.tryCovers();
                r.settle();
                r.seq.tryPrepare(1);
                r.run(30);
            }
            case RAISED -> {
                r.seq.tryPrepare(1);
                r.settle();
            }
            case COUNTDOWN -> {
                r.seq.tryPrepare(1);
                r.settle();
                r.seq.tryLaunch();
            }
            case LOWERING -> {
                r.seq.tryPrepare(1);
                r.settle();
                r.seq.tryLower(1);
            }
            case STOWING -> {
                r.seq.tryPrepare(1);
                r.settle();
                r.seq.tryStow();
            }
        }
        assertEquals(want, r.seq.state(), "set-up for " + want);
        return r;
    }

    @Test
    @DisplayName("every order is allowed or refused as the table says, from every state")
    void orderTable() {
        record Row(TelState state, boolean covers, boolean load, boolean unload, boolean prep1, boolean prep3,
                boolean launch, boolean abort, boolean lower, boolean stow) {
        }
        Row[] rows = {
                new Row(TelState.STOWED, true, false, false, true, true, false, false, false, false),
                new Row(TelState.COVERS_OPENING, false, false, false, false, false, false, false, false, false),
                new Row(TelState.COVERS_OPEN, true, true, true, true, true, false, false, false, true),
                new Row(TelState.COVERS_CLOSING, false, false, false, false, false, false, false, false, false),
                new Row(TelState.COVER_LOAD, false, false, false, false, false, false, false, false, false),
                new Row(TelState.JACKS_DOWN, false, false, false, false, false, false, false, false, true),
                new Row(TelState.JACKS_UP, false, false, false, false, false, false, false, false, false),
                new Row(TelState.DEPLOYING, false, false, false, false, false, false, false, false, true),
                new Row(TelState.RAISING, false, false, false, false, false, false, false, false, true),
                new Row(TelState.RAISED, false, false, false, true, true, true, false, true, true),
                new Row(TelState.COUNTDOWN, false, false, false, false, false, false, true, false, false),
                new Row(TelState.LOWERING, false, false, false, false, false, false, false, false, false),
                new Row(TelState.STOWING, false, false, false, false, false, false, false, false, false),
        };
        for (Row row : rows) {
            Rig r = inState(row.state());
            r.seq.setLoaded(0, true);
            r.seq.setLoaded(1, true);
            String at = " in " + row.state();
            assertEquals(row.covers(), r.seq.canCovers().ok(), "covers" + at);
            Rig empty = inState(row.state());
            empty.seq.setLoaded(0, false);
            assertEquals(row.load(), empty.seq.canLoad(0).ok(), "load" + at);
            assertEquals(row.unload(), r.seq.canUnload(1).ok(), "unload" + at);
            boolean prep1 = r.seq.canPrepare(row.state() == TelState.RAISED ? 2 : 1).ok();
            assertEquals(row.prep1(), prep1, "prepare one" + at);
            assertEquals(row.prep3(), r.seq.canPrepare(3).ok(), "prepare both" + at);
            assertEquals(row.launch(), r.seq.canLaunch().ok(), "launch" + at);
            assertEquals(row.lower(), r.seq.canLower(3).ok(), "lower" + at);
            assertEquals(row.stow(), r.seq.canStow().ok(), "stow" + at);
            assertEquals(row.abort(), inState(row.state()).seq.abort(), "abort" + at);
        }
    }

    @Test
    @DisplayName("a round the entity keeps back at its moment stays loaded and the schedule goes on")
    void entityKeepsARoundBack() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(3);
        r.settle();
        r.seq.tryLaunch();
        int fired = 0;
        for (int i = 0; i < 400; i++) {
            TelSequence.Event e = r.seq.tick();
            if (e == TelSequence.Event.LAUNCH_1) {
                r.seq.setLoaded(1, false);
                fired++;
            }
        }
        assertEquals(1, fired);
        assertTrue(r.seq.isLoaded(0), "the first is still aboard");
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(Refusal.NONE, r.seq.canLaunch(), "and can be tried again");
    }

    @Test
    @DisplayName("a snapshot taken at any moment of a long run restores to the same cycle")
    void snapshotRoundTrip() {
        Random rnd = new Random(7);
        Rig r = new Rig(2, true, true);
        for (int step = 0; step < 300; step++) {
            command(r, rnd.nextInt(9), rnd);
            r.run(1 + rnd.nextInt(150));
            TelSequence copy = new TelSequence();
            copy.setLoaded(0, r.seq.isLoaded(0));
            copy.setLoaded(1, r.seq.isLoaded(1));
            copy.restore(r.seq.snapshot());
            assertEquals(r.seq.snapshot(), copy.snapshot());
            for (int g = 0; g < TelBoneGroups.COUNT; g++) {
                assertEquals(r.seq.groupClip(g), copy.groupClip(g));
                assertEquals(r.seq.groupAge(g), copy.groupAge(g));
            }
            assertEquals(r.seq.state(), copy.state());
            assertEquals(r.seq.launchIn(0), copy.launchIn(0));
            assertEquals(r.seq.launchIn(1), copy.launchIn(1));
        }
    }

    @Test
    @DisplayName("a cycle restored mid-countdown fires on schedule")
    void restoredCountdown() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(3);
        r.settle();
        r.seq.tryLaunch();
        r.run(100);
        Rig copy = rig(true, true);
        copy.seq.restore(r.seq.snapshot());
        copy.totalTicks = 0;
        copy.run(200);
        assertEquals(List.of(40, 140), copy.launchTicks);
    }

    @Test
    @DisplayName("a cycle restored in the middle of a movement finishes it where it would have")
    void restoredMovement() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(2);
        r.run(30 + 100);
        assertEquals(TelState.DEPLOYING, r.seq.state());
        Rig copy = rig(true, true);
        copy.seq.restore(r.seq.snapshot());
        r.run(52);
        copy.run(52);
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(TelState.RAISED, copy.seq.state());
        assertEquals(2, copy.seq.raisedMask());
        assertEquals(Clip.HELD, copy.seq.groupClip(TelBoneGroups.LIFT_2));
    }

    @Test
    @DisplayName("reset gives the travelling position")
    void reset() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(1);
        r.run(100);
        r.seq.reset();
        assertEquals(TelState.STOWED, r.seq.state());
        assertEquals(0, r.seq.raisedMask());
        assertFalse(r.seq.jacksDown());
        for (int g = 0; g < TelBoneGroups.COUNT; g++) {
            assertEquals(Clip.REST, r.seq.groupClip(g));
        }
    }

    @Test
    @DisplayName("a save from before the groups were kept is read as the pose it had reached")
    void restoreResting() {
        Rig r = rig(true, true);
        r.seq.restoreResting(TelState.DEPLOYING.ordinal(), true, true, 1);
        assertEquals(TelState.RAISED, r.seq.state());
        assertEquals(1, r.seq.raisedMask());
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.LIFT_1));
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.LIFT_2));
        assertEquals(Clip.HELD, r.seq.groupClip(TelBoneGroups.COVERS));
        assertTrue(r.seq.jacksDown());

        r.seq.restoreResting(TelState.COVERS_CLOSING.ordinal(), true, false, 0);
        assertEquals(TelState.JACKS_UP, r.seq.state(), "jacks down with the covers shut: the jacks come up");
        r.settle();
        assertEquals(TelState.STOWED, r.seq.state());

        r.seq.restoreResting(TelState.STOWED.ordinal(), false, false, 0);
        assertEquals(TelState.STOWED, r.seq.state());
        assertEquals(Clip.REST, r.seq.groupClip(TelBoneGroups.COVERS));
    }

    @Test
    @DisplayName("the hit box pose reads each group's own clip and time")
    void hitboxPoseOfGroups() {
        Rig r = rig(true, true);
        r.seq.tryPrepare(1);
        r.run(30 + 56 + 24);
        var pose = r.seq.hitboxPose(0.0f);
        assertEquals("animation.iskander_tel_loaded.cover_open", pose.covers().clip());
        assertEquals(TelPose.HELD_SECONDS, pose.covers().seconds());
        assertEquals("animation.iskander_tel_loaded.raise_left", pose.lift1().clip());
        assertEquals(24 / 20.0, pose.lift1().seconds(), 1.0E-9);
        assertEquals(null, pose.lift2().clip(), "the other erector is at rest");
        assertEquals(1.0, pose.jackProgress(), 1.0E-9);
    }
}
