package com.synarsis.airtacticalarsenal.combat.damage;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.combat.hitbox.PartBox;
import com.synarsis.airtacticalarsenal.combat.hitbox.ShahedHitboxes;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Per-section durability")
class PartHealthTest {

    private static PartHealth full() {
        return new PartHealth(ShahedHitboxes.TEMPLATE, 100.0f);
    }

    @Test
    @DisplayName("the airframe starts at its full rating, shared out by the box table")
    void capacityMatchesTheTable() {
        PartHealth health = full();
        assertEquals(100.0f, health.totalCapacity(), 0.01f);
        assertEquals(100.0f, health.total(), 0.01f);
        assertEquals(18.0f, health.capacity(HitPart.WARHEAD), 0.01f);
    }

    @Test
    @DisplayName("aimed damage only drains the section it landed on")
    void aimedDamageIsLocal() {
        PartHealth health = full();
        float engineBefore = health.remaining(HitPart.ENGINE);
        float wingBefore = health.remaining(HitPart.WING_LEFT);

        health.hurt(HitPart.ENGINE, 3.0f);

        assertEquals(engineBefore - 3.0f, health.remaining(HitPart.ENGINE), 0.01f);
        assertEquals(wingBefore, health.remaining(HitPart.WING_LEFT), 0.01f,
                "damage to the engine must not drain the wing");
        assertEquals(97.0f, health.total(), 0.01f);
    }

    @Test
    @DisplayName("a section reports the blow that knocked it out, once")
    void knockOutIsReportedOnce() {
        PartHealth health = full();
        float capacity = health.capacity(HitPart.PROP);

        assertFalse(health.hurt(HitPart.PROP, capacity - 0.5f), "not out yet");
        assertTrue(health.hurt(HitPart.PROP, 5.0f), "this is the blow that finishes it");
        assertTrue(health.isKnockedOut(HitPart.PROP));
        assertFalse(health.hurt(HitPart.PROP, 5.0f),
                "already out: further hits must not report a second knock-out");
        assertTrue(health.remaining(HitPart.PROP) >= 0.0f, "durability must not go negative");
    }

    @Test
    @DisplayName("undirected damage is spread, not dropped")
    void spreadDamageHitsEverything() {
        PartHealth health = full();
        health.hurtEverywhere(20.0f);

        assertEquals(80.0f, health.total(), 0.05f);
        for (HitPart part : HitPart.values()) {
            assertTrue(health.remaining(part) < health.capacity(part),
                    part.id() + " took none of a blast that should have reached the whole airframe");
        }
    }

    @Test
    @DisplayName("a blast cannot drive a section below zero")
    void spreadDamageStopsAtZero() {
        PartHealth health = full();
        health.hurtEverywhere(1000.0f);
        assertEquals(0.0f, health.total(), 0.01f);
        for (HitPart part : HitPart.values()) {
            assertTrue(health.remaining(part) >= 0.0f);
        }
    }

    @Test
    @DisplayName("durability survives a save and load")
    void nbtRoundTrip() {
        PartHealth saved = full();
        saved.hurt(HitPart.WING_RIGHT, 4.0f);
        saved.hurt(HitPart.WARHEAD, 2.5f);

        CompoundTag tag = new CompoundTag();
        saved.save(tag);

        PartHealth loaded = full();
        loaded.load(tag);

        for (HitPart part : HitPart.values()) {
            assertEquals(saved.remaining(part), loaded.remaining(part), 0.001f, part.id());
        }
    }

    @Test
    @DisplayName("a world saved before sections existed loads as a proportional split")
    void legacyTotalIsSpread() {
        PartHealth health = full();
        health.setTotal(50.0f);
        assertEquals(50.0f, health.total(), 0.05f);
        assertEquals(9.0f, health.remaining(HitPart.WARHEAD), 0.05f,
                "the warhead's 18% share of a half-empty airframe");
    }

    @Test
    @DisplayName("a table listing one part twice does not double-count it")
    void repeatedPartsAggregate() {
        List<PartBox> table = List.of(
                PartBox.of(HitPart.TAIL, 1, 1, 1, 0, 0, 0, 0.5f),
                PartBox.of(HitPart.TAIL, 1, 1, 1, 0, 0, 2, 0.5f));
        PartHealth health = new PartHealth(table, 40.0f);
        assertEquals(40.0f, health.capacity(HitPart.TAIL), 0.01f);
        assertEquals(40.0f, health.totalCapacity(), 0.01f);
    }
}
