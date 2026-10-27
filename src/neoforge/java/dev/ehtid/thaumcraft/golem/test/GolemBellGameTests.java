package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemBellGameTests {
    private GolemBellGameTests() {}

    @GameTest(template="golem_test", batch="bell_recipe", timeoutTicks=40)
    public static void originalRecipeCraftsOneBellAndConsumesFourQuartzAndAStick(GameTestHelper h) {
        floor(h);
        var player=player(h,"BellCrafter");
        BlockPos pos=h.absolutePos(new BlockPos(6,0,6));
        h.setBlock(new BlockPos(6,0,6),Blocks.CRAFTING_TABLE);
        player.moveTo(Vec3.atBottomCenterOf(pos.above()));
        var menu=new CraftingMenu(11,player.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));
        var input=bellInput(false,2);
        for(int i=0;i<9;i++) menu.getSlot(i+1).set(input.get(i));
        h.assertTrue(menu.getSlot(0).getItem().is(GolemModule.GOLEM_BELL.get()),"Actual vanilla crafting menu matches source recipe, without research or vis");
        menu.clicked(0,0,ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().is(GolemModule.GOLEM_BELL.get()) && menu.getCarried().getCount()==1
                && menu.getCarried().getMaxStackSize()==1,"One nonstacking bell");
        for(int i:new int[]{1,2,4,5,6}) h.assertTrue(menu.getSlot(i+1).getItem().getCount()==1,"Exactly one of each paid ingredient slot");
        var mirrored=CraftingInput.of(3,3,bellInput(true,1));
        var match=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,mirrored,h.getLevel());
        h.assertTrue(match.isPresent() && match.get().value().assemble(mirrored,h.getLevel().registryAccess()).is(GolemModule.GOLEM_BELL.get()),"Original horizontal mirroring");
        var wrong=bellInput(false,1); wrong.set(1,new ItemStack(Items.GLASS));
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,CraftingInput.of(3,3,wrong),h.getLevel()).isEmpty(),"Wrong gem rejected");
        menu.setCarried(ItemStack.EMPTY); player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="bell_seals", timeoutTicks=40)
    public static void bellOpensExactSealAndReturnsOneTypedItemOnlyAfterAcceptedSpawn(GameTestHelper h) {
        floor(h); var level=h.getLevel();
        var owner=new MenuPlayer(level); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,3,6))));
        owner.setGameMode(GameType.SURVIVAL); owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get()));
        h.setBlock(new BlockPos(6,1,6),Blocks.STONE);
        var pos=h.absolutePos(new BlockPos(6,1,6)); var data=GolemSeals.get(level);
        h.assertTrue(data.place(pos,Direction.UP,owner.getUUID()) && data.place(pos,Direction.NORTH,owner.getUUID(),GolemSeals.Type.FILL),"Two different faces");
        var bell=GolemModule.GOLEM_BELL.get();
        h.assertTrue(bell.onItemUseFirst(owner.getMainHandItem(),context(owner,pos,Direction.UP)).consumesAction()
                && owner.opened!=null && owner.opened.stillValid(owner),"Bell creates real owner menu on selected face (test captures transport)");
        h.assertTrue(owner.opened.clickMenuButton(owner,81) && data.get(new GolemSeals.SealKey(pos,Direction.UP)).priority()==1
                && data.get(new GolemSeals.SealKey(pos,Direction.NORTH)).priority()==0,"Only selected seal configured");
        var oldMenu=owner.opened; owner.setShiftKeyDown(true);
        h.assertTrue(bell.useOn(context(owner,pos,Direction.UP)).consumesAction() && !data.contains(pos,Direction.UP),"Sneak bell removes selected seal");
        h.assertTrue(!oldMenu.stillValid(owner) && !bell.useOn(context(owner,pos,Direction.UP)).consumesAction(),"No stale menu or second refund");
        var stranger=player(h,"BellStranger"); stranger.moveTo(owner.position()); stranger.setShiftKeyDown(true);
        h.assertTrue(!bell.useOn(context(stranger,pos,Direction.NORTH)).consumesAction() && data.contains(pos,Direction.NORTH),"Other owner cannot remove it");
        Consumer<EntityJoinLevelEvent> reject=e->{ if(e.getLevel()==level && e.getEntity() instanceof ItemEntity item
                && item.getItem().is(GolemModule.FILL_SEAL.get())) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(reject);
        try { h.assertTrue(!bell.useOn(context(owner,pos,Direction.NORTH)).consumesAction() && data.contains(pos,Direction.NORTH),"Rejected refund leaves seal intact"); }
        finally { NeoForge.EVENT_BUS.unregister(reject); }
        h.assertTrue(bell.useOn(context(owner,pos,Direction.NORTH)).consumesAction(),"Accepted refund removes fill");
        var drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==2 && drops.stream().filter(e->e.getItem().is(GolemModule.PICKUP_SEAL.get())).count()==1
                && drops.stream().filter(e->e.getItem().is(GolemModule.FILL_SEAL.get())).count()==1,"Exactly one typed item each: " + drops.stream().map(e->e.getItem()+" at "+e.position()).toList());
        h.assertTrue(owner.getMainHandItem().getCount()==1 && owner.getMainHandItem().is(GolemModule.GOLEM_BELL.get()),"Bell never consumed");
        var remote=pos.offset(16000,0,16000);
        h.assertTrue(!level.hasChunkAt(remote) && !bell.useOn(context(owner,remote,Direction.UP)).consumesAction() && !level.hasChunkAt(remote),"Remote use does not load chunks");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="bell_preempt", timeoutTicks=90)
    public static void ownerResearchCommandReleasesActualWorkWithoutTakingCargo(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"BellDirector");
        var worker=worker(h,owner,new BlockPos(1,1,1)); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,1))));
        h.assertTrue(!worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && !worker.isFollowingOwner(),"GOLEMDIRECT required");
        owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("GOLEMDIRECT");
        var stranger=player(h,"BellNotOwner"); stranger.moveTo(owner.position());
        stranger.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("GOLEMDIRECT");
        h.assertTrue(!worker.interact(stranger,InteractionHand.MAIN_HAND).consumesAction() && !worker.isFollowingOwner(),"Research does not bypass ownership");
        var support=h.absolutePos(new BlockPos(8,0,1)); var data=GolemSeals.get(level);
        data.place(support,Direction.UP,owner.getUUID());
        Vec3 p=Vec3.atBottomCenterOf(support.above()); var item=new ItemEntity(level,p.x,p.y,p.z,new ItemStack(Items.EMERALD,7));
        item.setNoPickUpDelay(); item.setDeltaMovement(Vec3.ZERO); item.setOnGround(true); level.addFreshEntity(item);
        h.succeedWhen(()->{
            h.assertTrue(worker.hasWorkTask() && item.isAlive(),"Actual Pickup goal reserved work before arrival");
            owner.moveTo(worker.position().add(1,0,0));
            h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && worker.isFollowingOwner() && !worker.hasWorkTask(),"Bell cancels real goal immediately");
            var replacement=worker(h,owner,new BlockPos(3,1,1)); replacement.setNoAi(true);
            var lease=data.claim(replacement,ignored->true).orElseThrow();
            h.assertTrue(lease.targetId().equals(item.getUUID()) && worker.getMainHandItem().isEmpty() && item.getItem().getCount()==7,"Reservation available to another worker, cargo untouched");
            data.release(level,replacement,lease,false); data.remove(support,Direction.UP,owner.getUUID());
        });
    }

    @GameTest(template="golem_test", batch="bell_follow", timeoutTicks=260)
    public static void followingUsesNavigationAndStayMovesHomeBeforeResumingAutomation(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"BellWalker");
        owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,1)))); level.addNewPlayer(owner);
        owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("GOLEMDIRECT");
        var worker=worker(h,owner,new BlockPos(1,1,1));
        h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction(),"Begin follow via actual interaction");
        owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,1,10))));
        var support=h.absolutePos(new BlockPos(5,0,5)); var data=GolemSeals.get(level); data.place(support,Direction.UP,owner.getUUID());
        Vec3 p=Vec3.atBottomCenterOf(support.above()); var item=new ItemEntity(level,p.x,p.y,p.z,new ItemStack(Items.EMERALD,7));
        item.setNoPickUpDelay(); item.setDeltaMovement(Vec3.ZERO); item.setOnGround(true); level.addFreshEntity(item);
        Vec3[] previous={worker.position()};
        h.onEachTick(()->{
            h.assertTrue(worker.position().distanceToSqr(previous[0])<4,"Available flat path uses movement, not a teleport");
            previous[0]=worker.position();
        });
        h.startSequence().thenIdle(100).thenExecute(()->{
            h.assertTrue(worker.isFollowingOwner() && worker.distanceToSqr(owner)<10 && worker.getMainHandItem().isEmpty() && item.isAlive(),"Real follow to owner suppresses automation");
            BlockPos newHome=worker.blockPosition(); owner.moveTo(worker.position().add(1,0,0));
            h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && !worker.isFollowingOwner() && worker.home().equals(newHome),"Stay establishes current block as home");
        }).thenIdle(100).thenExecute(()->{
            h.assertTrue(!item.isAlive() && worker.getMainHandItem().getCount()==7,"Automation resumes from new home");
            data.remove(support,Direction.UP,owner.getUUID());
            level.removePlayerImmediately(owner,Entity.RemovalReason.DISCARDED);
        }).thenSucceed();
    }

    private static ArrayList<ItemStack> bellInput(boolean mirrored,int count) {
        var input=new ArrayList<ItemStack>(); for(int i=0;i<9;i++) input.add(ItemStack.EMPTY);
        for(int i:new int[]{1,2,4,5}) input.set(mirrored ? i/3*3+2-i%3 : i,new ItemStack(Items.QUARTZ,count));
        input.set(mirrored ? 8 : 6,new ItemStack(Items.STICK,count)); return input;
    }
    private static FakePlayer player(GameTestHelper h,String name) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name)); p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get())); return p;
    }
    private static ThaumcraftGolem worker(GameTestHelper h,FakePlayer owner,BlockPos relative) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); h.assertTrue(e!=null,"Factory");
        var pos=h.absolutePos(relative); e.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0); e.moveTo(Vec3.atBottomCenterOf(pos));
        e.setOnGround(true); // Fixture starts on its actual solid floor before first AI tick.
        h.assertTrue(h.getLevel().addFreshEntity(e),"Real worker"); return e;
    }
    private static UseOnContext context(FakePlayer player,BlockPos pos,Direction face) {
        Vec3 hit=Vec3.atCenterOf(pos).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);
        return new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,pos,false));
    }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }

    /** Captures only menu transport, since FakePlayer deliberately suppresses network GUI opening. */
    private static final class MenuPlayer extends FakePlayer {
        GolemSealMenu opened;
        MenuPlayer(ServerLevel level) { super(level,new GameProfile(UUID.randomUUID(),"BellMenuOwner")); }
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extra) {
            opened=(GolemSealMenu)provider.createMenu(12,getInventory(),this); containerMenu=opened; return OptionalInt.of(12);
        }
    }
}
