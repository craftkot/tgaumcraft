#!/usr/bin/env python3
import json, glob, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
research=ROOT/'src/main/resources/data/thaumcraft/research'
entries=[]
for p in sorted(research.glob('*.json')):
    doc=json.loads(p.read_text(encoding='utf-8'))
    entries.extend(doc.get('entries',[]))

def js(s):
    return json.dumps(s, ensure_ascii=False)

def list_expr(xs):
    xs=xs or []
    return 'List.of(' + ', '.join(js(x) for x in xs) + ')' if xs else 'List.of()'

def meta_expr(xs):
    xs=xs or []
    return 'Set.of(' + ', '.join('ResearchMeta.'+x for x in xs) + ')' if xs else 'Set.of()'

def stages_expr(stages):
    if stages is None:
        return 'null'
    parts=[]
    for st in stages:
        # Only the four exact fields inspected by BETA26 progressResearch determine auto-skip.
        parts.append('new ResearchStageData(%s, %s, %s, %s, %d)' % (
            str(bool(st.get('required_craft'))).lower(),
            str(bool(st.get('required_item'))).lower(),
            str(bool(st.get('required_knowledge'))).lower(),
            str(bool(st.get('required_research'))).lower(),
            int(st.get('warp') or 0)))
    return 'List.of(' + ', '.join(parts) + ')'

def addenda_expr(addenda):
    if not addenda:
        return 'List.of()'
    parts=[]
    for ad in addenda:
        parts.append('new ResearchAddendumData(%s)' % list_expr(ad.get('required_research') or []))
    return 'List.of(' + ', '.join(parts) + ')'

out=[]
out.append('package dev.ehtid.thaumcraft.port.core;')
out.append('')
out.append('import java.util.List;')
out.append('import java.util.Set;')
out.append('')
out.append('/** Generated projection of all 148 BETA26 JSON research entries. Do not hand-edit. */')
out.append('public final class BuiltInResearchCatalog {')
out.append('    private BuiltInResearchCatalog() {}')
out.append('')
out.append('    public static ResearchCatalog create() {')
out.append('        ResearchCatalog c = new ResearchCatalog();')
for e in entries:
    out.append('        c.add(new ResearchEntryData(%s, %s, %s, %s, %s, %s, %s, List.of()));' % (
        js(e['key']), js(e['category']), meta_expr(e.get('meta')), stages_expr(e.get('stages')),
        list_expr(e.get('parents')), list_expr(e.get('siblings')), addenda_expr(e.get('addenda'))))
out.append('        return c;')
out.append('    }')
out.append('}')
path=ROOT/'src/main/java/dev/ehtid/thaumcraft/port/core/BuiltInResearchCatalog.java'
path.write_text('\n'.join(out)+'\n',encoding='utf-8')
print(f'wrote {path} with {len(entries)} entries')
