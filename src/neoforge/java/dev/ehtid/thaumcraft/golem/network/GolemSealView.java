package dev.ehtid.thaumcraft.golem.network;

import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.SealArea;
import dev.ehtid.thaumcraft.port.core.golem.SealAreaControls;
import net.minecraft.core.BlockPos;
import java.util.Objects;
import java.util.UUID;

/** Public, immutable display metadata. No inventories, tasks, leases or client mutation authority. */
public record GolemSealView(GolemSeals.SealKey key, GolemSeals.Type type, BlockPos area,
                           int color, int priority, UUID owner, UUID instance, boolean locked, boolean redstone) {
    public GolemSealView {
        Objects.requireNonNull(key); Objects.requireNonNull(type); Objects.requireNonNull(owner); Objects.requireNonNull(instance);
        area = area.immutable();
        if (!SealAreaControls.valid(new SealArea.BlockPos(area.getX(),area.getY(),area.getZ()))
                || color < 0 || color > 16 || priority < -5 || priority > 5)
            throw new IllegalArgumentException("Invalid seal display metadata");
    }
    public static GolemSealView of(GolemSeals.Seal seal) {
        return new GolemSealView(seal.key(),seal.type(),seal.area(),seal.color(),seal.priority(),seal.owner(),seal.instanceId(),seal.locked(),seal.redstone());
    }
    public SealArea.Bounds bounds() {
        var p=key.pos();
        return new SealArea(new SealArea.SealPos(new SealArea.BlockPos(p.getX(),p.getY(),p.getZ()),
                SealArea.Facing.values()[key.face().ordinal()]),new SealArea.BlockPos(area.getX(),area.getY(),area.getZ())).bounds();
    }
}
