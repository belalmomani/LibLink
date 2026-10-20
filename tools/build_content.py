#!/usr/bin/env python3
"""يبني محتوى التطبيق من المصادر الأصلية دون تعديل نصوصها، ويحسب بصمات الملفات.

المصادر (تُستنسخ محليًا ولا تُضمَّن في المستودع):
  QURAN_JSON  = مستودع risan/quran-json  (يحمل نسخة Tanzil حرفيًا + بيانات السور)
  TAFSIR_API  = مستودع spa5k/tafsir_api  (نصوص التفاسير)
الاستعمال:  python3 tools/build_content.py <QURAN_JSON_DIR> <TAFSIR_API_DIR>
"""
import hashlib, json, os, sys

SURAH = 114
Q, T = sys.argv[1], sys.argv[2]
OUT = os.path.join(os.path.dirname(__file__), '..', 'content', 'bootstrap')


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def write(path, obj):
    p = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    data = json.dumps(obj, ensure_ascii=False, indent=1, sort_keys=False).encode('utf-8')
    open(p, 'wb').write(data)
    return {'path': path, 'sha256': sha(data), 'bytes': len(data)}


tz = json.load(open(f'{Q}/data/tanzil/uthmani.json', encoding='utf-8'))
chapters = json.load(open(f'{Q}/data/tanzil/chapters.json', encoding='utf-8'))['chapters']
src = {s['path']: s for s in json.load(open(f'{Q}/data/meta/sources.json', encoding='utf-8'))}
tz_src = src['data/tanzil/uthmani.json']

# ترتيب التسليم المطلوب: عكس المصحف
DELIVERY = list(range(114, 0, -1))
surahs = [{'number': c['id'], 'nameAr': c['name'], 'nameEn': c['transliteration'],
           'meaning': c['translation'], 'revelation': c['type'], 'ayahCount': c['total_verses']} for c in chapters]
assert len(surahs) == 114 and sum(s['ayahCount'] for s in surahs) == 6236
write('surahs.json', {'formatVersion': 1, 'textSource': {'name': 'Tanzil Uthmani', 'url': tz_src['url'],
     'license': tz_src['license'], 'licenseUrl': tz_src['license_url'], 'sourceSha256': tz_src['sha256']},
     'deliveryOrder': DELIVERY, 'surahs': surahs})

verses = tz[str(SURAH)]
ch = next(c for c in chapters if c['id'] == SURAH)
assert len(verses) == ch['total_verses'] == 6
assert [v['verse'] for v in verses] == list(range(1, 7))
raw_lines = [v['text'] for v in verses]
# نسخة Tanzil تبدأ الآية الأولى من كل سورة (عدا الفاتحة) بالبسملة؛ نفصلها عرضًا فقط دون تغيير حرف
BASMALA = tz['1'][0]['text']
basmala_prefixed = raw_lines[0].startswith(BASMALA + ' ')
text_lines = list(raw_lines)
if basmala_prefixed:
    text_lines[0] = raw_lines[0][len(BASMALA) + 1:]
assert BASMALA + ' ' + text_lines[0] == raw_lines[0] or not basmala_prefixed

