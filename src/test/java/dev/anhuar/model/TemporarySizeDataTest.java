package dev.anhuar.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporarySizeDataTest {

    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private TemporarySizeData expiringIn(long millisFromNow) {
        return new TemporarySizeData(UUID_A, 5.0, 1.0, System.currentTimeMillis() + millisFromNow);
    }

    @Test
    @DisplayName("is not expired while the expiration is in the future")
    void notExpiredBeforeExpiration() {
        assertFalse(expiringIn(60_000).isExpired());
    }

    @Test
    @DisplayName("is expired once the expiration has passed")
    void expiredAfterExpiration() {
        assertTrue(expiringIn(-1).isExpired());
    }

    @Test
    @DisplayName("reports a positive remaining time before expiring")
    void reportsRemainingTime() {
        long remaining = expiringIn(60_000).getRemainingTime();

        assertTrue(remaining > 0);
        assertTrue(remaining <= 60_000);
    }

    @Test
    @DisplayName("never reports a negative remaining time")
    void neverReportsNegativeRemainingTime() {
        // Callers feed this straight into the scheduler and into player-facing
        // messages, so a past expiration must clamp to zero rather than go negative.
        assertEquals(0, expiringIn(-60_000).getRemainingTime());
    }

    @Test
    @DisplayName("keeps the original size distinct from the temporary one")
    void keepsOriginalSizeDistinct() {
        TemporarySizeData data = expiringIn(60_000);

        // originalSize is what the player is restored to; losing it would strand
        // them at the temporary size permanently.
        assertEquals(5.0, data.getTemporarySize());
        assertEquals(1.0, data.getOriginalSize());
    }
}
