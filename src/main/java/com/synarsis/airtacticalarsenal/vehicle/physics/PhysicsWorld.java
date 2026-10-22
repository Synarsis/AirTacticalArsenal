package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.ContactManifold;
import com.github.stephengold.joltjni.CustomContactListener;
import com.github.stephengold.joltjni.JobSystemSingleThreaded;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.TempAllocatorImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PhysicsWorld implements AutoCloseable {

    public static final int SUBSTEPS = 3;
    public static final float SUBSTEP_SECONDS = 1.0f / 60.0f;
    public static final float STANDARD_GRAVITY = 9.81f;
    public static final int IDLE_LIMIT_TICKS = 600;

    private static final int MAX_BODIES = 256;
    private static final int TEMP_BYTES = 8 * 1024 * 1024;

    private final boolean clientSide;
    private final BroadPhaseLayerInterfaceTable broadPhaseLayers;
    private final ObjectLayerPairFilterTable pairFilter;
    private final ObjectVsBroadPhaseLayerFilterTable broadPhaseFilter;
    private final PhysicsSystem system;
    private final TempAllocatorImpl tempAllocator;
    private final JobSystemSingleThreaded jobs;
    private final boolean[] slotTaken = new boolean[PhysicsLayers.MAX_VEHICLES];
    private final List<Stepper> steppers = new ArrayList<>();
    private final Map<Integer, ImpactSink> impactSinks = new HashMap<>();
    private final CustomContactListener contacts;
    private int idleTicks;
    private boolean closed;
    private long stepCount;

    public interface ImpactSink {
        void hit(int otherBodyId, double approachSpeed, double normalY);
    }

    public interface Stepper {
        void beforeStep(float dt);

        void afterStep(float dt);
    }

    public PhysicsWorld(boolean clientSide, double gravityScale) {
        if (!JoltRuntime.available()) {
            throw new IllegalStateException("Jolt Physics is not available");
        }
        this.clientSide = clientSide;
        this.broadPhaseLayers = new BroadPhaseLayerInterfaceTable(PhysicsLayers.OBJECT_COUNT, PhysicsLayers.BROAD_COUNT);
        for (int layer = 0; layer < PhysicsLayers.OBJECT_COUNT; layer++) {
            this.broadPhaseLayers.mapObjectToBroadPhaseLayer(layer, PhysicsLayers.broadPhase(layer));
        }
        this.pairFilter = new ObjectLayerPairFilterTable(PhysicsLayers.OBJECT_COUNT);
        for (int a = 0; a < PhysicsLayers.OBJECT_COUNT; a++) {
            for (int b = a; b < PhysicsLayers.OBJECT_COUNT; b++) {
                if (PhysicsLayers.collide(a, b)) {
                    this.pairFilter.enableCollision(a, b);
                }
            }
        }
        // jolt-jni 6.2.0 reads the two layer counts of this constructor in swapped order
        this.broadPhaseFilter = new ObjectVsBroadPhaseLayerFilterTable(this.broadPhaseLayers,
                PhysicsLayers.OBJECT_COUNT, this.pairFilter, PhysicsLayers.BROAD_COUNT);
        this.system = new PhysicsSystem();
        this.system.init(MAX_BODIES, 0, MAX_BODIES * 4, MAX_BODIES * 4, this.broadPhaseLayers,
                this.broadPhaseFilter, this.pairFilter);
        this.setGravityScale(gravityScale);
        this.contacts = new CustomContactListener() {
            @Override
            public void onContactAdded(long body1Va, long body2Va, long manifoldVa, long settingsVa) {
                PhysicsWorld.this.contactAdded(body1Va, body2Va, manifoldVa);
            }
        };
        this.system.setContactListener(this.contacts);
        this.tempAllocator = new TempAllocatorImpl(TEMP_BYTES);
        this.jobs = new JobSystemSingleThreaded(Jolt.cMaxPhysicsJobs);
    }

    public void watchImpacts(int bodyId, ImpactSink sink) {
        this.impactSinks.put(bodyId, sink);
    }

    public void unwatchImpacts(int bodyId) {
        this.impactSinks.remove(bodyId);
    }

    private void contactAdded(long body1Va, long body2Va, long manifoldVa) {
        if (this.impactSinks.isEmpty()) {
            return;
        }
        Body first = new Body(body1Va);
        Body second = new Body(body2Va);
        int id1 = first.getId();
        int id2 = second.getId();
        ImpactSink sink1 = this.impactSinks.get(id1);
        ImpactSink sink2 = this.impactSinks.get(id2);
        if (sink1 == null && sink2 == null) {
            return;
        }
        com.github.stephengold.joltjni.Vec3 normal = new ContactManifold(manifoldVa).getWorldSpaceNormal();
        com.github.stephengold.joltjni.Vec3 v1 = first.getLinearVelocity();
        com.github.stephengold.joltjni.Vec3 v2 = second.getLinearVelocity();
        double approach = (v1.getX() - v2.getX()) * normal.getX() + (v1.getY() - v2.getY()) * normal.getY()
                + (v1.getZ() - v2.getZ()) * normal.getZ();
        if (sink1 != null && approach > 0.0) {
            sink1.hit(id2, approach, -normal.getY());
        }
        if (sink2 != null && approach > 0.0) {
            sink2.hit(id1, approach, normal.getY());
        }
    }

    public boolean broadPhaseCollides(int objectLayer, int broadLayer) {
        return this.broadPhaseFilter.shouldCollide(objectLayer, broadLayer);
    }

    public boolean isClientSide() {
        return this.clientSide;
    }

    public TempAllocatorImpl tempAllocator() {
        return this.tempAllocator;
    }

    public PhysicsSystem system() {
        return this.system;
    }

    public BodyInterface bodies() {
        return this.system.getBodyInterface();
    }

    public void setGravityScale(double scale) {
        this.system.setGravity(0.0f, (float) (-STANDARD_GRAVITY * scale), 0.0f);
    }

    public int bodyCount() {
        return this.system.getNumBodies();
    }

    public boolean hasBody(int bodyId) {
        return bodyId != Jolt.cInvalidBodyId && !this.closed && this.bodies().isAdded(bodyId);
    }

    public long stepCount() {
        return this.stepCount;
    }

    public synchronized int takeSlot() {
        for (int i = 0; i < this.slotTaken.length; i++) {
            if (!this.slotTaken[i]) {
                this.slotTaken[i] = true;
                return i;
            }
        }
        return -1;
    }

    public synchronized void releaseSlot(int slot) {
        if (slot >= 0 && slot < this.slotTaken.length) {
            this.slotTaken[slot] = false;
        }
    }

    public int slotsInUse() {
        int n = 0;
        for (boolean taken : this.slotTaken) {
            if (taken) {
                n++;
            }
        }
        return n;
    }

    public void addStepper(Stepper stepper) {
        this.steppers.add(stepper);
    }

    public void removeStepper(Stepper stepper) {
        this.steppers.remove(stepper);
    }

    public void step() {
        if (this.closed) {
            return;
        }
        for (int i = 0; i < SUBSTEPS; i++) {
            for (Stepper stepper : this.steppers) {
                stepper.beforeStep(SUBSTEP_SECONDS);
            }
            this.system.update(SUBSTEP_SECONDS, 1, this.tempAllocator, this.jobs);
            this.stepCount++;
            for (Stepper stepper : this.steppers) {
                stepper.afterStep(SUBSTEP_SECONDS);
            }
        }
    }

    public boolean idleExpired(boolean hasVehicles) {
        if (hasVehicles) {
            this.idleTicks = 0;
            return false;
        }
        return ++this.idleTicks >= IDLE_LIMIT_TICKS;
    }

    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.steppers.clear();
        this.impactSinks.clear();
        this.system.removeAllBodies();
        this.system.destroyAllBodies();
        this.jobs.close();
        this.tempAllocator.close();
        this.system.close();
        this.contacts.close();
        this.broadPhaseFilter.close();
        this.pairFilter.close();
        this.broadPhaseLayers.close();
    }
}
