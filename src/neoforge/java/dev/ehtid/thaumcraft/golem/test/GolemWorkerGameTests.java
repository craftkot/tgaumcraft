package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemWorker;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.inventory.GolemInventoryTransfer;
import dev.ehtid.thaumcraft.golem.seal.GolemProvisioning;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.port.core.golem.GolemTrait;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Addon-facing worker calls exercise the real entity inventory and transfer scope. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemWorkerGameTests {
    private GolemWorkerGameTests() {}

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void addonHoldsAndDetachesActualComponentSensitiveCargo(GameTestHelper h) {
        var golem=worker(h,GolemDesign.defaultWood());
        var api=bind(h,golem);
        var supplied=named(70,"Addon diamonds");
        h.assertTrue(api.canCarryAmount(supplied)==64 && api.canCarry(supplied,true) && !api.canCarry(supplied,false),
                "One-slot worker reports actual partial capacity");
        var remainder=api.holdItem(supplied);
        h.assertTrue(remainder.getCount()==6 && supplied.getCount()==70 && golem.getMainHandItem().getCount()==64,
                "Actual bounded hold returns six without aliasing the caller's stack");
        expect(h,IllegalArgumentException.class,()->api.holdItem(golem.getMainHandItem()));
        expect(h,IllegalArgumentException.class,()->api.dropItem(golem.getMainHandItem()));
        supplied.set(DataComponents.CUSTOM_NAME,Component.literal("Caller changed"));
        remainder.setCount(1);
        var view=api.getCarrying();
        view.getFirst().setCount(1);
        h.assertTrue(golem.getMainHandItem().getCount()==64 && api.isCarrying(named(1,"Addon diamonds"))
                && !api.isCarrying(named(1,"Other diamonds")),"Caller and snapshot mutations cannot edit live cargo");
        var requested=named(9,"Addon diamonds");
        var detached=api.dropItem(requested);
        h.assertTrue(detached.getCount()==9 && requested.getCount()==9 && golem.getMainHandItem().getCount()==55,
                "Detach debits nine real items and preserves the request");
        detached.setCount(1);
        h.assertTrue(api.dropItem(named(9,"Other diamonds")).isEmpty() && golem.getMainHandItem().getCount()==55,
                "A different component variant cannot detach cargo");
        var rest=api.dropItem(null);
        h.assertTrue(rest.getCount()==55 && api.dropItem(ItemStack.EMPTY).isEmpty() && golem.getMainHandItem().isEmpty()
                && h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),
                "Null detaches the first actual stack once and never spawns it");
        golem.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void haulerDetachesOneStackAndCompactsTheSecondHand(GameTestHelper h) {
        var golem=worker(h,GolemDesign.fromIds(0,0,0,0,3,0));
        var api=bind(h,golem);
        h.assertTrue(api.hasTrait(GolemTrait.HAULER) && api.getCarrying().size()==2
                && api.holdItem(named(80,"Hauler cargo")).isEmpty(),"HAULER exposes two real bounded carry slots");
        var first=api.dropItem(named(128,"Hauler cargo"));
        h.assertTrue(first.getCount()==64 && golem.getMainHandItem().getCount()==16 && golem.getOffhandItem().isEmpty(),
                "Source detach returns only the first matching stack, then compacts offhand");
        h.assertTrue(api.dropItem(ItemStack.EMPTY).getCount()==16 && api.getCarrying().stream().allMatch(ItemStack::isEmpty),
                "The compacted second stack is detached separately");
        var swords=new ItemStack(Items.IRON_SWORD,3);
        h.assertTrue(api.canCarryAmount(swords)==2 && api.holdItem(swords).getCount()==1
                && golem.getMainHandItem().getCount()==1 && golem.getOffhandItem().getCount()==1,
                "Modern item limits also constrain nonstackable addon cargo");
        golem.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void addonReadsLiveTraitsAndAwardsSourceRankExperience(GameTestHelper h) {
        var golem=worker(h,GolemDesign.fromIds(0,1,1,0,0,0));
        var api=bind(h,golem);
        var before=api.getProperties();
        h.assertTrue(api.hasTrait(GolemTrait.SMART) && api.hasTrait(GolemTrait.DEFT) && !api.hasTrait(GolemTrait.CLUMSY),
                "Addon trait gates use this actual worker's cancelled trait set");
        api.addRankXp(1000);
        h.assertTrue(before.rank()==0 && api.getProperties().rank()==1 && golem.rankXp()==0,
                "The live SMART worker earns rank one while prior property snapshots remain immutable");
        golem.setGolemColor((byte)7);
        h.assertTrue(api.getGolemColor()==7,"Addon color query reads current entity data");
        var basic=worker(h,GolemDesign.defaultWood());
        bind(h,basic).addRankXp(1000);
        h.assertTrue(basic.design().rank()==0 && basic.rankXp()==0,"Non-SMART workers retain source experience restriction");
        golem.discard(); basic.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void addonBindingRechecksOwnerAndRejectsRemovedEntity(GameTestHelper h) {
        var golem=worker(h,GolemDesign.defaultWood());
        var owner=golem.ownerId();
        var api=bind(h,golem);
        expect(h,SecurityException.class,()->GolemWorker.bind(h.getLevel(),UUID.randomUUID(),golem.getUUID()));
        expect(h,IllegalArgumentException.class,()->GolemWorker.bind(h.getLevel(),owner,UUID.randomUUID()));
        golem.initialize(golem.design(),UUID.randomUUID(),golem.home(),0);
        expect(h,SecurityException.class,()->api.holdItem(new ItemStack(Items.DIAMOND,3)));
        h.assertTrue(golem.getMainHandItem().isEmpty(),"Old owner binding cannot move cargo after ownership changes");
        var current=bind(h,golem);
        golem.discard();
        expect(h,IllegalStateException.class,current::getCarrying);
        expect(h,IllegalStateException.class,()->current.holdItem(new ItemStack(Items.DIAMOND,3)));
        h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void addonAccessRefusesForeignThreadBeforeWorldLookup(GameTestHelper h) {
        var golem=worker(h,GolemDesign.defaultWood());
        var api=bind(h,golem);
        var queryError=new AtomicReference<Throwable>();
        var bindError=new AtomicReference<Throwable>();
        var owner=golem.ownerId(); var workerId=golem.getUUID(); var level=h.getLevel();
        var thread=new Thread(()->{
            try { api.getCarrying(); } catch(Throwable error) { queryError.set(error); }
            try { GolemWorker.bind(level,owner,workerId); } catch(Throwable error) { bindError.set(error); }
        },"golem-addon-access-test");
        thread.start();
        try { thread.join(1000); } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        h.assertTrue(!thread.isAlive() && queryError.get() instanceof IllegalStateException && bindError.get() instanceof IllegalStateException,
                "Foreign-thread bind and retained-facade query both refuse");
        h.assertTrue(api.holdItem(new ItemStack(Items.DIAMOND,3)).isEmpty(),"Rejected thread access does not poison later server access");
        golem.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void capabilityCallbackCannotDetachOrFundTheTransferringWorker(GameTestHelper h) {
        var golem=worker(h,GolemDesign.defaultWood());
        var api=bind(h,golem);
        api.holdItem(new ItemStack(Items.DIAMOND,10));
        var detached=new AtomicReference<ItemStack>();
        var refused=new AtomicReference<ItemStack>();
        var destination=new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) {
                detached.set(api.dropItem(null));
                refused.set(api.holdItem(new ItemStack(Items.GOLD_INGOT,3)));
            }
        };
        int moved=GolemContainers.insertCarried(golem,destination);
        h.assertTrue(moved==10 && detached.get()!=null && detached.get().isEmpty() && refused.get()!=null && refused.get().getCount()==3,
                "Real insertion callback cannot reenter shared cargo ownership through addon API");
        h.assertTrue(destination.getStackInSlot(0).getCount()==10 && destination.getStackInSlot(0).is(Items.DIAMOND)
                && golem.getMainHandItem().isEmpty(),"The destination owns exactly the original ten diamonds");
        h.assertTrue(api.holdItem(new ItemStack(Items.GOLD_INGOT,3)).isEmpty() && api.dropItem(null).getCount()==3,
                "Transfer scope closes and subsequent addon transfers work");
        golem.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_api", timeoutTicks=40)
    public static void pendingRecoveryBlocksNewAddonCargoUntilActualRecoveryDrains(GameTestHelper h) {
        var golem=worker(h,GolemDesign.defaultWood());
        var api=bind(h,golem);
        api.holdItem(new ItemStack(Items.DIAMOND,64));
        golem.retainTransfer(new ItemStack(Items.GOLD_INGOT,3));
        h.assertTrue(api.canCarryAmount(new ItemStack(Items.DIAMOND))==0
                && api.holdItem(new ItemStack(Items.EMERALD,2)).getCount()==2,"Recovery queue refuses unrelated addon funding");
        h.assertTrue(api.dropItem(null).getCount()==64 && golem.hasTransferRecovery(),"Detach frees a real hand while keeping recovery owned");
        h.assertTrue(api.canCarryAmount(new ItemStack(Items.EMERALD,2))==0
                && api.holdItem(new ItemStack(Items.EMERALD,2)).getCount()==2 && golem.getMainHandItem().isEmpty(),
                "Pending actual recovery excludes addon offers even when the hand is empty");
        golem.tick();
        h.assertTrue(!golem.hasTransferRecovery() && api.isCarrying(new ItemStack(Items.GOLD_INGOT))
                && api.dropItem(null).getCount()==3,"Actual entity tick drains the recovery before further addon use");
        try(var scope=GolemInventoryTransfer.begin(golem)) {
            h.assertTrue(scope!=null && api.dropItem(null).isEmpty()
                    && api.holdItem(new ItemStack(Items.EMERALD,2)).getCount()==2,"Explicit shared transfer scope also excludes addon writes");
        }
        golem.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="worker_provider_api", timeoutTicks=80)
    public static void providerEntityPublicationExcludesAddonReentryAndKeepsFundedCargo(GameTestHelper h) {
        var fixture=providerEntity(h);
        var api=bind(h,fixture.worker);
        h.runAfterDelay(25,()->{
            var delivery=paidDelivery(h,fixture);
            var funding=new ItemStackHandler(1); funding.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,3));
            var entered=new AtomicBoolean(); var debited=new AtomicBoolean();
            var detached=new AtomicReference<ItemStack>(); var refused=new AtomicReference<ItemStack>();
            Consumer<EntityJoinLevelEvent> callback=event->{
                if(providerDrop(h,event) && entered.compareAndSet(false,true)) {
                    debited.set(api.getCarrying().stream().allMatch(ItemStack::isEmpty));
                    detached.set(api.dropItem(null));
                    refused.set(api.holdItem(funding.extractItem(0,3,false)));
                    fixture.worker.retainTransfer(refused.get());
                }
            };
            boolean completed;
            NeoForge.EVENT_BUS.addListener(callback);
            try { completed=fixture.seals.completeBlock(h.getLevel(),fixture.worker,delivery); }
            finally { NeoForge.EVENT_BUS.unregister(callback); }
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
            try {
                h.assertTrue(completed && entered.get() && debited.get() && detached.get()!=null && detached.get().isEmpty()
                        && refused.get()!=null && refused.get().is(Items.GOLD_INGOT) && refused.get().getCount()==3,
                        "Actual provider entity publication debits first and excludes nested addon detach/hold");
                h.assertTrue(funding.getStackInSlot(0).isEmpty() && fixture.source.getStackInSlot(0).isEmpty()
                        && fixture.worker.getMainHandItem().is(Items.GOLD_INGOT) && fixture.worker.getMainHandItem().getCount()==3
                        && !fixture.worker.hasTransferRecovery() && drops.size()==1
                        && ItemStack.matches(drops.getFirst().getItem(),named(4,"Provider addon cargo")),
                        "Only paid diamonds4 are published and independently funded gold3 survives the callback");
                h.assertTrue(!fixture.seals.completeBlock(h.getLevel(),fixture.worker,delivery),"Completed provider delivery cannot publish twice");
            } finally { fixture.close(); drops.forEach(ItemEntity::discard); }
            h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="worker_provider_api", timeoutTicks=80)
    public static void canceledProviderDropRetainsChangedActualPayloadWithoutOverwritingFunding(GameTestHelper h) {
        var fixture=providerEntity(h);
        var api=bind(h,fixture.worker);
        h.runAfterDelay(25,()->{
            var delivery=paidDelivery(h,fixture);
            var funding=new ItemStackHandler(1); funding.setStackInSlot(0,new ItemStack(Items.EMERALD,2));
            var rejected=new AtomicReference<ItemEntity>();
            Consumer<EntityJoinLevelEvent> callback=event->{
                if(providerDrop(h,event)) {
                    var drop=(ItemEntity)event.getEntity(); rejected.set(drop);
                    drop.getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Changed canceled provider cargo"));
                    fixture.worker.retainTransfer(funding.extractItem(0,2,false));
                    event.setCanceled(true);
                }
            };
            boolean completed;
            NeoForge.EVENT_BUS.addListener(callback);
            try { completed=fixture.seals.completeBlock(h.getLevel(),fixture.worker,delivery); }
            finally { NeoForge.EVENT_BUS.unregister(callback); }
            try {
                var recovery=fixture.worker.transferRecoveryItems();
                h.assertTrue(!completed && rejected.get()!=null && rejected.get().getItem().isEmpty()
                        && h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),
                        "Canceled provider publication empties the rejected entity and reports no delivered cargo");
                h.assertTrue(fixture.source.getStackInSlot(0).isEmpty() && funding.getStackInSlot(0).isEmpty()
                        && fixture.worker.getMainHandItem().is(Items.EMERALD) && fixture.worker.getMainHandItem().getCount()==2
                        && recovery.size()==1 && ItemStack.matches(recovery.getFirst(),named(4,"Changed canceled provider cargo")),
                        "Actual changed diamonds4 recover alongside separately funded emeralds2, with no original snapshot restored");
                var emeralds=api.dropItem(null); fixture.worker.tick(); var diamonds=api.dropItem(null);
                h.assertTrue(emeralds.is(Items.EMERALD) && emeralds.getCount()==2
                        && ItemStack.matches(diamonds,named(4,"Changed canceled provider cargo"))
                        && !fixture.worker.hasTransferRecovery() && api.getCarrying().stream().allMatch(ItemStack::isEmpty),
                        "Both actual payloads drain exactly once after cancellation");
            } finally { fixture.close(); }
            h.succeed();
        });
    }

    private record ProviderEntity(GolemSeals seals,GolemSeals.SealKey provider,ThaumcraftGolem worker,
                                  ArmorStand recipient,IItemHandlerModifiable source) {
        void close() { seals.remove(provider.pos(),provider.face(),worker.ownerId()); worker.discard(); recipient.discard(); }
    }
    private static ProviderEntity providerEntity(GameTestHelper h) {
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        h.setBlock(new BlockPos(2,1,4),Blocks.BARREL);
        var provider=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,4)),Direction.UP);
        var golem=worker(h,GolemDesign.defaultWood());
        golem.moveTo(Vec3.atBottomCenterOf(provider.pos().north()));
        var seals=GolemSeals.get(h.getLevel());
        h.assertTrue(seals.place(provider.pos(),provider.face(),golem.ownerId(),GolemSeals.Type.PROVIDE),"Actual provider seal placed");
        seals.configureControls(seals.get(provider),golem.ownerId(),0,0,true,false);
        var source=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),provider);
        source.setStackInSlot(0,named(4,"Provider addon cargo"));
        var recipient=EntityType.ARMOR_STAND.create(h.getLevel());
        recipient.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(9,1,7))));
        h.assertTrue(h.getLevel().addFreshEntity(recipient),"Actual entity provision recipient spawned");
        h.assertTrue(GolemProvisioning.request(h.getLevel(),new ProvisionRequest.EntityTarget(recipient.getUUID()),
                named(4,"Provider addon cargo"),0).isPresent(),"External request is enrolled for four real provider diamonds");
        return new ProviderEntity(seals,provider,golem,recipient,source);
    }
    private static GolemSeals.BlockAssignment paidDelivery(GameTestHelper h,ProviderEntity fixture) {
        var pickup=fixture.seals.claimBlock(fixture.worker,ignored->true).orElseThrow();
        h.assertTrue(fixture.seals.completeBlock(h.getLevel(),fixture.worker,pickup)
                && fixture.source.getStackInSlot(0).isEmpty()
                && ItemStack.matches(fixture.worker.getMainHandItem(),named(4,"Provider addon cargo")),
                "Provider pickup debits actual source diamonds4 before entity delivery");
        var delivery=fixture.seals.claimBlock(fixture.worker,ignored->true).orElseThrow();
        fixture.worker.moveTo(fixture.recipient.position().add(1,0,0));
        return delivery;
    }
    private static boolean providerDrop(GameTestHelper h,EntityJoinLevelEvent event) {
        return event.getLevel()==h.getLevel() && event.getEntity() instanceof ItemEntity item
                && h.getBounds().contains(item.position()) && ItemStack.isSameItemSameComponents(item.getItem(),named(1,"Provider addon cargo"));
    }

    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design) {
        var golem=GolemModule.GOLEM.get().create(h.getLevel());
        var pos=h.absolutePos(new BlockPos(5,1,5));
        golem.moveTo(Vec3.atBottomCenterOf(pos));
        golem.initialize(design,UUID.randomUUID(),pos,0); golem.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(golem),"Actual worker is registered in the server level");
        return golem;
    }
    private static GolemWorker bind(GameTestHelper h,ThaumcraftGolem golem) {
        return GolemWorker.bind(h.getLevel(),golem.ownerId(),golem.getUUID());
    }
    private static ItemStack named(int count,String name) {
        var stack=new ItemStack(Items.DIAMOND,count);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(name)); return stack;
    }
    private static void expect(GameTestHelper h,Class<? extends Throwable> expected,Runnable action) {
        Throwable actual=null;
        try { action.run(); } catch(Throwable error) { actual=error; }
        h.assertTrue(expected.isInstance(actual),"Expected "+expected.getSimpleName()+", got "+actual);
    }
}