TAFSIRS = [
    ('saadi', 'ar-tafseer-al-saddi', 'تفسير السعدي (تيسير الكريم الرحمن)', 'عبد الرحمن بن ناصر السعدي',
     'https://qul.tarteel.ai/resources/tafsir/24'),
    ('ibn-kathir', 'ar-tafsir-ibn-kathir', 'تفسير القرآن العظيم', 'إسماعيل بن عمر بن كثير',
     'https://qul.tarteel.ai/resources/tafsir/22'),
    ('tabari', 'ar-tafsir-al-tabari', 'جامع البيان عن تأويل آي القرآن', 'محمد بن جرير الطبري',
     'https://qul.tarteel.ai/resources/tafsir/37'),
]
records = []
for sid, slug, book, author, url in TAFSIRS:
    files = [json.load(open(f'{T}/tafsir/{slug}/{SURAH}/{a}.json', encoding='utf-8')) for a in range(1, 7)]
    texts = {f['text'] for f in files}
    # هذه الطبعات الرقمية تخزّن نص السورة كاملًا لكل آية؛ نحتفظ به كما هو دون تقطيعه يدويًا
    notes = []
    if len(texts) != 1:
        # مصدر البيانات نفسه غير متسق بين ملفات الآيات: نعتمد نص الآية الأولى ونسجّل الاختلاف ليراجَع
        diff = [a for a in range(2, 7) if files[a - 1]['text'] != files[0]['text']]
        notes.append(f'نسخ الآيات {diff} تختلف عن نسخة الآية 1 في بيانات المصدر (حذف أقواس المحقق أو تكرار النص)؛ اعتُمدت نسخة الآية 1 كما وردت.')
    records.append({'id': sid, 'bookAr': book, 'authorAr': author, 'scope': 'surah', 'ayahs': [1, 2, 3, 4, 5, 6],
                    'text': files[0]['text'], 'dataset': 'spa5k/tafsir_api', 'origin': url,
                    'attribution': 'النص منقول حرفيًا من النسخة الرقمية دون تعديل، وتبقى فيه علامات المحقّق إن وُجدت.',
                    'sourceNotes': notes, 'licenseStatus': 'unverified', 'attributionReview': 'pending'})
# «التفسير الميسّر» (مجمع الملك فهد): سجل لكل آية، نص حرفي من النسخة الرقمية؛ هو الأساس الأول لربط المعنى بالمشهد
MUY_SRC = 'https://qul.tarteel.ai/resources/tafsir/38'
muy_records = []
for a in range(1, 7):
    f = json.load(open(f'{T}/tafsir/ar-tafsir-muyassar/{SURAH}/{a}.json', encoding='utf-8'))
    muy_records.append({'id': f'muyassar-{a}', 'bookAr': 'التفسير الميسّر', 'authorAr': 'نخبة من العلماء (مجمع الملك فهد لطباعة المصحف الشريف)',
                        'scope': 'ayah', 'ayahs': [a], 'text': f['text'], 'dataset': 'spa5k/tafsir_api', 'origin': MUY_SRC,
                        'attribution': 'النص منقول حرفيًا من النسخة الرقمية دون تعديل (وتتضمن آية 1 تمهيدًا عن اسم السورة ومقاصدها).',
                        'sourceNotes': [], 'licenseStatus': 'unverified', 'attributionReview': 'pending'})
# ملخص معاني الآيات كما أرسله صاحب المشروع (يذكر أنه من «التفسير الميسّر»). يُحفظ كما ورد دون تصحيح،
# ويختلف عن نص النسخة الرقمية للميسّر (انظر sourceNotes)، فتبقى نسبته قيد التحقق من الطبعة المطبوعة.
USER_BRIEF = {
 1: 'قل -أيها الرسول-: أعتصم وأستجير برب الناس، القادر وحده على دفع الشرور عنهم.',
 2: 'مالك جميع الناس ومدبر أمورهم، الذي له السلطان الكمل عليهم.',
 3: 'معبودهم الحق الذي لا معبود بحق سواه.',
 4: 'من شر الشيطان الذي يوسوس عند الغفلة، ويختفي وينقبض عند ذكر الله.',
 5: 'الذي يبث الشكوك والشرور في قلوب البشر.',
 6: 'وهذا الوسواس يكون من شياطين الجن، ويكون أيضاً من شياطين الإنس.',
}
user_records = [{'id': f'user-brief-{a}', 'bookAr': 'ملخص المعنى (مقدَّم من صاحب المشروع)', 'authorAr': 'يُنسب إلى التفسير الميسّر بعد التحقق',
                 'scope': 'ayah', 'ayahs': [a], 'text': USER_BRIEF[a], 'dataset': 'user-provided', 'origin': 'رسالة صاحب المشروع (19 أكتوبر 2026)',
                 'attribution': 'نص مقدَّم من صاحب المشروع على أنه من التفسير الميسّر؛ لا يطابق حرفيًا نص النسخة الرقمية، فلا يُنسب للمؤلفين قبل التحقق.',
                 'sourceNotes': ['كلمة «الكمل» في الآية 2 تبدو سهوًا عن «الكامل»؛ أُبقيت كما وردت إلى أن تُراجع.'] if a == 2 else [],
                 'licenseStatus': 'unverified', 'attributionReview': 'pending'} for a in range(1, 7)]
