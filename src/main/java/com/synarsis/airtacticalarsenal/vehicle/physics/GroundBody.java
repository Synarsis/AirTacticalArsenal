package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.HeightFieldShape;
import com.github.stephengold.joltjni.HeightFieldShapeSettings;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeRefC;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

import java.nio.FloatBuffer;

public final class GroundBody implements AutoCloseable {

    private final PhysicsWorld world;
    private final int layer;
    private final GroundPatch patch;
    private ShapeRefC shape;
    private int bodyId = Jolt.cInvalidBodyId;

    public GroundBody(PhysicsWorld world, int slot, GroundPatch patch) {
        this.world = world;
        this.layer = PhysicsLayers.ground(slot);
        this.patch = patch;
        HeightFieldShapeSettings settings = new HeightFieldShapeSettings(patch.heights(), new Vec3(0f, 0f, 0f),
                new Vec3((float) patch.step(), 1f, (float) patch.step()), patch.count());
        if (patch.hasRaw()) {
            settings.setMinHeightValue((float) patch.low() - 0.01f);
            settings.setMaxHeightValue((float) patch.high() + 0.01f);
        }
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
        patch.takeDirty();
    }

    public GroundPatch patch() {
        return this.patch;
    }

    public int bodyId() {
        return this.bodyId;
    }

    public void pushChanges() {
        boolean moved = this.patch.originMoved();
        GroundPatch.Region region = this.patch.takeDirty();
        if (region == null) {
            return;
        }
        if (moved) {
            this.world.bodies().setPosition(this.bodyId, new RVec3(this.patch.originX(), 0.0, this.patch.originZ()),
                    EActivation.DontActivate);
        }
        int count = this.patch.count();
        int startX = region.minX() & ~1;
        int startZ = region.minZ() & ~1;
        int endX = Math.min(count - 1, region.maxX() | 1);
        int endZ = Math.min(count - 1, region.maxZ() | 1);
        int sizeX = endX - startX + 1;
        int sizeZ = endZ - startZ + 1;
        FloatBuffer values = Jolt.newDirectFloatBuffer(sizeX * sizeZ);
        float[] heights = this.patch.heights();
        for (int iz = 0; iz < sizeZ; iz++) {
            values.position(iz * sizeX);
            values.put(heights, (startZ + iz) * count + startX, sizeX);
        }
        values.position(0);
        HeightFieldShape field = (HeightFieldShape) this.shape.getPtr();
        field.setHeights(startX, startZ, sizeX, sizeZ, values, sizeX, this.world.tempAllocator());
        this.world.bodies().notifyShapeChanged(this.bodyId, new Vec3(0f, 0f, 0f), false, EActivation.DontActivate);
    }

    @Override
    public void close() {
        if (this.bodyId != Jolt.cInvalidBodyId) {
            BodyInterface bodies = this.world.bodies();
            bodies.removeBody(this.bodyId);
            bodies.destroyBody(this.bodyId);
            this.bodyId = Jolt.cInvalidBodyId;
        }
        if (this.shape != null) {
            this.shape.close();
            this.shape = null;
        }
    }
}
