package dev.ehtid.thaumcraft.golem.press;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.lang.ref.WeakReference;
import java.util.*;

/** Dimension-owned delayed assembly and idempotent restoration; never obtains an unloaded chunk. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class GolemPressStructures extends SavedData {
    private static final Factory<GolemPressStructures> FACTORY = new Factory<>(GolemPressStructures::new, GolemPressStructures::load, null);
    private enum Phase { PENDING, ACTIVE, RESTORING }
    private static final class Structure {
        BlockPos origin; int rotation; Direction facing; UUID owner; long due;
        Phase phase; int restoreMask; List<BlockState> source;
        WeakReference<ServerPlayer> actor = new WeakReference<>(null);
        List<GolemPressBlueprint.Cell> cells() { return GolemPressBlueprint.cells(rotation); }
        BlockPos pos(GolemPressBlueprint.Cell cell) { return origin.offset(cell.x(), cell.y(), cell.z()); }
        BlockPos controller() { return pos(cells().get(Part.CONTROLLER.ordinal())); }
    }
    private final Map<BlockPos, Structure> structures = new LinkedHashMap<>();
    private final Map<BlockPos, Structure> byCell = new HashMap<>();
    private boolean changing;
    public static GolemPressStructures get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, "thaumcraft_golem_press_structures"); }
    public int size() { return structures.size(); }
    public static boolean reserved(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return false;
        var s = get(server).byCell.get(pos);
        return s != null && s.phase != Phase.ACTIVE;
    }
    public boolean canOperate(BlockPos controller) { var structure = byCell.get(controller); return structure == null || structure.phase == Phase.ACTIVE; }
    public BlockPos controllerFor(BlockPos pos) { var s = byCell.get(pos); return s != null && s.phase == Phase.ACTIVE ? s.controller() : null; }

    /** Called only after the existing simple dust triggers have declined, preserving their precedence. */
    public static InteractionResult use(ItemStack dust, UseOnContext context) {
        var player = context.getPlayer(); var level = context.getLevel();
        if (player == null || dust.isEmpty() || player.isSpectator() || !player.isAlive() || player.isShiftKeyDown()
                || !player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().isResearchKnown(GolemPressBlueprint.RESEARCH)) return InteractionResult.PASS;
        if (reserved(level, context.getClickedPos())) return InteractionResult.CONSUME;
        Structure match = find(level, context.getClickedPos());
        if (match == null) return InteractionResult.PASS;
        for (var cell : match.cells()) {
            BlockPos pos = match.pos(cell);
            if (!level.getWorldBorder().isWithinBounds(pos) || !player.mayInteract(level, pos)
                    || !player.mayUseItemAt(pos, context.getClickedFace(), dust)) return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer actor)) return InteractionResult.SUCCESS;
        var data = get(server);
        for (var cell : match.cells()) if (data.byCell.containsKey(match.pos(cell))) return InteractionResult.CONSUME;
        match.owner = actor.getUUID(); match.actor = new WeakReference<>(actor);
        match.facing = context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace() : player.getDirection().getOpposite();
        match.phase = Phase.PENDING; match.due = server.getGameTime() + GolemPressBlueprint.DELAY;
        match.source = match.cells().stream().map(c -> server.getBlockState(match.pos(c))).toList();
        data.put(match);
        if (!player.getAbilities().instabuild) dust.shrink(1);
        player.swing(context.getHand(), true);
        return InteractionResult.CONSUME;
    }

    private static Structure find(Level level, BlockPos clicked) {
        // Same bounded anchor scan and horizontal order as DustTriggerMultiblock.
        for (int y = -2; y <= 0; y++) for (int x = -2; x <= 0; x++) for (int z = -2; z <= 0; z++)
            for (int rotation : new int[]{3, 2, 1, 0}) {
                var s = new Structure(); s.origin = clicked.offset(x, y, z); s.rotation = rotation;
                boolean fits = true;
                for (var cell : s.cells()) {
                    BlockPos pos = s.pos(cell);
                    if (!loaded(level,pos) || !sourceMatches(cell.part(), level.getBlockState(pos))) { fits = false; break; }
                }
                if (fits) return s;
            }
        return null;
    }
    private static boolean loaded(Level level, BlockPos pos) {
        // The vanilla piston signal checks also inspect neighbors of a neighbor.
        // Four corner chunks cover this 5x5 horizontal region without requesting one.
        for (int x : new int[]{-2,2}) for (int z : new int[]{-2,2})
            if (!level.hasChunkAt(pos.offset(x,0,z))) return false;
        return true;
    }
    private static boolean sourceMatches(Part part, BlockState state) {
        return switch (part) {
            case CONTROLLER -> state == Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP);
            case CAULDRON -> state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);
            case ANVIL -> state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL);
            case TABLE -> state.is(GolemModule.STONE_TABLE.get());
            case BARS -> state.is(Blocks.IRON_BARS);
        };
    }
    private static BlockState original(Part part) {
        return switch (part) {
            case CONTROLLER -> Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP);
            case CAULDRON -> Blocks.CAULDRON.defaultBlockState(); case ANVIL -> Blocks.ANVIL.defaultBlockState();
            case TABLE -> GolemModule.STONE_TABLE.get().defaultBlockState(); case BARS -> Blocks.IRON_BARS.defaultBlockState();
        };
    }
    private static Block target(Part part) {
        return switch (part) {
            case CONTROLLER -> GolemModule.PRESS.get(); case CAULDRON -> GolemModule.PLACEHOLDER_CAULDRON.get();
            case ANVIL -> GolemModule.PLACEHOLDER_ANVIL.get(); case TABLE -> GolemModule.PLACEHOLDER_TABLE.get();
            case BARS -> GolemModule.PLACEHOLDER_BARS.get();
        };
    }
    private void put(Structure s) { structures.put(s.controller(), s); for (var c : s.cells()) byCell.put(s.pos(c), s); setDirty(); }
    private void forget(Structure s) { structures.remove(s.controller()); for (var c : s.cells()) byCell.remove(s.pos(c), s); setDirty(); }

    @SubscribeEvent public static void onTick(LevelTickEvent.Post event) { if (event.getLevel() instanceof ServerLevel level) get(level).tick(level); }
    public void tick(ServerLevel level) {
        if (changing) return;
        for (var s : List.copyOf(structures.values())) {
            if (s.phase == Phase.PENDING && level.getGameTime() >= s.due) {
                if (s.cells().stream().anyMatch(c -> !loaded(level,s.pos(c)))) {
                    if (level.getGameTime() > s.due + 200) forget(s);
                    continue;
                }
                var actor = level.getServer().getPlayerList().getPlayer(s.owner);
                if (actor == null && s.actor.get() instanceof FakePlayer fake) actor = fake;
                boolean valid = actor != null && actor.isAlive() && !actor.isSpectator() && actor.level() == level
                        && actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().isResearchKnown(GolemPressBlueprint.RESEARCH);
                for (var c : s.cells()) if (valid) valid = level.getBlockState(s.pos(c)) == s.source.get(c.part().ordinal()) && actor.mayInteract(level, s.pos(c));
                if (valid) assemble(level, s, actor); else forget(s); // Dust was paid at activation, as in BETA26.
            } else if (s.phase == Phase.RESTORING && level.getGameTime() % 20 == 0) restore(level, s);
            else if (s.phase == Phase.ACTIVE && level.getGameTime() % 20 == 0) {
                for (var c : s.cells()) if (level.hasChunkAt(s.pos(c)) && !level.getBlockState(s.pos(c)).is(target(c.part()))) {
                    dismantle(level, s, c.part()); break;
                }
            }
        }
    }
    private void assemble(ServerLevel level, Structure s, ServerPlayer actor) {
        changing = true;
        boolean committed = false;
        try {
            var snapshots = s.cells().stream().map(c -> BlockSnapshot.create(level.dimension(), level, s.pos(c))).toList();
            boolean placed = true;
            for (var c : s.cells()) {
                BlockState state = target(c.part()).defaultBlockState();
                if (c.part() == Part.CONTROLLER) state = state.setValue(GolemPressBlock.FACING, s.facing);
                placed &= level.setBlock(s.pos(c), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            // Original VirtualSwapper uses UP. The event's placed-against lookup stays in a loaded chunk.
            boolean canceled = EventHooks.onMultiBlockPlace(actor, snapshots, Direction.UP);
            if (placed && !canceled && s.cells().stream().allMatch(c -> level.getBlockState(s.pos(c)).is(target(c.part())))) {
                s.phase = Phase.ACTIVE; s.source = List.of(); setDirty(); committed = true;
            } else {
                for (var c : s.cells()) if (level.getBlockState(s.pos(c)).is(target(c.part())))
                    level.setBlock(s.pos(c), s.source.get(c.part().ordinal()), Block.UPDATE_ALL);
                forget(s);
            }
        } finally { changing = false; }
        if (committed) for (var c : s.cells()) level.updateNeighborsAt(s.pos(c), target(c.part()));
    }
    public void removed(ServerLevel level, BlockPos broken) {
        if (changing) return;
        Structure s = byCell.get(broken);
        if (s == null) return;
        if (s.phase == Phase.PENDING) return; // Exact snapshot check at due time aborts the whole operation.
        if (s.phase == Phase.ACTIVE) for (var c : s.cells()) if (s.pos(c).equals(broken)) { dismantle(level, s, c.part()); break; }
    }
    private void dismantle(ServerLevel level, Structure s, Part broken) {
        s.phase = Phase.RESTORING; s.restoreMask = 31 & ~(1 << broken.ordinal()); setDirty(); restore(level, s);
    }
    private void restore(ServerLevel level, Structure s) {
        changing = true;
        try {
            for (var c : s.cells()) {
                int bit = 1 << c.part().ordinal(); BlockPos pos = s.pos(c);
                if ((s.restoreMask & bit) == 0 || !loaded(level,pos)) continue;
                if (level.getBlockState(pos).is(target(c.part()))) level.setBlock(pos, original(c.part()), Block.UPDATE_ALL);
                s.restoreMask &= ~bit; setDirty();
            }
            if (s.restoreMask == 0) forget(s);
        } finally { changing = false; }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var rows = new ListTag();
        for (var s : structures.values()) {
            var row = new CompoundTag(); row.putLong("Origin", s.origin.asLong()); row.putInt("Rotation", s.rotation);
            row.putString("Phase", s.phase.name()); row.putInt("Facing", s.facing.get2DDataValue()); row.putUUID("Owner", s.owner);
            row.putLong("Due", s.due); row.putInt("RestoreMask", s.restoreMask);
            var source = new ListTag(); for (var state : s.source) source.add(NbtUtils.writeBlockState(state)); row.put("Source", source); rows.add(row);
        }
        tag.put("Structures", rows); return tag;
    }
    public static GolemPressStructures load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new GolemPressStructures(); var rows = tag.getList("Structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) try {
            var row = rows.getCompound(i); var s = new Structure(); s.origin = BlockPos.of(row.getLong("Origin")); s.rotation = row.getInt("Rotation");
            s.phase = Phase.valueOf(row.getString("Phase")); s.facing = Direction.from2DDataValue(row.getInt("Facing")); s.owner = row.getUUID("Owner");
            s.due = row.getLong("Due"); s.restoreMask = row.getInt("RestoreMask");
            if (s.rotation < 0 || s.rotation > 3 || s.restoreMask < 0 || s.restoreMask > 31) continue;
            var list = row.getList("Source", Tag.TAG_COMPOUND); var source = new ArrayList<BlockState>();
            for (int entry = 0; entry < list.size(); entry++) source.add(NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), list.getCompound(entry)));
            s.source = List.copyOf(source);
            if (s.phase == Phase.PENDING && (source.size() != 5 || s.cells().stream().anyMatch(c -> !sourceMatches(c.part(), source.get(c.part().ordinal()))))) continue;
            if (s.cells().stream().noneMatch(c -> data.byCell.containsKey(s.pos(c)))) data.put(s);
        } catch (IllegalArgumentException ignored) { /* Invalid records never authorize a free structure. */ }
        return data;
    }
}
