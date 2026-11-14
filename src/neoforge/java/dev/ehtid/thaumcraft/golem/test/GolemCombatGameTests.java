package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.ai.GolemOwnerTargetGoal;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

/** Real source melee foundation; these tests deliberately do not claim Guard seal selection. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemCombatGameTests {
    private GolemCombatGameTests() {}
    private static ThaumcraftGolem worker(GameTestHelper h,GolemDesign design,boolean ai) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.STONE);
        var g=GolemModule.GOLEM.get().create(h.getLevel());
        g.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,5))));
        g.initialize(design,UUID.randomUUID(),h.absolutePos(new BlockPos(5,1,5)),0); g.setNoAi(!ai);
        h.assertTrue(h.getLevel().addFreshEntity(g),"Actual supported fighter spawns"); return g;
    }
    private static Pig victim(GameTestHelper h,float hp) {
        var pig=EntityType.PIG.create(h.getLevel()); pig.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7,1,5)))); pig.setNoAi(true); pig.setHealth(hp);
        h.assertTrue(h.getLevel().addFreshEntity(pig),"Actual native victim spawns"); return pig;
    }
    private static int xp(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ExperienceOrb.class,new AABB(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(12,8,12))))).stream().mapToInt(ExperienceOrb::getValue).sum();
    }
    @GameTest(template="golem_test",timeoutTicks=160)
    public static void sourceClawsActuallyNavigateFightAndPreserveCargo(GameTestHelper h) {
        var g=worker(h,GolemDesign.fromIds(3,1,2,1,0,0),true); var pig=victim(h,6); var cargo=new ItemStack(Items.GOLD_INGOT,7); g.setItemSlot(EquipmentSlot.MAINHAND,cargo.copy());
        double initial=g.distanceToSqr(pig); g.setTarget(pig);
        h.onEachTick(()->h.assertTrue(g.getMainHandItem().getCount()==7,"Actual combat never consumes cargo"));
        h.succeedWhen(()->{ h.assertTrue(!pig.isAlive()&&g.rankXp()==8,"Native melee kills actual target and source SMART killXP8"); h.assertTrue(g.distanceToSqr(pig)<initial,"Native melee actually navigates toward target"); h.assertTrue(xp(h)==0,"NonDEFT kill gives no invented player XP"); });
    }
    @GameTest(template="golem_test",timeoutTicks=30)
    public static void ordinaryWorkerCannotAttackEvenWithAssignedTarget(GameTestHelper h) {
        var g=worker(h,GolemDesign.defaultWood(),false); var pig=victim(h,10);
        h.assertTrue(!g.doHurtTarget(pig)&&pig.getHealth()==10&&g.rankXp()==0,"No FIGHTER means no melee damage or killXP"); h.succeed();
    }
    @GameTest(template="golem_test",timeoutTicks=30)
    public static void deftMarksActualRecentlyHitThenNativeLootXp(GameTestHelper h) {
        var g=worker(h,GolemDesign.fromIds(0,1,1,0,2,0),false); var pig=victim(h,2); int before=xp(h);
        h.assertTrue(g.doHurtTarget(pig)&&pig.isAlive()&&pig.lastHurtByPlayerTime==100,"Source successful DEFT nonlethal hit marks real recentlyHit100");
        pig.invulnerableTime=0;
        h.assertTrue(g.doHurtTarget(pig)&&!pig.isAlive()&&g.rankXp()==8,"Second actual hit kills and rewards SMART8");
        h.assertTrue(xp(h)>before,"Native death path emits actual XP orbs without fake-player attribution"); h.succeed();
    }
    @GameTest(template="golem_test",timeoutTicks=30)
    public static void deftLethalFirstHitPreservesSourceAfterDamageOrdering(GameTestHelper h) {
        var g=worker(h,GolemDesign.fromIds(0,1,1,0,2,0),false); var pig=victim(h,1); int before=xp(h);
        h.assertTrue(g.doHurtTarget(pig)&&!pig.isAlive()&&pig.lastHurtByPlayerTime==100,"Source DEFT flag is set after actual lethal damage");
        h.assertTrue(xp(h)==before&&g.rankXp()==8,"No retroactive XP orb reward on source first-hit kill"); h.succeed();
    }
    @GameTest(template="golem_test",timeoutTicks=80)
    public static void sourceFighterRetaliatesButWorkerDoesNot(GameTestHelper h) {
        var g=worker(h,GolemDesign.fromIds(0,1,0,0,2,0),true); var pig=victim(h,10);
        h.runAfterDelay(5,()->g.hurt(h.getLevel().damageSources().mobAttack(pig),1));
        h.succeedWhen(()->h.assertTrue(g.getTarget()==pig&&pig.getHealth()<10,"Native HurtByTarget selects real attacker and melee follows"));
    }
    @GameTest(template="golem_test",timeoutTicks=50)
    public static void sourceOwnerAndTeamAllianceAndFollowingDefense(GameTestHelper h) {
        var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CombatOwner"));
        // Register the server-side fixture owner lookup only; no fake unnegotiated client login.
        h.getLevel().players().add(owner); var g=worker(h,GolemDesign.fromIds(0,1,0,0,2,0),false); var pig=victim(h,10);
        g.initialize(g.design(),owner.getUUID(),g.home(),0); var score=h.getLevel().getScoreboard(); var team=score.addPlayerTeam("g"+UUID.randomUUID().toString().substring(0,10));
        score.addPlayerToTeam(owner.getScoreboardName(),team); score.addPlayerToTeam(pig.getScoreboardName(),team);
        h.runAfterDelay(5,()->{
            try {
                h.assertTrue(g.ownerEntity()==owner&&g.getTeam()==team&&g.isAlliedTo(owner)&&g.isAlliedTo(pig),"Loaded owner identity and team delegated exactly");
                h.assertTrue(!g.doHurtTarget(owner)&&!g.doHurtTarget(pig),"Allied owner/team cannot be struck");
                g.setTarget(pig); g.tick(); h.assertTrue(g.getTarget()==null,"Source OwnedConstruct clears newly allied target");
                score.removePlayerFromTeam(pig.getScoreboardName(),team); owner.tickCount=10; owner.setLastHurtByMob(pig); var defense=new GolemOwnerTargetGoal(g,true);
                h.assertTrue(!defense.canUse(),"Working golem does not defend owner without follow flag"); g.setFollowingOwner(true);
                h.assertTrue(defense.canUse(),"Following FIGHTER sees actual owner attacker timestamp"); defense.start(); h.assertTrue(g.getTarget()==pig,"Source owner-defense sets actual living target");
                g.setFollowingOwner(false); h.assertTrue(!defense.canContinueToUse(),"Leaving follow mode disables owner-defense goal"); defense.stop();
                h.succeed();
            } finally { score.removePlayerTeam(team); h.getLevel().players().remove(owner); owner.discard(); }
        });
    }
    @GameTest(template="golem_test",timeoutTicks=30)
    public static void canceledNativeHitHasNoDeftFlagXpOrCargoLoss(GameTestHelper h) {
        var g=worker(h,GolemDesign.fromIds(0,1,1,0,2,0),false); var pig=victim(h,2); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> guard=e->{ if(e.getEntity()==pig) e.setCanceled(true); };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(guard);
        try { h.assertTrue(!g.doHurtTarget(pig)&&pig.getHealth()==2&&pig.lastHurtByPlayerTime==0&&g.rankXp()==0,"Canceled native damage cannot mark DEFT or grant killXP"); h.assertTrue(g.getMainHandItem().is(Items.DIAMOND_SWORD)&&g.getMainHandItem().getDamageValue()==0,"Actual carried weapon remains intact after canceled attack"); h.succeed(); }
        finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(guard); }
    }
    @GameTest(template="golem_test",timeoutTicks=150)
    public static void paidSourceClawsPressSpawnsOnePersistentFighter(GameTestHelper h) {
        var level=h.getLevel(); var design=GolemDesign.fromIds(0,1,2,0,0,0);
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),net.minecraft.world.level.block.Blocks.STONE);
        var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ClawBuilder")); owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); owner.getInventory().clearContent(); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,1,5))));
        var knowledge=owner.getData(dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); dev.ehtid.thaumcraft.port.core.golem.GolemRuntimeRules.research(design).forEach(knowledge::addResearch);
        h.setBlock(new BlockPos(5,1,5),GolemModule.PRESS.get()); var press=(dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity)level.getBlockEntity(h.absolutePos(new BlockPos(5,1,5)));
        h.assertTrue(press.select(owner,1,1)&&press.select(owner,2,1)&&press.selection().equals(design),"Existing editor selects researched SMART claws, not gated darts");
        int slot=0; for(var part:dev.ehtid.thaumcraft.port.core.golem.GolemAssembly.components(design)) owner.getInventory().setItem(slot++,dev.ehtid.thaumcraft.golem.press.GolemComponents.stack(part));
        h.setBlock(new BlockPos(5,0,5),dev.ehtid.thaumcraft.registry.ThaumcraftBlocks.JAR_NORMAL.get()); var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)level.getBlockEntity(h.absolutePos(new BlockPos(5,0,5)));
        for(int n=0;n<22;n++) h.assertTrue(jar.addFromTransport("machina",1,net.minecraft.core.Direction.UP)==1,"Real Machina22 supply");
        h.assertTrue(press.start(owner)&&press.job().maximum()==22&&owner.getInventory().isEmpty(),"Source wood3+mind1+module1+shears2+base1+mechanisms2: ten actual inputs plus six traits: Machina22");
        h.runAfterDelay(125,()->{
            h.assertTrue(!press.job().active()&&jar.snapshot().amount()==0,"Actual paid fighter job completed"); var placer=press.removeItem(0,1); owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,placer); var pos=h.absolutePos(new BlockPos(9,0,8));
            var ctx=new net.minecraft.world.item.context.UseOnContext(owner,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),net.minecraft.core.Direction.UP,pos,false));
            h.assertTrue(GolemModule.GOLEM_PLACER.get().useOn(ctx).consumesAction()&&owner.getMainHandItem().isEmpty(),"One actual fighter placer debited");
            var entities=level.getEntitiesOfClass(ThaumcraftGolem.class,h.getBounds()); h.assertTrue(entities.size()==1&&entities.getFirst().fighter()&&!entities.getFirst().isNoAi(),"One real enabled fighter"); var g=entities.getFirst(); var tag=new net.minecraft.nbt.CompoundTag(); g.addAdditionalSaveData(tag); var restored=GolemModule.GOLEM.get().create(level); restored.readAdditionalSaveData(tag);
            h.assertTrue(restored.design().equals(design)&&restored.ownerId().equals(owner.getUUID())&&restored.fighter()&&restored.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==2,"Actual packed NBT restores source fighter damage2"); g.discard(); h.succeed();
        });
    }

}
