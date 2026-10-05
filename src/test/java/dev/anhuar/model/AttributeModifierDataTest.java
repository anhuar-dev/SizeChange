package dev.anhuar.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeModifierDataTest {

    private AttributeModifierData permanent() {
        return new AttributeModifierData("minecraft:movement_speed", 0.2, "ADD_SCALAR", null);
    }

    private AttributeModifierData temporaryExpiringIn(long millisFromNow) {
        return new AttributeModifierData("minecraft:attack_damage", 4.0, "ADD_NUMBER",
                System.currentTimeMillis() + millisFromNow);
    }

    @Test
    @DisplayName("a null expiration marks a permanent modifier")
    void permanentWhenNoExpiration() {
        AttributeModifierData data = permanent();
        assertFalse(data.isTemporary());
        assertFalse(data.isExpired());
        assertEquals(-1, data.getRemainingTime());
    }

    @Test
    @DisplayName("a future expiration is temporary and not yet expired")
    void temporaryNotExpired() {
        AttributeModifierData data = temporaryExpiringIn(60_000);
        assertTrue(data.isTemporary());
        assertFalse(data.isExpired());
        assertTrue(data.getRemainingTime() > 0);
        assertTrue(data.getRemainingTime() <= 60_000);
    }

    @Test
    @DisplayName("a past expiration is expired and never reports negative remaining time")
    void temporaryExpired() {
        AttributeModifierData data = temporaryExpiringIn(-60_000);
        assertTrue(data.isExpired());
        assertEquals(0, data.getRemainingTime());
    }
}