def user_quote(a):
    return USER_BRIEF[a]
records = user_records + muy_records + records
def muy_quote(a):
    t = muy_records[a - 1]['text']
    return t.split('[التفسير]')[1].strip() if '[التفسير]' in t else t.strip()
tafsir_meta = write('modules/114/tafsir.json', {'formatVersion': 1, 'surah': SURAH, 'records': records})

# ---------------------------------------------------------------------------
# المشاهد (formatVersion 2): المعنى هو الذي يحدّد ما يحدث في المشهد.
# لكل آية "نبضة" (beat) تحوي: المعنى المستخلص من التفسير (اقتباس حرفي + خلاصة تحريرية)،
# الحدث البصري الذي سيراه المستخدم، وسبب الملاءمة. والحدث يغيّر عناصر المشهد نفسها
# (ظهور/اختفاء/حركة/تشكّل)، لا الإضاءة ولا الكاميرا.
# كل مشهد رمزي: لا تجسيد لله ولا لنبي ولا لشيطان/جنّ؛ الوسوسة تُمثَّل بخيوط دخانية بلا صورة.
# ---------------------------------------------------------------------------
def quote(sub):
    full = next(r for r in records if r['id'] == 'saadi')['text']
    assert sub in full, sub
    return sub

def K(t, **sets):
    return {'t': t, 'set': sets}

