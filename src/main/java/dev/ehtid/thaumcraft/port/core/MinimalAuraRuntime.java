package dev.ehtid.thaumcraft.port.core;

import java.util.HashMap;
import java.util.Map;

/** Minimal persistent chunk-vis store needed by the Arcane Workbench vertical slice. */
public final class MinimalAuraRuntime {
    private final Map<Long, Float> vis = new HashMap<>();
    public float get(long chunkKey) { return vis.getOrDefault(chunkKey, 0f); }
    public void set(long chunkKey, float amount) { vis.put(chunkKey, amount); }
    public boolean canSpend(long chunkKey, int amount) { return amount <= 0 || get(chunkKey) >= amount; }
    public boolean spend(long chunkKey, int amount) {
        if (amount <= 0) return true; // SlotCraftingArcaneWorkbench skips spendAura for <=0.
        float have=get(chunkKey); if (have < amount) return false; vis.put(chunkKey, have-amount); return true;
    }
    public Map<Long,Float> snapshot() { return Map.copyOf(vis); }
    public void load(Map<Long,Float> data) { vis.clear(); if (data!=null) vis.putAll(data); }
}
