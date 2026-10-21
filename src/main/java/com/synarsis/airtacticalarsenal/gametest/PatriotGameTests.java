package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.config.VehiclePhysicsMode;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotGeometry;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotJolt;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotRefusal;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotTiming;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
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

    private static Player board(GameTestHelper helper, PatriotVehicleEntity vehicle) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.setPos(vehicle.getX(), vehicle.getY() + 3.0, vehicle.getZ() + 14.0);
        driver.startRiding(vehicle);
        return driver;
    }

    private static void command(PatriotVehicleEntity vehicle, Player player, Command command) {
        vehicle.handleCommand(player, (byte) command.ordinal());
    }

    private static boolean has(PatriotVehicleEntity vehicle, int bit) {
        return (vehicle.currentMask() & bit) != 0;
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 200)
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

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 500)
    public static void deployFinishesInTheRightTime(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Player[] driver = new Player[1];
        long[] startedAt = new long[1];
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(vehicle.usesJolt(), "the launcher should be driven by Jolt: " + vehicle.debugState() + " ticks " + vehicle.tickCount + " removed " + vehicle.isRemoved());
            driver[0] = board(helper, vehicle);
        });
        helper.runAfterDelay(SETTLE + 10, () -> {
            helper.assertTrue(vehicle.hasPassenger(driver[0]), "could not board");
            startedAt[0] = helper.getLevel().getGameTime();
            command(vehicle, driver[0], Command.DEPLOY);
            var blocker = vehicle.supportsBlocker();
            StringBuilder feet = new StringBuilder();
            for (var box : com.synarsis.airtacticalarsenal.entity.patriot.PatriotHitbox.of(vehicle.kind()).compute(
                    com.synarsis.airtacticalarsenal.entity.patriot.PatriotClearance.withSupportsOut(vehicle.kind(),
                            vehicle.hitboxPose()), vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(),
                    vehicle.getXRot(), vehicle.getRoll())) {
                if (box.zone() == com.synarsis.airtacticalarsenal.entity.patriot.PatriotZone.SUPPORT && box.centre().x < vehicle.getX() - 2.0 && box.centre().z < vehicle.getZ() - 5.0) {
                    feet.append(String.format(" [y %.3f..%.3f centre %.2f %.2f %.2f half %.3f %.3f %.3f]", box.minY(), box.maxY(), box.centre().x - vehicle.getX(), box.centre().y, box.centre().z - vehicle.getZ(), box.hx(), box.hy(), box.hz()));
                }
            }
            helper.assertTrue(vehicle.denial(Command.DEPLOY).reason() == PatriotRefusal.ALREADY,
                    "a second DEPLOY should be refused as done: " + vehicle.denial(Command.DEPLOY).reason()
                            + " blocker " + (blocker == null ? "none" : blocker.at() + " " + blocker.detail()) + " at " + vehicle.position()
                            + feet + " pitch " + vehicle.getXRot() + " floor " + helper.absolutePos(BlockPos.ZERO));
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
            helper.assertTrue(vehicle.joltView() != null && vehicle.joltView().role() == PatriotJolt.Role.HOLD,
                    "with the supports out it must be held: "
                            + (vehicle.joltView() == null ? "no physics" : vehicle.joltView().role()));
            helper.assertTrue(vehicle.whyNotDrive() != PatriotRefusal.NONE, "it must not drive deployed");
            driver[0].stopRiding();
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300)
    public static void detachRefusedWithAWallAhead(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        Player[] driver = new Player[1];
        helper.runAfterDelay(SETTLE, () -> {
            driver[0] = board(helper, vehicle);
            int wallZ = (int) Math.floor(vehicle.getZ()) + 19;
            for (int dx = -7; dx <= 7; dx++) {
                for (int dy = 1; dy <= 6; dy++) {
                    helper.getLevel().setBlock(new BlockPos((int) Math.floor(vehicle.getX()) + dx,
                            helper.absolutePos(BlockPos.ZERO).getY() + 1 + dy, wallZ), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        });
        helper.runAfterDelay(SETTLE + 25, () -> {
            helper.assertTrue(vehicle.denial(Command.DETACH).reason() == PatriotRefusal.TRACTOR_NO_ROOM,
                    "the wall must stop the detach: " + vehicle.denial(Command.DETACH).reason());
            command(vehicle, driver[0], Command.DETACH);
        });
        helper.runAfterDelay(SETTLE + 40, () -> {
            helper.assertTrue(vehicle.currentOp() < 0 && vehicle.currentMask() == 0,
                    "nothing may start: " + vehicle.debugState());
            driver[0].stopRiding();
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 900)
    public static void detachAndAttachOnTheFlat(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Player[] driver = new Player[1];
        double[] seatBefore = new double[1];
        int detach = PatriotTiming.SUPPORTS_TICKS + PatriotTiming.LATCH_TICKS + PatriotTiming.TRACTOR_TICKS;
        int attach = PatriotTiming.TRACTOR_TICKS + PatriotTiming.LATCH_TICKS;
        int t0 = SETTLE + 10;
        helper.runAfterDelay(SETTLE, () -> driver[0] = board(helper, vehicle));
        helper.runAfterDelay(t0, () -> {
            helper.assertTrue(vehicle.hasPassenger(driver[0]), "could not board");
            seatBefore[0] = driver[0].position().subtract(vehicle.position()).length();
            command(vehicle, driver[0], Command.DETACH);
        });
        helper.runAfterDelay(t0 + detach + OPERATION_SLACK, () -> {
            helper.assertTrue(vehicle.currentOp() < 0, "still working: " + vehicle.debugState());
            helper.assertTrue(has(vehicle, PatriotSequence.TRACTOR_OUT_BIT) && has(vehicle, PatriotSequence.LATCH_OPEN_BIT)
                    && has(vehicle, PatriotSequence.SUPPORTS_BIT), "not detached: " + vehicle.debugState() + " " + vehicle.denial(Command.DETACH).reason()
                    + " " + java.util.Arrays.toString(vehicle.denial(Command.DETACH).args()) + " entity " + vehicle.position());
            helper.assertTrue(Math.abs(vehicle.tractorOffsetBlocks() - PatriotGeometry.TRACTOR_OFFSET_BLOCKS) < 0.01,
                    "tractor offset " + vehicle.tractorOffsetBlocks());
            double moved = driver[0].position().subtract(vehicle.position()).length() - seatBefore[0];
            helper.assertTrue(moved > 4.0, "the seat must ride with the tractor, it moved " + moved);
            helper.assertTrue(vehicle.joltView().train() != null && !vehicle.joltView().train().linked(),
                    "the link must be gone");
            helper.assertTrue(vehicle.joltView().train().tractor().mode() != JoltVehicle.Mode.DYNAMIC,
                    "tractor mode " + vehicle.joltView().train().tractor().mode());
            helper.assertTrue(vehicle.joltView().train().trailer().mode() == JoltVehicle.Mode.STATIC,
                    "the trailer stands: " + vehicle.joltView().train().trailer().mode());
            command(vehicle, driver[0], Command.ATTACH);
        });
        helper.runAfterDelay(t0 + detach + OPERATION_SLACK + attach + OPERATION_SLACK, () -> {
            helper.assertTrue(vehicle.currentOp() < 0 && vehicle.currentMask() == PatriotSequence.SUPPORTS_BIT,
                    "not attached: " + vehicle.debugState());
            helper.assertTrue(vehicle.tractorOffsetBlocks() < 0.01, "tractor offset " + vehicle.tractorOffsetBlocks());
            command(vehicle, driver[0], Command.SUPPORTS_IN);
        });
        helper.runAfterDelay(t0 + detach + attach + 2 * OPERATION_SLACK + PatriotTiming.SUPPORTS_TICKS + OPERATION_SLACK,
                () -> {
                    helper.assertTrue(vehicle.currentMask() == 0 && vehicle.currentOp() < 0,
                            "not stowed: " + vehicle.debugState());
                    helper.assertTrue(vehicle.whyNotDrive() == PatriotRefusal.NONE,
                            "it should be able to drive: " + vehicle.whyNotDrive());
                    helper.assertTrue(vehicle.joltView().role() == PatriotJolt.Role.FOLLOW,
                            "driven on the server it follows: " + vehicle.joltView().role());
                    helper.assertTrue(vehicle.seqView().linked() && vehicle.seqView().attached(), "the hitch must be coupled again");
                    driver[0].stopRiding();
                    vehicle.discard();
                    helper.succeed();
                });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300, batch = "legacyPatriot")
    public static void withoutJoltItDeploysButDoesNotDrive(GameTestHelper helper) {
        VehiclePhysicsMode before = VehiclePhysicsMode.JOLT;
        ShahedConfig.VEHICLE_PHYSICS.set(VehiclePhysicsMode.LEGACY);
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Player[] driver = new Player[1];
        boolean[] physicsOff = new boolean[1];
        PatriotRefusal[] hint = new PatriotRefusal[1];
        helper.runAfterDelay(40, () -> driver[0] = board(helper, vehicle));
        helper.runAfterDelay(50, () -> {
            physicsOff[0] = !vehicle.usesJolt();
            hint[0] = vehicle.whyNotDrive();
            command(vehicle, driver[0], Command.SUPPORTS_OUT);
        });
        helper.runAfterDelay(50 + PatriotTiming.SUPPORTS_TICKS + OPERATION_SLACK, () -> {
            boolean out = has(vehicle, PatriotSequence.SUPPORTS_BIT);
            String state = vehicle.debugState();
            ShahedConfig.VEHICLE_PHYSICS.set(before);
            driver[0].stopRiding();
            vehicle.discard();
            helper.assertTrue(physicsOff[0], "Jolt must stay off in LEGACY");
            helper.assertTrue(hint[0] == PatriotRefusal.NO_PHYSICS, "the hint must say why: " + hint[0]);
            helper.assertTrue(out, "it must still deploy: " + state);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 400)
    public static void driverlessTrainFollowsItsTractor(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Vec3[] start = new Vec3[1];
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(vehicle.usesJolt(), "needs Jolt");
            start[0] = vehicle.position();
            vehicle.setTestThrottle(0.6);
        });
        helper.runAfterDelay(SETTLE + 90, () -> {
            vehicle.setTestThrottle(0.0);
            double moved = vehicle.position().subtract(start[0]).length();
            helper.assertTrue(moved > 3.0, "it should have driven off, moved " + moved);
            var train = vehicle.joltView().train();
            helper.assertTrue(train != null && train.linked(), "the hitch must hold");
            var tractor = train.tractor().snapshot();
            var trailer = train.trailer().snapshot();
            double gap = Math.hypot(tractor.x() - trailer.x(), tractor.z() - trailer.z());
            helper.assertTrue(gap < 12.0, "the trailer lost the tractor, gap " + gap);
            double[] articulation = train.articulation();
            helper.assertTrue(Math.abs(articulation[0]) < 10.0, "hitch angle " + articulation[0]);
            helper.assertTrue(trailer.z() > start[0].z, "the trailer must have followed, z " + trailer.z());
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 400)
    public static void savesAndLoadsInTheMiddleOfAnOperation(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        Player[] driver = new Player[1];
        helper.runAfterDelay(SETTLE, () -> {
            driver[0] = board(helper, vehicle);
            vehicle.setOwnerUuid(driver[0].getUUID());
        });
        helper.runAfterDelay(SETTLE + 10, () -> command(vehicle, driver[0], Command.DEPLOY));
        helper.runAfterDelay(SETTLE + 10 + 100, () -> {
            helper.assertTrue(vehicle.currentOp() >= 0, "the supports should still be moving: " + vehicle.debugState());
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            vehicle.saveWithoutId(tag);
            helper.assertTrue(tag.getList("Seats", 10).size() == 1, "the seat is not saved");
            tag.putFloat("Health", 321.0f);
            PatriotVehicleEntity loaded = new com.synarsis.airtacticalarsenal.entity.patriot.PatriotRadarEntity(
                    ModEntities.PATRIOT_RADAR.get(), helper.getLevel());
            loaded.load(tag);
            helper.assertTrue(loaded.currentOp() < 0, "an operation must be finished at once on loading");
            helper.assertTrue(has(loaded, PatriotSequence.SUPPORTS_BIT) && has(loaded, PatriotSequence.LIFT_BIT),
                    "the queue must be finished too: " + loaded.debugState());
            helper.assertTrue(Math.abs(loaded.getHealth() - 321.0f) < 0.01f, "health " + loaded.getHealth());
            helper.assertTrue(loaded.isLegitSpawn, "legitimate spawn is not saved");
            helper.assertTrue(driver[0].getUUID().equals(loaded.getOwnerUuid()), "owner is not saved");
            driver[0].stopRiding();
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300)
    public static void zonesScaleTheDamageAndTheVehicleDies(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        helper.runAfterDelay(SETTLE, () -> {
            float full = vehicle.getHealth();
            var source = helper.getLevel().damageSources().generic();
            helper.assertTrue(vehicle.hurtInZone(source, 52.0f, com.synarsis.airtacticalarsenal.entity.patriot.PatriotZone.TRACTOR),
                    "no damage taken");
            float tractor = full - vehicle.getHealth();
            vehicle.setHealth(full);
            vehicle.hurtInZone(source, 52.0f, com.synarsis.airtacticalarsenal.entity.patriot.PatriotZone.PACK);
            float pack = full - vehicle.getHealth();
            helper.assertTrue(Math.abs(tractor - 50.0f) < 0.01f, "tractor " + tractor);
            helper.assertTrue(Math.abs(pack - 75.0f) < 0.01f, "the pack takes one and a half times: " + pack);
            helper.assertTrue(!vehicle.hurtInZone(source, 1.5f, null), "the armour stops small hits");
            helper.assertTrue(full == 500.0f, "launcher health " + full);

            vehicle.setHealth(full);
            vehicle.hurtInZone(source, 10000.0f, null);
            helper.assertTrue(vehicle.isRemoved(), "it should be destroyed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300)
    public static void ironRepairsIt(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        helper.runAfterDelay(20, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            vehicle.setHealth(100.0f);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));
            vehicle.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(Math.abs(vehicle.getHealth() - (100.0f + 0.45f * 450.0f)) < 0.5f,
                    "health " + vehicle.getHealth());
            helper.assertTrue(vehicle.getMaxHealth() == 450.0f, "radar health " + vehicle.getMaxHealth());
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300)
    public static void explosionPushesIt(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        Vec3 start = helper.absoluteVec(new Vec3(24.5, 2.1, 7.0));
        helper.runAfterDelay(60, () -> {
            Vec3 blast = helper.absoluteVec(new Vec3(24.5, 2.5, 1.0));
            helper.getLevel().explode(null, blast.x, blast.y, blast.z, 12.0f,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        });
        helper.runAfterDelay(140, () -> {
            double moved = vehicle.position().subtract(start).length();
            helper.assertTrue(moved > 0.5, "moved only " + moved);
            vehicle.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 200)
    public static void theDriversPoseIsChecked(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300)
    public static void givesItsBodiesBack(GameTestHelper helper) {
        int before = com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds.bodyCount(helper.getLevel());
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.RADAR);
        helper.runAfterDelay(SETTLE, () -> {
            int during = com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds.bodyCount(helper.getLevel());
            helper.assertTrue(during >= before + 3, "tractor, trailer and the ground: " + before + " -> " + during);
            vehicle.discard();
            helper.runAfterDelay(2, () -> {
                int after = com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds.bodyCount(helper.getLevel());
                helper.assertTrue(after == during - 3, "bodies during " + during + ", after " + after);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 500)
    public static void anIronBlockPutsItBackOnItsWheels(GameTestHelper helper) {
        PatriotVehicleEntity vehicle = spawn(helper, PatriotKind.LAUNCHER);
        helper.runAfterDelay(SETTLE, () -> {
            var train = vehicle.joltView().train();
            helper.assertTrue(train != null, "needs Jolt");
            train.teleport(vehicle.getX(), vehicle.getY() + 2.0, vehicle.getZ(), vehicle.getYRot(), 0.0, 100.0,
                    0.0, 0.0, 0.0, null, null);
        });
        helper.runAfterDelay(SETTLE + 80, () -> {
            helper.assertTrue(vehicle.isOverturned(), "it should lie on its side: roll " + vehicle.getRoll()
                    + " pitch " + vehicle.getXRot());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK));
            vehicle.interact(player, InteractionHand.MAIN_HAND);
        });
        helper.runAfterDelay(SETTLE + 80 + 80, () -> {
            helper.assertTrue(!vehicle.isOverturned(), "it should be upright: roll " + vehicle.getRoll()
                    + " pitch " + vehicle.getXRot());
            helper.assertTrue(Math.abs(vehicle.getArticulationYaw()) < 15.0f,
                    "the hitch straightened: " + vehicle.getArticulationYaw());
            vehicle.discard();
            helper.succeed();
        });
    }
}
