#!/usr/bin/env python3
"""يقصّ لوحات المشاهد من الصورة المجمَّعة التي أرسلها صاحب المشروع (سورة الناس) إلى ملفات منفصلة.
- يمسح شارة الرقم (inpaint) ويقصّ شريط النص المرسوم داخل الصورة: نص الآية يُرسم دائمًا من نص Tanzil
  (وفي الصورة المرسلة خطأ إملائي في الآية 2: «مبلك»).
- الآية 6: لوحتان (الظل ثم الإنسان) تُلصقان في صورة واحدة؛ الربط الصحيح: الظل = الجنّة، الهمس البشري = الناس.
هذا مسار مؤقت: الدقة (~410px) لا تصلح للنسخة النهائية؛ تُستبدل بصور منفصلة عالية الدقة بلا نص ولا أرقام.
    python3 tools/slice_scenes.py COMPOSITE.png [OUT_DIR]
"""
import os, sys
import cv2, numpy as np
from PIL import Image

src = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '..', 'content', 'bootstrap', 'modules', '114', 'img')
os.makedirs(out, exist_ok=True)
im = Image.open(src).convert('RGB')

# (x0, y0, x1, y1) لكل لوحة في الصورة المجمَّعة، وارتفاع الجزء المحفوظ قبل شريط النص
PANELS = {
    'n1': ((0, 0, 415, 548), 440),
    'n2': ((419, 0, 837, 548), 440),
    'n3': ((841, 0, 1254, 548), 440),
    'n4': ((0, 552, 415, 1010), 362),
    'n5': ((419, 552, 837, 1010), 362),
    'n6a': ((841, 552, 1038, 1010), 362),
    'n6b': ((1042, 552, 1254, 1010), 362),
}
BADGE = {'n1', 'n2', 'n3', 'n4', 'n5', 'n6a'}   # لوحات عليها شارة رقم في الزاوية العليا اليسرى
crops = {}
for k, (box, keep_h) in PANELS.items():
    c = np.asarray(im.crop(box))
    if k in BADGE:
        mask = np.zeros(c.shape[:2], np.uint8)
        cv2.circle(mask, (47, 46), 37, 255, -1)
        c = cv2.inpaint(c[:, :, ::-1].copy(), mask, 5, cv2.INPAINT_TELEA)[:, :, ::-1]
    crops[k] = Image.fromarray(c).crop((0, 0, c.shape[1], keep_h))

for k in ('n1', 'n2', 'n3', 'n4', 'n5'):
    crops[k].save(os.path.join(out, f'{k}.jpg'), quality=95)
a, b = crops['n6a'], crops['n6b']
w = Image.new('RGB', (a.width + b.width, a.height))
w.paste(a, (0, 0)); w.paste(b, (a.width, 0))
w.save(os.path.join(out, 'n6.jpg'), quality=95)
print('saved to', out)
