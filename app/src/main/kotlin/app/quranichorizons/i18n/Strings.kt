package app.quranichorizons.i18n

/**
 * نصوص الواجهة فقط (أزرار وعناوين). ليست نصًّا قرآنيًا ولا تفسيرًا.
 * ترجمات الواجهة هنا مسودات تحريرية تحتاج مراجعة متحدثين أصليين قبل الاعتماد.
 */
object Strings {
    fun get(lang: Lang, key: String, vararg args: Any): String {
        var s = table[key]?.get(lang) ?: table[key]?.get(Lang.AR) ?: key
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }

    private fun e(en: String, zh: String, hi: String, es: String, ar: String, fr: String, bn: String) =
        mapOf(Lang.EN to en, Lang.ZH to zh, Lang.HI to hi, Lang.ES to es, Lang.AR to ar, Lang.FR to fr, Lang.BN to bn)

    val table: Map<String, Map<Lang, String>> = mapOf(
        "tagline" to e("A visual journey through the Holy Quran", "在古兰经中的视觉之旅", "पवित्र क़ुरआन की एक दृश्य यात्रा",
            "Un viaje visual por el Sagrado Corán", "رحلة بصرية في رحاب القرآن الكريم", "Un voyage visuel dans le Saint Coran", "পবিত্র কুরআনের এক দৃশ্যযাত্রা"),
        "continue" to e("Continue", "继续", "जारी रखें", "Continuar", "متابعة", "Continuer", "চালিয়ে যান"),
        "surahs" to e("Surahs", "章", "सूरह", "Suras", "السور", "Sourates", "সূরা"),
        "settings" to e("Settings & content status", "设置与内容状态", "सेटिंग्स और सामग्री की स्थिति",
            "Ajustes y estado del contenido", "الإعدادات وحالة المحتوى", "Réglages et état du contenu", "সেটিংস ও বিষয়বস্তুর অবস্থা"),
        "settingsTitle" to e("Settings", "设置", "सेटिंग्स", "Ajustes", "الإعدادات", "Réglages", "সেটিংস"),
        "back" to e("Back", "返回", "वापस", "Atrás", "رجوع", "Retour", "ফিরে যান"),
        "ayahsCount" to e("{0} verses", "{0} 节", "{0} आयतें", "{0} versículos", "{0} آية", "{0} versets", "{0}টি আয়াত"),
        "surahNamed" to e("Surah {0}", "第{0}章", "सूरह {0}", "Sura {0}", "سورة {0}", "Sourate {0}", "সূরা {0}"),
        "ayahNo" to e("Verse {0}", "第{0}节", "आयत {0}", "Versículo {0}", "الآية {0}", "Verset {0}", "আয়াত {0}"),
        "inPrep" to e("In preparation", "准备中", "तैयारी में", "En preparación", "قيد الإعداد", "En préparation", "প্রস্তুতি চলছে"),
        "preview" to e("Preview — under review", "预览——审核中", "पूर्वावलोकन — समीक्षाधीन", "Vista previa — en revisión", "معاينة — قيد المراجعة", "Aperçu — en cours de relecture", "প্রিভিউ — পর্যালোচনাধীন"),
        "ready" to e("Ready", "已就绪", "तैयार", "Lista", "جاهزة", "Prête", "প্রস্তুত"),
        "start" to e("Start the journey", "开始旅程", "यात्रा शुरू करें", "Comenzar el viaje", "ابدأ الرحلة", "Commencer le voyage", "যাত্রা শুরু করুন"),
        "startPreview" to e("Start preview", "开始预览", "पूर्वावलोकन शुरू करें", "Iniciar vista previa", "ابدأ المعاينة", "Lancer l'aperçu", "প্রিভিউ শুরু করুন"),
        "prev" to e("Previous", "上一节", "पिछली", "Anterior", "السابقة", "Précédent", "আগের"),
        "next" to e("Next", "下一节", "अगली", "Siguiente", "التالية", "Suivant", "পরের"),
        "play" to e("Play", "播放", "चलाएँ", "Reproducir", "تشغيل", "Lecture", "চালান"),
        "pause" to e("Pause", "暂停", "रोकें", "Pausa", "إيقاف مؤقت", "Pause", "বিরতি"),
        "meaningBtn" to e("Meaning of the verse & why this scene", "经文含义与场景依据", "आयत का अर्थ और दृश्य का कारण",
            "Significado del versículo y razón de la escena", "معنى الآية وسبب المشهد", "Sens du verset et raison de la scène", "আয়াতের অর্থ ও দৃশ্যের কারণ"),
        "replay" to e("Replay", "重播", "फिर से चलाएँ", "Repetir", "إعادة الرحلة", "Rejouer", "আবার চালান"),
        "toSurahs" to e("Back to surahs", "返回章列表", "सूरह सूची पर लौटें", "Volver a las suras", "العودة إلى السور", "Retour aux sourates", "সূরার তালিকায় ফিরুন"),
        "finished" to e("Surah {0} is finished", "第{0}章已结束", "सूरह {0} समाप्त हुई", "Terminó la sura {0}", "انتهت سورة {0}", "Fin de la sourate {0}", "সূরা {0} শেষ হয়েছে"),
        "displaySettings" to e("Display settings", "显示设置", "प्रदर्शन सेटिंग्स", "Ajustes de pantalla", "إعدادات العرض", "Réglages d'affichage", "প্রদর্শন সেটিংস"),
        "done" to e("Done", "完成", "ठीक है", "Hecho", "تم", "OK", "সম্পন্ন"),
        "reduceMotion" to e("Reduce motion", "减少动态效果", "गति कम करें", "Reducir movimiento", "تقليل الحركة", "Réduire les animations", "গতি কমান"),
        "textSize" to e("Verse text size: {0}%", "经文字号：{0}%", "आयत के पाठ का आकार: {0}%", "Tamaño del texto: {0}%", "حجم نص الآية: {0}٪", "Taille du texte : {0} %", "আয়াতের লেখার আকার: {0}%"),
        "pace" to e("Seconds per verse in reading mode: {0}", "阅读模式每节停留秒数：{0}", "पठन मोड में प्रति आयत सेकंड: {0}",
            "Segundos por versículo en modo lectura: {0}", "مدة بقاء كل آية في وضع القراءة: {0} ث", "Secondes par verset en mode lecture : {0}", "পঠন মোডে প্রতি আয়াতের সময় (সেকেন্ড): {0}"),
        "language" to e("Language", "语言", "भाषा", "Idioma", "اللغة", "Langue", "ভাষা"),
        "langAuto" to e("Device language (automatic)", "跟随设备语言（自动）", "डिवाइस की भाषा (स्वचालित)", "Idioma del dispositivo (automático)", "لغة الجهاز (تلقائي)", "Langue de l'appareil (automatique)", "ডিভাইসের ভাষা (স্বয়ংক্রিয়)"),
        "langNote" to e("The language applies to the app and the translation of meanings. The Quranic text always stays in Arabic.",
            "语言适用于应用界面和含义翻译。古兰经经文始终保持阿拉伯语原文。",
            "भाषा ऐप और अर्थ के अनुवाद पर लागू होती है। क़ुरआन का पाठ हमेशा अरबी में ही रहता है।",
            "El idioma se aplica a la app y a la traducción de los significados. El texto coránico siempre permanece en árabe.",
            "تنطبق اللغة على التطبيق وترجمة المعاني فقط. نص القرآن يبقى دائمًا بالعربية دون ترجمة.",
            "La langue s'applique à l'application et à la traduction des sens. Le texte coranique reste toujours en arabe.",
            "ভাষা অ্যাপ ও অর্থের অনুবাদে প্রযোজ্য। কুরআনের পাঠ সর্বদা আরবিতেই থাকে।"),
        "audioCredit" to e("Recitation: {0} — streamed from Quran.Foundation", "诵读：{0}——来自 Quran.Foundation 的在线播放",
            "तिलावत: {0} — Quran.Foundation से स्ट्रीम", "Recitación: {0} — transmitida desde Quran.Foundation",
            "التلاوة: {0} — بثّ مباشر من Quran.Foundation", "Récitation : {0} — diffusée depuis Quran.Foundation", "তিলাওয়াত: {0} — Quran.Foundation থেকে স্ট্রিম"),
        "audioOffline" to e("Recitation unavailable (offline) — reading mode", "无法播放诵读（离线）——阅读模式", "तिलावत उपलब्ध नहीं (ऑफ़लाइन) — पठन मोड",
            "Recitación no disponible (sin conexión) — modo lectura", "التلاوة غير متاحة (لا اتصال) — وضع القراءة", "Récitation indisponible (hors ligne) — mode lecture", "তিলাওয়াত অনুপলব্ধ (অফলাইন) — পঠন মোড"),
        "audioStatusOn" to e("Recitation: online streaming (Quran.Foundation)", "诵读：在线播放（Quran.Foundation）", "तिलावत: ऑनलाइन स्ट्रीमिंग (Quran.Foundation)",
            "Recitación: transmisión en línea (Quran.Foundation)", "التلاوة: بثّ أونلاين (Quran.Foundation)", "Récitation : streaming en ligne (Quran.Foundation)", "তিলাওয়াত: অনলাইন স্ট্রিমিং (Quran.Foundation)"),
        "audioStatusOff" to e("Recitation: not enabled yet (awaiting Quran.Foundation production access)", "诵读：尚未启用（等待 Quran.Foundation 正式权限）", "तिलावत: अभी सक्रिय नहीं (Quran.Foundation प्रोडक्शन एक्सेस की प्रतीक्षा)",
            "Recitación: aún no activada (a la espera de acceso de producción de Quran.Foundation)", "التلاوة: غير مفعّلة بعد (بانتظار موافقة الإنتاج من Quran.Foundation)", "Récitation : pas encore activée (en attente de l'accès production Quran.Foundation)", "তিলাওয়াত: এখনো চালু নয় (Quran.Foundation প্রোডাকশন অনুমোদনের অপেক্ষায়)"),
        "audioTest" to e("Recitation test (technical)", "诵读测试（技术）", "तिलावत परीक्षण (तकनीकी)", "Prueba de recitación (técnica)", "تجربة التلاوة (اختبار تقني)", "Test de récitation (technique)", "তিলাওয়াত পরীক্ষা (প্রযুক্তিগত)"),
        "audioTestNote" to e("Streams Al-Fatiha from the test environment. For testing only; not the final reciter.",
            "从测试环境在线播放开篇章。仅用于测试，并非最终诵读者。", "परीक्षण वातावरण से अल-फ़ातिहा स्ट्रीम होती है। केवल परीक्षण के लिए; अंतिम क़ारी नहीं।",
            "Transmite Al-Fátiha desde el entorno de pruebas. Solo para pruebas; no es el recitador final.",
            "يبثّ سورة الفاتحة من بيئة الاختبار. للتجربة فقط وليس القارئ النهائي.",
            "Diffuse Al-Fatiha depuis l'environnement de test. Pour test uniquement ; pas le récitant final.",
            "পরীক্ষামূলক পরিবেশ থেকে আল-ফাতিহা স্ট্রিম হয়। কেবল পরীক্ষার জন্য; চূড়ান্ত ক্বারী নন।"),
        "audioTestPlay" to e("Play Al-Fatiha", "播放开篇章", "अल-फ़ातिहा चलाएँ", "Reproducir Al-Fátiha", "تشغيل الفاتحة", "Lire Al-Fatiha", "আল-ফাতিহা চালান"),
        "audioTestStop" to e("Stop", "停止", "रोकें", "Detener", "إيقاف", "Arrêter", "থামান"),
        "transHeader" to e("Translation of the meaning", "含义翻译", "अर्थ का अनुवाद", "Traducción del significado", "ترجمة المعنى", "Traduction du sens", "অর্থের অনুবাদ"),
        "transUnapproved" to e("Translation not yet reviewed — the Arabic tafsir is the reference.",
            "翻译尚未审校——以阿拉伯语经注为准。", "अनुवाद की समीक्षा अभी नहीं हुई — अरबी तफ़सीर ही आधार है।",
            "Traducción aún sin revisar: la referencia es el tafsir en árabe.",
            "الترجمة لم تُراجَع بعد — المرجع هو التفسير العربي.",
            "Traduction pas encore relue — la référence est le tafsir en arabe.",
            "অনুবাদ এখনো পর্যালোচিত নয় — আরবি তাফসিরই মূল সূত্র।"),
        "transMissing" to e("No translation for this language yet; the Arabic text is shown.",
            "此语言暂无翻译，显示阿拉伯语原文。", "इस भाषा में अनुवाद अभी उपलब्ध नहीं; अरबी पाठ दिखाया जा रहा है।",
            "Aún no hay traducción en este idioma; se muestra el texto árabe.",
            "لا توجد ترجمة بهذه اللغة بعد؛ يُعرض النص العربي.",
            "Pas encore de traduction dans cette langue ; le texte arabe est affiché.",
            "এই ভাষায় এখনো অনুবাদ নেই; আরবি পাঠ দেখানো হচ্ছে।"),
    )
}
