package com.synarsis.airtacticalarsenal.entity.patriot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TractorJoltRulesTest {

    @Test
    void serverHoldWinsOverDriverAndClientSimulation() {
        for (boolean simulating : new boolean[] {false, true}) {
            for (boolean driven : new boolean[] {false, true}) {
                for (boolean proxy : new boolean[] {false, true}) {
                    assertEquals(TractorJolt.Role.HOLD, TractorJolt.decide(false, simulating, driven, true, proxy));
                    assertEquals(driven ? TractorJolt.Role.FOLLOW : TractorJolt.Role.SIMULATE,
                            TractorJolt.decide(false, simulating, driven, false, proxy));
                }
            }
        }
    }

    @Test
    void synchronizedHoldStopsLocalDriverWithoutChangingRemoteProxyRules() {
        for (boolean driven : new boolean[] {false, true}) {
            for (boolean proxy : new boolean[] {false, true}) {
                for (boolean held : new boolean[] {false, true}) {
                    assertEquals(held ? TractorJolt.Role.HOLD : TractorJolt.Role.SIMULATE,
                            TractorJolt.decide(true, true, driven, held, proxy));
                    assertEquals(proxy ? TractorJolt.Role.FOLLOW : TractorJolt.Role.NONE,
                            TractorJolt.decide(true, false, driven, held, proxy));
                }
            }
        }
    }
}
