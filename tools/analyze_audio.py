#!/usr/bin/env python3
"""يقترح نقاط تقسيم تلاوة سورة إلى آيات بتحليل طاقة الصوت (فجوات الصمت/الخفوت).
المخرج مقترح غير موثّق: لا يُعتمد قبل الاستماع ومطابقة كل مقطع بآيته.
    python3 tools/analyze_audio.py FILE.m4a [out.json]
يتطلب ffmpeg وnumpy.
"""
import json, subprocess, sys, tempfile, os, wave
import numpy as np

src = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else None
tmp = os.path.join(tempfile.mkdtemp(), 'a.wav')
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', src, '-ac', '1', '-ar', '16000', tmp], check=True)
w = wave.open(tmp)
x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
sr = 16000
hop = int(sr * 0.05)
env = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2) + 1e-12) for i in range(0, len(x) - hop, hop)])
db = 20 * np.log10(env + 1e-9)
dur = len(x) / sr
voiced = db[db > -30]
ref = float(np.median(voiced))
THRESH = ref - 10      # فجوة = خفوت بمقدار 10 ديسيبل عن المستوى المعتاد
MIN_LEN = 0.25         # ثانية
gaps, i = [], 0
while i < len(db):
    if db[i] < THRESH:
        j = i
        while j < len(db) and db[j] < THRESH:
            j += 1
        if (j - i) * 0.05 >= MIN_LEN and i * 0.05 > 0.2 and j * 0.05 < dur - 0.2:
            gaps.append((round(i * 0.05, 2), round(j * 0.05, 2)))
        i = j
    else:
        i += 1
cuts = [round((a + b) / 2, 2) for a, b in gaps]
bounds = [0.0] + cuts + [round(dur, 2)]
segs = [{'index': k + 1, 'startMs': int(bounds[k] * 1000), 'endMs': int(bounds[k + 1] * 1000)} for k in range(len(bounds) - 1)]
res = {'file': os.path.basename(src), 'durationMs': int(dur * 1000), 'referenceDb': round(ref, 1), 'thresholdDb': round(THRESH, 1),
       'gaps': gaps, 'segments': segs, 'status': 'proposed-unverified',
       'note': 'مقترح آلي من الطاقة الصوتية فقط. لا يُعرف أي مقطع هو أي آية قبل الاستماع.'}
txt = json.dumps(res, ensure_ascii=False, indent=2)
print(txt)
if out:
    open(out, 'w', encoding='utf-8').write(txt)
