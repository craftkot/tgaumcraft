# M60 acquisition and real levitator — 27 October 2026, full native PASS

Branch `feature/golems`, starting HEAD `f566569348b7a221309f3845eed4907120b77f73`. The coordinated root run verifies the complete working source snapshot; final results and raw logs are below.

## Source and behavior

- ConfigRecipes.initializeArcaneRecipes124–244: `mechanism_complex`, BASEARTIFICE,50Vis,Ignis1+Aqua1. Pattern ` M `/`TQT`/` M `: two simple mechanisms, two thaumium plates, ordinary piston (`field_150331_J` decoded PISTON from MCP stable39). Existing workbench/menu debit reused.
- ConfigRecipes.initializeInfusionRecipes0–400,3804–3902: four data-only descriptors in `/data/thaumcraft/golem/infusion_recipes.json`, agreed with the infusion team. Harvest0:Herba10/Sensus10/Humanus10; Butcher0:Bestia10/Sensus10/Humanus10, guard center; Breaker1:Instrumentum10/Perditio10/Humanus10; Biothaumic4:Cognitio50/Machina25, clockwork mind center, brain then complex mechanism. All sides preserve source order. Ordinary source metadata@0/1 minds and NBT seals resolve to distinct existing modern items.
- EntityEvents.livingDrops300–415: recently hit Zombie/subclasses, excluding BrainyZombie, `nextInt10 - looting < 1` grants brain1. The restored event appends a real registered brain to the native drop collection. NeoForge removed getLootingLevel; modern current Looting is read from the damage-source living attacker. Optional entity-type tag preserves the future brainy exclusion. Original champion-only fake-player exclusion does not apply to this separate source branch.
- Common tag audit: NeoForge21.1.250 `c:leathers` contains leather plus optional forge:leather; vanilla minecraft:wool contains all16colours. These match source ore leather and wool@32767 without expanding leather to rabbit_hide.

## Test-first evidence

1. `complex-red.log`: main Core and existing40016construction pass, then new source fixture fails with `Missing original arcane recipe mechanism_complex`.
2. `brain-red.log`: new brain fixture cannot compile without the missing GolemBrainDropRules class.
3. `infusion-resource-red.log`: golden fixture fails because the agreed data resource is absent.
4. `complex-green.log`: source acquisition355 assertions PASS.
5. `brain-green.log`:574 assertions PASS, all ten rolls across Looting−1..12, wrong mob/Brainy/environmental exclusions.
6. `construction-regression-green.log`: existing construction40052 assertions PASS. The original40016count grows because existing substitutions iterate the two additional ingredient kinds.
7. `infusion-resource-green.log`: independent original fixture checks all four recipes, costs, gates, instability, center/output mapping and side order.

Targeted Java used `/workspace/toolchain/jdk-21.0.12.1+1/bin/{javac,java}`, isolated `build/golem-acquisition-targeted` and existing `build/core-test-classes` dependencies. Python command: `python3 tools/test_golem_infusion_recipes.py`. `git diff --check` and unique-key EN/RU JSON parsing pass.

New native tests, executed by root in the final295required suite: `GolemAcquisitionGameTests.complexMechanismPaysTwoSimpleMechanismsThaumiumOrdinaryPistonAndBothCrystals`; both `GolemBrainAcquisitionGameTests` methods (event gate/collection preservation and actual player-attributed death with Looting9). All passed in the final full native run. The golem branch has no generic infusion imports: its recipes become functional when the infusion team's optional JSON loader is present in a temporary combined checkout.

## Restored levitator: native validation passed

Source ConfigRecipes3805–3961: LEVITATOR,35Vis,Aer1; `WIW`/`BNB`/`WGW` where W=plankWood,I=plateThaumium,B=plateIron,N=nitor,G=simple mechanism. A dummy registered item would conceal the missing original device. TileLevitator has range4/8/16/32(default8), opaque-block scan, aura buffer1Vis→1200credits when below10,2×range credits per affected entity, .1directional acceleration and±.35 clamp, horizontal gravity compensation, sneak-UP damping, redstone enable state, range cycling and persisted range/vis. The real `thaumcraft:levitator` block, item, block entity and server ticker are now registered only through GolemModule. This resolves the existing actual golem levitator-leg ingredient reference. Original art is not copied.

Full source disassembly is retained only outside Git in executor `/workspace/reference/javap/m60-source-{Tile,Block}Levitator.javap`. `source-disassembly-provenance.json` records its SHA-256; original complete classes are not redistributed. The exact algorithm includes source subtleties: range scan and refill continue under redstone; force requires actualRange>0 and credits>0; the AABB includes the device cell and extends by actualRange; opaque stop uses modern `isSolidRender`, so partial slabs pass; each eligible entity resets fallDistance and spends2*selectedRange, including sneak-UP. Scan/counter remain transient; only source `range` byte and `vis` int persist, including negative final-entity credit remainder.

Presentation/modern-runtime adaptations are explicit: original-free vanilla-textured six-direction model, body collision trimmed by1/8 at its back; empty-hand use anywhere replaces the small original knob hit target; modern look-direction placement retains source sneak inversion; server force marks velocity for native client delivery instead of running a second client-side paid simulation. Absent-chunk probes stop without acquiring a chunk ticket; invalid saved range restores default8. Original particles, knob ray tracing, sounds and complete visual acceptance remain outside this bounded slice.

