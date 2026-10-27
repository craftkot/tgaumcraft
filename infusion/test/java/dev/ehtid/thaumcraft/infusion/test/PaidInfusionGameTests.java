package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Seven independent real caster -> world ticker -> paid physical result cases. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class PaidInfusionGameTests {
    private PaidInfusionGameTests() {}
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidAer(GameTestHelper h) { paid(h,"aer"); }
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidIgnis(GameTestHelper h) { paid(h,"ignis"); }
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidAqua(GameTestHelper h) { paid(h,"aqua"); }
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidTerra(GameTestHelper h) { paid(h,"terra"); }
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidOrdo(GameTestHelper h) { paid(h,"ordo"); }
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidPerditio(GameTestHelper h) { paid(h,"perditio"); }
    // This proves paid vitium output and saved instability4, not unported source instability effects.
    @GameTest(template="infusion_paid_blank",batch="infusion_paid",timeoutTicks=700)
    public static void paidVitium(GameTestHelper h) { paid(h,"vitium"); }

    @GameTest(template="infusion_paid_blank",batch="infusion_legacy_checkpoint",timeoutTicks=700)
    public static void legacyCrystalCheckpointMigratesWithoutLosingActualPartialPayment(GameTestHelper h) {
        paid(h,"aer",true);
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_checkpoint_identity",timeoutTicks=300)
    public static void checkpointWithoutRecipeIdentityCannotSpendOrPublish(GameTestHelper h) {
        corruptCheckpointRejected(h,false);
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_checkpoint_identity",timeoutTicks=300)
    public static void checkpointWithChangedRecipeIdentityCannotSpendOrPublish(GameTestHelper h) {
        corruptCheckpointRejected(h,true);
    }

    private static void corruptCheckpointRejected(GameTestHelper h,boolean altered) {
        var f=new PaidInfusionFixture(h.getLevel(),h.absolutePos(new BlockPos(12,8,12)),"aer");
        f.constructAndStart(); int[] rejectedAt={-1}; int[] ticks={0}; int[] balance={0};
        ItemStack[] inputs=new ItemStack[3];
        h.onEachTick(() -> {
            ticks[0]++;
            if(rejectedAt[0]<0) {
                f.checkLivePayment();
                if(f.lastPaid<8) return;
                var saved=f.tag(f.matrix()); var pending=saved.getCompound("PortInfusionLedger");
                h.assertTrue(pending.contains("RecipeFingerprint"),"Started recipe freezes its complete identity");
                if(altered) pending.putString("RecipeFingerprint","0".repeat(64));
                else pending.remove("RecipeFingerprint");
                balance[0]=f.totalEssentia();
                for(int i=0;i<3;i++) inputs[i]=f.pedestal(i).displayedItem().copy();
                f.matrix().loadWithComponents(saved,h.getLevel().registryAccess());
                rejectedAt[0]=ticks[0];
            }
            h.assertTrue(!f.matrix().crafting() && !f.tag(f.matrix()).contains("PortInfusionLedger"),
                    "Invalid recipe identity rejects the saved ritual");
            h.assertTrue(f.totalEssentia()==balance[0],"Rejected checkpoint cannot spend more actual essentia");
            for(int i=0;i<3;i++) h.assertTrue(ItemStack.matches(inputs[i],f.pedestal(i).displayedItem()),
                    "Rejected checkpoint cannot consume inputs or publish output");
            if(ticks[0]-rejectedAt[0]>=30) {
                f.actor.getInventory().clearContent();
                LogUtils.getLogger().info("INFUSION_INVALID_RECIPE_IDENTITY_REJECTED altered={} paid={} observationTicks=30",altered,f.lastPaid);
                h.succeed();
            }
        });
    }

    private static void paid(GameTestHelper h,String aspect) { paid(h,aspect,false); }
    private static void paid(GameTestHelper h,String aspect,boolean legacyMigration) {
        var f = new PaidInfusionFixture(h.getLevel(),h.absolutePos(new BlockPos(12,8,12)),aspect);
        f.constructAndStart(); int[] tick={0}; boolean[] migrated={false}; ItemStack[] published={ItemStack.EMPTY};
        h.onEachTick(() -> {
            tick[0]++;
            f.checkLivePayment();
            if(legacyMigration && !migrated[0] && f.matrix().crafting() && f.lastPaid>=8) {
                // Explicit compatibility fixture from a genuinely started, actually partially paid ritual.
                var saved=f.tag(f.matrix()); var pending=saved.getCompound("PortInfusionLedger");
                long[] positions=pending.getLongArray("SidePedestals");
                pending.remove("Format"); pending.remove("SidePedestals"); pending.remove("RecipeFingerprint");
                pending.putLong("SeedsPedestal",positions[0]); pending.putLong("SalisPedestal",positions[1]);
                f.matrix().loadWithComponents(saved,h.getLevel().registryAccess());
                h.assertTrue(f.matrix().crafting() && f.ledger().getInt("Paid")==f.lastPaid
                        && f.ritual.equals(f.ledger().getUUID("RitualId")),"Legacy checkpoint preserves actual paid progress and ritual identity");
                migrated[0]=true; f.snapshot("LEGACY_CHECKPOINT_MIGRATED");
            }
            if(f.matrix().crafting()) return;
            f.verifyFinished();
            if(f.completionTick<0) {
                f.completionTick=tick[0]; published[0]=f.pedestal(0).displayedItem().copy();
                f.snapshot("FINISHED"); f.casterClick();
                h.assertTrue(!f.matrix().crafting(),"Repeat caster click cannot replay consumed recipe");
            }
            h.assertTrue(ItemStack.matches(published[0],f.pedestal(0).displayedItem()),"Published exact output remains unchanged");
            if(tick[0]-f.completionTick>=120) {
                f.snapshot("NO_REPLAY_120_TICKS"); f.actor.getInventory().clearContent();
                LogUtils.getLogger().info("INFUSION_PAID_NATIVE_ASSERTIONS_OK aspect={} paid=25 output=1 replayTicks=120 research=explicit_fixture",aspect);
                h.succeed();
            }
        });
    }
}