REFUGE = {
 'id': 'n-refuge', 'ayahs': [1, 2, 3], 'kind': 'procedural', 'title': 'رب الناس ومالكهم وإلههم',
 'env': {'skyTop': '#050B1F', 'skyBottom': '#12306A', 'stars': 70, 'twinkle': 0.3,
         'glow': {'x': 0.5, 'y': 0.58, 'r': 0.6, 'color': '#D9A84F', 'alpha': 0.22},
         'ridges': [{'h': 0.46, 'color': '#07122B'}, {'h': 0.40, 'color': '#050E24'}], 'mist': 0.12, 'drift': 0.2},
 'actors': [
   {'id': 'man', 'kind': 'figure', 'x': 0.5, 'y': 0.97, 's': 1.2, 'init': {'a': 1.0, 'chest': 0.0}},
   {'id': 'shade', 'kind': 'shade', 'init': {'a': 0.0, 'r': 0.0}},
   {'id': 'dome', 'kind': 'dome', 'x': 0.5, 'y': 0.97, 's': 0.5, 'init': {'a': 1.0, 'draw': 0.0}},
   {'id': 'crowd', 'kind': 'crowd', 'n': 21, 'seed': 7, 'init': {'a': 0.0, 'gather': 0.0, 'rows': 0.0, 'bind': 0.0}},
   {'id': 'dim', 'kind': 'dimlights', 'n': 7, 'init': {'a': 0.0}},
   {'id': 'beam', 'kind': 'beam', 'x': 0.5, 'init': {'a': 0.0, 'h': 0.0}},
 ],
 'beats': [
  {'ayah': 1,
   'tafsirRecord': 'user-brief-1',
   'tafsirQuote': user_quote(1),
   'meaning': 'المتكلم يعتصم ويستجير برب الناس، القادر وحده على دفع الشرور عنهم.',
   'visualEvent': 'إنسان واحد مجهول الهوية يقف في الليل، وتزحف نحوه ظلال داكنة (الشرور)؛ فتُرسَم حوله قبّة نور حامية، وتُدفَع الظلال وتتراجع.',
   'rationale': 'الآية فيها اعتصام واستجارة، وفيها قدرة الله وحده على دفع الشرور؛ فالمشهد يُظهر الشرور مقبلة ثم مدفوعة بحماية تُرسَم حول المعتصم. لا يُصوَّر الرسول ولا الله تعالى.',
   'symbolic': True, 'symbolicNote': 'الظلال رمز للشرور، والقبّة رمز لحماية الله ودفعه؛ وليست صورة له. الإنسان شخص عام بلا هوية.',
   'keys': [K(0.0, shade={'a': 0.9}), K(0.4, shade={'r': 0.8}), K(0.58, dome={'draw': 1.0}), K(0.92, shade={'r': 0.0, 'a': 0.0})]},
  {'ayah': 2,
   'tafsirRecord': 'user-brief-2',
   'tafsirQuote': user_quote(2),
   'meaning': 'الله مالك جميع الناس ومدبّر أمورهم، وله السلطان الكامل عليهم.',
   'visualEvent': 'يظهر الناس موزَّعين في كل الأرجاء، ثم تنزل خيوط نور من نقطة واحدة فوق الإطار فتصل كلَّ إنسان.',
   'rationale': '«جميع الناس» تعني الكل لا فردًا؛ و«مدبّر أمورهم» تُرى في خيوط تصل كل واحد منهم من مصدر واحد، و«السلطان الكامل» في أن كل الخيوط تنتهي إلى مصدر واحد. المصدر خارج الإطار فلا يُصوَّر.',
   'symbolic': True, 'symbolicNote': 'الخيوط رمز للتدبير والسلطان، ومصدرها خارج الإطار عمدًا: لا تُصوَّر ذات الله.',
   'keys': [K(0.15, man={'a': 0.0}, dome={'a': 0.0}), K(0.35, crowd={'a': 1.0}), K(0.85, crowd={'bind': 1.0})]},
  {'ayah': 3,
   'tafsirRecord': 'user-brief-3',
   'tafsirQuote': user_quote(3),
   'meaning': 'الله معبودهم الحق، لا معبود بحق سواه.',
   'visualEvent': 'تظهر أضواء خافتة كثيرة في جهات متفرقة، ثم يرتفع شعاع واحد فتنطفئ الأضواء الأخرى ويصطفّ الناس صفوفًا متجهين إلى الشعاع وحده.',
   'rationale': '«لا معبود بحق سواه» تُرى في اختفاء كل الجهات الأخرى وبقاء جهة واحدة يتوجه إليها الجميع. تُفكّ الخيوط لأن الحدث انتقل من التدبير إلى العبادة.',
   'symbolic': True, 'symbolicNote': 'الشعاع جهة رمزية للعبادة، وليس صورة لله. الأضواء الخافتة رمز عام لغير الحق ولا تمثّل شيئًا بعينه.',
   'keys': [K(0.12, dim={'a': 1.0}, crowd={'bind': 0.0}), K(0.42, beam={'a': 1.0, 'h': 0.0}), K(0.58, beam={'h': 1.0}), K(0.7, dim={'a': 0.0}), K(0.92, crowd={'rows': 1.0})]},
 ]}