Test-first recipe evidence: `levitator-recipe-red.log` is the source golden fixture run against starting HEAD, failing with `Missing original arcane levitator acquisition`; the initial live pre-change isolated run failed for the same reason. `levitator-recipe-green.log` has132 assertions. `levitator-rules-green.log` has263 source golden assertions; isolated mutation1Vis→600credits fails in `levitator-rules-mutation-red.log` (`1200 != 600`), with production unchanged. Both tests are wired in the full parity runner. Independent source review caught zero-range force and broad canOcclude opacity before native compilation; both are fixed and included in obstruction regression.

Six additional native tests passed in root's final full run:

- `GolemAcquisitionGameTests.levitatorActualMenuPaysSourcePlanksPlatesNitorMechanismAerAnd35Vis`: actual menu research gate,9physical slots,35Vis/Aer1, any original nitor colour, wrong substitute rejection and shift payment.
- `GolemLevitatorGameTests.registeredTickerPaysSharedAuraAndPushesItemsInAllSixDirections`: manually invokes actual registered ticker and verifies exact six-way velocity/aura/debit/fall reset.
- `GolemLevitatorGameTests.redstonePausesForceButKeepsSourceScanAndAuraRefill`: actual neighbor power, no force while powered, continued refill/scan, paid resume.
- `GolemLevitatorGameTests.opaqueProbeLimitsBeamAndSourceRangeCyclesThroughFourSettings`: immediate-blocked zero range cannot affect own-cell cargo; partial slab passes/full stone stops, behind-stop cargo remains still,4range/cost cycle.
- `GolemLevitatorGameTests.negativeCreditRemainderAndSneakUpPersistWithoutSavingTransientScan`: source sneak-UP force exception remains paid, exact negative remainder and source keys survive nativeNBT, invalid range handling.
- `GolemLevitatorGameTests.actualWorldTicksRaiseRealItemOnlyAfterSharedAuraPayment`: uses no direct ticker invocation; actual world/entity ticks leave real item stationary for10ticks at0aura, then consume1sharedVis and raise that same item more than1block over10ticks while paying entity credits.

These tests are not a client gameplay/visual or cold-restart proof. Root ran the full native build and all295required tests passed.

Full research/Greatwood/aura generation and full survival progression remain external dependencies. No full-survival or M06 completion claim.


## Root validation checkpoint, before final rerun

Root's final full Core log `/workspace/reference/dual-golems-final-core-2026-10-27.log` contains `CIRCLECI_THAUMCRAFT_CORE_PARITY_OK`: construction40088, module acquisition409, brain574, levitator recipe132/rules263, tasks2909. The increase from historical355/40052 is caused by the existing substitution matrices iterating the additional two levitator ingredient enum values.

First full native run compiled successfully and ran295required tests:294passed, one obsolete baseline test failed because it asserted the original levitator registry entry was still absent. All6new levitator tests passed, including actual elapsed-world-tick paid item movement. This is an explicit failed run, not final completion. Root log: `/workspace/reference/dual-golems-first-native-2026-10-27.log`.

The obsolete test is now replaced with `GolemFlightGameTests.registeredLevitatorIsDebitedByActualFlyerPressAndEarnedPlacerCreatesWorker`. It takes an actual source-priced levitator from the real arcane menu (all9inputs,35Vis,Aer1), temporarily removes that physical component to verify atomic Press refusal, then restores it into the exact source flyer bill. The actual Press consumes all components, receives its full source Machina price, emits one configured placer, and that earned output creates one enabled real flyer. Final full native rerun passed295/295 with BUILD SUCCESSFUL in1m50s; raw log `/workspace/reference/dual-golems-final-native-2026-10-27.log`. Fixture research/aura and remaining component supplies are explicitly not a full survival claim.


## Final validation

Root Java21 full build and295/295required native GameTests PASS,1m50s. The final log includes actual `:compileJava`, `:jar`, `:assemble`, `:build` and `:runGolemGameTest` tasks, all required tests passed and `BUILD SUCCESSFUL`. No production/test source changed after this final run. Full Core marker PASS is recorded above. New client/visual and cold-process tests were not run for this slice. Combined optional4recipe interop remains root-owned and separate from this independent branch build.


## Combined integration result

A separate source directory overlaid infusion-only changed/new source files onto the golem tree. No Git merge/rebase/branch history mutation occurred. Only tools/run_core_parity.sh overlaps and was combined explicitly in that test directory. The full source manifest and actual combined log are attached as .gz.

`THAUMCRAFT_SMOKE=1 gradle --offline --init-script tools/infusion.gradle build -x test runGolemInfusionGameTest`: **4/4 required PASS, BUILD SUCCESSFUL1m25s, exit0**. Actual caster, research refusal then explicit research fixture, native ticks, exact per-aspect jars/side receipts, untouched unrelated Aer7, one output and120 ticks no replay. Independent expected source prices: Harvest30, Butcher30, Break30, Biothaumic75. The tests belong to the infusion branch's optional harness; the descriptor/items belong to the golem branch.

The separate branch JAR audit is in jar-audit.json:773 Java21 classes, CRC/duplicate entries/EN-RU unique keys passed. No original JAR/assets/full disassembled class is redistributed here. Main remains a4280834; M06/M10 global completion is not claimed.
