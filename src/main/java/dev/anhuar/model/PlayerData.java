package dev.anhuar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerData {

    private UUID uuid;
    private String name;
    private double size;
    private long lastUpdate;

    private Double temporarySize;
    private Long temporaryExpiration;

    public PlayerData(UUID uuid, String name, double size) {
        this.uuid = uuid;
        this.name = name;
        this.size = size;
        this.lastUpdate = System.currentTimeMillis();
        this.temporarySize = null;
        this.temporaryExpiration = null;
    }

    public boolean hasTemporarySize() {
        return temporarySize != null && temporaryExpiration != null &&
                temporaryExpiration > System.currentTimeMillis();
    }

    public void setTemporarySize(double tempSize, long expirationTime) {
        this.temporarySize = tempSize;
        this.temporaryExpiration = expirationTime;
    }

    public void clearTemporarySize() {
        this.temporarySize = null;
        this.temporaryExpiration = null;
    }
}
