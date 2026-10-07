#!/usr/bin/env python3
from __future__ import annotations
import json, zipfile, re, hashlib
from pathlib import Path
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
JAR = Path('/mnt/data/Thaumcraft-1.12.2-6.1.BETA26.jar')

RU_PATCH = {
    'enchantment.infusion.LAMPLIGHT': 'Фонарщик',
    'golem.prop.silk': 'Использовать «Шёлковое касание»',
    'item.seal.breaker_advanced.name': 'Печать управления: продвинутый разрушитель блоков',
    'research.SEALBREAK.stage.ADV': 'Теперь я могу создать улучшенную версию печати, которая позволяет фильтровать больше предметов одновременно и даёт голему возможность разрушать блоки так, словно он использует «Шёлковое касание».<BR>При использовании «Шёлкового касания» големы будут разрушать блоки дольше. ',
    'stability.gain': 'прирост / цикл',
    'stability.loss': 'потеря / цикл',
    'stability.range': 'от 0 до ',
    'thaumcraft.BOLT.text': 'Направляет магию прямо в цель в виде концентрированного сгустка энергии. Эффект мгновенный, но дальность ограничена 16 блоками.',
    'tile.inlay.name': 'Редстоуновая инкрустация',
    'workbench.available': 'доступно',
    'workbench.cost': 'вис',
    'workbench.discount': 'скидка',
}

def parse_lang(raw: bytes) -> dict[str,str]:
    out = {}
    text = raw.decode('utf-8', errors='replace')
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
            continue
        k,v = line.split('=',1)
        out[k.strip()] = v
    return out

with zipfile.ZipFile(JAR) as z:
    names = z.namelist()
    classes = [n for n in names if n.endswith('.class')]
    tc_classes = [n for n in classes if n.startswith('thaumcraft/')]
    assets = [n for n in names if n.startswith('assets/') and not n.endswith('/')]
    tc_assets = [n for n in assets if n.startswith('assets/thaumcraft/')]
    sha256 = hashlib.sha256(JAR.read_bytes()).hexdigest()

    langs = {}
    for n in names:
        if n.startswith('assets/thaumcraft/lang/') and n.endswith('.lang'):
            langs[Path(n).stem] = parse_lang(z.read(n))

    en = langs['en_us']
    ru_original = langs['ru_ru']
    ru = dict(ru_original)
    for k in en.keys() - ru.keys():
        if k not in RU_PATCH:
            raise SystemExit(f'Missing explicit RU translation for {k}')
        ru[k] = RU_PATCH[k]

    lang_out = ROOT/'src/main/resources/assets/thaumcraft/lang'
    lang_out.mkdir(parents=True, exist_ok=True)
    for code, data in langs.items():
        if code == 'ru_ru': data = ru
        (lang_out/f'{code}.json').write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)+'\n', encoding='utf-8')

    research_files = [n for n in names if n.startswith('assets/thaumcraft/research/') and n.endswith('.json')]
    all_entries=[]
    per_file={}
    for n in research_files:
        doc=json.loads(z.read(n).decode('utf-8'))
        es=doc.get('entries',[])
        per_file[Path(n).name]=len(es)
        for e in es:
            ee=dict(e)
            ee['_file']=Path(n).name
            all_entries.append(ee)

cats=Counter(e.get('category') for e in all_entries)
meta=Counter(m for e in all_entries for m in (e.get('meta') or []))
req=Counter()
for e in all_entries:
    for s in e.get('stages') or []:
        for k in s:
            if k.startswith('required_'):
                req[k]+=1

research_manifest={
    'entry_count':len(all_entries),
    'stage_count':sum(len(e.get('stages') or []) for e in all_entries),
    'addenda_count':sum(len(e.get('addenda') or []) for e in all_entries),
    'categories':dict(sorted(cats.items())),
    'meta_counts':dict(sorted(meta.items())),
    'required_field_stage_counts':dict(sorted(req.items())),
    'files':dict(sorted(per_file.items())),
    'keys':[e['key'] for e in all_entries],
    'entries':all_entries,
}
(ROOT/'analysis/research_manifest.json').write_text(json.dumps(research_manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

loc_report={
    'english_keys':len(en),
    'russian_original_keys':len(ru_original),
    'russian_final_keys':len(ru),
    'missing_in_original_ru':sorted(en.keys()-ru_original.keys()),
    'missing_in_final_ru':sorted(en.keys()-ru.keys()),
    'extra_in_final_ru':sorted(ru.keys()-en.keys()),
    'patched_ru':RU_PATCH,
}
(ROOT/'analysis/localization_report.json').write_text(json.dumps(loc_report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

inventory={
    'jar':JAR.name,
    'sha256':sha256,
    'class_files':len(classes),
    'thaumcraft_class_files':len(tc_classes),
    'asset_files_all_namespaces':len(assets),
    'asset_files_thaumcraft_namespace':len(tc_assets),
    'research_entries':len(all_entries),
    'en_keys':len(en),
    'ru_keys_original':len(ru_original),
    'ru_keys_final':len(ru),
}
(ROOT/'analysis/jar_inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(inventory, ensure_ascii=False, indent=2))
