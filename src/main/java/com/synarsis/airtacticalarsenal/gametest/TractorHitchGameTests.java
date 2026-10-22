package com.synarsis.airtacticalarsenal.gametest;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.patriot.M983Coupling;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity.CouplingState;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotLauncherEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotRadarEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotTiming;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.TractorJolt;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AirTacticalArsenal.MODID)
@PrefixGameTestTemplate(false)
public final class TractorHitchGameTests {

    private static final String TEMPLATE = "flat_wide";
    private static final String BATCH = "tractorHitch";
    private static final int SETTLE = 60;
    private static final double TRAILER_X = 20.5;
    private static final double TRAILER_Z = 8.0;
    private static final double PIN_OFFSET = 5.875;
    private static final double FLOOR = 2.1;

    private TractorHitchGameTests() {
    }

    private static PatriotVehicleEntity trailer(GameTestHelper helper, PatriotKind kind, double x, double z) {
        PatriotVehicleEntity trailer = kind.isLauncher()
                ? new PatriotLauncherEntity(ModEntities.PATRIOT_LAUNCHER.get(), helper.getLevel())
                : new PatriotRadarEntity(ModEntities.PATRIOT_RADAR.get(), helper.getLevel());
        trailer.isLegitSpawn = true;
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        trailer.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        trailer.placeOnSupports();
        helper.getLevel().addFreshEntity(trailer);
        return trailer;
    }

    private static M983TractorEntity tractor(GameTestHelper helper, double x, double z, float yaw) {
        M983TractorEntity tractor = ModEntities.M983_TRACTOR.get().create(helper.getLevel());
        tractor.isLegitSpawn = true;
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        tractor.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        helper.getLevel().addFreshEntity(tractor);
        return tractor;
    }

    private static double remaining(M983TractorEntity tractor, PatriotVehicleEntity trailer) {
        return M983Coupling.saddleWorld(tractor).z - M983Coupling.kingpinWorld(trailer).z;
    }

    private static void backUp(M983TractorEntity tractor, PatriotVehicleEntity trailer) {
        double remaining = remaining(tractor, trailer);
        if (remaining > 1.5) {
            tractor.setTestThrottle(-0.4);
        } else if (remaining > 0.45) {
            tractor.setTestThrottle(-0.22);
        } else {
            tractor.setTestThrottle(0.0);
        }
    }

