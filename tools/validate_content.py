#!/usr/bin/env python3
"""يتحقق من وحدات المحتوى: البصمات، عدد الآيات وترتيبها، تغطية المشاهد، وحالة الاعتماد.
اختياري: تمرير مجلد quran-json ليطابق النص حرفيًا مع نسخة Tanzil:
    python3 tools/validate_content.py [QURAN_JSON_DIR]
"""
import hashlib, json, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'content', 'bootstrap')
errors, warnings = [], []


def image_size(path):
    """أبعاد JPEG/PNG بلا مكتبات خارجية (يعمل على الـCI دون تثبيت شيء)."""
    import struct
    b = open(path, 'rb').read()
    if b[:8] == b'\x89PNG\r\n\x1a\n':
        return struct.unpack('>II', b[16:24])
    if b[:2] == b'\xff\xd8':
        i = 2
        while i + 9 < len(b):
            if b[i] != 0xFF:
                i += 1
                continue
            m = b[i + 1]
            if m in (0xD8, 0x01) or 0xD0 <= m <= 0xD7:
                i += 2
                continue
            ln = struct.unpack('>H', b[i + 2:i + 4])[0]
            if m in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                h, w = struct.unpack('>HH', b[i + 5:i + 9])
                return w, h
            i += 2 + ln
    raise ValueError('صيغة صورة غير مدعومة: ' + path)


def load(rel):
    return json.load(open(os.path.join(ROOT, rel), encoding='utf-8'))


def err(m):
    errors.append(m)


# معاملات كل نوع عنصر؛ والبنيوية منها هي التي تُحسب تغيّرًا دلاليًا (ظهور/حركة/تشكّل).
# الإضاءة (مثل g في القنديل) ليست بنيوية ولا تكفي لتبرير مشهد.
KIND_PARAMS = {
    'crowd': {'a', 'gather', 'rows', 'bind'}, 'dome': {'a', 'draw'}, 'starOrder': {'a', 'order'},
    'beam': {'a', 'h'}, 'figure': {'a', 'chest'}, 'lantern': {'a', 'g'},
    'wisps': {'a', 'r'}, 'smoke': {'a'}, 'shade': {'a', 'r'}, 'dimlights': {'a'}, 'image': {'a', 'z'},
}
STRUCTURAL = {
    'crowd': {'a', 'gather', 'rows', 'bind'}, 'dome': {'a', 'draw'}, 'starOrder': {'a', 'order'},
    'beam': {'a', 'h'}, 'figure': {'a', 'chest'}, 'lantern': {'a'},
    'wisps': {'a', 'r'}, 'smoke': {'a'}, 'shade': {'a', 'r'}, 'dimlights': {'a'}, 'image': {'a'},
}


def check_scene_semantics(n, scene, tafsir, err):
    by_id = {t['id']: t for t in tafsir}
    sid = scene['id']
    actors = {a['id']: a for a in scene.get('actors', [])}
    state = {i: dict(a.get('init', {})) for i, a in actors.items()}
    for i, a in actors.items():
        if a['kind'] not in KIND_PARAMS:
            err(f'{sid}: نوع عنصر غير معروف {a["kind"]}')
        for k in state[i]:
            if k not in KIND_PARAMS.get(a['kind'], set()):
                err(f'{sid}: معامل غير معروف {k} في {i}')
    beats = {b['ayah']: b for b in scene.get('beats', [])}
    if set(beats) != set(scene['ayahs']):
        err(f'{sid}: نبضات المشهد لا تغطي آياته بالضبط')
    prev_changes = None
    for ay in sorted(scene['ayahs']):
        b = beats.get(ay)
        if not b:
            continue
        for f in ('meaning', 'visualEvent', 'rationale', 'tafsirQuote'):
            if not str(b.get(f, '')).strip():
                err(f'{sid} آية {ay}: الحقل {f} فارغ — لا يُعتمد مشهد بلا ربط بالمعنى')
        rec = by_id.get(b.get('tafsirRecord', ''))
        if rec is None:
            err(f'{sid} آية {ay}: سجل التفسير المرجعي غير موجود ({b.get("tafsirRecord")})')
        elif ay not in rec.get('ayahs', []) or (b.get('tafsirQuote') and b['tafsirQuote'] not in rec['text']):
            err(f'{sid} آية {ay}: الاقتباس ليس حرفيًا من سجل التفسير المرجعي أو السجل لا يخص الآية')
        before = {i: dict(v) for i, v in state.items()}
        last_t = -1
        changes = set()
        for key in b.get('keys', []):
            if not (0 <= key['t'] <= 0.95) or key['t'] < last_t:
                err(f'{sid} آية {ay}: أزمنة المفاتيح يجب أن تتصاعد ضمن 0..0.95')
            last_t = key['t']
            for aid, st in key['set'].items():
                if aid not in actors:
                    err(f'{sid} آية {ay}: عنصر غير معرّف {aid}')
                    continue
                for k, v in st.items():
                    if k not in KIND_PARAMS[actors[aid]['kind']]:
                        err(f'{sid} آية {ay}: معامل {k} غير صالح لـ {aid}')
                    state[aid][k] = v
            # التغيّر يُقاس عند كل مفتاح (قد يقترب العنصر ثم يتراجع ضمن الآية نفسها)
            for i, a in actors.items():
                for k in STRUCTURAL[a['kind']]:
                    if abs(state[i].get(k, 0) - before[i].get(k, 0)) >= 0.5:
                        changes.add((i, k))
        if not changes:
            err(f'{sid} آية {ay}: لا تغيّر دلاليًا (ظهور/حركة/تشكّل). تغيير الضوء أو الحركة الشكلية لا يكفي')
        elif prev_changes is not None and changes == prev_changes and not b.get('continuity'):
            err(f'{sid} آية {ay}: نفس حدث الآية السابقة دون تبرير استمرار (continuity)')
        prev_changes = changes
        if b.get('symbolic') and not str(b.get('symbolicNote', '')).strip():
            err(f'{sid} آية {ay}: تمثيل رمزي بلا توضيح (symbolicNote)')