WHISPER = {
 'id': 'n-whisper', 'ayahs': [4, 5, 6], 'kind': 'procedural', 'title': 'الوسواس الخنّاس',
 'env': {'skyTop': '#04081A', 'skyBottom': '#0C1B44', 'stars': 45, 'twinkle': 0.25,
         'glow': {'x': 0.5, 'y': 0.62, 'r': 0.45, 'color': '#C9A24B', 'alpha': 0.16},
         'ridges': [{'h': 0.46, 'color': '#050E24'}, {'h': 0.40, 'color': '#040A1C'}], 'mist': 0.3, 'drift': 0.3},
 'actors': [
   {'id': 'man', 'kind': 'figure', 'x': 0.5, 'y': 0.97, 's': 1.2, 'init': {'a': 1.0, 'chest': 0.0}},
   {'id': 'lantern', 'kind': 'lantern', 'x': 0.6, 'y': 0.80, 'init': {'a': 1.0, 'g': 0.3}},
   {'id': 'w1', 'kind': 'wisps', 'n': 7, 'sx': 1.05, 'sy': 0.50, 'tx': 0.60, 'ty': 0.80, 'init': {'a': 0.0, 'r': 0.0}},
   {'id': 'w2', 'kind': 'wisps', 'n': 7, 'sx': 0.95, 'sy': 0.40, 'tx': 0.50, 'ty': 0.74, 'init': {'a': 0.0, 'r': 0.0}},
   {'id': 'smoke', 'kind': 'smoke', 'x': 0.13, 'y': 0.50, 'init': {'a': 0.0}},
   {'id': 'other', 'kind': 'figure', 'x': 0.88, 'y': 0.97, 's': 1.1, 'init': {'a': 0.0, 'chest': 0.0}},
   {'id': 'wU', 'kind': 'wisps', 'n': 7, 'sx': 0.18, 'sy': 0.55, 'tx': 0.47, 'ty': 0.74, 'init': {'a': 0.0, 'r': 0.0}},
   {'id': 'wP', 'kind': 'wisps', 'n': 7, 'sx': 0.84, 'sy': 0.74, 'tx': 0.53, 'ty': 0.74, 'init': {'a': 0.0, 'r': 0.0}},
 ],
 'beats': [
  {'ayah': 4,
   'tafsirRecord': 'user-brief-4',
   'tafsirQuote': user_quote(4),
   'meaning': 'من أذى الشيطان الذي يوسوس عند الغفلة ويختفي عند ذكر الله.',
   'visualEvent': 'والقنديل خافت (حال الغفلة) تقترب خيوط دخانية داكنة من إنسان؛ فإذا اشتدّ نور القنديل (ذكر الله) انكمشت الخيوط واختفت.',
   'rationale': 'الآية تذكر حالين: وسوسة عند الغفلة، واختفاء عند الذكر؛ فالحدث يُظهر الاقتراب مع خفوت النور ثم الاختفاء مع اشتداده، لا مجرد تغيّر لون.',
   'symbolic': True, 'symbolicNote': 'الخيوط الدخانية رمز للوسوسة غير المرئية، وليست صورة لشيطان. نور القنديل رمز للذكر.',
   'keys': [K(0.0, w1={'a': 0.9}), K(0.42, w1={'r': 0.9}), K(0.5, lantern={'g': 1.0}), K(0.92, w1={'r': 0.0, 'a': 0.0})]},
  {'ayah': 5,
   'tafsirRecord': 'user-brief-5',
   'tafsirQuote': user_quote(5),
   'meaning': 'الذي يبثّ الشر والشكوك في صدور الناس.',
   'visualEvent': 'تتجه الخيوط الداكنة إلى صدر الإنسان نفسه فتدخل منطقة الصدر وتظهر فيها ذرّات داكنة مترددة (الشكوك)، ويخفت القنديل.',
   'rationale': 'الآية تحدّد موضع البثّ (الصدور) وما يُبَثّ (الشر والشكوك)؛ فالحدث يصل إلى الصدر نفسه لا إلى محيط الإنسان كما في الآية 4.',
   'symbolic': True, 'symbolicNote': 'ما يجري في الصدر رمز لحديث النفس الخفي ولا يُصوَّر حرفيًا.',
   'keys': [K(0.0, w2={'a': 0.9}), K(0.6, w2={'r': 1.0}), K(0.72, man={'chest': 1.0}, lantern={'g': 0.3})]},
  {'ayah': 6,
   'tafsirRecord': 'user-brief-6',
   'tafsirQuote': user_quote(6),
   'meaning': 'من شياطين الجن والإنس.',
   'visualEvent': 'يظهر مصدران للوسوسة: دخان بلا شكل من جهة المجهول، وإنسان آخر من جهة أخرى؛ ويرسل كلٌّ منهما خيطه إلى الإنسان.',
   'rationale': 'الآية تُثبت مصدرين مختلفين؛ فتظهر عناصر جديدة (مصدر خفي بلا صورة ومصدر بشري) لم تكن في الآيتين السابقتين.',
   'symbolic': True, 'symbolicNote': 'الدخان بلا ملامح عمدًا: الجنّة غيب لا يُصوَّر. الإنسان الآخر شخص عام بلا هوية.',
   'keys': [K(0.0, w2={'a': 0.0, 'r': 0.0}), K(0.25, smoke={'a': 1.0}, other={'a': 1.0}), K(0.5, wU={'a': 0.9, 'r': 0.5}, wP={'a': 0.9, 'r': 0.5}), K(0.88, wU={'r': 1.0}, wP={'r': 1.0})]},
 ]}