    private static void backUpUntilCoupled(GameTestHelper helper, M983TractorEntity tractor,
            PatriotVehicleEntity trailer, int ticks, Runnable then) {
        int[] left = {ticks};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (tractor.couplingState() == CouplingState.COUPLED) {
                tractor.setTestThrottle(0.0);
                then.run();
                return;
            }
            if (left[0]-- <= 0) {
                helper.fail("the tractor did not couple: state " + tractor.couplingState() + " remaining "
                        + remaining(tractor, trailer) + " lateral "
                        + (M983Coupling.kingpinWorld(trailer).x - M983Coupling.saddleWorld(tractor).x) + " speed "
                        + tractor.getDeltaMovement().length() * 20.0);
                return;
            }
            backUp(tractor, trailer);
            helper.runAfterDelay(1, () -> step[0].run());
        };
        step[0].run();
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 700)
    public static void tractorBacksUnderAndCouples(GameTestHelper helper) {
        PatriotVehicleEntity trailer = trailer(helper, PatriotKind.LAUNCHER, TRAILER_X, TRAILER_Z);
        M983TractorEntity tractor = tractor(helper, TRAILER_X, TRAILER_Z + PIN_OFFSET + 6.0, 0.0f);
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(trailer.canBeCoupledNow(), "the trailer should be free: " + trailer.debugState());
            helper.assertTrue(tractor.couplingState() == CouplingState.FREE, "starts free");
            backUpUntilCoupled(helper, tractor, trailer, 500, () -> {
                helper.assertTrue(trailer.isHitched(), "the trailer must know it is hitched");
                double gap = M983Coupling.saddleWorld(tractor).distanceTo(M983Coupling.kingpinWorld(trailer));
                helper.assertTrue(gap < 0.05, "the kingpin sits on the saddle, gap " + gap);
                helper.assertTrue(tractor.coupledTrailer() == trailer, "the tractor must hold this trailer");
                helper.assertTrue(!trailer.usesJolt(), "a coupled trailer has no body of its own");
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(tractor.joltView() != null && tractor.joltView().hitch() != null,
                            "the train needs its hitch body");
                    helper.assertTrue(tractor.joltView().role() == com.synarsis.airtacticalarsenal.entity.patriot
                            .TractorJolt.Role.HOLD, "supports out: the train is held, got "
                            + tractor.joltView().role());
                    double moved = M983Coupling.saddleWorld(tractor).distanceTo(M983Coupling.kingpinWorld(trailer));
                    helper.assertTrue(moved < 0.05, "the held train drifted by " + moved);
                    trailer.discard();
                    tractor.discard();
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 800)
    public static void uncouplingNeedsTheSupports(GameTestHelper helper) {
        PatriotVehicleEntity trailer = trailer(helper, PatriotKind.RADAR, TRAILER_X, TRAILER_Z);
        M983TractorEntity tractor = tractor(helper, TRAILER_X, TRAILER_Z + PIN_OFFSET + 5.0, 0.0f);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(trailer.getX() + 3.0, trailer.getY(), trailer.getZ());
        helper.runAfterDelay(SETTLE, () -> backUpUntilCoupled(helper, tractor, trailer, 500, () -> {
            helper.assertTrue(tractor.uncoupleVerdict() == M983Coupling.Uncouple.OK,
                    "supports out and standing: " + tractor.uncoupleVerdict());
            trailer.handleCommand(player, (byte) Command.SUPPORTS_IN.ordinal());
            helper.runAfterDelay(PatriotTiming.SUPPORTS_TICKS + 20, () -> {
                helper.assertTrue((trailer.currentMask() & PatriotSequence.SUPPORTS_BIT) == 0,
                        "the supports should be in: " + trailer.debugState());
                helper.assertTrue(tractor.uncoupleVerdict() == M983Coupling.Uncouple.SUPPORTS,
                        "without supports: " + tractor.uncoupleVerdict());
                tractor.requestUncouple(player);
                helper.assertTrue(tractor.couplingState() == CouplingState.COUPLED,
                        "the refusal keeps it coupled, got " + tractor.couplingState());
                trailer.handleCommand(player, (byte) Command.SUPPORTS_OUT.ordinal());
                helper.runAfterDelay(PatriotTiming.SUPPORTS_TICKS + 20, () -> {
                    helper.assertTrue(tractor.uncoupleVerdict() == M983Coupling.Uncouple.OK,
                            "supports out again: " + tractor.uncoupleVerdict());
                    trailer.discard();
                    tractor.discard();
                    helper.succeed();
                });
            });
        }));
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 900)
    public static void uncoupleAndDriveOff(GameTestHelper helper) {
        PatriotVehicleEntity trailer = trailer(helper, PatriotKind.LAUNCHER, TRAILER_X, TRAILER_Z);
        M983TractorEntity tractor = tractor(helper, TRAILER_X, TRAILER_Z + PIN_OFFSET + 5.0, 0.0f);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(trailer.getX() + 3.0, trailer.getY(), trailer.getZ());
        helper.runAfterDelay(SETTLE, () -> backUpUntilCoupled(helper, tractor, trailer, 500, () -> {
            Vec3 parked = trailer.position();
            helper.runAfterDelay(20, () -> {
                tractor.requestUncouple(player);
                helper.assertTrue(tractor.couplingState() == CouplingState.UNLOCKING,
                        "uncoupling should start, got " + tractor.couplingState());
                helper.runAfterDelay(40, () -> {
                    helper.assertTrue(tractor.couplingState() == CouplingState.FREE,
                            "free again, got " + tractor.couplingState());
                    helper.assertTrue(!trailer.isHitched(), "the trailer is free");
                    helper.assertTrue(trailer.position().distanceTo(parked) < 0.05, "the trailer stays: "
                            + trailer.position().distanceTo(parked));
                    tractor.setTestThrottle(0.5);
                    helper.runAfterDelay(100, () -> {
                        tractor.setTestThrottle(0.0);
                        double away = tractor.position().distanceTo(parked);
                        helper.assertTrue(away > 8.0, "the tractor drove off only " + away);
                        helper.assertTrue(trailer.position().distanceTo(parked) < 0.05, "the trailer still stands");
                        helper.assertTrue(trailer.usesJolt(), "the trailer has its static body again");
                        helper.assertTrue(trailer.canBeCoupledNow(), "and can be coupled again");
                        trailer.discard();
                        tractor.discard();
                        helper.succeed();
                    });
                });
            });
        }));
    }

    private static CompoundTag stageSevenSave(GameTestHelper helper, boolean tractorOut, float bend) {
        PatriotVehicleEntity old = trailer(helper, PatriotKind.LAUNCHER, TRAILER_X, TRAILER_Z);
        old.setYRot(30.0f);
        CompoundTag tag = new CompoundTag();
        old.saveWithoutId(tag);
        old.discard();
        CompoundTag cycle = tag.getCompound("Cycle");
        cycle.putBoolean("LatchOpen", tractorOut);
        cycle.putBoolean("TractorOut", tractorOut);
        cycle.putInt("Op", 12);
        cycle.putIntArray("Queue", new int[] {9, 10, 0});
        tag.putFloat("ArticulationYaw", bend);
        tag.remove("Hitched");
        tag.remove("CoupledTractor");
        tag.remove("UUID");
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(30.0f));
        rotation.add(FloatTag.valueOf(0.0f));
        tag.put("Rotation", rotation);
        return tag;
    }

    private static List<M983TractorEntity> tractorsAround(GameTestHelper helper, Vec3 centre) {
        return helper.getLevel().getEntitiesOfClass(M983TractorEntity.class,
                new net.minecraft.world.phys.AABB(centre, centre).inflate(30.0));
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 300)
    public static void aStageSevenSaveGetsItsTractorBack(GameTestHelper helper) {
        CompoundTag tag = stageSevenSave(helper, false, 12.0f);
        PatriotVehicleEntity trailer = ModEntities.PATRIOT_LAUNCHER.get().create(helper.getLevel());
        trailer.load(tag);
        helper.assertTrue(trailer.pendingMigration() != null, "the old format must be noticed");
        helper.assertTrue(!trailer.isHitched(), "nothing is coupled before the tractor exists");
        helper.getLevel().addFreshEntity(trailer);
        helper.runAfterDelay(10, () -> {
            List<M983TractorEntity> found = tractorsAround(helper, trailer.position());
            helper.assertTrue(found.size() == 1, "one tractor is made, got " + found.size());
            M983TractorEntity tractor = found.get(0);
            helper.assertTrue(tractor.couplingState() == CouplingState.COUPLED, "state " + tractor.couplingState());
            helper.assertTrue(tractor.coupledTrailer() == trailer && trailer.isHitched(), "they hold each other");
            double gap = M983Coupling.saddleWorld(tractor).distanceTo(M983Coupling.kingpinWorld(trailer));
            helper.assertTrue(gap < 0.05, "the kingpin sits on the saddle, gap " + gap);
            helper.assertTrue(Math.abs(M983Coupling.bend(tractor.getYRot(), trailer.getYRot()) - 12.0) < 0.01,
                    "the bend is kept: " + M983Coupling.bend(tractor.getYRot(), trailer.getYRot()));
            helper.assertTrue(Math.abs(tractor.getYRot() - 30.0f) < 0.01f, "the tractor takes the heading of the hitch");
            helper.assertTrue(trailer.pendingMigration() == null, "the migration is done once");
            helper.assertTrue(trailer.supportsOutNow(), "the supports of the old save are still out");
            helper.assertTrue(trailer.currentOp() < 0, "the removed operations of the old queue are dropped");
            CompoundTag again = new CompoundTag();
            trailer.saveWithoutId(again);
            helper.assertTrue(!again.getCompound("Cycle").contains("TractorOut"), "it saves in the new format");
            tractor.discard();
            trailer.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 300)
    public static void aDetachedStageSevenSaveLeavesTheTractorFree(GameTestHelper helper) {
        CompoundTag tag = stageSevenSave(helper, true, 0.0f);
        PatriotVehicleEntity trailer = ModEntities.PATRIOT_LAUNCHER.get().create(helper.getLevel());
        trailer.load(tag);
        helper.getLevel().addFreshEntity(trailer);
        helper.runAfterDelay(10, () -> {
            List<M983TractorEntity> found = tractorsAround(helper, trailer.position());
            helper.assertTrue(found.size() == 1, "one tractor is made, got " + found.size());
            M983TractorEntity tractor = found.get(0);
            helper.assertTrue(tractor.couplingState() == CouplingState.FREE, "state " + tractor.couplingState());
            helper.assertTrue(!trailer.isHitched(), "the trailer stays free");
            helper.assertTrue(trailer.supportsOutNow(), "a detached trailer stands on its supports");
            double ahead = M983Coupling.saddleWorld(tractor).distanceTo(M983Coupling.kingpinWorld(trailer));
            helper.assertTrue(Math.abs(ahead - 95.0 / 16.0) < 0.05, "the tractor stands 95 px ahead, got " + ahead);
            tractor.discard();
            trailer.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 300)
    public static void aNewSaveIsNotMigratedAgain(GameTestHelper helper) {
        PatriotVehicleEntity trailer = trailer(helper, PatriotKind.RADAR, TRAILER_X, TRAILER_Z);
        CompoundTag tag = new CompoundTag();
        trailer.saveWithoutId(tag);
        trailer.discard();
        PatriotVehicleEntity loaded = ModEntities.PATRIOT_RADAR.get().create(helper.getLevel());
        loaded.load(tag);
        helper.assertTrue(loaded.pendingMigration() == null, "a stage eight save needs no tractor");
        helper.getLevel().addFreshEntity(loaded);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(tractorsAround(helper, loaded.position()).isEmpty(), "no tractor appears from nothing");
            loaded.discard();
            helper.succeed();
        });
    }

    private static void whenTrue(GameTestHelper helper, java.util.function.BooleanSupplier condition, int ticks,
            String what, Runnable then) {
        int[] left = {ticks};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (condition.getAsBoolean()) {
                then.run();
                return;
            }
            if (left[0]-- <= 0) {
                helper.fail("timed out waiting for: " + what);
                return;
            }
            helper.runAfterDelay(1, () -> step[0].run());
        };
        step[0].run();
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 2200)
    public static void coupleDriveAndUncoupleTheWholeWay(GameTestHelper helper) {
        PatriotVehicleEntity trailer = trailer(helper, PatriotKind.LAUNCHER, TRAILER_X, 7.0);
        M983TractorEntity tractor = tractor(helper, TRAILER_X, 7.0 + PIN_OFFSET + 5.0, 0.0f);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(trailer.getX() + 3.0, trailer.getY(), trailer.getZ());
        helper.runAfterDelay(SETTLE, () -> backUpUntilCoupled(helper, tractor, trailer, 500, () -> {
            trailer.handleCommand(player, (byte) Command.SUPPORTS_IN.ordinal());
            whenTrue(helper, () -> trailer.canBeTowedNow() && !trailer.operationBusy(),
                    PatriotTiming.SUPPORTS_TICKS + 60, "the supports to come in", () -> {
                        helper.runAfterDelay(10, () -> {
                            helper.assertTrue(tractor.joltView().role() == TractorJolt.Role.SIMULATE,
                                    "free to tow: the train is simulated, got " + tractor.joltView().role());
                            helper.assertTrue(tractor.joltView().hitchLinked(), "the hitch has its joint");
                            Vec3 start = tractor.position();
                            double[] travelled = {0.0};
                            Vec3[] last = {start};
                            double[] worstBend = {0.0};
                            double[] fastest = {0.0};
                            int[] ticks = {0};
                            Runnable[] drive = new Runnable[1];
                            drive[0] = () -> {
                                Vec3 now = tractor.position();
                                travelled[0] += now.distanceTo(last[0]);
                                last[0] = now;
                                worstBend[0] = Math.max(worstBend[0],
                                        Math.abs(M983Coupling.bend(tractor.getYRot(), trailer.getYRot())));
                                fastest[0] = Math.max(fastest[0], tractor.getDeltaMovement().length() * 20.0 * 3.6);
                                if (travelled[0] >= 8.0 || ticks[0]++ > 600) {
                                    tractor.setTestThrottle(0.0);
                                    tractor.setTestSteer(0.0);
                                    helper.assertTrue(travelled[0] >= 8.0, "the train drove only " + travelled[0]);
                                    helper.assertTrue(worstBend[0] > 0.5, "the hitch bent with the turn: " + worstBend[0]);
                                    helper.assertTrue(fastest[0] < 61.0, "60 km/h limit with the trailer: " + fastest[0]);
                                    helper.assertTrue(tractor.couplingState() == CouplingState.COUPLED, "still coupled");
                                    helper.assertTrue(M983Coupling.saddleWorld(tractor).distanceTo(
                                            M983Coupling.kingpinWorld(trailer)) < 0.35, "the kingpin stayed on the saddle");
                                    afterTheDrive(helper, tractor, trailer, player);
                                    return;
                                }
                                tractor.setTestThrottle(0.7);
                                tractor.setTestSteer(travelled[0] > 3.0 && travelled[0] < 6.0 ? 0.5 : 0.0);
                                helper.runAfterDelay(1, () -> drive[0].run());
                            };
                            drive[0].run();
                        });
                    });
        }));
    }

    private static void afterTheDrive(GameTestHelper helper, M983TractorEntity tractor, PatriotVehicleEntity trailer,
            Player player) {
        int[] still = {0};
        whenTrue(helper, () -> {
            boolean slow = tractor.getDeltaMovement().horizontalDistance() * 20.0 < 0.05
                    && trailer.getDeltaMovement().horizontalDistance() * 20.0 < 0.05 && trailer.isStanding();
            still[0] = slow ? still[0] + 1 : 0;
            return still[0] >= 10;
        }, 300, "the train to stop",
                () -> {
                    player.setPos(trailer.getX() + 3.0, trailer.getY(), trailer.getZ());
                    trailer.handleCommand(player, (byte) Command.SUPPORTS_OUT.ordinal());
                    helper.assertTrue(trailer.operationBusy() || trailer.supportsOutNow(),
                            "the supports command was refused: " + trailer.denial(Command.SUPPORTS_OUT).reason()
                                    + " standing " + trailer.isStanding() + " " + trailer.debugState());
                    whenTrue(helper, () -> trailer.supportsOutNow() && !trailer.operationBusy(),
                            PatriotTiming.SUPPORTS_TICKS + 60, "the supports to go out", () -> {
                                Vec3 parked = trailer.position();
                                helper.assertTrue(tractor.uncoupleVerdict() == M983Coupling.Uncouple.OK,
                                        "ready to uncouple: " + tractor.uncoupleVerdict());
                                tractor.requestUncouple(player);
                                whenTrue(helper, () -> tractor.couplingState() == CouplingState.FREE, 60, "the lock to open",
                                        () -> {
                                            helper.assertTrue(trailer.position().distanceTo(parked) < 0.1,
                                                    "the trailer stays where it was left");
                                            Vec3 from = tractor.position();
                                            tractor.setTestThrottle(0.5);
                                            whenTrue(helper, () -> tractor.position().distanceTo(from) >= 8.0, 300,
                                                    "the tractor to drive off", () -> {
                                                        tractor.setTestThrottle(0.0);
                                                        helper.assertTrue(trailer.usesJolt()
                                                                && trailer.joltView().body().mode() == JoltVehicle.Mode.STATIC,
                                                                "the trailer is a static body again");
                                                        helper.assertTrue(trailer.position().distanceTo(parked) < 0.1,
                                                                "and has not moved");
                                                        helper.assertTrue(tractor.joltView().hitch() == null,
                                                                "the tractor has let go of the trailer body");
                                                        tractor.discard();
                                                        trailer.discard();
                                                        helper.succeed();
                                                    });
                                        });
                            });
                });
    }

    @GameTest(template = TEMPLATE, templateNamespace = AirTacticalArsenal.MODID, batch = BATCH, timeoutTicks = 300)
    public static void freeTrailerPanelByRightClick(GameTestHelper helper) {
        PatriotVehicleEntity trailer = trailer(helper, PatriotKind.RADAR, TRAILER_X, TRAILER_Z);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(TRAILER_X + 4.0, FLOOR, TRAILER_Z));
        Vec3 far = helper.absoluteVec(new Vec3(TRAILER_X + 30.0, FLOOR, TRAILER_Z));
        helper.runAfterDelay(20, () -> {
            player.setPos(near.x, near.y, near.z);
            InteractionResult result = trailer.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "a right click opens the panel: " + result);
            helper.assertTrue(trailer.inPanelRange(player), "6 blocks is enough");
            player.setPos(far.x, far.y, far.z);
            helper.assertTrue(!trailer.inPanelRange(player), "30 blocks is too far");
            trailer.handleCommand(player, (byte) Command.LIFT_UP.ordinal());
            helper.assertTrue(trailer.currentOp() < 0, "a command from far away does nothing");
            trailer.discard();
            helper.succeed();
        });
    }
}
