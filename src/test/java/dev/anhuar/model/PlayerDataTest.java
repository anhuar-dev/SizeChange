package dev.anhuar.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataTest {

    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private PlayerData newPlayer(double size) {
        return new PlayerData(UUID_A, "Steve", size);
    }

    @Test
    @DisplayName("a fresh player has no temporary size")
    void freshPlayerHasNoTemporarySize() {
        PlayerData data = newPlayer(1.0);

        assertFalse(data.hasTemporarySize());
        assertNull(data.getTemporarySize());
        assertNull(data.getTemporaryExpiration());
    }

    @Test
    @DisplayName("the convenience constructor stamps lastUpdate")
    void convenienceConstructorStampsLastUpdate() {
        long before = System.currentTimeMillis();
        PlayerData data = newPlayer(2.5);

        assertEquals(2.5, data.getSize());
        assertTrue(data.getLastUpdate() >= before);
    }

    @Test
    @DisplayName("a temporary size expiring in the future is active")
    void futureTemporarySizeIsActive() {
        PlayerData data = newPlayer(1.0);
        data.setTemporarySize(5.0, System.currentTimeMillis() + 60_000);

        assertTrue(data.hasTemporarySize());
        assertEquals(5.0, data.getTemporarySize());
    }

    @Test
    @DisplayName("a temporary size whose expiration has passed is not active")
    void pastTemporarySizeIsNotActive() {
        PlayerData data = newPlayer(1.0);
        data.setTemporarySize(5.0, System.currentTimeMillis() - 1);

        // The fields are still populated, but the player should be treated as
        // having no temporary size — this is what stops an expired temp from
        // being restored on reconnect.
        assertFalse(data.hasTemporarySize());
    }

    @Test
    @DisplayName("clearing removes both temporary fields")
    void clearingRemovesBothFields() {
        PlayerData data = newPlayer(1.0);
        data.setTemporarySize(5.0, System.currentTimeMillis() + 60_000);

        data.clearTemporarySize();

        assertFalse(data.hasTemporarySize());
        assertNull(data.getTemporarySize());
        assertNull(data.getTemporaryExpiration());
    }

    @Test
    @DisplayName("the permanent size is independent of the temporary one")
    void permanentSizeIsIndependentOfTemporary() {
        PlayerData data = newPlayer(3.0);
        data.setTemporarySize(0.5, System.currentTimeMillis() + 60_000);

        // The permanent size must survive untouched while a temp is active,
        // otherwise it would be lost when the temp expires.
        assertEquals(3.0, data.getSize());

        data.clearTemporarySize();
        assertEquals(3.0, data.getSize());
    }
}
