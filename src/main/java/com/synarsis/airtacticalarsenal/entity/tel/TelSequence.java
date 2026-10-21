package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules.Refusal;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose.Clip;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelBoneGroups;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;

public final class TelSequence {

    public static final int COUNTDOWN_TICKS = 140;
    public static final int MIN_INTERVAL_SECONDS = 5;
    public static final int MAX_INTERVAL_SECONDS = 30;
    public static final int DEFAULT_INTERVAL_SECONDS = 5;
    public static final double HELD_SECONDS = TelPose.HELD_SECONDS;
    public static final int DEFAULT_LOAD_TICKS = 160;
    private static final int AGE_LIMIT = 100_000;

    public enum Event {
        NONE,
        LOAD_COMMIT,
        UNLOAD_COMMIT,
        LAUNCH_0,
        LAUNCH_1
    }

    public enum Next {
        NONE,
        DEPLOY,
        RAISE
    }

    public enum Load {
        NONE,
        IN,
        OUT
    }

    private TelState state = TelState.STOWED;
    private int ticks;

    private boolean jacksDown;
    private boolean coversOpen;
    private int raisedMask;
    private final boolean[] loaded = new boolean[2];

    private final Clip[] groupClip = {Clip.REST, Clip.REST, Clip.REST};
    private final int[] groupAge = new int[TelBoneGroups.COUNT];

    private Next next = Next.NONE;
    private int nextMask;
    private boolean stowAfter;

    private Load load = Load.NONE;
    private int pendingSlot;
    private boolean roundHeld;
    private int loadTicks = DEFAULT_LOAD_TICKS;

    private final int[] launchAt = {-1, -1};
    private int intervalSeconds = DEFAULT_INTERVAL_SECONDS;

    private boolean changed;

    public TelState state() {
        return this.state;
    }

    public int ticks() {
        return this.ticks;
    }

    public int durationOf(TelState s) {
        return s == TelState.COVER_LOAD ? this.loadTicks : s.duration();
    }

    public boolean jacksDown() {
        return this.jacksDown;
    }

    public boolean coversOpen() {
        return this.coversOpen;
    }

    public int raisedMask() {
        return this.raisedMask;
    }

    public boolean isLoaded(int slot) {
        return this.loaded[slot];
    }

    public int loadedMask() {
        return (this.loaded[0] ? 1 : 0) | (this.loaded[1] ? 2 : 0);
    }

    public Load load() {
        return this.load;
    }

    public int pendingSlot() {
        return this.pendingSlot;
    }

    public boolean roundHeld() {
        return this.roundHeld;
    }

    public int intervalSeconds() {
        return this.intervalSeconds;
    }

    public int loadTicks() {
        return this.loadTicks;
    }

    public boolean stowQueued() {
        return this.stowAfter;
    }

    public int risingMask() {
        return this.nextMask;
    }

    public Next next() {
        return this.next;
    }

    public int launchIn(int slot) {
        return this.state == TelState.COUNTDOWN && this.launchAt[slot] >= 0
                ? Math.max(0, this.launchAt[slot] - this.ticks) : -1;
    }

    public boolean isRaised(int slot) {
        return this.state.isUpright() && (this.raisedMask & (1 << slot)) != 0;
    }

    public boolean changed() {
        return this.changed;
    }

    public boolean isStowed() {
        return this.state == TelState.STOWED;
    }

    public Clip groupClip(int group) {
        return this.groupClip[group];
    }

    public int groupAge(int group) {
        return this.groupAge[group];
    }

    public TelHitboxPose hitboxPose(float partialTick) {
        return TelPose.pose(this.groupClip[TelBoneGroups.COVERS], this.groupAge[TelBoneGroups.COVERS],
                this.groupClip[TelBoneGroups.LIFT_1], this.groupAge[TelBoneGroups.LIFT_1],
                this.groupClip[TelBoneGroups.LIFT_2], this.groupAge[TelBoneGroups.LIFT_2],
                this.jackProgress(partialTick), this.loaded[0], this.loaded[1]);
    }

