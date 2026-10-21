package com.synarsis.airtacticalarsenal.client.fx;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Wind, trail, ground colour, glow and smoke settings")
class FxWorldPartsTest {

    @AfterEach
    void clean() {
        AtaLights.clear();
    }

    @Test
    @DisplayName("the wind is the same for the same time and seed, and differs for another seed")
    void windIsDeterministic() {
        double[] a = FxWind.compute(123456L, 42L, 70.0);
        double[] b = FxWind.compute(123456L, 42L, 70.0);
        assertEquals(a[0], b[0], 0.0);
        assertEquals(a[1], b[1], 0.0);
        double[] other = FxWind.compute(123456L, 4242L, 70.0);
        assertTrue(a[0] != other[0] || a[1] != other[1]);
        assertEquals(FxWind.seedOf("minecraft:overworld"), FxWind.seedOf("minecraft:overworld"));
        assertTrue(FxWind.seedOf("minecraft:overworld") != FxWind.seedOf("minecraft:the_nether"));
    }

    @Test
    @DisplayName("the wind blows between 0.01 and 0.04 blocks a tick at the ground, harder with height")
    void windSpeed() {
        double lowest = Double.MAX_VALUE;
        double highest = 0.0;
        for (long t = 0; t < 40000; t += 37) {
            double[] w = FxWind.compute(t, 99L, 0.0);
            double speed = Math.hypot(w[0], w[1]);
            lowest = Math.min(lowest, speed);
            highest = Math.max(highest, speed);
            double[] up = FxWind.compute(t, 99L, 128.0);
            assertEquals(speed * 2.0, Math.hypot(up[0], up[1]), 1.0E-9, "twice as hard at 128 blocks");
        }
        assertTrue(lowest >= FxWind.MIN_SPEED - 1.0E-9, "lowest " + lowest);
        assertTrue(highest <= FxWind.MAX_SPEED + 1.0E-9, "highest " + highest);
        assertTrue(highest - lowest > 0.01, "it should wander");
    }

    @Test
    @DisplayName("the wind turns once round in ten minutes")
    void windTurns() {
        double[] now = FxWind.compute(1000L, 7L, 0.0);
        double[] later = FxWind.compute(1000L + (long) FxWind.TURN_PERIOD_TICKS / 4, 7L, 0.0);
        double a0 = Math.atan2(now[1], now[0]);
        double a1 = Math.atan2(later[1], later[0]);
        double turn = Math.floorMod((long) Math.round(Math.toDegrees(a1 - a0)), 360L);
        assertEquals(90.0, turn, 1.0, "a quarter of the period is a quarter turn");
    }

    @Test
    @DisplayName("at 4 blocks a tick and a 1 block step four puffs are made, evenly along the move")
    void fastTrail() {
        FxTrail trail = new FxTrail();
        Vec3 from = new Vec3(0, 100, 0);
        Vec3 to = new Vec3(0, 104, 0);
        List<Vec3> puffs = trail.advance(from, to, 1.0);
        assertEquals(4, puffs.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(100.0 + (i + 1), puffs.get(i).y, 1.0E-9);
        }
    }

    @Test
    @DisplayName("the step carries over from tick to tick: no gaps, and the same spacing across the joins")
    void trailCarries() {
        FxTrail trail = new FxTrail();
        double y = 0.0;
        double lastPuff = 0.0;
        int count = 0;
        for (int tick = 0; tick < 200; tick++) {
            double speed = 0.4 + (tick % 7) * 0.45;
            List<Vec3> puffs = trail.advance(new Vec3(0, y, 0), new Vec3(0, y + speed, 0), 0.8);
            for (Vec3 p : puffs) {
                if (count > 0) {
                    assertEquals(0.8, p.y - lastPuff, 1.0E-9, "spacing at tick " + tick);
                }
                lastPuff = p.y;
                count++;
            }
            y += speed;
        }
        assertEquals(Math.floor(y / 0.8), count, 1.0, "one puff per step travelled");
    }

