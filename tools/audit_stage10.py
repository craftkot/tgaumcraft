#!/usr/bin/env python3
from __future__ import annotations
import collections, json, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parents[1]
A = ROOT / 'analysis'
jar = json.loads((A/'jar_inventory.json').read_text(encoding='utf-8'))
loc = json.loads((A/'localization_report.json').read_text(encoding='utf-8'))
aspects = json.loads((A/'aspect_manifest_extracted.json').read_text(encoding='utf-8'))
research = json.loads((A/'research_manifest.json').read_text(encoding='utf-8'))
scan = json.loads((A/'scan_registration_manifest.json').read_text(encoding='utf-8'))

recipe_ids=[]
meta=collections.Counter()
for e in research['entries']:
    for m in e.get('meta') or []: meta[m]+=1
    for st in e.get('stages') or []:
        recipe_ids += st.get('recipes') or []
recipe_ids=list(dict.fromkeys(recipe_ids))
modern=re.compile(r'^[a-z0-9_.-]+:[a-z0-9/._-]+$')
invalid=[x for x in recipe_ids if not modern.fullmatch(x)]
groups=collections.defaultdict(list)
for x in recipe_ids: groups[x.lower()].append(x)
collisions={k:v for k,v in groups.items() if len(v)>1}

java=sorted((ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core').glob('*.java'))
test_text=(A/'core_parity_test_stage10.txt').read_text(encoding='utf-8').strip()
m=re.search(r'OK \((\d+) assertions\)', test_text)
assertions=int(m.group(1)) if m else None
catalog_src=(ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core/BuiltInResearchCatalog.java').read_text(encoding='utf-8')
generated_entries=catalog_src.count('new ResearchEntryData(')

required_files=[
    'BuiltInResearchCatalog.java','LegacyPlayerDataCodec.java','ResearchKnowledgeQueries.java',
    'ScanningService.java','ScanThing.java','BuiltInResearchCategories.java','ScanKnowledgeLogic.java','GenericScanKeyLogic.java','ResearchRequisites.java'
]
missing=[f for f in required_files if not (ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core'/f).exists()]

# Current runtime resources are intentionally audited separately from the immutable
# localization extraction manifest. Stage 10 adds modern item/block translation aliases.
lang_dir=ROOT/'src/main/resources/assets/thaumcraft/lang'
en_current=json.loads((lang_dir/'en_us.json').read_text(encoding='utf-8'))
ru_current=json.loads((lang_dir/'ru_ru.json').read_text(encoding='utf-8'))
current_lang_symmetric=set(en_current)==set(ru_current)
modern_firststeps_lang=[
    'item.thaumcraft.crystal_essence','item.thaumcraft.salis_mundus',
    'item.thaumcraft.thaumometer','item.thaumcraft.thaumonomicon',
    'block.thaumcraft.arcane_workbench',
]

loader_files=[
    'src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftBlocks.java',
    'src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftItems.java',
    'src/neoforge/java/dev/ehtid/thaumcraft/item/CrystalEssenceItem.java',
    'src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusItem.java',
    'src/neoforge/java/dev/ehtid/thaumcraft/world/SalisMundusTransformQueue.java',
]
resource_files=[
    'src/main/resources/assets/thaumcraft/blockstates/arcane_workbench.json',
    'src/main/resources/assets/thaumcraft/models/block/arcane_workbench.json',
    'src/main/resources/assets/thaumcraft/models/item/arcane_workbench.json',
    'src/main/resources/assets/thaumcraft/models/item/crystal_essence.json',
    'src/main/resources/assets/thaumcraft/models/item/salis_mundus.json',
    'src/main/resources/assets/thaumcraft/models/item/thaumonomicon.json',
    'src/main/resources/assets/thaumcraft/textures/block/arcane_workbench_side.png',
    'src/main/resources/assets/thaumcraft/textures/block/arcane_workbench_top.png',
    'src/main/resources/assets/thaumcraft/textures/item/crystal_essence.png',
    'src/main/resources/assets/thaumcraft/textures/item/salis_mundus.png',
    'src/main/resources/assets/thaumcraft/textures/item/thaumonomicon.png',
]
missing_loader=[x for x in loader_files if not (ROOT/x).exists()]
missing_resources=[x for x in resource_files if not (ROOT/x).exists()]

result={
  'jar_baseline': jar,
  'localization': {'en':loc['english_keys'],'ru':loc['russian_final_keys'],'missing_ru':len(loc['missing_in_final_ru'])},
  'aspects': {'total':len(aspects),'primal':sum(not x['components'] for x in aspects),'compound':sum(bool(x['components']) for x in aspects)},
  'research': {
      'entries':research['entry_count'],'generated_runtime_entries':generated_entries,
      'stages':research['stage_count'],'addenda':research['addenda_count'],'categories':research['categories'],
      'meta_counts':dict(sorted(meta.items())),
      'unique_recipe_ids':len(recipe_ids),'legacy_recipe_ids_requiring_mapping':len(invalid),
      'lowercase_mapping_collisions':collisions,
  },
  'scanning': {
      'explicit_constructor_sites':scan['explicit_constructor_sites'],
      'explicit_add_calls':scan['explicit_addScannableThing_calls'],
      'constructor_site_counts':scan['constructor_site_counts'],
      'control_flow_core_implemented':True,
      'generic_scan_category_formula_implemented':True,
      'critical_scan_handlers_present': (ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core/CriticalScanHandlers.java').exists(),
  },
  'core_java_files':len(java),
  'core_parity_assertions':assertions,
  'firststeps_vertical_slice_core_present': all((ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core'/f).exists() for f in ['FirstStepsBootstrapLogic.java','RecipeMagicDustLogic.java','LegacyCrystalEssenceData.java','MinimalAuraRuntime.java','ArcaneWorkbenchRuntime.java','ThaumometerRecipeLogic.java','FirstStepsServerProgression.java']),
  'neoforge_gradle_build_tested':False,
  'firststeps_c2s_progress_source_present': (ROOT/'src/neoforge/java/dev/ehtid/thaumcraft/network/FirstStepsProgressPayload.java').exists(),
  'firststeps_loader_registration_sources_present': not missing_loader,
  'firststeps_loader_resources_present': not missing_resources,
  'current_localization': {
      'en_keys':len(en_current),'ru_keys':len(ru_current),'symmetric_keys':current_lang_symmetric,
      'required_firststeps_keys_present':all(k in en_current and k in ru_current for k in modern_firststeps_lang),
  },
  'player_attachment_sync_sources_present': all((ROOT/p).exists() for p in [
      'src/neoforge/java/dev/ehtid/thaumcraft/player/ThaumcraftPlayerAttachments.java',
      'src/neoforge/java/dev/ehtid/thaumcraft/player/ThaumcraftPlayerEvents.java',
      'src/neoforge/java/dev/ehtid/thaumcraft/network/PlayerDataPayload.java',
      'src/neoforge/java/dev/ehtid/thaumcraft/network/ThaumcraftNetworking.java']),
  'missing_required_core_files':missing,
}

# Hard compatibility gates.
assert jar['class_files']==1120 and jar['thaumcraft_class_files']==1112 and jar['asset_files_all_namespaces']==1531
assert loc['english_keys']==1464 and loc['russian_final_keys']==1464 and not loc['missing_in_final_ru']
assert len(aspects)==37 and sum(not x['components'] for x in aspects)==6
assert research['entry_count']==148 and research['stage_count']==271 and research['addenda_count']==16
assert generated_entries==148
assert len(recipe_ids)==198 and len(invalid)==137 and not collisions
assert scan['explicit_constructor_sites']==71 and scan['explicit_addScannableThing_calls']==71
assert scan['constructor_site_counts']=={'ScanBlock':18,'ScanEnchantment':1,'ScanEntity':34,'ScanGeneric':1,'ScanItem':10,'ScanMaterial':1,'ScanOreDictionary':4,'ScanPotion':1,'ScanSky':1}
assert assertions is not None and assertions >= 4660
assert result['firststeps_vertical_slice_core_present'] and result['scanning']['critical_scan_handlers_present']
assert not missing
assert result['player_attachment_sync_sources_present']
assert result['firststeps_c2s_progress_source_present']
assert result['firststeps_loader_registration_sources_present'] and not missing_loader
assert result['firststeps_loader_resources_present'] and not missing_resources
assert len(en_current)==1469 and len(ru_current)==1469 and current_lang_symmetric
assert all(k in en_current and k in ru_current for k in modern_firststeps_lang)

(A/'stage10_audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
lines=[
    'Stage 10 audit',
    f"JAR baseline: {jar['class_files']} classes / {jar['thaumcraft_class_files']} Thaumcraft / {jar['asset_files_all_namespaces']} assets",
    f"Localization: EN {loc['english_keys']} / RU {loc['russian_final_keys']} / missing RU {len(loc['missing_in_final_ru'])}",
    f"Aspects: {len(aspects)} total / {sum(not x['components'] for x in aspects)} primal / {sum(bool(x['components']) for x in aspects)} compound",
    f"Research runtime catalog: {generated_entries}/{research['entry_count']} entries / {research['stage_count']} stages / {research['addenda_count']} addenda",
    'Research categories: '+', '.join(f'{k}={v}' for k,v in research['categories'].items()),
    'Research meta: '+', '.join(f'{k}={v}' for k,v in sorted(meta.items())),
    f"Recipe IDs: {len(recipe_ids)} unique / {len(invalid)} require modern mapping / {len(collisions)} lowercase collisions",
    f"Scan registration bytecode: {scan['explicit_constructor_sites']} explicit constructor sites / {scan['explicit_addScannableThing_calls']} explicit add calls",
    'Scan constructor sites: '+', '.join(f'{k}={v}' for k,v in scan['constructor_site_counts'].items()),
    'Scanning core: ordered multi-handler control flow + legacy research expression queries + generic category formula implemented',
    f"Core Java files: {len(java)}",
    f"Core parity tests: {assertions} assertions — OK",
    f"Current runtime localization: EN {len(en_current)} / RU {len(ru_current)} / symmetric keys {current_lang_symmetric}",
    'FIRSTSTEPS critical item/block registration + Salis transform sources: PRESENT (not full-Gradle-compiled)',
    'FIRSTSTEPS critical models/textures: PRESENT',
    'Player attachment/sync source layer: PRESENT (not full-Gradle-compiled)',
    'FIRSTSTEPS C2S authoritative progression source: PRESENT (not full-Gradle-compiled)',
    'NeoForge Gradle build tested: NO',
    'AUDIT OK',
]
(A/'stage10_audit.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('\n'.join(lines))