    public float jackProgress(float partialTick) {
        return switch (this.state) {
            case JACKS_DOWN -> Math.min(1.0f, (this.ticks + partialTick) / TelState.JACKS_DOWN.duration());
            case JACKS_UP -> Math.max(0.0f, 1.0f - (this.ticks + partialTick) / TelState.JACKS_UP.duration());
            default -> this.jacksDown ? 1.0f : 0.0f;
        };
    }

    public void setLoaded(int slot, boolean value) {
        this.loaded[slot] = value;
    }

    public void setIntervalSeconds(int seconds) {
        this.intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, seconds));
    }

    public void setLoadTicks(int ticks) {
        this.loadTicks = Math.max(1, ticks);
    }

    public Refusal canCovers() {
        return this.state == TelState.STOWED || this.state == TelState.COVERS_OPEN ? Refusal.NONE : Refusal.BUSY;
    }

    public Refusal tryCovers() {
        Refusal r = this.canCovers();
        if (r.ok()) {
            this.enter(this.state == TelState.STOWED ? TelState.COVERS_OPENING : TelState.COVERS_CLOSING);
        }
        return r;
    }

    private Refusal canWorkBay() {
        return switch (this.state) {
            case COVERS_OPEN -> Refusal.NONE;
            case STOWED -> Refusal.COVERS_CLOSED;
            default -> Refusal.BUSY;
        };
    }

    public Refusal canLoad(int slot) {
        if (slot < 0 || slot > 1) {
            return Refusal.BUSY;
        }
        Refusal bay = this.canWorkBay();
        if (!bay.ok()) {
            return bay;
        }
        return this.loaded[slot] ? Refusal.SLOT_FULL : Refusal.NONE;
    }

    public Refusal tryLoad(int slot) {
        Refusal r = this.canLoad(slot);
        if (r.ok()) {
            this.beginLoad(Load.IN, slot);
            this.roundHeld = true;
        }
        return r;
    }

    public Refusal canUnload(int slot) {
        if (slot < 0 || slot > 1) {
            return Refusal.BUSY;
        }
        Refusal bay = this.canWorkBay();
        if (!bay.ok()) {
            return bay;
        }
        return this.loaded[slot] ? Refusal.NONE : Refusal.SLOT_EMPTY;
    }

    public Refusal tryUnload(int slot) {
        Refusal r = this.canUnload(slot);
        if (r.ok()) {
            this.beginLoad(Load.OUT, slot);
        }
        return r;
    }

    private void beginLoad(Load kind, int slot) {
        this.load = kind;
        this.pendingSlot = slot;
        this.enter(TelState.COVER_LOAD);
    }

    private int toRaise(int mask) {
        return mask & ~this.raisedMask & 3;
    }

    public Refusal canPrepare(int mask) {
        if (mask < 1 || mask > 3) {
            return Refusal.BUSY;
        }
        if (this.state != TelState.STOWED && this.state != TelState.COVERS_OPEN && this.state != TelState.RAISED) {
            return Refusal.BUSY;
        }
        int moving = this.toRaise(mask);
        if (moving == 0) {
            return Refusal.ALREADY_RAISED;
        }
        return (moving & ~this.loadedMask()) != 0 ? Refusal.NO_MISSILE : Refusal.NONE;
    }

    public Refusal tryPrepare(int mask) {
        Refusal r = this.canPrepare(mask);
        if (!r.ok()) {
            return r;
        }
        this.nextMask = this.toRaise(mask);
        switch (this.state) {
            case STOWED -> this.beginJacksThen(Next.DEPLOY);
            case COVERS_OPEN -> this.beginJacksThen(Next.RAISE);
            default -> this.enter(TelState.RAISING);
        }
        return r;
    }

    private void beginJacksThen(Next after) {
        this.next = after;
        if (this.jacksDown) {
            this.enter(after == Next.DEPLOY ? TelState.DEPLOYING : TelState.RAISING);
        } else {
            this.enter(TelState.JACKS_DOWN);
        }
    }

    public Refusal canLaunch() {
        if (this.state != TelState.RAISED) {
            return Refusal.NOT_RAISED;
        }
        return (this.raisedMask & this.loadedMask()) == 0 ? Refusal.NO_MISSILE : Refusal.NONE;
    }

    public int launchMask() {
        return this.state == TelState.RAISED ? this.raisedMask & this.loadedMask() : 0;
    }

    public Refusal tryLaunch() {
        Refusal r = this.canLaunch();
        if (!r.ok()) {
            return r;
        }
        this.launchAt[0] = -1;
        this.launchAt[1] = -1;
        boolean first = true;
        for (int slot = 0; slot < 2; slot++) {
            if ((this.raisedMask & this.loadedMask() & (1 << slot)) != 0) {
                this.launchAt[slot] = first ? COUNTDOWN_TICKS : COUNTDOWN_TICKS + this.intervalSeconds * 20;
                first = false;
            }
        }
        this.enter(TelState.COUNTDOWN);
        return r;
    }

    public boolean abort() {
        if (this.state != TelState.COUNTDOWN) {
            return false;
        }
        this.launchAt[0] = -1;
        this.launchAt[1] = -1;
        this.enter(TelState.RAISED);
        return true;
    }

    public Refusal canLower(int mask) {
        if (this.state != TelState.RAISED) {
            return this.state == TelState.COUNTDOWN ? Refusal.BUSY : Refusal.NOT_RAISED;
        }
        return (mask & this.raisedMask & 3) == 0 ? Refusal.NOT_RAISED : Refusal.NONE;
    }

    public Refusal tryLower(int mask) {
        Refusal r = this.canLower(mask);
        if (r.ok()) {
            this.next = Next.NONE;
            this.nextMask = mask & this.raisedMask & 3;
            this.enter(TelState.LOWERING);
        }
        return r;
    }

    public Refusal canStow() {
        return switch (this.state) {
            case RAISED, COVERS_OPEN -> Refusal.NONE;
            case JACKS_DOWN, DEPLOYING, RAISING -> this.stowAfter ? Refusal.BUSY : Refusal.NONE;
            default -> Refusal.BUSY;
        };
    }

    public Refusal tryStow() {
        Refusal r = this.canStow();
        if (!r.ok()) {
            return r;
        }
        switch (this.state) {
            case RAISED -> this.enter(TelState.STOWING);
            case COVERS_OPEN -> this.enter(TelState.COVERS_CLOSING);
            default -> {
                this.stowAfter = true;
                this.changed = true;
            }
        }
        return r;
    }

    public boolean dropHeldRound() {
        boolean was = this.roundHeld;
        this.roundHeld = false;
        this.load = Load.NONE;
        return was;
    }

    private void setGroup(int group, Clip clip) {
        this.groupClip[group] = clip;
        this.groupAge[group] = 0;
        this.changed = true;
    }

    private void setLifts(int mask, Clip clip) {
        for (int slot = 0; slot < 2; slot++) {
            if ((mask & (1 << slot)) != 0) {
                this.setGroup(TelBoneGroups.liftOf(slot), clip);
            }
        }
    }

    private void enter(TelState to) {
        this.state = to;
        this.ticks = 0;
        this.changed = true;
        switch (to) {
            case COVERS_OPENING, DEPLOYING -> this.setGroup(TelBoneGroups.COVERS, Clip.OPENING);
            case COVERS_CLOSING -> this.setGroup(TelBoneGroups.COVERS, Clip.CLOSING);
            case RAISING -> this.setLifts(this.nextMask, Clip.OPENING);
            case LOWERING -> this.setLifts(this.nextMask, Clip.CLOSING);
            case STOWING -> this.setLifts(this.raisedMask, Clip.CLOSING);
            default -> {
            }
        }
    }

    public Event tick() {
        this.changed = false;
        for (int g = 0; g < this.groupAge.length; g++) {
            if (this.groupAge[g] < AGE_LIMIT) {
                this.groupAge[g]++;
            }
        }
        this.ticks++;
        if (this.state == TelState.COUNTDOWN) {
            return this.tickCountdown();
        }
        if (!this.state.isTransition()) {
            return Event.NONE;
        }
        if (this.state == TelState.DEPLOYING && this.ticks == TelPose.COVER_TICKS) {
            this.setGroup(TelBoneGroups.COVERS, Clip.HELD);
            this.setLifts(this.nextMask, Clip.OPENING);
        } else if (this.state == TelState.STOWING && this.ticks == TelPose.LIFT_TICKS) {
            this.setLifts(this.raisedMask, Clip.REST);
            this.setGroup(TelBoneGroups.COVERS, Clip.CLOSING);
        }
        if (this.ticks < this.durationOf(this.state)) {
            return Event.NONE;
        }
        return this.finish();
    }

    private Event tickCountdown() {
        for (int slot = 0; slot < 2; slot++) {
            if (this.launchAt[slot] >= 0 && this.ticks >= this.launchAt[slot]) {
                this.launchAt[slot] = -1;
                if (this.launchAt[0] < 0 && this.launchAt[1] < 0) {
                    this.enter(TelState.RAISED);
                }
                return slot == 0 ? Event.LAUNCH_0 : Event.LAUNCH_1;
            }
        }
        return Event.NONE;
    }

    private Event finish() {
        switch (this.state) {
            case COVERS_OPENING -> {
                this.coversOpen = true;
                this.setGroup(TelBoneGroups.COVERS, Clip.HELD);
                this.enter(TelState.COVERS_OPEN);
            }
            case COVERS_CLOSING -> {
                this.coversOpen = false;
                this.setGroup(TelBoneGroups.COVERS, Clip.REST);
                this.enter(this.jacksDown ? TelState.JACKS_UP : TelState.STOWED);
            }
            case COVER_LOAD -> {
                this.enter(TelState.COVERS_OPEN);
                return this.commit();
            }
            case JACKS_DOWN -> {
                this.jacksDown = true;
                this.enter(this.next == Next.DEPLOY ? TelState.DEPLOYING : TelState.RAISING);
            }
            case JACKS_UP -> {
                this.jacksDown = false;
                this.enter(TelState.STOWED);
            }
            case DEPLOYING, RAISING -> {
                this.coversOpen = true;
                this.raisedMask |= this.nextMask;
                this.setLifts(this.nextMask, Clip.HELD);
                this.next = Next.NONE;
                this.nextMask = 0;
                if (this.stowAfter) {
                    this.stowAfter = false;
                    this.enter(TelState.STOWING);
                } else {
                    this.enter(TelState.RAISED);
                }
            }
            case LOWERING -> {
                this.raisedMask &= ~this.nextMask;
                this.setLifts(this.nextMask, Clip.REST);
                this.nextMask = 0;
                this.enter(this.raisedMask == 0 ? TelState.COVERS_OPEN : TelState.RAISED);
            }
            case STOWING -> {
                this.raisedMask = 0;
                this.coversOpen = false;
                this.setGroup(TelBoneGroups.COVERS, Clip.REST);
                this.enter(TelState.JACKS_UP);
            }
            default -> {
            }
        }
        return Event.NONE;
    }

    private Event commit() {
        Event e = this.load == Load.IN ? Event.LOAD_COMMIT : Event.UNLOAD_COMMIT;
        this.load = Load.NONE;
        this.roundHeld = false;
        return e;
    }

    public record Snapshot(int state, int ticks, int coverClip, int coverAge, int lift1Clip, int lift1Age,
            int lift2Clip, int lift2Age, boolean jacksDown, boolean coversOpen, int raisedMask, int next,
            int nextMask, boolean stowAfter, int load, int pendingSlot, boolean roundHeld, int launchAt0,
            int launchAt1, int intervalSeconds) {
    }

    public Snapshot snapshot() {
        return new Snapshot(this.state.ordinal(), this.ticks,
                this.groupClip[0].ordinal(), this.groupAge[0], this.groupClip[1].ordinal(), this.groupAge[1],
                this.groupClip[2].ordinal(), this.groupAge[2], this.jacksDown, this.coversOpen, this.raisedMask,
                this.next.ordinal(), this.nextMask, this.stowAfter, this.load.ordinal(), this.pendingSlot,
                this.roundHeld, this.launchAt[0], this.launchAt[1], this.intervalSeconds);
    }

    public void restore(Snapshot s) {
        this.state = TelState.byOrdinal(s.state());
        this.ticks = Math.max(0, s.ticks());
        this.groupClip[0] = Clip.byOrdinal(s.coverClip());
        this.groupClip[1] = Clip.byOrdinal(s.lift1Clip());
        this.groupClip[2] = Clip.byOrdinal(s.lift2Clip());
        this.groupAge[0] = Math.max(0, s.coverAge());
        this.groupAge[1] = Math.max(0, s.lift1Age());
        this.groupAge[2] = Math.max(0, s.lift2Age());
        this.jacksDown = s.jacksDown();
        this.coversOpen = s.coversOpen();
        this.raisedMask = s.raisedMask() & 3;
        this.next = Next.values()[Math.max(0, Math.min(Next.values().length - 1, s.next()))];
        this.nextMask = s.nextMask() & 3;
        this.stowAfter = s.stowAfter();
        this.load = Load.values()[Math.max(0, Math.min(Load.values().length - 1, s.load()))];
        this.pendingSlot = s.pendingSlot() == 1 ? 1 : 0;
        this.roundHeld = s.roundHeld();
        this.launchAt[0] = s.launchAt0();
        this.launchAt[1] = s.launchAt1();
        this.setIntervalSeconds(s.intervalSeconds());
        this.changed = true;
    }

    public static TelSequence view(TelState state, int raisedMask, int loadedMask) {
        TelSequence s = new TelSequence();
        s.restore(new Snapshot(state.ordinal(), 0, 0, 0, 0, 0, 0, 0, false, false, raisedMask,
                Next.NONE.ordinal(), 0, false, Load.NONE.ordinal(), 0, false, -1, -1, DEFAULT_INTERVAL_SECONDS));
        s.setLoaded(0, (loadedMask & 1) != 0);
        s.setLoaded(1, (loadedMask & 2) != 0);
        return s;
    }

    public void restoreResting(int stateOrdinal, boolean jacksWereDown, boolean coversWereOpen, int raised) {
        TelState was = TelState.byOrdinal(stateOrdinal);
        int mask = raised & 3;
        boolean open = coversWereOpen || mask != 0 || was == TelState.COVERS_OPEN;
        TelState to;
        if (mask != 0) {
            to = TelState.RAISED;
        } else if (open) {
            to = TelState.COVERS_OPEN;
        } else {
            to = jacksWereDown ? TelState.JACKS_UP : TelState.STOWED;
        }
        this.restore(new Snapshot(to.ordinal(), 0, (open ? Clip.HELD : Clip.REST).ordinal(), 0,
                (mask & 1) != 0 ? Clip.HELD.ordinal() : 0, 0, (mask & 2) != 0 ? Clip.HELD.ordinal() : 0, 0,
                jacksWereDown && to != TelState.STOWED, open, mask, Next.NONE.ordinal(), 0, false,
                Load.NONE.ordinal(), 0, false, -1, -1, this.intervalSeconds));
    }

    public void reset() {
        this.restore(new Snapshot(TelState.STOWED.ordinal(), 0, 0, 0, 0, 0, 0, 0, false, false, 0,
                Next.NONE.ordinal(), 0, false, Load.NONE.ordinal(), 0, false, -1, -1, this.intervalSeconds));
    }
}