    @Test
    @DisplayName("a move shorter than the step makes none until enough has been covered")
    void slowTrail() {
        FxTrail trail = new FxTrail();
        int made = 0;
        double y = 0.0;
        for (int tick = 0; tick < 10; tick++) {
            made += trail.advance(new Vec3(0, y, 0), new Vec3(0, y + 0.3, 0), 1.0).size();
            y += 0.3;
        }
        assertEquals(3, made, "3.0 blocks at one a block");
        assertTrue(trail.advance(new Vec3(1, 1, 1), new Vec3(1, 1, 1), 1.0).isEmpty(), "standing still");
        assertTrue(trail.advance(new Vec3(0, 0, 0), new Vec3(0, 5, 0), 0.0).isEmpty(), "a bad step gives nothing");
    }

    @Test
    @DisplayName("puffs lie on the segment, whichever way it points")
    void trailIsOnTheSegment() {
        FxTrail trail = new FxTrail();
        Vec3 from = new Vec3(10, 20, 30);
        Vec3 to = new Vec3(14, 23, 42);
        for (Vec3 p : trail.advance(from, to, 1.5)) {
            double total = from.distanceTo(p) + p.distanceTo(to);
            assertEquals(from.distanceTo(to), total, 1.0E-9);
        }
    }

    @Test
    @DisplayName("the cloud is beige over sand, grey-brown over earth and grass, grey over stone, white over snow")
    void cloudColours() {
        assertEquals(FxPalette.Ground.SAND, FxPalette.kindOf("sand"));
        assertEquals(FxPalette.Ground.SAND, FxPalette.kindOf("red_sand"));
        assertEquals(FxPalette.Ground.DIRT, FxPalette.kindOf("grass_block"));
        assertEquals(FxPalette.Ground.DIRT, FxPalette.kindOf("dirt"));
        assertEquals(FxPalette.Ground.DIRT, FxPalette.kindOf("podzol"));
        assertEquals(FxPalette.Ground.STONE, FxPalette.kindOf("stone"));
        assertEquals(FxPalette.Ground.STONE, FxPalette.kindOf("cobblestone"));
        assertEquals(FxPalette.Ground.STONE, FxPalette.kindOf("deepslate"));
        assertEquals(FxPalette.Ground.SNOW, FxPalette.kindOf("snow_block"));
        assertEquals(FxPalette.Ground.SNOW, FxPalette.kindOf("snow"));

        float[] sand = FxPalette.cloudColour(FxPalette.Ground.SAND);
        float[] earth = FxPalette.cloudColour(FxPalette.Ground.DIRT);
        float[] stone = FxPalette.cloudColour(FxPalette.Ground.STONE);
        float[] snow = FxPalette.cloudColour(FxPalette.Ground.SNOW);
        assertTrue(sand[0] > sand[2] + 0.2f, "beige is warm");
        assertTrue(earth[0] > earth[2] && earth[0] < 0.6f, "grey-brown is dim and a little warm");
        assertTrue(Math.abs(stone[0] - stone[2]) < 0.05f, "grey has no tint");
        assertTrue(snow[0] > 0.9f && snow[1] > 0.9f && snow[2] > 0.9f, "white");
        assertTrue(snow[0] > stone[0] && stone[0] > earth[0], "and brighter the paler the ground");
    }

    @Test
    @DisplayName("smoke inside the reach of a flash is pulled towards its colour, most at the centre, none outside")
    void glowTint() {
        FxGlow.add(new Vec3(0, 0, 0), 20.0, 1.0f, 0.5f, 0.1f, 1.0, 30);
        float[] grey = {0.5f, 0.5f, 0.5f};
        assertEquals(1.0, FxGlow.tint(0, 0, 0, grey), 1.0E-9);
        assertEquals(1.0f, grey[0], 1.0E-6f);
        assertEquals(0.1f, grey[2], 1.0E-6f);

        float[] near = {0.5f, 0.5f, 0.5f};
        float[] far = {0.5f, 0.5f, 0.5f};
        double nearLit = FxGlow.tint(5, 0, 0, near);
        double farLit = FxGlow.tint(15, 0, 0, far);
        assertTrue(nearLit > farLit && farLit > 0.0);
        assertTrue(near[0] - 0.5f > far[0] - 0.5f, "closer is redder");
        assertEquals(0.5625 * FxGlow.TINT_GAIN, nearLit, 1.0E-9, "(1 - 5/20)^2, one and a half times as hard");
        assertEquals(1.5, FxGlow.TINT_GAIN, 0.0);

        float[] outside = {0.5f, 0.5f, 0.5f};
        assertEquals(0.0, FxGlow.tint(25, 0, 0, outside), 0.0);
        assertEquals(0.5f, outside[0], 0.0f);
    }

