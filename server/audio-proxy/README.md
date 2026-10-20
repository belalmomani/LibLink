# خدمة وسيط التلاوة (Quran.Foundation)
- تحمل client_secret (لا يجوز وضعه في التطبيق) وتُرجع JSON بروابط الآيات فقط. لا تخزّن ملفات صوت.
- التثبيت: ارفع `audio.php` و`config.php` (من `config.example.php`) إلى مجلد على الاستضافة، ملفا `.htaccess` المرفقان (الجذر و`cache/`) يمنعان الوصول المباشر إلى `config.php` و`cache/`؛ ارفعهما أيضًا (ملفات الاسم الذي يبدأ بنقطة قد تكون مخفية في مدير الملفات).
- ضع في `allowed_recitations` أرقام التلاوات المعتمدة فقط. ثم في التطبيق: `AUDIO_API_BASE_URL` و`RECITATION_ID` و`AUDIO_ENABLED=true`.
- غير مختبرة: لا توجد بيانات اعتماد هنا. مسارات REST (`/recitations/{id}/by_chapter/{n}`, `/resources/recitations`) وأسماء الحقول يجب التأكد منها أول تشغيل. تنبيه: بعض الاستضافات المجانية تحجب cURL الصادر.
