<?php
// انسخ هذا الملف إلى config.php على الاستضافة فقط (config.php مُتجاهَل في git). لا تضع الأسرار في المستودع.
return [
    'env'           => 'production',          // 'prelive' للاختبار (سورتا 1 و2 فقط)
    'client_id'     => '',
    'client_secret' => '',
    'cache_dir'     => __DIR__ . '/cache',   // يخزّن الرمز وقوائم الروابط فقط (JSON)، لا ملفات صوت
    'manifest_ttl'  => 86400,                 // أقل بكثير من حدّ الأسبوع في شروط Quran.Foundation
    'allowed_recitations' => [],              // أرقام التلاوات المعتمدة فقط (بعد التحقق من القارئ)
    'rate_per_minute' => 30,
];
