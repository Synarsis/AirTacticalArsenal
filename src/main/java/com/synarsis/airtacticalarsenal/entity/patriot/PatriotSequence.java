package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelPose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class PatriotSequence {

    public enum Op {
        SUPPORTS_OUT(PatriotBones.SUPPORTS, true),
        SUPPORTS_IN(PatriotBones.SUPPORTS, false),
        RAISE(PatriotBones.LIFT, true),
        LOWER(PatriotBones.LIFT, false),
        ANTENNA_UP(PatriotBones.LIFT, true),
        ANTENNA_DOWN(PatriotBones.LIFT, false),
        SERVICE_OPEN(PatriotBones.SERVICE, true),
        SERVICE_CLOSE(PatriotBones.SERVICE, false),
        CENTER(-1, false);

        private static final Op[] VALUES = values();
        private static final int[] STAGE_SEVEN = {0, 1, 2, 3, 4, 5, 6, 7, -1, -1, -1, -1, 8};

        public final int group;
        public final boolean opening;

        Op(int group, boolean opening) {
            this.group = group;
            this.opening = opening;
        }

        public static Op byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
        }

        public static Op byStageSevenOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < STAGE_SEVEN.length ? byOrdinal(STAGE_SEVEN[ordinal]) : null;
        }
    }

    public enum Command {
        DEPLOY,
        STOW,
        SUPPORTS_OUT,
        SUPPORTS_IN,
        LIFT_UP,
        LIFT_DOWN,
        TRAVERSE_LEFT,
        TRAVERSE_RIGHT,
        TRAVERSE_CENTER,
        SERVICE_OPEN,
        SERVICE_CLOSE;

        private static final Command[] VALUES = values();

        public static Command byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
        }
    }

    public interface Gate {
        Gate OPEN = op -> PatriotRefusal.Denial.NONE;

        PatriotRefusal.Denial check(Op op);
    }

    public record Snapshot(boolean supports, boolean lift, boolean service, double traverse, double traverseTarget,
            int op, int ticks, int[] queue) {
    }

    public static final int SUPPORTS_BIT = 1;
    public static final int LIFT_BIT = 2;
    public static final int SERVICE_BIT = 4;

    public static final int QUEUE_LIMIT = 8;
    public static final double TRAVERSE_STEP = 6.0;

    private static final double TRAVERSE_PER_TICK = PatriotTiming.TRAVERSE_DEGREES_PER_SECOND / 20.0;

    private final PatriotKind kind;

    private boolean supports;
    private boolean lift;
    private boolean service;
    private double traverse;
    private double traverseTarget;

    private Op current;
    private int ticks;
    private final ArrayDeque<Op> queue = new ArrayDeque<>();

    private boolean changed;
    private Op started;
    private Op finished;
    private PatriotRefusal.Denial dropped = PatriotRefusal.Denial.NONE;

    public PatriotSequence(PatriotKind kind) {
        this.kind = kind;
    }

    public static PatriotSequence view(PatriotKind kind, int mask, int op, double traverse, double traverseTarget) {
        PatriotSequence s = new PatriotSequence(kind);
        s.setMask(mask);
        s.current = Op.byOrdinal(op);
        s.traverse = traverse;
        s.traverseTarget = traverseTarget;
        return s;
    }

    public PatriotKind kind() {
        return this.kind;
    }

    public boolean supports() {
        return this.supports;
    }

    public boolean lift() {
        return this.lift;
    }

    public boolean service() {
        return this.service;
    }

    public double traverse() {
        return this.traverse;
    }

    public double traverseTarget() {
        return this.traverseTarget;
    }

    public Op current() {
        return this.current;
    }

    public int ticks() {
        return this.ticks;
    }

    public boolean busy() {
        return this.current != null || !this.queue.isEmpty();
    }

    public int queued() {
        return this.queue.size();
    }

    public boolean canDrive() {
        return !this.supports && !this.lift && !this.service && !this.busy();
    }

    public PatriotRefusal whyNotDrive() {
        if (this.busy()) {
            return PatriotRefusal.BUSY;
        }
        if (this.supports) {
            return PatriotRefusal.NEED_SUPPORTS;
        }
        if (this.lift) {
            return PatriotRefusal.LIFT_NOT_DOWN;
        }
        if (this.service) {
            return PatriotRefusal.SERVICE_OPEN;
        }
        return PatriotRefusal.NONE;
    }

    public boolean changed() {
        boolean was = this.changed;
        this.changed = false;
        return was;
    }

    public Op started() {
        return this.started;
    }

    public Op finished() {
        return this.finished;
    }

    public PatriotRefusal.Denial takeDropped() {
        PatriotRefusal.Denial d = this.dropped;
        this.dropped = PatriotRefusal.Denial.NONE;
        return d;
    }

    public int mask() {
        return (this.supports ? SUPPORTS_BIT : 0) | (this.lift ? LIFT_BIT : 0) | (this.service ? SERVICE_BIT : 0);
    }

    private void setMask(int mask) {
        this.supports = (mask & SUPPORTS_BIT) != 0;
        this.lift = (mask & LIFT_BIT) != 0;
        this.service = (mask & SERVICE_BIT) != 0;
    }

    private Op liftOp(boolean up) {
        if (this.kind.isLauncher()) {
            return up ? Op.RAISE : Op.LOWER;
        }
        return up ? Op.ANTENNA_UP : Op.ANTENNA_DOWN;
    }

    public int durationOf(Op op) {
        return switch (op) {
            case SUPPORTS_OUT, SUPPORTS_IN -> PatriotTiming.SUPPORTS_TICKS;
            case RAISE, LOWER -> PatriotTiming.ERECTOR_TICKS;
            case ANTENNA_UP, ANTENNA_DOWN -> PatriotTiming.ANTENNA_TICKS;
            case SERVICE_OPEN, SERVICE_CLOSE -> PatriotTiming.SERVICE_TICKS;
            case CENTER -> (int) Math.ceil(Math.abs(this.traverse) / TRAVERSE_PER_TICK);
        };
    }

    public TelPose.Clip groupClip(int group) {
        if (this.current != null && this.current.group == group) {
            return this.current.opening ? TelPose.Clip.OPENING : TelPose.Clip.CLOSING;
        }
        return switch (group) {
            case PatriotBones.SUPPORTS -> this.supports ? TelPose.Clip.HELD : TelPose.Clip.REST;
            case PatriotBones.LIFT -> this.lift ? TelPose.Clip.HELD : TelPose.Clip.REST;
            case PatriotBones.SERVICE -> this.service ? TelPose.Clip.HELD : TelPose.Clip.REST;
            default -> TelPose.Clip.REST;
        };
    }

    public int groupAge(int group) {
        if (this.current != null && this.current.group == group) {
            return this.ticks;
        }
        return 0;
    }

    public double supportsProgress(float partialTick) {
        return this.groupProgress(PatriotBones.SUPPORTS, this.supports, partialTick);
    }

    private double groupProgress(int group, boolean flag, float partialTick) {
        if (this.current != null && this.current.group == group) {
            double t = Math.min(1.0, (this.ticks + partialTick) / this.durationOf(this.current));
            return this.current.opening ? t : 1.0 - t;
        }
        return flag ? 1.0 : 0.0;
    }

    private static final class Projection {
        boolean supports;
        boolean lift;
        boolean service;

        void apply(Op op) {
            switch (op) {
                case SUPPORTS_OUT -> this.supports = true;
                case SUPPORTS_IN -> this.supports = false;
                case RAISE, ANTENNA_UP -> this.lift = true;
                case LOWER, ANTENNA_DOWN -> this.lift = false;
                case SERVICE_OPEN -> this.service = true;
                case SERVICE_CLOSE -> this.service = false;
                default -> {
                }
            }
        }
    }

    private Projection projected() {
        Projection p = new Projection();
        p.supports = this.supports;
        p.lift = this.lift;
        p.service = this.service;
        if (this.current != null) {
            p.apply(this.current);
        }
        for (Op op : this.queue) {
            p.apply(op);
        }
        return p;
    }

    public record Plan(PatriotRefusal refusal, List<Op> ops, double traverseTarget) {
    }

    public Plan plan(Command command, boolean standing) {
        Projection p = this.projected();
        List<Op> ops = new ArrayList<>();
        boolean launcher = this.kind.isLauncher();
        double target = this.traverseTarget;

        switch (command) {
            case DEPLOY -> {
                if (!standing) {
                    return refuse(PatriotRefusal.MOVING);
                }
                if (!p.supports) {
                    ops.add(Op.SUPPORTS_OUT);
                }
                if (!p.lift) {
                    ops.add(this.liftOp(true));
                }
                if (ops.isEmpty()) {
                    return refuse(PatriotRefusal.ALREADY);
                }
            }
            case STOW -> {
                if (!standing) {
                    return refuse(PatriotRefusal.MOVING);
                }
                if (p.lift) {
                    if (launcher && (this.traverse != 0.0 || this.traverseTarget != 0.0)) {
                        ops.add(Op.CENTER);
                    }
                    ops.add(this.liftOp(false));
                }
                if (p.service) {
                    ops.add(Op.SERVICE_CLOSE);
                }
                if (p.supports) {
                    ops.add(Op.SUPPORTS_IN);
                }
                if (ops.isEmpty()) {
                    return refuse(PatriotRefusal.ALREADY);
                }
            }
            case SUPPORTS_OUT -> {
                if (!standing) {
                    return refuse(PatriotRefusal.MOVING);
                }
                if (p.supports) {
                    return refuse(PatriotRefusal.ALREADY);
                }
                ops.add(Op.SUPPORTS_OUT);
            }
            case SUPPORTS_IN -> {
                if (!standing) {
                    return refuse(PatriotRefusal.MOVING);
                }
                if (!p.supports) {
                    return refuse(PatriotRefusal.ALREADY);
                }
                if (p.lift) {
                    return refuse(PatriotRefusal.LIFT_NOT_DOWN);
                }
                if (p.service) {
                    return refuse(PatriotRefusal.SERVICE_OPEN);
                }
                ops.add(Op.SUPPORTS_IN);
            }
            case LIFT_UP -> {
                if (!standing) {
                    return refuse(PatriotRefusal.MOVING);
                }
                if (!p.supports) {
                    return refuse(PatriotRefusal.NEED_SUPPORTS);
                }
                if (p.lift) {
                    return refuse(PatriotRefusal.ALREADY);
                }
                ops.add(this.liftOp(true));
            }
            case LIFT_DOWN -> {
                if (!p.lift) {
                    return refuse(PatriotRefusal.ALREADY);
                }
                if (launcher && (this.traverse != 0.0 || this.traverseTarget != 0.0)) {
                    ops.add(Op.CENTER);
                }
                ops.add(this.liftOp(false));
            }
            case TRAVERSE_LEFT, TRAVERSE_RIGHT, TRAVERSE_CENTER -> {
                if (!launcher) {
                    return refuse(PatriotRefusal.WRONG_VEHICLE);
                }
                if (this.busy()) {
                    return refuse(PatriotRefusal.BUSY);
                }
                if (!this.lift) {
                    return refuse(PatriotRefusal.NEED_LIFT_UP);
                }
                double limit = PatriotTiming.TRAVERSE_LIMIT_DEGREES;
                double next = switch (command) {
                    case TRAVERSE_LEFT -> Math.max(-limit, this.traverseTarget - TRAVERSE_STEP);
                    case TRAVERSE_RIGHT -> Math.min(limit, this.traverseTarget + TRAVERSE_STEP);
                    default -> 0.0;
                };
                if (next == this.traverseTarget) {
                    return refuse(command == Command.TRAVERSE_CENTER ? PatriotRefusal.ALREADY
                            : PatriotRefusal.TRAVERSE_LIMIT);
                }
                target = next;
            }
            case SERVICE_OPEN -> {
                if (launcher) {
                    return refuse(PatriotRefusal.WRONG_VEHICLE);
                }
                if (!standing) {
                    return refuse(PatriotRefusal.MOVING);
                }
                if (p.service) {
                    return refuse(PatriotRefusal.ALREADY);
                }
                ops.add(Op.SERVICE_OPEN);
            }
            case SERVICE_CLOSE -> {
                if (launcher) {
                    return refuse(PatriotRefusal.WRONG_VEHICLE);
                }
                if (!p.service) {
                    return refuse(PatriotRefusal.ALREADY);
                }
                ops.add(Op.SERVICE_CLOSE);
            }
        }
        if (this.queue.size() + ops.size() > QUEUE_LIMIT) {
            return refuse(PatriotRefusal.BUSY);
        }
        return new Plan(PatriotRefusal.NONE, ops, target);
    }

    private static Plan refuse(PatriotRefusal refusal) {
        return new Plan(refusal, List.of(), 0.0);
    }

    public PatriotRefusal can(Command command, boolean standing) {
        return this.plan(command, standing).refusal();
    }

    public PatriotRefusal command(Command command, boolean standing) {
        Plan plan = this.plan(command, standing);
        if (!plan.refusal().ok()) {
            return plan.refusal();
        }
        this.traverseTarget = plan.traverseTarget();
        this.queue.addAll(plan.ops());
        this.changed = true;
        return PatriotRefusal.NONE;
    }

    public void clearQueue() {
        this.queue.clear();
        this.changed = true;
    }

    public void tick(Gate gate) {
        this.started = null;
        this.finished = null;

        if (this.traverse != this.traverseTarget) {
            double step = Math.min(TRAVERSE_PER_TICK, Math.abs(this.traverseTarget - this.traverse));
            this.traverse += Math.signum(this.traverseTarget - this.traverse) * step;
            this.changed = true;
        }

        if (this.current == null) {
            this.startNext(gate);
        }
        if (this.current == null) {
            return;
        }
        this.ticks++;
        if (this.isDone()) {
            this.finishCurrent();
            this.startNext(gate);
        }
    }

    private boolean isDone() {
        if (this.current == Op.CENTER) {
            return this.traverse == 0.0;
        }
        return this.ticks >= this.durationOf(this.current);
    }

    private void startNext(Gate gate) {
        while (this.current == null && !this.queue.isEmpty()) {
            Op next = this.queue.peekFirst();
            PatriotRefusal.Denial denial = gate.check(next);
            if (!denial.ok()) {
                this.queue.clear();
                this.dropped = denial;
                this.changed = true;
                return;
            }
            this.queue.pollFirst();
            this.current = next;
            this.ticks = 0;
            this.started = next;
            this.changed = true;
            if (next == Op.CENTER) {
                this.traverseTarget = 0.0;
                if (this.traverse == 0.0) {
                    this.finishCurrent();
                }
            }
        }
    }

    private void finishCurrent() {
        Op op = this.current;
        this.apply(op);
        this.finished = op;
        this.current = null;
        this.ticks = 0;
        this.changed = true;
    }

    private void apply(Op op) {
        switch (op) {
            case SUPPORTS_OUT -> this.supports = true;
            case SUPPORTS_IN -> this.supports = false;
            case RAISE, ANTENNA_UP -> this.lift = true;
            case LOWER, ANTENNA_DOWN -> this.lift = false;
            case SERVICE_OPEN -> this.service = true;
            case SERVICE_CLOSE -> this.service = false;
            case CENTER -> {
                this.traverse = 0.0;
                this.traverseTarget = 0.0;
            }
        }
    }

    public boolean skip(Gate gate) {
        if (this.current == null) {
            this.startNext(gate);
        }
        if (this.current == null) {
            return false;
        }
        this.finishCurrent();
        return true;
    }

    public Snapshot snapshot() {
        int[] pending = new int[this.queue.size()];
        int i = 0;
        for (Op op : this.queue) {
            pending[i++] = op.ordinal();
        }
        return new Snapshot(this.supports, this.lift, this.service, this.traverse, this.traverseTarget,
                this.current == null ? -1 : this.current.ordinal(), this.ticks, pending);
    }

    public void restore(Snapshot s) {
        this.restore(s, false);
    }

    public void restore(Snapshot s, boolean stageSeven) {
        this.supports = s.supports();
        this.lift = s.lift();
        this.service = s.service();
        this.traverse = s.traverse();
        this.traverseTarget = s.traverseTarget();
        this.current = null;
        this.ticks = 0;
        this.queue.clear();
        Op running = stageSeven ? Op.byStageSevenOrdinal(s.op()) : Op.byOrdinal(s.op());
        if (running != null) {
            this.apply(running);
        }
        for (int ordinal : s.queue()) {
            Op op = stageSeven ? Op.byStageSevenOrdinal(ordinal) : Op.byOrdinal(ordinal);
            if (op != null) {
                this.apply(op);
            }
        }
        this.traverse = this.traverseTarget;
        this.changed = true;
    }

    public void reset() {
        this.restore(new Snapshot(false, false, false, 0.0, 0.0, -1, 0, new int[0]));
    }

    public void parkOnSupports() {
        this.restore(new Snapshot(true, false, false, 0.0, 0.0, -1, 0, new int[0]));
    }
}
