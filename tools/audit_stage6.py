#!/usr/bin/env python3
import hashlib, json, pathlib, re, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
JAR=pathlib.Path('/mnt/data/Thaumcraft-1.12.2-6.1.BETA26.jar')
errors=[]
report={}

with zipfile.ZipFile(JAR) as z:
    names=z.namelist()
report['jar_sha256']=hashlib.sha256(JAR.read_bytes()).hexdigest()
report['class_files_total']=sum(n.endswith('.class') for n in names)
report['thaumcraft_classes']=sum(n.startswith('thaumcraft/') and n.endswith('.class') for n in names)
report['assets_files']=sum(n.startswith('assets/') and not n.endswith('/') for n in names)
report['thaumcraft_assets_files']=sum(n.startswith('assets/thaumcraft/') and not n.endswith('/') for n in names)

langs=ROOT/'src/main/resources/assets/thaumcraft/lang'
en=json.loads((langs/'en_us.json').read_text())
ru=json.loads((langs/'ru_ru.json').read_text())
report['localization']={'en':len(en),'ru':len(ru),'missing_ru':sorted(set(en)-set(ru)),'extra_ru':sorted(set(ru)-set(en))}
if set(en)!=set(ru): errors.append('EN/RU key sets differ')

research_dir=ROOT/'src/main/resources/data/thaumcraft/research'
entries=[]
legacy_recipe_ids=[]
required_strings=[]
unknown_stage_fields=[]
known_stage={'text','recipes','required_knowledge','required_research','required_item','required_craft','warp'}
for p in sorted(research_dir.glob('*.json')):
    data=json.loads(p.read_text())
    for e in data.get('entries',[]):
        entries.append(e)
        for st in e.get('stages') or []:
            legacy_recipe_ids += st.get('recipes',[])
            required_strings += st.get('required_item',[])+st.get('required_craft',[])
            unknown_stage_fields += [(e['key'],k) for k in st if k not in known_stage]
report['research']={
    'files':len(list(research_dir.glob('*.json'))),
    'entries':len(entries),
    'stages':sum(len(e.get('stages') or []) for e in entries),
    'addenda':sum(len(e.get('addenda') or []) for e in entries),
    'unknown_or_legacy_ignored_stage_fields':unknown_stage_fields,
}
if len(entries)!=148: errors.append(f'research entries={len(entries)}, expected 148')

modern_rl=re.compile(r'^[a-z0-9_.-]+:[a-z0-9/._-]+$')
invalid_recipe=sorted({x for x in legacy_recipe_ids if not modern_rl.fullmatch(x)})
report['recipe_refs']={'total':len(legacy_recipe_ids),'unique':len(set(legacy_recipe_ids)),'modern_invalid_case_or_chars':len(invalid_recipe)}
report['legacy_requirement_strings']={'total':len(required_strings),'unique':len(set(required_strings))}

aspect_manifest=json.loads((ROOT/'analysis/aspect_manifest_extracted.json').read_text())
report['aspects']={'count':len(aspect_manifest),'primals':sum(not x['components'] for x in aspect_manifest),'compounds':sum(bool(x['components']) for x in aspect_manifest)}
if len(aspect_manifest)!=37: errors.append('aspect count != 37')

java_core=list((ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core').glob('*.java'))
report['core_java_files']=len(java_core)
result=(ROOT/'analysis/core_parity_test.txt').read_text().strip() if (ROOT/'analysis/core_parity_test.txt').exists() else ''
report['core_test_result']=result
if 'OK' not in result: errors.append('core parity tests not OK')

report['neoforge_build']={
    'build_gradle':(ROOT/'build.gradle').exists(),
    'wrapper_properties':(ROOT/'gradle/wrapper/gradle-wrapper.properties').exists(),
    'wrapper_jar':(ROOT/'gradle/wrapper/gradle-wrapper.jar').exists(),
    'compile_tested':False,
    'reason':'No Gradle executable/wrapper JAR and runtime dependency download is unavailable in this container.'
}

report['errors']=errors
(ROOT/'analysis/stage6_audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
summary=[
    f"JAR: {report['class_files_total']} classes ({report['thaumcraft_classes']} thaumcraft), {report['assets_files']} assets",
    f"Localization: EN {len(en)} / RU {len(ru)}, missing RU {len(report['localization']['missing_ru'])}",
    f"Research: {report['research']['entries']} entries / {report['research']['stages']} stages / {report['research']['addenda']} addenda",
    f"Aspects: {report['aspects']['count']} ({report['aspects']['primals']} primal + {report['aspects']['compounds']} compound)",
    f"Legacy recipe refs: {report['recipe_refs']['unique']} unique, {report['recipe_refs']['modern_invalid_case_or_chars']} need modern ID migration",
    f"Core Java files: {report['core_java_files']}",
    f"Core tests: {result}",
    f"NeoForge compile-tested: NO ({report['neoforge_build']['reason']})",
    'AUDIT ' + ('OK' if not errors else 'FAILED: '+', '.join(errors)),
]
(ROOT/'analysis/stage6_audit.txt').write_text('\n'.join(summary)+'\n')
print('\n'.join(summary))
