#!/usr/bin/env python3
import json,re,pathlib,subprocess,sys,collections
ROOT=pathlib.Path(__file__).resolve().parents[1]
manifest=json.loads((ROOT/'analysis/research_manifest.json').read_text())
entries=manifest['entries']
recipe_ids=[]
for e in entries:
    for st in e.get('stages',[]):
        recipe_ids += st.get('recipes') or []
recipe_ids=list(dict.fromkeys(recipe_ids))
modern=re.compile(r'^[a-z0-9_.-]+:[a-z0-9/._-]+$')
invalid=[x for x in recipe_ids if not modern.fullmatch(x)]
groups=collections.defaultdict(list)
for x in recipe_ids: groups[x.lower()].append(x)
collisions={k:v for k,v in groups.items() if len(v)>1}
java=list((ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core').glob('*.java'))
result={
 'research_entries':manifest['entry_count'],
 'research_stages':manifest['stage_count'],
 'research_addenda':manifest['addenda_count'],
 'research_categories':manifest['categories'],
 'unique_recipe_ids':len(recipe_ids),
 'legacy_recipe_ids_requiring_mapping':len(invalid),
 'lowercase_mapping_collisions':collisions,
 'core_java_files':len(java),
}
(ROOT/'analysis/stage7_recipe_id_compat.json').write_text(json.dumps({
 'unique_recipe_ids':recipe_ids,
 'requires_mapping':invalid,
 'mapping':{x:x.lower() for x in invalid},
 'collisions':collisions,
},indent=2,ensure_ascii=False)+'\n')
print('Stage 7 audit')
print(f"Research: {manifest['entry_count']} entries / {manifest['stage_count']} stages / {manifest['addenda_count']} addenda")
print('Categories:', ', '.join(f'{k}={v}' for k,v in manifest['categories'].items()))
print(f'Recipe ids: {len(recipe_ids)} unique / {len(invalid)} legacy ids need modern mapping / {len(collisions)} lowercase collisions')
print(f'Core Java files: {len(java)}')
if manifest['entry_count']!=148 or manifest['stage_count']!=271: raise SystemExit('research baseline mismatch')
if len(recipe_ids)!=198 or len(invalid)!=137 or collisions: raise SystemExit('recipe id compatibility mismatch')
print('AUDIT OK')
(ROOT/'analysis/stage7_audit.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
