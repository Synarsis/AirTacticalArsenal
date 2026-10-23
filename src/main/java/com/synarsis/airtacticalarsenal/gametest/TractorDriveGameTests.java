package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AirTacticalArsenal.MODID)
@PrefixGameTestTemplate(false)
public final class TractorDriveGameTests {

    private static final String TEMPLATE = "flat_wide";
    private static final String BATCH = "tractorDrive";
    private static final double FLOOR = 2.1;
    private static final double X = 24.5;

    private TractorDriveGameTests() {
    }

    private static M983TractorEntity tractor(GameTestHelper helper, double z) {
        M983TractorEntity tractor = ModEntities.M983_TRACTOR.get().create(helper.getLevel());
        tractor.isLegitSpawn = true;
        Vec3 at = helper.absoluteVec(new Vec3(X, FLOOR, z));
        tractor.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(tractor);
        return tractor;
    }

    private static void fill(GameTestHelper helper, int fromZ, int toZ, int height) {
        for (int x = 12; x <= 36; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                for (int y = 2; y < 2 + height; y++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 500)
    public static void climbsAStepOfOneBlock(GameTestHelper helper) {
        fill(helper, 28, 47, 1);
        M983TractorEntity tractor = tractor(helper, 4.0);
        helper.runAfterDelay(60, () -> tractor.setTestThrottle(1.0));
        helper.runAfterDelay(400, () -> {
            double z = tractor.getZ() - helper.absoluteVec(Vec3.ZERO).z;
            helper.assertTrue(z > 34.0, "the tractor stopped at z=" + z);
            tractor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 600)
    public static void climbsASlopeOfOneToThree(GameTestHelper helper) {
        for (int step = 0; step < 4; step++) {
            fill(helper, 24 + step * 3, 26 + step * 3, step + 1);
        }
        fill(helper, 36, 47, 4);
        M983TractorEntity tractor = tractor(helper, 2.0);
        helper.runAfterDelay(60, () -> tractor.setTestThrottle(1.0));
        helper.runAfterDelay(500, () -> {
            double z = tractor.getZ() - helper.absoluteVec(Vec3.ZERO).z;
            helper.assertTrue(z > 34.0, "the tractor stopped at z=" + z);
            tractor.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 400)
    public static void backsAwayFromAWall(GameTestHelper helper) {
        fill(helper, 17, 47, 3);
        M983TractorEntity tractor = tractor(helper, 6.0);
        double[] start = new double[1];
        helper.runAfterDelay(60, () -> {
            start[0] = tractor.getZ();
            tractor.setTestThrottle(-1.0);
        });
        helper.runAfterDelay(260, () -> {
            double moved = tractor.getZ() - start[0];
            helper.assertTrue(moved < -3.0, "the tractor moved only " + moved);
            tractor.discard();
            helper.succeed();
        });
    }

    private static ServerPlayer seated(GameTestHelper helper, M983TractorEntity tractor, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
            }
        };
        player.setPos(tractor.getX(), tractor.getY(), tractor.getZ());
        helper.getLevel().addNewPlayer(player);
        helper.assertTrue(player.startRiding(tractor, true), name + " could not board");
        return player;
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 200)
    public static void crewSwitchTheHeadlightsFromTheirSeats(GameTestHelper helper) {
        M983TractorEntity tractor = tractor(helper, 10.0);
        helper.runAfterDelay(20, () -> {
            ServerPlayer driver = seated(helper, tractor, "tractor-driver");
            ServerPlayer passenger = seated(helper, tractor, "tractor-passenger");
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(tractor.getControllingPassenger() == driver, "the first to board drives");
                helper.assertTrue(driver.distanceToSqr(tractor) > M983TractorEntity.PANEL_RANGE
                        * M983TractorEntity.PANEL_RANGE, "the driver sits farther from the origin than the panel range");
                helper.assertTrue(tractor.isCrewSeat(driver) && tractor.isCrewSeat(passenger), "both are crew");
                helper.assertTrue(tractor.headlightMode() == 0, "starts dark");
                tractor.handleCommand(driver, M983TractorEntity.CMD_HEADLIGHTS);
                helper.assertTrue(tractor.headlightMode() == 1, "dipped beam, got " + tractor.headlightMode());
                tractor.handleCommand(passenger, M983TractorEntity.CMD_HEADLIGHTS);
                helper.assertTrue(tractor.headlightMode() == 2, "main beam, got " + tractor.headlightMode());
                tractor.handleCommand(driver, M983TractorEntity.CMD_HEADLIGHTS);
                helper.assertTrue(tractor.headlightMode() == 0, "dark again, got " + tractor.headlightMode());
                passenger.stopRiding();
                tractor.handleCommand(passenger, M983TractorEntity.CMD_HEADLIGHTS);
                helper.assertTrue(tractor.headlightMode() == 0, "someone who is not aboard cannot switch them");
                driver.stopRiding();
                driver.discard();
                passenger.discard();
                tractor.discard();
                helper.succeed();
            });
        });
    }
}
