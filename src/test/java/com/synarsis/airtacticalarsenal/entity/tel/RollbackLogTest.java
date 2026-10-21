package com.synarsis.airtacticalarsenal.entity.tel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Report of the server rolling a launcher back")
class RollbackLogTest {

    @Test
    @DisplayName("the first rollback is reported at once with its reason, the next ones wait five seconds and are counted")
    void throttled() {
        RollbackLog log = new RollbackLog();
        String first = log.note(1000, 42, "Dev", "speed 130 km/h");
        assertNotNull(first);
        assertTrue(first.contains("42") && first.contains("Dev") && first.contains("speed 130 km/h"), first);
        assertTrue(first.contains("1 rollback "), first);

        assertNull(log.note(1010, 42, "Dev", "speed 140 km/h"));
        assertNull(log.note(1050, 42, "Dev", "body 0.80 m into the ground"));
        assertNull(log.note(1099, 42, "Dev", "speed 150 km/h"));

        String later = log.note(1100, 42, "Dev", "body 0.70 m into the ground");
        assertNotNull(later);
        assertTrue(later.contains("body 0.70 m into the ground"), later);
        assertTrue(later.contains("4 rollbacks since the last report"), later);

        assertNull(log.note(1101, 42, "Dev", "speed 120 km/h"));
        assertEquals(null, log.note(1150, 42, "Dev", "speed 120 km/h"));
    }
}
