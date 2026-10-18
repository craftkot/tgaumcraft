package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarLogic;
import dev.ehtid.thaumcraft.port.core.LegacyVoidJarLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.port.core.LegacyPhialJarExchange;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent server-side BETA26 jar. No passive essentia generation. */
public final class EssentiaJarBlockEntity extends BlockEntity implements EssentiaTransportPort {
    private int transportTicks;
    private LegacyEssentiaJarLogic.State stored = LegacyEssentiaJarLogic.State.empty();
    private int facing = 2;
    private boolean blocked;

    public EssentiaJarBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.JAR_NORMAL.get(), pos, state);
    }
    public LegacyEssentiaJarLogic.State snapshot() { return stored; }
    public int facing() { return facing; }
    public boolean blocked() { return blocked; }
    /** Original TileJarFillableVoid, not a normal jar renamed for display. */
    public boolean isVoidJar() {
        return getBlockState().is(ThaumcraftBlocks.JAR_VOID.get());
    }
    private void voidJarOverflow(int destroyed) {
        if (destroyed > 0 && level instanceof net.minecraft.server.level.ServerLevel server
                && server.random.nextInt(250) == 0) {
            dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData.get(server)
                    .polluteFlux(worldPosition, 1);
        }
    }
    /** Source IEssentiaTransport#isBlocked; a braced jar refuses pipe IO. */
    public boolean isBlocked() { return blocked; }

    /**
     * Original BlockJar#applyLabel: unfilled label learns the jar's current
     * aspect; a pre-typed label can label an empty jar. The filter blocks
     * mixing and persists with a mined jar.
     */
    public boolean applyLabel(String requested, net.minecraft.core.Direction labelSide) {
        if (level == null || level.isClientSide || stored.filter() != null
                || labelSide == null || !labelSide.getAxis().isHorizontal())
            return false;
        String chosen = stored.amount() > 0 ? stored.aspect() : requested;
        if (chosen == null || new AspectRegistry().get(chosen) == null)
            return false;
        facing = labelSide.get3DDataValue();
        updateStored(new LegacyEssentiaJarLogic.State(
                stored.aspect(), chosen, stored.amount()));
        return true;
    }

    /** Source: shift-right-click on label-facing side tears off a blank one. */
    public boolean removeLabel(net.minecraft.core.Direction side) {
        if (level == null || level.isClientSide || stored.filter() == null
                || side == null || facing != side.get3DDataValue())
            return false;
        updateStored(new LegacyEssentiaJarLogic.State(
                stored.aspect(), null, stored.amount()));
        return true;
    }

    /** Original BlockJar: a consumed jar brace locks the glass jar once. */
    public boolean applyBrace() {
        if (level == null || level.isClientSide || blocked) return false;
        blocked = true;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    /**
     * Original BlockJar sneaking + empty-hand interaction:
     * pour any contents into the local aura as flux and clear the jar.
     * Existing aspect labels remain attached and restrict future contents.
     * A void operation does not add flux or change inventory.
     */
    public boolean emptyByHand() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || stored.amount() <= 0) return false;
        int spilled = stored.amount();
        dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData.get(server)
                .polluteFlux(worldPosition, spilled);
        updateStored(new LegacyEssentiaJarLogic.State(null, stored.filter(), 0));
        return true;
    }
    /** True if the jar item must retain non-default BlockEntity state. */
    public boolean hasStoredData() {
        return stored.amount() > 0 || stored.filter() != null || blocked || facing != 2;
    }

    /**
     * Called only by the genuine block loot path. Vanilla BlockItem applies
     * minecraft:block_entity_data when this exact jar is subsequently placed.
     * Include the registered BlockEntity id, not a guessed legacy NBT id;
     * otherwise 1.21 silently discards the item component on placement.
     */
    public boolean writeJarDropData(ItemStack drop, HolderLookup.Provider registries) {
        if (drop.isEmpty() || !(drop.is(ThaumcraftItems.JAR_NORMAL.get())
                || drop.is(ThaumcraftItems.JAR_VOID.get()))
                || !hasStoredData()) return false;
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putString("id",
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType()).toString());
        drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        return true;
    }
    public boolean isConnectable(Direction side) { return side == Direction.UP; }
    public int suction(Direction side) {
        if (!isConnectable(side)) return 0;
        return isVoidJar() ? LegacyVoidJarLogic.suction(stored)
                : LegacyEssentiaJarLogic.suction(stored);
    }
    public String suctionAspect(Direction side) {
        return isConnectable(side) ? LegacyEssentiaJarLogic.suctionAspect(stored) : null;
    }
    /** Caller must debit the returned amount from its actual source. */
    public int addFromTransport(String aspect, int offered, Direction side) {
        if (level == null || level.isClientSide || blocked || !isConnectable(side)
                || !LegacyEssentiaJarLogic.accepts(stored, aspect)) return 0;
        if (isVoidJar()) {
            var transfer = LegacyVoidJarLogic.add(stored, aspect, offered);
            if (transfer.consumed() > 0) {
                if (!transfer.state().equals(stored)) updateStored(transfer.state());
                // The source makes ONE 1/250 roll per overflowing addToContainer call.
                voidJarOverflow(transfer.destroyed());
            }
            return transfer.consumed();
        }
        var transfer = LegacyEssentiaJarLogic.add(stored, aspect, offered);
        if (transfer.transferred() > 0) updateStored(transfer.state());
        return transfer.transferred();
    }
    /**
     * Safe remote mirror credit path. Compute the exact one-unit destination
     * state BEFORE touching the source, then debit via the synchronous
     * server-thread callback and commit that already-validated state.
     * Normal jars only; void overflow must never silently destroy a relay unit.
     */
    public boolean canReceiveMirror(String aspect) {
        if(!(level instanceof ServerLevel)||blocked||isVoidJar()
                ||!LegacyEssentiaJarLogic.accepts(stored,aspect))return false;
        return LegacyEssentiaJarLogic.add(stored,aspect,1).transferred()==1;
    }

    public boolean canReceiveAnyMirror() {
        return level instanceof ServerLevel&&!blocked&&!isVoidJar()
                &&stored.amount()<LegacyEssentiaJarLogic.CAPACITY;
    }

    public boolean receiveOneFromMirror(String aspect,java.util.function.BooleanSupplier debitOnce) {
        if(debitOnce==null||!canReceiveMirror(aspect))return false;
        // Nobody can mutate a block entity between these two synchronous
        // operations on the server thread: no event loop yield or scheduler.
        var plan=LegacyEssentiaJarLogic.add(stored,aspect,1);
        if(plan.transferred()!=1||!debitOnce.getAsBoolean())return false;
        updateStored(plan.state());
        dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("MIRROR_TO_REMOTE_JAR");
        return true;
    }

    /** Original TileJarFillable.update() pulls one unit from the tile ABOVE
     * every fifth server tick (void jars continue pulling when full).
     * Only real loaded transport neighbours are eligible; no free essence.
     */
    public static void serverTick(net.minecraft.server.level.ServerLevel level,
                                  BlockPos pos, BlockState state, EssentiaJarBlockEntity jar) {
        if (++jar.transportTicks % 5 != 0 || jar.blocked
                || (!jar.isVoidJar() && jar.stored.amount() >= LegacyEssentiaJarLogic.CAPACITY))
            return;
        BlockPos sourcePos = pos.above();
        // A jar at y=15 of a chunk section / beside an unloaded chunk
        // may only pull from an already-loaded real BlockEntity. A plain
        // getBlockEntity() lookup is not an unloaded-chunk guard, and must
        // never force-generate a chunk on a 5-tick suction probe.
        if (!level.hasChunkAt(sourcePos)) return;
        if (!(level.getBlockEntity(sourcePos) instanceof EssentiaTransportPort above)
                || !above.transportConnects(Direction.DOWN)
                || !above.canTransportOutput(Direction.DOWN)) return;

        int ownSuction = jar.suction(Direction.UP);
        if (ownSuction <= above.transportSuction(Direction.DOWN)) return;
        String aspect = jar.stored.filter() != null ? jar.stored.filter()
                : jar.stored.amount() > 0 ? jar.stored.aspect() : null;
        // BETA26 only consults the source essentia type when the unlabeled
        // and empty jar has a stronger suction than the source.
        if (aspect == null && above.transportAmount(Direction.DOWN) > 0
                && ownSuction >= above.transportMinimumSuction())
            aspect = above.transportAspect(Direction.DOWN);
        if (aspect == null || above.transportAmount(Direction.DOWN) <= 0
                || !aspect.equals(above.transportAspect(Direction.DOWN))
                || !LegacyEssentiaJarLogic.accepts(jar.stored, aspect)
                || (jar.stored.amount() > 0 && !aspect.equals(jar.stored.aspect())))
            return;
        if (!jar.isVoidJar() && jar.stored.amount() >= LegacyEssentiaJarLogic.CAPACITY)
            return;
        // Atomically debit source before granting one unit to the target.
        // Both adapters use the same synchronous server-thread transaction.
        if (above.takeTransport(aspect, 1, Direction.DOWN) == 1) {
            int accepted=jar.addFromTransport(aspect, 1, Direction.UP);
            if (accepted==1 && above instanceof EssentiaTubeBlockEntity)
                dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TUBE_TO_JAR");
            if (accepted==1 && above instanceof EssentiaMirrorBlockEntity)
                dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("MIRROR_TO_JAR");
        }
    }

    @Override public boolean transportConnects(Direction face) {
        return !blocked && isConnectable(face);
    }
    @Override public boolean canTransportInput(Direction face) {
        return transportConnects(face);
    }
    @Override public boolean canTransportOutput(Direction face) {
        return transportConnects(face);
    }
    @Override public int transportSuction(Direction face) {
        return transportConnects(face) ? suction(face) : 0;
    }
    @Override public String transportSuctionAspect(Direction face) {
        return transportConnects(face) ? suctionAspect(face) : null;
    }
    @Override public int transportMinimumSuction() {
        return isVoidJar() ? LegacyVoidJarLogic.minimumSuction(stored)
                : LegacyEssentiaJarLogic.minimumSuction(stored);
    }
    @Override public int transportAmount(Direction face) {
        return transportConnects(face) ? stored.amount() : 0;
    }
    @Override public String transportAspect(Direction face) {
        return transportConnects(face) ? stored.aspect() : null;
    }
    @Override public int takeTransport(String aspect,int amount,Direction face) {
        return transportConnects(face) ? takeForTransport(aspect,amount,face) : 0;
    }
    @Override public int addTransport(String aspect,int amount,Direction face) {
        return transportConnects(face) ? addFromTransport(aspect,amount,face) : 0;
    }

    /** Source jar debit: one complete 10-unit phial, no partial withdrawal. */
    public boolean drawIntoPhial(String aspect, int amount) {
        if (level == null || level.isClientSide) return false;
        var transfer = LegacyPhialJarExchange.withdraw(stored, aspect, amount);
        if (transfer.transferred() != amount) return false;
        updateStored(transfer.state());
        return true;
    }
    /** Preflight for the item-side manual transfer; no jar mutation. */
    public boolean canReceivePhial(String aspect, int amount) {
        if (isVoidJar()) {
            return amount > 0 && amount <= 10
                    && LegacyEssentiaJarLogic.accepts(stored, aspect)
                    && (stored.amount() == 0 || aspect.equals(stored.aspect()));
        }
        return LegacyPhialJarExchange.deposit(stored, aspect, amount)
                .transferred() == amount;
    }
    /** Server-only all-or-nothing deposit; transport UP rule is irrelevant to a held phial. */
    public boolean receivePhial(String aspect, int amount) {
        if (level == null || level.isClientSide || !canReceivePhial(aspect, amount))
            return false;
        if (isVoidJar()) {
            var transfer = LegacyVoidJarLogic.add(stored, aspect, amount);
            if (transfer.consumed() != amount) return false;
            if (!transfer.state().equals(stored)) updateStored(transfer.state());
            voidJarOverflow(transfer.destroyed());
            return true;
        }
        var transfer = LegacyPhialJarExchange.deposit(stored, aspect, amount);
        if (transfer.transferred() != amount) return false;
        updateStored(transfer.state());
        return true;
    }
    /** No partial debit. Caller must have verified recipe and world state. */
    public boolean takeForInfusion(String aspect, int amount) {
        if (level == null || level.isClientSide || amount <= 0) return false;
        var transfer = LegacyEssentiaJarLogic.take(stored, aspect, amount);
        if (transfer.transferred() != amount) return false;
        updateStored(transfer.state());
        return true;
    }
    public int takeForTransport(String aspect, int amount, Direction side) {
        return !blocked && isConnectable(side) && takeForInfusion(aspect, amount) ? amount : 0;
    }
    private void updateStored(LegacyEssentiaJarLogic.State next) {
        stored = next;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // A comparator facing this jar must update when phials, transport
            // or infusion change its amount, not only when the block breaks.
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stored.aspect() != null) tag.putString("Aspect", stored.aspect());
        if (stored.filter() != null) tag.putString("AspectFilter", stored.filter());
        tag.putShort("Amount", (short) stored.amount());
        tag.putByte("facing", (byte) facing);
        tag.putBoolean("blocked", blocked);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String aspect = tag.contains("Aspect", Tag.TAG_STRING) ? tag.getString("Aspect") : null;
        String filter = tag.contains("AspectFilter", Tag.TAG_STRING)
                ? tag.getString("AspectFilter") : null;
        int amount = tag.contains("Amount", Tag.TAG_SHORT)
                ? tag.getShort("Amount") : tag.getInt("Amount");
        amount = Math.max(0, Math.min(LegacyEssentiaJarLogic.CAPACITY, amount));
        if (aspect == null || aspect.isBlank()) amount = 0;
        stored = new LegacyEssentiaJarLogic.State(aspect, filter, amount);
        int oldFacing = tag.getByte("facing");
        facing = oldFacing >= 0 && oldFacing <= 5 ? oldFacing : 2;
        blocked = tag.getBoolean("blocked");
    }
}