procedural_scenes = [REFUGE, WHISPER]
for sc in procedural_scenes:
    sc['review'] = {'status': 'draft', 'note': 'مشهد إجرائي بديل (مرسوم بالكود)؛ غير مستعمل في الوحدة الحالية ومحفوظ في content/drafts.'}
    sc['transition'] = {'type': 'crossfade', 'ms': 1600}
drafts = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'content', 'drafts')
os.makedirs(drafts, exist_ok=True)
open(os.path.join(drafts, 'an-nas-procedural-scenes.json'), 'w', encoding='utf-8').write(
    json.dumps({'formatVersion': 2, 'surah': SURAH, 'scenes': procedural_scenes}, ensure_ascii=False, indent=1))

# ---------- المشهد المعتمد حاليًا: صورة لكل آية (قصّتها tools/slice_scenes.py من لوحة صاحب المشروع) ----------
from PIL import Image
IMG_DIR = os.path.join(OUT, 'modules', '114', 'img')
img_files, img_info = [], {}
for i in range(1, 7):
    rel = f'modules/114/img/n{i}.jpg'
    data = open(os.path.join(OUT, rel), 'rb').read()
    w_, h_ = Image.open(os.path.join(OUT, rel)).size
    img_files.append({'path': rel, 'sha256': sha(data), 'bytes': len(data)})
    img_info[i] = (rel, w_, h_)
MIN_W = 1080
low_res = [i for i, (_, w_, h_) in img_info.items() if w_ < MIN_W]

def IMG(i):
    rel, w_, h_ = img_info[i]
    return {'id': f'img{i}', 'kind': 'image', 'file': rel, 'init': {'a': 0.0, 'z': 0.0}}

def shot(i, prev):
    keys = [K(0.2, **{f'img{i}': {'a': 1.0}})] if prev is None else [K(0.2, **{f'img{prev}': {'a': 0.0}, f'img{i}': {'a': 1.0}})]
    keys.append(K(0.95, **{f'img{i}': {'z': 1.0}}))
    return keys

