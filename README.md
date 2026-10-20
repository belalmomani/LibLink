# الآفاق القرآنية — Quranic Horizons
رحلة بصرية في رحاب القرآن الكريم. تتغير المشاهد مع كل آية وفق تفسير موثوق، مع تلاوة الشيخ ياسر الدوسري (بعد التحقق من الترخيص).

## الحالة
المرحلة 1: الأساس + سورة الناس (معاينة قيد المراجعة). راجع `docs/STATUS.md`.

## البنية
- `app/` تطبيق أندرويد (Kotlin + Compose، بلا مكتبات غير لازمة).
- `content/bootstrap/` وحدات المحتوى (نص، تفسير، مشاهد) + `catalog.json` ببصمات SHA-256.
- `tools/build_content.py` يبني المحتوى من مصادر حرفية؛ `tools/validate_content.py` يتحقق منه.
- `.github/workflows/build.yml` تحقق المحتوى + اختبارات JVM + بناء APK (لا حاجة لـ Android Studio).

<<<<<<< HEAD
## Menu (like MGit)
Pull, Push, Add all to stage, Commit, Reset (HARD), Merge, Fetch, Rebase, Cherry Pick, Diff,
New File, New Directory, Add Remote, Remove Remote, Delete, Raw config, Config + in-app file browser.

## Cloud sync (library + settings)
1. Firebase Console → Authentication → Sign-in method → enable **Email/Password**.
2. Firebase Console → Firestore Database → create a database.
3. Add the rule from `firebase/firestore.rules` (merge it with existing rules if the project is shared).
4. In the app: Settings → Cloud sync → sign in with the same email on every device.
The GitHub token is synced only if you enable it, and it is encrypted with your passphrase before upload.
=======
## البناء
يتم على GitHub Actions: ادفع إلى المستودع وحمّل APK من Artifacts. محليًا: `gradle :app:testDebugUnitTest :app:assembleDebug` (Gradle 8.9، JDK 17).

## قواعد المحتوى
لا تعديل لنص القرآن؛ التفسير نقل حرفي مع النسبة والحالة؛ المشاهد تُصمَّم وتُراجَع مسبقًا؛ لا تُعلَّم سورة «جاهزة» قبل اكتمال النص والتفسير والمشاهد والتلاوة والاختبار. لا أسرار في المستودع.
>>>>>>> branch 'main' of https://github.com/belalmomani/LibLink.git
