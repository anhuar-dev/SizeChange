package dev.anhuar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One attribute modifier applied to a player by the ATTRIBUTES feature. Persisted
// as a small JSON object (see SQLiteDB / MongoDB); the in-memory owner is
// AttributeModifierManager. Mirrors TemporarySizeData's expiry semantics:
//   expiration == null  -> permanent (survives relog, only removed by a command)
//   expiration != null  -> temporary (auto-removed once the time passes)
//
// A player may hold at most one permanent AND one temporary modifier per
// attribute at the same time — both apply while the temporary one lasts.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttributeModifierData {

    // Namespaced key of the Bukkit attribute, e.g. "minecraft:movement_speed".
    private String attribute;

    // Raw modifier amount, already normalised for the operation below:
    //   ADD_NUMBER        -> flat amount (e.g. 4.0)
    //   ADD_SCALAR        -> fraction of base (e.g. 0.2 for +20%)
    //   MULTIPLY_SCALAR_1 -> fraction added to the total (e.g. 0.5 for x1.5)
    private double amount;

    // AttributeModifier.Operation name.
    private String operation;

    // Epoch millis when a temporary modifier expires, or null for a permanent one.
    private Long expiration;

    public boolean isTemporary() {
        return expiration != null;
    }

    public boolean isExpired() {
        return expiration != null && System.currentTimeMillis() >= expiration;
    }

    // Milliseconds left before a temporary modifier expires (never negative), or
    // -1 for a permanent modifier. Callers feed this straight into the scheduler
    // and into player-facing messages, so it must never go negative.
    public long getRemainingTime() {
        if (expiration == null) {
            return -1;
        }
        return Math.max(0, expiration - System.currentTimeMillis());
    }
}