IMG_BEATS = {
 1: dict(meaning='المتكلم يعتصم ويستجير برب الناس، القادر وحده على دفع الشرور عنهم.',
         visualEvent='رجل مجهول الهوية يجلس على سجادة في ليل هادئ تحت الهلال، يرفع يديه بالدعاء ومعه قنديل، والقرية الوادعة أمامه.',
         rationale='رفع اليدين بالدعاء في الليل هو صورة الالتجاء والاستجارة. لا يُصوَّر الرسول، والرجل مرسوم من الخلف بلا هوية.',
         symbolic=False, symbolicNote='', gap='دفع الشرور (قدرة الله وحده) مفهوم من السياق ولا تُظهره اللقطة.'),
 2: dict(meaning='الله مالك جميع الناس ومدبّر أمورهم، وله السلطان الكامل عليهم.',
         visualEvent='سوق عامر بجميع أصناف الناس يمضون في شؤونهم، وتعلو المدينة قلعة على التل في ضوء الغروب.',
         rationale='«جميع الناس» ظاهر في الحشد المتنوع، و«مدبّر أمورهم» في حياتهم اليومية وشؤونهم، و«السلطان» في القلعة المشرفة على المدينة.',
         symbolic=True, symbolicNote='القلعة إشارة رمزية إلى السلطان، وليست تصويرًا لذات الله.', gap=''),
 3: dict(meaning='الله معبودهم الحق، لا معبود بحق سواه.',
         visualEvent='رجل مجهول الهوية ساجد في مسجد، وأشعة ضوء تنساب من النوافذ العالية نحو المصلّى.',
         rationale='السجود أخصّ صور العبادة، وهو لله وحده؛ والأشعة تعطي جلال المكان ولا تُصوِّر ذات الله.',
         symbolic=True, symbolicNote='الأشعة تعبير عن السكينة والجلال، وليست صورة لله.', gap=''),
 4: dict(meaning='الاستعاذة من شر الشيطان الذي يوسوس عند الغفلة ويختفي وينقبض عند ذكر الله.',
         visualEvent='رجل شارد يضع يده على فمه، وخلفه شكل دخاني داكن يميل إلى أذنه (الوسوسة عند الغفلة).',
         rationale='الصورة تُظهر الجزء الأول من الآية: الوسوسة عند الغفلة.',
         symbolic=True, symbolicNote='الشكل الدخاني تمثيل رمزي للوسوسة الخفية، وليس صورة لحقيقة الشيطان.',
         gap='«ويختفي وينقبض عند ذكر الله» غير مُصوَّر؛ يلزم لقطة ثانية (الرجل يذكر الله فينحسر الدخان).'),
 5: dict(meaning='الذي يبثّ الشكوك والشرور في قلوب البشر.',
         visualEvent='رجل يضع يده على صدره وقد تسلّل دخان داكن متوهّج إلى موضع القلب.',
         rationale='وضع اليد على الصدر وتسلّل الدخان إلى موضع القلب يصوّران بثّ الشكوك والشرور في الصدور.',
         symbolic=True, symbolicNote='الدخان في الصدر تمثيل رمزي لحديث النفس الخفي.', gap=''),
 6: dict(meaning='هذا الوسواس يكون من شياطين الجن، ويكون أيضًا من شياطين الإنس.',
         visualEvent='لوحتان متجاورتان: يسارًا شكل دخاني خلف رجل (الجنّ)، ويمينًا رجل يهمس في أذن آخر (الإنس).',
         rationale='الآية تجمع مصدرين: خفيّ (الجنّة) وبشري (الناس)؛ فاللقطة تضع المصدرين جنبًا إلى جنب.',
         symbolic=True, symbolicNote='الشكل الدخاني تمثيل رمزي، والهمس البشري تمثيل مباشر.',
         gap='ترتيب الصورة الأصلية يضع «من الجنة» على لوحة الهمس البشري و«والناس» على لوحة الظل؛ اعتُمد الربط الصحيح (الظل = الجنّة، الهمس البشري = الناس).'),
}
img_beats = []
for i in range(1, 7):
    d = IMG_BEATS[i]
    img_beats.append({'ayah': i, 'tafsirRecord': f'user-brief-{i}', 'tafsirQuote': user_quote(i), 'meaning': d['meaning'],
                      'visualEvent': d['visualEvent'], 'rationale': d['rationale'], 'symbolic': d['symbolic'],
                      'symbolicNote': d['symbolicNote'], 'gap': d['gap'], 'keys': shot(i, None if i == 1 else i - 1)})
IMAGES_SCENE = {
 'id': 'n-images', 'ayahs': [1, 2, 3, 4, 5, 6], 'kind': 'image', 'title': 'سورة الناس — لقطة لكل آية',
 'env': {'skyTop': '#050B1F', 'skyBottom': '#050B1F', 'stars': 0, 'twinkle': 0.0, 'ridges': [], 'mist': 0.0, 'drift': 0.0},
 'actors': [IMG(i) for i in range(1, 7)], 'beats': img_beats,
 'review': {'status': 'draft', 'note': 'صور مقدَّمة من صاحب المشروع (لوحة مجمَّعة ~1254px)؛ مسودة بدقة منخفضة تنتظر الأصول عالية الدقة.'},
 'transition': {'type': 'crossfade', 'ms': 1600}}
