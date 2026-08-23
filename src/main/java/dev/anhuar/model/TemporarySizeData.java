package dev.anhuar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemporarySizeData {

    private UUID uuid;
    private double temporarySize;
    private double originalSize;
    private long expirationTime;

    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTime;
    }

    public long getRemainingTime() {
        return Math.max(0, expirationTime - System.currentTimeMillis());
    }
}