    @Test
    @DisplayName("a source fades evenly over its life and is dropped when it is spent or put out")
    void glowFades() {
        AtaLights.Light s = FxGlow.add(new Vec3(0, 0, 0), 10.0, 1f, 1f, 1f, 0.8, 30);
        assertEquals(0.8 * FxGlow.FULL_TINT, s.strength(0.0f), 1.0E-9);
        for (int i = 0; i < 15; i++) {
            AtaLights.tick();
        }
        assertEquals(0.4 * FxGlow.FULL_TINT, s.strength(0.0f), 1.0E-9);
        for (int i = 0; i < 15; i++) {
            AtaLights.tick();
        }
        assertTrue(AtaLights.all().isEmpty(), "spent");

        AtaLights.Light flare = FxGlow.add(new Vec3(0, 0, 0), 10.0, 1f, 1f, 1f, 1.0, 1000);
        flare.moveTo(new Vec3(3, 0, 0));
        assertEquals(3.0, flare.position(1.0f).x, 0.0);
        flare.kill();
        assertEquals(0.0, flare.strength(0.0f), 0.0);
        AtaLights.tick();
        assertTrue(AtaLights.all().isEmpty(), "put out");
    }

    private static SmokeParams sample() {
        return SmokeParams.soft(0.9f, 0.8f, 0.7f).withColours(0.9f, 0.8f, 0.7f, 0.3f, 0.3f, 0.35f).withSize(1.5f, 7.0f)
                .withLife(600).withGrowth(5, 200).withDrift(0.93f, 0.004f, 0.7f).withAlpha(0.8f).withCap(0.02f)
                .withGlow(true).withCollide(false).withPixel(true);
    }

    @Test
    @DisplayName("smoke settings survive the wire and the JSON codec unchanged")
    void smokeRoundTrips() {
        SmokeParams in = sample();
        ByteBuf buf = Unpooled.buffer();
        SmokeParams.STREAM_CODEC.encode(buf, in);
        SmokeParams out = SmokeParams.STREAM_CODEC.decode(buf);
        assertEquals(in, out);
        assertEquals(0, buf.readableBytes(), "nothing left over");

        DataResult<JsonElement> json = SmokeParams.CODEC.codec().encodeStart(JsonOps.INSTANCE, in);
        SmokeParams back = SmokeParams.CODEC.codec().parse(JsonOps.INSTANCE, json.getOrThrow()).getOrThrow();
        assertEquals(in, back);
        assertTrue(json.getOrThrow().toString().contains("\"pixel\":true"));
    }

    @Test
    @DisplayName("each change touches only its own setting")
    void smokeChanges() {
        SmokeParams base = SmokeParams.soft(0.5f, 0.5f, 0.5f);
        assertFalse(base.pixel());
        assertTrue(base.withPixel(true).pixel());
        assertEquals(base.withPixel(true).withPixel(false), base);
        assertEquals(600, base.withLife(600).lifetime());
        assertEquals(base.size1(), base.withLife(600).size1());
        assertEquals(2.0f, base.withSize(2.0f, 9.0f).size0());
        assertEquals(9.0f, base.withSize(2.0f, 9.0f).size1());
        assertEquals(0.92f, base.withDrift(0.92f, 0.005f, 1.0f).drag());
        assertEquals(base.lifetime(), base.withDrift(0.92f, 0.005f, 1.0f).lifetime());
        SmokeParams twice = base.withSize(2.0f, 4.0f).scaled(2.5);
        assertEquals(5.0f, twice.size0(), 1.0E-6f);
        assertEquals(10.0f, twice.size1(), 1.0E-6f);
    }
}
