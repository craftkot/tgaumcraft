package dev.ehtid.thaumcraft.golem.press;

import dev.ehtid.thaumcraft.blockentity.EssentiaTransportPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.port.core.golem.GolemPress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Paid default-wood processor. The full construction editor remains separate work. */
public final class GolemPressBlockEntity extends BlockEntity implements WorldlyContainer, EssentiaTransportPort {
    private ItemStack output = ItemStack.EMPTY;
    private GolemPress.State job = GolemPress.State.idle();
    private GolemDesign selected=GolemDesign.defaultWood();
    private int ticks;

    public GolemPressBlockEntity(BlockPos pos, BlockState state) {
        super(GolemModule.PRESS_ENTITY.get(), pos, state);
    }
    public GolemPress.State job() { return job; }
    public GolemDesign selection() { return selected; }
    public boolean selectionKnown(Player player) {
        var knowledge=player.getData(dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        return dev.ehtid.thaumcraft.port.core.golem.GolemRuntimeRules.research(selected).stream().allMatch(knowledge::isResearchKnown);
    }
    public boolean select(Player player,int part,int delta) {
        if(!stillValid(player) || job.active() || part<0 || part>4 || Math.abs(delta)!=1) return false;
        int[] ids={selected.material().id(),selected.head().id(),selected.arms().id(),selected.legs().id(),selected.addon().id()};
        int[] sizes={6,5,5,4,4};
        var knowledge=player.getData(dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        for(int i=0;i<sizes[part];i++) {
            ids[part]=Math.floorMod(ids[part]+delta,sizes[part]);
            var candidate=GolemDesign.fromIds(ids[0],ids[1],ids[2],ids[3],ids[4],0);
            if(!GolemModule.supportsRuntime(candidate) || !dev.ehtid.thaumcraft.port.core.golem.GolemRuntimeRules.research(candidate).stream().allMatch(knowledge::isResearchKnown)) continue;
            if(candidate.equals(selected)) return false;
            selected=candidate; setChanged(); return true;
        }
        return false;
    }

    public boolean start(Player player) {
        if (!(level instanceof ServerLevel server) || !stillValid(player) || job.active()
                || !GolemPressStructures.get(server).canOperate(worldPosition) || !GolemModule.supportsRuntime(selected)
                || !selectionKnown(player)) return false;
        ItemStack product = GolemPlacerItem.stack(selected, 0);
        if (!room(product) || !GolemPressPayment.pay(server, worldPosition, player,selected)) return false;
        job = job.startPaid(selected);
        ticks = 0;
        setChanged();
        return true;
    }

    private boolean room(ItemStack product) {
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, product)
                && output.getCount() < Math.min(getMaxStackSize(), output.getMaxStackSize());
    }

    public static void tick(ServerLevel level, BlockPos pos, BlockState state, GolemPressBlockEntity press) {
        if (!level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(pos)) || ++press.ticks % GolemPress.INTERVAL != 0 || !press.job.active()
                || !GolemPressStructures.get(level).canOperate(pos)) return;
        if (press.job.remaining() > 0) {
            if (!press.job.buffered()) {
                for (Direction face : Direction.values()) {
                    if (!press.canTransportInput(face) || !level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(pos.relative(face)))) continue;
                    if (!(level.getBlockEntity(pos.relative(face)) instanceof EssentiaTransportPort source)) continue;
                    Direction other = face.getOpposite();
                    if (!source.transportConnects(other) || !source.canTransportOutput(other)
                            || source.transportSuction(other) >= GolemPress.SUCTION) continue;
                    if (source.takeTransport(GolemPress.ASPECT, 1, other) == 1) {
                        press.job = press.job.receive(GolemPress.ASPECT, 1);
                        break;
                    }
                }
            }
            var next = press.job.consumeBuffered();
            if (!next.equals(press.job)) { press.job = next; press.setChanged(); }
        }
        if (press.job.ready()) {
            ItemStack product = GolemPlacerItem.stack(press.job.props(), 0);
            if (!press.room(product)) return; // Keep the fully paid product pending; retry without another debit.
            if (press.output.isEmpty()) press.output = product;
            else press.output.grow(1);
            press.job = press.job.finish();
            press.setChanged();
        }
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return output.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? output : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        ItemStack taken = output.split(amount); if (!taken.isEmpty()) setChanged(); return taken;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack taken = output; output = ItemStack.EMPTY; return taken;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        output = stack; setChanged();
    }
    @Override public void clearContent() { output = ItemStack.EMPTY; setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && player.level() == level && player.isAlive() && !player.isSpectator()
                && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64
                && player.mayInteract(level, worldPosition);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public int[] getSlotsForFace(Direction face) { return new int[]{0}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return slot == 0; }

    @Override public boolean transportConnects(Direction face) { return face != null && face != Direction.UP; }
    @Override public boolean canTransportInput(Direction face) { return transportConnects(face); }
    @Override public boolean canTransportOutput(Direction face) { return false; }
    @Override public int transportSuction(Direction face) { return job.active() && job.remaining() > 0 && transportConnects(face) ? GolemPress.SUCTION : 0; }
    @Override public String transportSuctionAspect(Direction face) { return GolemPress.ASPECT; }
    @Override public int transportMinimumSuction() { return 0; }
    @Override public int transportAmount(Direction face) { return 0; }
    @Override public String transportAspect(Direction face) { return null; }
    @Override public int takeTransport(String aspect, int amount, Direction face) { return 0; }
    @Override public int addTransport(String aspect, int amount, Direction face) {
        if (!(level instanceof ServerLevel) || !canTransportInput(face) || !job.accepts(aspect, amount)) return 0;
        job = job.receive(aspect, amount); setChanged(); return 1;
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("golem", job.props()); tag.putInt("cost", job.remaining()); tag.putInt("mcost", job.maximum());
        tag.putBoolean("BufferedMachina", job.buffered());
        tag.putLong("SelectedDesign",selected.toLong());
        if (!output.isEmpty()) tag.put("Output", output.save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        job = GolemPress.State.idle();
        selected=GolemDesign.defaultWood();
        try {
            var design=GolemDesign.fromLong(tag.getLong("SelectedDesign"));
            if(design.rank()==0 && GolemModule.supportsRuntime(design)) selected=design;
        } catch(IllegalArgumentException ignored) { /* Invalid selection cannot alter a paid job. */ }
        if (tag.contains("golem")) {
            try {
                var candidate=new GolemPress.State(tag.getLong("golem"), tag.getInt("cost"), tag.getInt("mcost"), tag.getBoolean("BufferedMachina"));
                if(!candidate.active() || GolemModule.supportsRuntime(GolemDesign.fromLong(candidate.props()))) job=candidate;
            }
            catch (IllegalArgumentException ignored) { /* Never turn malformed or unsupported jobs into free output. */ }
        }
        output = tag.contains("Output") ? ItemStack.parseOptional(registries, tag.getCompound("Output")) : ItemStack.EMPTY;
    }
}
