package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AirTacticalArsenal.MODID)
@PrefixGameTestTemplate(false)
public final class TelPhysicsTests {

    private TelPhysicsTests() {
    }

    private static double floorUnder(GameTestHelper helper, IskanderTelEntity tel) {
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos(
                tel.getBlockX(), tel.getBlockY() + 3, tel.getBlockZ());
        for (int i = 0; i < 20; i++) {
            if (!helper.getLevel().getBlockState(pos).isAir()) {
                return pos.getY() + 1.0;
            }
            pos.move(0, -1, 0);
        }
        return Double.NaN;
    }

    private static IskanderTelEntity spawn(GameTestHelper helper, double height) {
        return spawnAt(helper, 12.0, 2.0 + height, 12.0);
    }

    private static IskanderTelEntity spawnAt(GameTestHelper helper, double x, double y, double z) {
        IskanderTelEntity tel = new IskanderTelEntity(ModEntities.ISKANDER_TEL.get(), helper.getLevel());
        tel.isLegitSpawn = true;
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        tel.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(tel);
        return tel;
    }

    private static void ramp(GameTestHelper helper) {
        for (int x = 6; x <= 18; x++) {
            for (int z = 2; z <= 22; z++) {
                double top = Math.max(1.0, 3.5 - Math.floor((z - 2) / 3.0) * 0.5);
                int whole = (int) Math.floor(top);
                for (int y = 0; y < whole; y++) {
                    helper.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.STONE);
                }
                if (top > whole) {
                    helper.setBlock(x, whole, z, net.minecraft.world.level.block.Blocks.STONE_SLAB);
                }
            }
        }
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, batch = "telPhysics", timeoutTicks = 300)
    public static void parkedStaysOnTheGround(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 0.1);
        helper.runAfterDelay(120, () -> {
            double floor = floorUnder(helper, tel);
            helper.assertTrue(tel.usesJolt(), "the launcher should be driven by Jolt");
            helper.assertTrue(Math.abs(tel.getY() - floor) < 0.3, "height off the floor: " + (tel.getY() - floor));
            helper.assertTrue(Math.abs(tel.getXRot()) < 3.0f && Math.abs(tel.roll) < 3.0f, "tilted");
            int before = PhysicsWorlds.bodyCount(helper.getLevel());
            helper.assertTrue(before >= 2, "bodies: " + before);
            tel.discard();
            helper.runAfterDelay(2, () -> {
                int after = PhysicsWorlds.bodyCount(helper.getLevel());
                helper.assertTrue(after == before - 2, "bodies before " + before + ", after " + after);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, batch = "telPhysics", timeoutTicks = 300)
    public static void dropsAndLands(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 5.0);
        helper.runAfterDelay(160, () -> {
            double floor = floorUnder(helper, tel);
            helper.assertTrue(Math.abs(tel.getY() - floor) < 0.3, "height off the floor: " + (tel.getY() - floor));
            helper.assertTrue(tel.getDeltaMovement().lengthSqr() < 0.01, "still moving");
            tel.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 400,
            batch = "physicsFixes")
    public static void rollsDownASlope(GameTestHelper helper) {
        ramp(helper);
        IskanderTelEntity tel = spawnAt(helper, 12.0, 3.8, 9.0);
        double[] start = new double[1];
        helper.runAfterDelay(5, () -> start[0] = tel.getZ());
        helper.runAfterDelay(20, () -> {
            double moved = tel.getZ() - start[0];
            helper.assertTrue(tel.getSpeedKmh() > 0.1f,
                    "speed at the end of the tick: " + tel.getSpeedKmh() + " after " + moved + " blocks");
            helper.assertTrue(moved > 0.2, "it only moved " + moved + " blocks downhill");
            tel.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 200,
            batch = "physicsFixes")
    public static void riderStaysOnTheSeat(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 3.0);
        net.minecraft.world.entity.decoration.ArmorStand first = new net.minecraft.world.entity.decoration.ArmorStand(
                net.minecraft.world.entity.EntityType.ARMOR_STAND, helper.getLevel());
        net.minecraft.world.entity.decoration.ArmorStand second = new net.minecraft.world.entity.decoration.ArmorStand(
                net.minecraft.world.entity.EntityType.ARMOR_STAND, helper.getLevel());
        helper.runAfterDelay(3, () -> {
            first.moveTo(tel.getX(), tel.getY() + 2.0, tel.getZ());
            helper.getLevel().addFreshEntity(first);
            helper.assertTrue(first.startRiding(tel, true), "the first rider could not board");
            second.moveTo(tel.getX(), tel.getY() + 2.0, tel.getZ());
            helper.getLevel().addFreshEntity(second);
            helper.assertTrue(second.startRiding(tel, true), "the second rider could not board");
        });
        helper.runAfterDelay(18, () -> {
            Vec3 seat = tel.getPassengerRidingPosition(second);
            double gap = second.position().distanceTo(seat);
            helper.assertTrue(tel.getPassengers().indexOf(second) == 1, "the rider is not on seat 1");
            helper.assertTrue(gap < 0.05, "the rider is " + gap + " blocks behind the seat");
            second.discard();
            first.discard();
            tel.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, batch = "telPhysics", timeoutTicks = 300)
    public static void explosionPushesIt(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 0.1);
        Vec3 start = helper.absoluteVec(new Vec3(12.0, 2.0, 12.0));
        helper.runAfterDelay(60, () -> {
            Vec3 blast = helper.absoluteVec(new Vec3(12.0, 2.5, 6.0));
            helper.getLevel().explode(null, blast.x, blast.y, blast.z, 12.0f, Level.ExplosionInteraction.NONE);
        });
        helper.runAfterDelay(140, () -> {
            double moved = tel.position().distanceTo(start);
            helper.assertTrue(moved > 0.5, "moved only " + moved);
            tel.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, batch = "telPhysics", timeoutTicks = 400)
    public static void boardingHandsControlOver(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 0.1);
        net.minecraft.world.entity.player.Player driver = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        driver.setPos(tel.getX(), tel.getY() + 2.0, tel.getZ());
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(driver.startRiding(tel), "could not board");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(tel.joltView() != null && tel.joltView().role() == com.synarsis.airtacticalarsenal.entity.tel.TelJolt.Role.FOLLOW,
                    "role while driven: " + (tel.joltView() == null ? "none" : tel.joltView().role()));
            driver.stopRiding();
        });
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(tel.joltView() != null && tel.joltView().role() == com.synarsis.airtacticalarsenal.entity.tel.TelJolt.Role.SIMULATE,
                    "role after leaving: " + (tel.joltView() == null ? "none" : tel.joltView().role()));
            double floor = floorUnder(helper, tel);
            helper.assertTrue(Math.abs(tel.getY() - floor) < 0.3, "height off the floor: " + (tel.getY() - floor));
            tel.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, batch = "telPhysics", timeoutTicks = 400)
    public static void groundFollowsBlockChanges(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 0.1);
        helper.runAfterDelay(80, () -> {
            double floor = floorUnder(helper, tel);
            helper.assertTrue(Math.abs(tel.getY() - floor) < 0.3, "not on the floor yet: " + (tel.getY() - floor));
            int y = (int) Math.round(floor) - 1;
            Vec3 centre = tel.position();
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    helper.getLevel().setBlock(new net.minecraft.core.BlockPos((int) Math.floor(centre.x) + dx, y,
                            (int) Math.floor(centre.z) + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(tel.getY() < tel.level().getMinBuildHeight() + 400 && tel.getY() < floorOf(tel) - 0.5,
                    "it should have dropped through the hole, y=" + tel.getY());
            tel.discard();
            helper.succeed();
        });
    }

    private static double floorOf(IskanderTelEntity tel) {
        return Math.round(tel.getY() + 3.0);
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, timeoutTicks = 300, batch = "legacy")
    public static void builtInPhysicsStillWorks(GameTestHelper helper) {
        com.synarsis.airtacticalarsenal.config.VehiclePhysicsMode before = com.synarsis.airtacticalarsenal.config.ShahedConfig.getVehiclePhysics();
        com.synarsis.airtacticalarsenal.config.ShahedConfig.VEHICLE_PHYSICS.set(com.synarsis.airtacticalarsenal.config.VehiclePhysicsMode.LEGACY);
        IskanderTelEntity tel = spawn(helper, 0.1);
        helper.runAfterDelay(100, () -> {
            double floor = floorUnder(helper, tel);
            boolean jolt = tel.usesJolt();
            com.synarsis.airtacticalarsenal.config.ShahedConfig.VEHICLE_PHYSICS.set(before);
            helper.assertTrue(!jolt, "Jolt must stay off in LEGACY");
            helper.assertTrue(Math.abs(tel.getY() - floor) < 0.4, "height off the floor: " + (tel.getY() - floor));
            tel.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "flat", templateNamespace = AirTacticalArsenal.MODID, batch = "telPhysics", timeoutTicks = 300)
    public static void harmlessBlocksDoNotRebuildTheGround(GameTestHelper helper) {
        IskanderTelEntity tel = spawn(helper, 0.1);
        int[] before = new int[2];
        helper.runAfterDelay(70, () -> {
            double floor = floorUnder(helper, tel);
            before[0] = tel.joltView().ground().rebuilds();
            before[1] = tel.joltView().ground().fullRebuilds();
            int y = (int) Math.round(floor);
            int x = tel.getBlockX() + 4;
            int z = tel.getBlockZ();
            helper.getLevel().setBlock(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
            helper.getLevel().setBlock(new net.minecraft.core.BlockPos(x, y + 1, z + 3), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState(), 3);
            helper.getLevel().setBlock(new net.minecraft.core.BlockPos(x, y + 30, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new net.minecraft.core.BlockPos(x, y, z + 6), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(tel.joltView().ground().rebuilds() == before[0],
                    "grass, leaves, air and a block far above rebuilt the ground: " + before[0] + " -> " + tel.joltView().ground().rebuilds());
            double floor = floorUnder(helper, tel);
            helper.getLevel().setBlock(new net.minecraft.core.BlockPos(tel.getBlockX() + 5, (int) Math.round(floor), tel.getBlockZ() + 2),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(tel.joltView().ground().rebuilds() == before[0] + 1,
                    "a stone block should rebuild once: " + before[0] + " -> " + tel.joltView().ground().rebuilds());
            helper.assertTrue(tel.joltView().ground().fullRebuilds() == before[1], "full rebuilds " + tel.joltView().ground().fullRebuilds());
            tel.discard();
            helper.succeed();
        });
    }
}
