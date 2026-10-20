#!/usr/bin/env python3
"""تنبيه: لا تُستخدم مع تسجيلات Quran.Foundation (شروطهم تمنع التخزين/إعادة التوزيع خارج Content Sync؛ التلاوة تُبثّ أونلاين).
مخصّصة فقط لتسجيلات تملك ترخيصها صراحةً.

أداة إدارة الصوت (محلية/CI، ليست جزءًا من التطبيق): تضيف تلاوة كل آية إلى وحدة سورة
وتقيس مدتها الفعلية، فتصبح مدة كل آية هي ما يحرّك صور المشهد (التوقيتات في scenes.json نسبية 0..1).

الاستخدام:
  python3 tools/admin_audio.py <SURAH> <AUDIO_DIR> --reciter "اسم القارئ" [--license-url URL --license-verified]

AUDIO_DIR: ملف لكل آية باسم رقم الآية (1.mp3 أو 001.mp3 أو 114001.mp3 ...).
الحالة تبقى license_unverified ما لم تُمرَّر --license-verified مع --license-url (تحقق بشري من الترخيص).
لا تغيّر نص القرآن ولا التفسير. تُحدَّث بصمات module.json والملفات الصوتية في catalog.json.
"""
import argparse, hashlib, json, os, re, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOOT = os.path.join(ROOT, 'content', 'bootstrap')

def sha(b): return hashlib.sha256(b).hexdigest()

def duration_ms(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                         capture_output=True, text=True)
    if out.returncode != 0 or not out.stdout.strip():
        sys.exit(f'تعذر قياس مدة {path}: {out.stderr.strip()}')
    return int(round(float(out.stdout.strip()) * 1000))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('surah', type=int); ap.add_argument('audio_dir')
    ap.add_argument('--reciter', required=True)
    ap.add_argument('--license-url', default=''); ap.add_argument('--license-verified', action='store_true')
    ap.add_argument('--boot', default=BOOT)
    a = ap.parse_args()
    if a.license_verified and not a.license_url:
        sys.exit('--license-verified يتطلب --license-url')
    mdir = os.path.join(a.boot, 'modules', str(a.surah))
    mpath = os.path.join(mdir, 'module.json')
    mod = json.load(open(mpath, encoding='utf-8'))
    files = {}
    for f in os.listdir(a.audio_dir):
        m = re.match(r'^(?:\d{3})?0*(\d+)\.(mp3|m4a|ogg)$', f)
        if m: files[int(m.group(1))] = os.path.join(a.audio_dir, f)
    missing = [x['number'] for x in mod['ayahs'] if x['number'] not in files]
    if missing: sys.exit(f'ملفات صوت ناقصة للآيات: {missing}')
    os.makedirs(os.path.join(mdir, 'audio'), exist_ok=True)
    cat_path = os.path.join(a.boot, 'catalog.json')
    cat = json.load(open(cat_path, encoding='utf-8'))
    entry = next(m for m in cat['modules'] if m['surah'] == a.surah)
    entry['files'] = [f for f in entry['files'] if not f['path'].startswith(f'modules/{a.surah}/audio/')]
    status = 'licensed' if a.license_verified else 'license_unverified'
    for x in mod['ayahs']:
        src = files[x['number']]; ext = os.path.splitext(src)[1]
        rel = f"modules/{a.surah}/audio/{x['number']:03d}{ext}"
        shutil.copyfile(src, os.path.join(a.boot, rel))
        data = open(src, 'rb').read()
        x['audio'] = {'url': rel, 'durationMs': duration_ms(src), 'status': status, 'sha256': sha(data),
                      'reciter': a.reciter, 'licenseUrl': a.license_url}
        entry['files'].append({'path': rel, 'sha256': sha(data), 'bytes': len(data)})
    raw = (json.dumps(mod, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    open(mpath, 'wb').write(raw)
    for f in entry['files']:
        if f['path'] == f'modules/{a.surah}/module.json':
            f['sha256'], f['bytes'] = sha(raw), len(raw)
    entry['totalBytes'] = sum(f['bytes'] for f in entry['files'])
    open(cat_path, 'w', encoding='utf-8').write(json.dumps(cat, ensure_ascii=False, indent=2) + '\n')
    tot = sum(x['audio']['durationMs'] for x in mod['ayahs'])
    print(f'تمت إضافة {len(mod["ayahs"])} ملف صوت، المجموع {tot/1000:.1f} ث، الحالة: {status}')
    for x in mod['ayahs']: print(f"  آية {x['number']}: {x['audio']['durationMs']} ms")
    print('الصور تتبع هذه المدد تلقائيًا (توقيت اللقطات نسبي لمدة الآية).')

if __name__ == '__main__':
    main()
