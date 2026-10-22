package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.config.VehiclePhysicsMode;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotRefusal;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotTiming;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorld;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(AirTacticalArsenal.MODID)
@PrefixGameTestTemplate(false)
public final class PatriotGameTests {

    private static final String TEMPLATE = "flat_wide";
    private static final int SETTLE = 70;
    private static final int OPERATION_SLACK = 12;

    private PatriotGameTests() {
    }

    private static PatriotVehicleEntity spawn(GameTestHelper helper, PatriotKind kind) {
        PatriotVehicleEntity vehicle = kind.isLauncher()
                ? new com.synarsis.airtacticalarsenal.entity.patriot.PatriotLauncherEntity(
                        ModEntities.PATRIOT_LAUNCHER.get(), helper.getLevel())
                : new com.synarsis.airtacticalarsenal.entity.patriot.PatriotRadarEntity(
                        ModEntities.PATRIOT_RADAR.get(), helper.getLevel());
        vehicle.isLegitSpawn = true;
        Vec3 at = helper.absoluteVec(new Vec3(24.5, 2.1, 7.0));
        vehicle.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(vehicle);
        return vehicle;
    }

    private static Player stand(GameTestHelper helper, PatriotVehicleEntity vehicle) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(vehicle.getX(), vehicle.getY() + 1.0, vehicle.getZ() + 3.0);
        return player;
    }

    private static void command(PatriotVehicleEntity vehicle, Player player, Command command) {
        vehicle.handleCommand(player, (byte) command.ordinal());
    }

    private static boolean has(PatriotVehicleEntity vehicle, int bit) {
        return (vehicle.currentMask() & bit) != 0;
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 200)
    public static void placesLauncherAndRadar(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos floor = helper.absolutePos(new BlockPos(24, 1, 6));
        Vec3 click = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        player.setYRot(0.0f);

        ItemStack launcher = new ItemStack(ModItems.PATRIOT_LAUNCHER.get());
        InteractionResult first = launcher.getItem().useOn(new UseOnContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, launcher, new BlockHitResult(click, Direction.UP, floor, false)));
        var area = com.synarsis.airtacticalarsenal.item.PatriotItem.footprint(click.x, click.y + 0.05, click.z, 0.0f);
        StringBuilder why = new StringBuilder();
        for (var shape : helper.getLevel().getBlockCollisions(null, area.worldAabb())) {
            for (var box : shape.toAabbs()) {
                if (com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat.mtv(area, box.deflate(0.02)) != null) {
                    why.append(box).append(' ');
                }
            }
        }
        helper.assertTrue(first.consumesAction(), "the launcher was not placed: " + first + " blocks " + why + " area " + area.worldAabb() + " "
                + com.synarsis.airtacticalarsenal.item.PatriotItem.placementProblem(helper.getLevel(), player,
                        click.x, click.y, click.z, 0.0f));

        ItemStack radar = new ItemStack(ModItems.PATRIOT_RADAR.get());
        BlockPos overlapping = floor.offset(0, 0, 4);
        InteractionResult refused = radar.getItem().useOn(new UseOnContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, radar, new BlockHitResult(
                        new Vec3(overlapping.getX() + 0.5, overlapping.getY() + 1.0, overlapping.getZ() + 0.5),
                        Direction.UP, overlapping, false)));
        helper.assertTrue(!refused.consumesAction(), "the radar must not go into the launcher");

        BlockPos clear = floor.offset(12, 0, 0);
        InteractionResult second = radar.getItem().useOn(new UseOnContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, radar, new BlockHitResult(
                        new Vec3(clear.getX() + 0.5, clear.getY() + 1.0, clear.getZ() + 0.5), Direction.UP, clear,
                        false)));
        helper.assertTrue(second.consumesAction(), "the radar was not placed: " + second);

        List<PatriotVehicleEntity> found = helper.getLevel().getEntitiesOfClass(PatriotVehicleEntity.class,
                new net.minecraft.world.phys.AABB(Vec3.atCenterOf(floor), Vec3.atCenterOf(floor)).inflate(60.0),
                v -> player.getUUID().equals(v.getOwnerUuid()));
        helper.assertTrue(found.size() == 2, "vehicles found: " + found.size());
        boolean launchers = false;
        boolean radars = false;
        for (PatriotVehicleEntity vehicle : found) {
            helper.assertTrue(vehicle.isLegitSpawn, "not marked as legitimate");
            launchers |= vehicle.kind() == PatriotKind.LAUNCHER;
            radars |= vehicle.kind() == PatriotKind.RADAR;
            vehicle.discard();
        }
        helper.assertTrue(launchers && radars, "one of each expected");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 200,
            batch = "physicsFixes")
    public static void aTrailerTakesNoRiders(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        vehicle.placeOnSupports();
        net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(
                net.minecraft.world.entity.EntityType.ARMOR_STAND, helper.getLevel());
        helper.runAfterDelay(3, () -> {
            stand.moveTo(vehicle.getX(), vehicle.getY() + 3.0, vehicle.getZ());
            helper.getLevel().addFreshEntity(stand);
            helper.assertTrue(!stand.startRiding(vehicle), "a trailer must not be rideable");
            helper.assertTrue(vehicle.getPassengers().isEmpty(), "the trailer has passengers");
            helper.assertTrue(vehicle.getControllingPassenger() == null, "the trailer has a driver");
        });
        helper.runAfterDelay(18, () -> {
            stand.discard();
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 500)
    public static void deployFinishesInTheRightTime(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Player[] operator = new Player[1];
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(vehicle.usesJolt(), "the free trailer should have its static body: "
                    + vehicle.debugState() + " ticks " + vehicle.tickCount + " removed " + vehicle.isRemoved());
            operator[0] = stand(helper, vehicle);
        });
        helper.runAfterDelay(SETTLE + 10, () -> {
            command(vehicle, operator[0], Command.DEPLOY);
            var blocker = vehicle.supportsBlocker();
            helper.assertTrue(vehicle.denial(Command.DEPLOY).reason() == PatriotRefusal.ALREADY,
                    "a second DEPLOY should be refused as done: " + vehicle.denial(Command.DEPLOY).reason()
                            + " blocker " + (blocker == null ? "none" : blocker.at() + " " + blocker.detail())
                            + " at " + vehicle.position() + " pitch " + vehicle.getXRot());
        });
        int total = PatriotTiming.SUPPORTS_TICKS + PatriotTiming.ERECTOR_TICKS;
        helper.runAfterDelay(SETTLE + 10 + total - 8, () -> {
            helper.assertTrue(vehicle.currentOp() >= 0, "finished early");
            helper.assertTrue(!has(vehicle, PatriotSequence.LIFT_BIT), "raised early");
        });
        helper.runAfterDelay(SETTLE + 10 + total + OPERATION_SLACK, () -> {
            helper.assertTrue(vehicle.currentOp() < 0, "still working: op " + vehicle.currentOp());
            helper.assertTrue(has(vehicle, PatriotSequence.SUPPORTS_BIT) && has(vehicle, PatriotSequence.LIFT_BIT),
                    "supports or launcher missing: " + vehicle.debugState());
            helper.assertTrue(vehicle.joltView() != null && vehicle.joltView().active(),
                    "the free trailer must keep its static body: " + vehicle.debugState());
            helper.assertTrue(vehicle.whyNotDrive() == PatriotRefusal.NEED_COUPLED,
                    "a trailer cannot drive itself: " + vehicle.whyNotDrive());
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 300)
    public static void m983TractorPlacesAndTakesACrew(GameTestHelper helper) {
        com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity tractor =
                ModEntities.M983_TRACTOR.get().create(helper.getLevel());
        helper.assertTrue(tractor != null, "the tractor entity was not created");
        Vec3 at = helper.absoluteVec(new Vec3(22.5, 2.1, 7.0));
        tractor.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        tractor.isLegitSpawn = true;
        helper.getLevel().addFreshEntity(tractor);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        Player passenger = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(driver.startRiding(tractor), "the driver could not board");
            helper.assertTrue(passenger.startRiding(tractor), "the passenger could not board");
            helper.assertTrue(tractor.seatOf(driver) == 0, "the driver must sit at the wheel");
            helper.assertTrue(tractor.seatOf(passenger) == 1, "the passenger must take the second seat");
            helper.assertTrue(tractor.getHealth() == 300.0f, "tractor health " + tractor.getHealth());
            helper.assertTrue(tractor.getParts().length == 4, "tractor parts " + tractor.getParts().length);
        });
        helper.runAfterDelay(SETTLE + 20, () -> {
            driver.stopRiding();
            passenger.stopRiding();
            tractor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 400)
    public static void m983TractorDrivesOnItsOwn(GameTestHelper helper) {
        com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity tractor =
                ModEntities.M983_TRACTOR.get().create(helper.getLevel());
        helper.assertTrue(tractor != null, "the tractor entity was not created");
        Vec3 at = helper.absoluteVec(new Vec3(22.5, 2.1, 7.0));
        tractor.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        tractor.isLegitSpawn = true;
        helper.getLevel().addFreshEntity(tractor);
        helper.runAfterDelay(SETTLE, () -> tractor.setTestThrottle(0.5));
        helper.runAfterDelay(SETTLE + 90, () -> {
            helper.assertTrue(tractor.getZ() > at.z + 2.0,
                    "the tractor did not drive forward: z " + tractor.getZ() + " from " + at.z);
            tractor.setTestThrottle(0.0);
            tractor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 500)
    public static void aFreeTrailerStandsOnItsSupports(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        vehicle.placeOnSupports();
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(has(vehicle, PatriotSequence.SUPPORTS_BIT),
                    "a free trailer must stand on its supports: " + vehicle.debugState());
            helper.assertTrue(vehicle.joltView() != null && vehicle.joltView().active(),
                    "the free trailer needs a static body: " + vehicle.debugState());
            int[] ids = vehicle.joltView().bodyIds();
            helper.assertTrue(ids.length == 1, "one body expected, got " + ids.length);
            PhysicsWorld world = PhysicsWorlds.existing(helper.getLevel());
            helper.assertTrue(world != null && world.hasBody(ids[0]), "the static body is missing");
            helper.assertTrue(vehicle.joltView().body().mode() == JoltVehicle.Mode.STATIC,
                    "the body must be static: " + vehicle.joltView().body().mode());
            helper.assertTrue(vehicle.getPassengers().isEmpty(), "a trailer carries no crew");
            double y = vehicle.getY();
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(Math.abs(vehicle.getY() - y) < 0.02,
                        "the supports must hold it, y moved " + (vehicle.getY() - y));
                vehicle.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300, batch = "legacyPatriot")
    public static void withoutJoltItDeploysButDoesNotDrive(GameTestHelper helper) {
        VehiclePhysicsMode before = VehiclePhysicsMode.JOLT;
        ShahedConfig.VEHICLE_PHYSICS.set(VehiclePhysicsMode.LEGACY);
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Player[] operator = new Player[1];
        helper.runAfterDelay(40, () -> operator[0] = stand(helper, vehicle));
        helper.runAfterDelay(50, () -> command(vehicle, operator[0], Command.SUPPORTS_OUT));
        helper.runAfterDelay(50 + PatriotTiming.SUPPORTS_TICKS + OPERATION_SLACK, () -> {
            boolean out = has(vehicle, PatriotSequence.SUPPORTS_BIT);
            String state = vehicle.debugState();
            boolean noBody = !vehicle.usesJolt();
            PatriotRefusal why = vehicle.whyNotDrive();
            ShahedConfig.VEHICLE_PHYSICS.set(before);
            vehicle.discard();
            helper.assertTrue(noBody, "Jolt must stay off in LEGACY");
            helper.assertTrue(why == PatriotRefusal.NEED_COUPLED,
                    "a trailer never drives itself: " + why);
            helper.assertTrue(out, "the supports must still move: " + state);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 400)
    public static void aFreeTrailerDoesNotDriveItself(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        vehicle.placeOnSupports();
        Vec3 start = vehicle.position();
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(vehicle.usesJolt(), "needs its static Jolt body");
            helper.assertTrue(vehicle.joltView().body().mode() == JoltVehicle.Mode.STATIC,
                    "the free body must be static: " + vehicle.joltView().body().mode());
        });
        helper.runAfterDelay(SETTLE + 90, () -> {
            double moved = vehicle.position().distanceTo(start);
            helper.assertTrue(moved < 0.02, "a free trailer must stand still, moved " + moved);
            helper.assertTrue(vehicle.getControllingPassenger() == null, "there is no driver");
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 400)
    public static void savesAndLoadsInTheMiddleOfAnOperation(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        Player[] operator = new Player[1];
        helper.runAfterDelay(SETTLE, () -> {
            operator[0] = stand(helper, vehicle);
            vehicle.setOwnerUuid(operator[0].getUUID());
        });
        helper.runAfterDelay(SETTLE + 10, () -> command(vehicle, operator[0], Command.DEPLOY));
        helper.runAfterDelay(SETTLE + 10 + 100, () -> {
            helper.assertTrue(vehicle.currentOp() >= 0, "the supports should still be moving: " + vehicle.debugState());
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            vehicle.saveWithoutId(tag);
            helper.assertTrue(!tag.contains("Seats"), "a trailer stores no seats");
            tag.putFloat("Health", 321.0f);
            PatriotVehicleEntity loaded = new com.synarsis.airtacticalarsenal.entity.patriot.PatriotRadarEntity(
                    ModEntities.PATRIOT_RADAR.get(), helper.getLevel());
            loaded.load(tag);
            helper.assertTrue(loaded.currentOp() < 0, "an operation must be finished at once on loading");
            helper.assertTrue(has(loaded, PatriotSequence.SUPPORTS_BIT) && has(loaded, PatriotSequence.LIFT_BIT),
                    "the queue must be finished too: " + loaded.debugState());
            helper.assertTrue(Math.abs(loaded.getHealth() - 321.0f) < 0.01f, "health " + loaded.getHealth());
            helper.assertTrue(loaded.isLegitSpawn, "legitimate spawn is not saved");
            helper.assertTrue(operator[0].getUUID().equals(loaded.getOwnerUuid()), "owner is not saved");
            helper.assertTrue(loaded.getPassengers().isEmpty(), "a trailer loads with no crew");
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 300)
    public static void zonesScaleTheDamageAndTheVehicleDies(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        helper.runAfterDelay(SETTLE, () -> {
            float full = vehicle.getHealth();
            var source = helper.getLevel().damageSources().generic();
            helper.assertTrue(vehicle.hurtInZone(source, 52.0f, com.synarsis.airtacticalarsenal.entity.patriot.PatriotZone.TRAILER),
                    "no damage taken");
            float hull = full - vehicle.getHealth();
            vehicle.setHealth(full);
            vehicle.hurtInZone(source, 52.0f, com.synarsis.airtacticalarsenal.entity.patriot.PatriotZone.PACK);
            float pack = full - vehicle.getHealth();
            helper.assertTrue(Math.abs(hull - 50.0f) < 0.01f, "hull " + hull);
            helper.assertTrue(Math.abs(pack - 75.0f) < 0.01f, "the pack takes one and a half times: " + pack);
            helper.assertTrue(!vehicle.hurtInZone(source, 1.5f, null), "the armour stops small hits");
            helper.assertTrue(full > 0.0f, "launcher health " + full);

            vehicle.setHealth(full);
            vehicle.hurtInZone(source, 10000.0f, null);
            helper.assertTrue(vehicle.isRemoved(), "it should be destroyed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 300)
    public static void ironRepairsIt(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        helper.runAfterDelay(20, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            vehicle.setHealth(100.0f);
            float max = vehicle.getMaxHealth();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));
            vehicle.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(Math.abs(vehicle.getHealth() - Math.min(max, 100.0f + 0.45f * max)) < 0.5f,
                    "health " + vehicle.getHealth() + " max " + max);
            helper.assertTrue(max > 0.0f, "radar health " + max);
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 300)
    public static void explosionPushesIt(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        vehicle.placeOnSupports();
        Vec3 start = vehicle.position();
        float health = vehicle.getHealth();
        helper.runAfterDelay(60, () -> {
            Vec3 blast = helper.absoluteVec(new Vec3(24.5, 2.5, 1.0));
            helper.getLevel().explode(null, blast.x, blast.y, blast.z, 6.0f,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        });
        helper.runAfterDelay(140, () -> {
            double moved = vehicle.position().distanceTo(start);
            helper.assertTrue(moved < 0.05, "a static trailer must not be pushed, moved " + moved);
            helper.assertTrue(vehicle.getHealth() <= health, "the blast must not heal it");
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 200)
    public static void theTrailerPoseIsChecked(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        helper.runAfterDelay(10, () -> {
            vehicle.acceptPose(1.0f, 12.0f, 3.0f, 1.0f);
            helper.assertTrue(Math.abs(vehicle.getArticulationYaw() - 12.0f) < 1.0E-4f, "a plausible pose is taken");
            vehicle.acceptPose(0.0f, 120.0f, 0.0f, 0.0f);
            helper.assertTrue(Math.abs(vehicle.getArticulationYaw() - 12.0f) < 1.0E-4f, "a bent hitch beyond the limit is refused");
            vehicle.acceptPose(0.0f, Float.NaN, 0.0f, 0.0f);
            helper.assertTrue(Math.abs(vehicle.getArticulationYaw() - 12.0f) < 1.0E-4f, "not a number is refused");
            vehicle.acceptPose(200.0f, 0.0f, 0.0f, 0.0f);
            helper.assertTrue(Math.abs(vehicle.getRoll() - 1.0f) < 1.0E-4f, "an impossible roll is refused");
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 300)
    public static void givesItsBodiesBack(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        vehicle.placeOnSupports();
        helper.runAfterDelay(SETTLE, () -> {
            PhysicsWorld world = PhysicsWorlds.existing(helper.getLevel());
            helper.assertTrue(world != null, "needs a Jolt world");
            int[] own = vehicle.joltView().bodyIds();
            helper.assertTrue(own.length == 1, "one static body expected, got " + own.length);
            helper.assertTrue(world.hasBody(own[0]), "its own body " + own[0] + " is missing");
            vehicle.discard();
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!world.hasBody(own[0]), "body " + own[0] + " was left behind");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = "patriotPhysics", timeoutTicks = 500)
    public static void aStaticTrailerDoesNotSink(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        vehicle.placeOnSupports();
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(!vehicle.isOverturned(),
                    "the trailer must stand: roll " + vehicle.getRoll() + " pitch " + vehicle.getXRot());
            double y = vehicle.getY();
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(Math.abs(vehicle.getY() - y) < 0.02,
                        "the static body sank by " + (vehicle.getY() - y));
                helper.assertTrue(!vehicle.isOverturned(), "it stayed upright");
                vehicle.discard();
                helper.succeed();
            });
        });
    }
}