surahs = load('surahs.json')
by_num = {s['number']: s for s in surahs['surahs']}
if len(by_num) != 114 or sum(s['ayahCount'] for s in by_num.values()) != 6236:
    err('surahs.json: يجب أن يضم 114 سورة و6236 آية')
if surahs['deliveryOrder'] != list(range(114, 0, -1)):
    err('deliveryOrder ليس عكس ترتيب المصحف')

catalog = load('catalog.json')
tz = None
if len(sys.argv) > 1:
    tz = json.load(open(os.path.join(sys.argv[1], 'data', 'tanzil', 'uthmani.json'), encoding='utf-8'))

for m in catalog['modules']:
    n = m['surah']
    for f in m['files']:
        data = open(os.path.join(ROOT, f['path']), 'rb').read()
        if hashlib.sha256(data).hexdigest() != f['sha256'] or len(data) != f['bytes']:
            err(f'{f["path"]}: البصمة أو الحجم لا يطابق الكتالوج')
    mod = load(f'{m["dir"]}/module.json')
    scenes = {s['id']: s for s in load(f'{m["dir"]}/scenes.json')['scenes']}
    tafsir = load(f'{m["dir"]}/tafsir.json')['records']
    want = by_num[n]['ayahCount']
    nums = [a['number'] for a in mod['ayahs']]
    if nums != list(range(1, want + 1)):
        err(f'سورة {n}: ترتيب/عدد الآيات غير صحيح')
    for a in mod['ayahs']:
        if a['sceneId'] not in scenes:
            err(f'سورة {n} آية {a["number"]}: مشهد غير موجود')
        if not a['text'].strip():
            err(f'سورة {n} آية {a["number"]}: نص فارغ')
    listed = {f['path']: f for f in m['files']}
    low_res = False
    for sc in scenes.values():
        check_scene_semantics(n, sc, tafsir, err)
        for a_ in sc.get('actors', []):
            if a_['kind'] != 'image':
                continue
            f_ = a_.get('file', '')
            if f_ not in listed:
                err(f'{sc["id"]}: صورة {a_["id"]} غير مدرجة في الكتالوج ({f_})')
                continue
            w_, h_ = image_size(os.path.join(ROOT, f_))
            if w_ < 1080:
                low_res = True
    if low_res and m['status'] == 'approved':
        err(f'سورة {n}: صور منخفضة الدقة (< 1080px عرضًا) في وحدة معلنة approved')
    if low_res and not any('دقة' in b for b in mod['review'].get('blockers', [])):
        err(f'سورة {n}: صور منخفضة الدقة بلا معوّق مسجّل في module.json')
    covered = {x for s in scenes.values() for x in s['ayahs']}
    if covered != set(nums):
        err(f'سورة {n}: المشاهد لا تغطي كل الآيات بالضبط')
    if not tafsir:
        err(f'سورة {n}: لا تفسير')
    for t in tafsir:
        if not t.get('text') or not t.get('origin') or not t.get('authorAr'):
            err(f'سورة {n}: سجل تفسير ناقص ({t.get("id")})')
    # بوابة الاعتماد: لا تُعلن السورة مكتملة وفيها معوّقات
    status = m['status']
    pending = (mod['review'].get('blockers') or []) or any(s['review']['status'] != 'approved' for s in scenes.values()) \
        or any(a['audio']['status'] != 'verified' for a in mod['ayahs']) or any(t['attributionReview'] != 'approved' for t in tafsir)
    if status == 'approved' and pending:
        err(f'سورة {n}: معلنة approved وفيها معوّقات')
    if status != 'approved' and not pending:
        warnings.append(f'سورة {n}: لا معوّقات؛ يمكن اعتمادها')
    # مطابقة النص مع المصدر
    raw_ok = mod['text']['sha256OfSourceLines']
    lines = [a['text'] for a in mod['ayahs']]
    if mod.get('basmala'):
        lines[0] = mod['basmala'] + ' ' + lines[0]
    if hashlib.sha256('\n'.join(lines).encode('utf-8')).hexdigest() != raw_ok:
        err(f'سورة {n}: النص لا يطابق بصمة المصدر')
    if tz is not None and lines != [v['text'] for v in tz[str(n)]]:
        err(f'سورة {n}: النص لا يطابق Tanzil حرفيًا')

for w in warnings:
    print('تنبيه:', w)
if errors:
    print('فشل التحقق:')
    for e in errors:
        print(' -', e)
    sys.exit(1)
print('تحقق المحتوى: سليم (%d وحدة)' % len(catalog['modules']))