scenes = [IMAGES_SCENE]
scene_meta = write('modules/114/scenes.json', {'formatVersion': 2, 'surah': SURAH, 'scenes': scenes})

# التلاوة: رابط ملف كل آية؛ لا تُضمَّن الملفات. الترخيص غير موثّق فتبقى معطّلة افتراضيًا.
AUDIO_BASE = 'https://everyayah.com/data/Yasser_Ad-Dussary_128kbps'
ayahs = [{'number': v['verse'], 'text': text_lines[v['verse'] - 1],
          'sceneId': next(s['id'] for s in scenes if v['verse'] in s['ayahs']),
          'audio': {'url': f"{AUDIO_BASE}/{SURAH:03d}{v['verse']:03d}.mp3", 'durationMs': None, 'status': 'license_unverified'}}
         for v in verses]
module = {'formatVersion': 1, 'surah': SURAH, 'moduleVersion': '0.1.0',
          'nameAr': ch['name'], 'ayahCount': ch['total_verses'],
          'review': {'status': 'draft', 'blockers': [
              'ترخيص التلاوة غير موثّق: روابط EveryAyah بلا ترخيص معلن، وملف m4a الذي أرسلته بلا بيانات قارئ أو ترخيص (وسوم الملف تدل على تنزيل من منصة بث)؛ لا يُعاد توزيعه قبل التحقق كتابيًا',
              'مراجعة نسبة نصوص التفاسير وحقوقها (attributionReview = pending)',
              'مراجعتك البصرية للقطات الست (review.status = draft)',
              *(['دقة صور المشاهد منخفضة (%s): قُصّت من لوحة مجمَّعة ~1254px؛ المطلوب صور منفصلة بعرض ≥ %d px بلا نص ولا أرقام' % ('، '.join('n%d=%dx%d' % (i, img_info[i][1], img_info[i][2]) for i in low_res), MIN_W)] if low_res else []),
              'نص الآية داخل الصور المرسلة غير صالح (آية 2 مكتوبة «مبلك»)؛ قُصّ، ويُرسم نص الآية من Tanzil فقط — الصور النهائية بلا أي نص',
              'لقطة الآية 4 تُظهر الوسوسة فقط، و«يختفي عند ذكر الله» غير مصوَّر (لقطة ثانية مطلوبة)',
              'مصدر الصور وحقوقها: صور مولَّدة قدّمها صاحب المشروع؛ يلزم توثيق الأداة وشروط الاستخدام قبل التوزيع',
              'هل ملف الآية الأولى في التسجيل يتضمن البسملة؟ يلزم الاستماع والتحقق قبل ضبط البداية',
              'التوقيت: اقتُرحت نقاط تقسيم آلية لملف m4a (7 مقاطع) في docs/audio/an-nas-cues.draft.json، لكن لا يُعرف أي مقطع هو أي آية (وهل الأول بسملة) قبل استماعك؛ ومدد الملفات في module.json ما زالت null']},
          'basmala': BASMALA if basmala_prefixed else None,
          'text': {'source': 'Tanzil Uthmani', 'sha256OfSourceLines': sha('\n'.join(raw_lines).encode('utf-8')), 'note': 'سطر الآية 1 في المصدر يبدأ بالبسملة وقد فُصلت عرضًا فقط'},
          'ayahs': ayahs}
mod_meta = write('modules/114/module.json', module)

files = [mod_meta, tafsir_meta, scene_meta] + img_files
catalog = {'formatVersion': 1, 'appMinVersion': 1, 'modules': [
    {'surah': SURAH, 'version': '0.1.0', 'status': 'draft', 'dir': 'modules/114', 'files': files,
     'totalBytes': sum(f['bytes'] for f in files)}]}
write('catalog.json', catalog)
print('OK', json.dumps({f['path']: f['bytes'] for f in files}))
