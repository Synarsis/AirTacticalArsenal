package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.HeightFieldShapeSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

public final class GroundBody implements AutoCloseable {

    private final PhysicsWorld world;
    private final int layer;
    private final GroundPatch patch;
    private ShapeRefC shape;
    private int bodyId = com.github.stephengold.joltjni.Jolt.cInvalidBodyId;

    public GroundBody(PhysicsWorld world, int slot, GroundPatch patch) {
        this.world = world;
        this.layer = PhysicsLayers.ground(slot);
        this.patch = patch;
        HeightFieldShapeSettings settings = new HeightFieldShapeSettings(patch.heights(), new Vec3(0f, 0f, 0f),
                new Vec3((float) patch.step(), 1f, (float) patch.step()), patch.count());
        ShapeResult result = settings.create();
        if (result.hasError()) {
            settings.close();
            throw new IllegalStateException("ground patch shape: " + result.getError());
        }
        this.shape = result.get();
        settings.close();
        BodyCreationSettings body = new BodyCreationSettings(this.shape,
                new RVec3(patch.originX(), 0.0, patch.originZ()), new Quat(0f, 0f, 0f, 1f),
                EMotionType.Static, this.layer);
        body.setFriction(1.0f);
        body.setRestitution(0.0f);
        BodyInterface bodies = world.bodies();
        Body created = bodies.createBody(body);
        body.close();
        this.bodyId = created.getId();
        bodies.addBody(this.bodyId, EActivation.DontActivate);
    }

    public GroundPatch patch() {
        return this.patch;
    }

    public int bodyId() {
        return this.bodyId;
    }

    @Override
    public void close() {
        if (this.bodyId != com.github.stephengold.joltjni.Jolt.cInvalidBodyId) {
            BodyInterface bodies = this.world.bodies();
            bodies.removeBody(this.bodyId);
            bodies.destroyBody(this.bodyId);
            this.bodyId = com.github.stephengold.joltjni.Jolt.cInvalidBodyId;
        }
        if (this.shape != null) {
            this.shape.close();
            this.shape = null;
        }
    }
}
