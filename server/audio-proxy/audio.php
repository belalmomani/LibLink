<?php
// خدمة وسيطة صغيرة: تحمل client_secret لدى Quran.Foundation وتُرجع روابط تلاوة سورة كاملة (JSON فقط).
// لا تُنزّل ولا تخزّن أي ملف صوتي؛ التطبيق يبثّ مباشرة من خادم الصوت الرسمي.
header('Content-Type: application/json; charset=utf-8');
$cfg = @include __DIR__ . '/config.php';
if (!is_array($cfg) || !$cfg['client_id'] || !$cfg['client_secret']) { http_response_code(503); exit('{"error":"not_configured"}'); }

$surah = filter_input(INPUT_GET, 'surah', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 114]]);
$rec   = filter_input(INPUT_GET, 'recitation', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
if (!$surah || !$rec || !in_array($rec, $cfg['allowed_recitations'], true)) { http_response_code(400); exit('{"error":"bad_request"}'); }

$dir = $cfg['cache_dir']; if (!is_dir($dir)) @mkdir($dir, 0700, true);
$apiHost = $cfg['env'] === 'production' ? 'https://apis.quran.foundation' : 'https://apis-prelive.quran.foundation';
$oauth   = $cfg['env'] === 'production' ? 'https://oauth2.quran.foundation/oauth2/token' : 'https://prelive-oauth2.quran.foundation/oauth2/token';

// حدّ بسيط للمعدل لكل IP
$rl = "$dir/rl_" . md5($_SERVER['REMOTE_ADDR'] ?? '') . '.json';
$h = is_file($rl) ? json_decode(file_get_contents($rl), true) : ['t' => time(), 'n' => 0];
if (time() - $h['t'] > 60) $h = ['t' => time(), 'n' => 0];
if (++$h['n'] > $cfg['rate_per_minute']) { http_response_code(429); exit('{"error":"rate_limited"}'); }
file_put_contents($rl, json_encode($h));

function http($url, $headers, $post = null, $basic = null) {
    $c = curl_init($url);
    curl_setopt_array($c, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 15, CURLOPT_HTTPHEADER => $headers]);
    if ($post !== null) { curl_setopt($c, CURLOPT_POST, true); curl_setopt($c, CURLOPT_POSTFIELDS, $post); }
    if ($basic) curl_setopt($c, CURLOPT_USERPWD, $basic);
    $body = curl_exec($c); $code = curl_getinfo($c, CURLINFO_HTTP_CODE); $err = curl_error($c); curl_close($c);
    $GLOBALS['curl_err'] = $err;
    return [$code, $body];
}

$mf = "$dir/m_{$cfg['env']}_{$rec}_{$surah}.json";
if (is_file($mf) && time() - filemtime($mf) < $cfg['manifest_ttl']) { echo file_get_contents($mf); exit; }

$tf = "$dir/token_{$cfg['env']}.json";
$tok = is_file($tf) ? json_decode(file_get_contents($tf), true) : null;
if (!$tok || $tok['exp'] < time() + 60) {
    [$code, $body] = http($oauth, ['Content-Type: application/x-www-form-urlencoded'], 'grant_type=client_credentials&scope=content', $cfg['client_id'] . ':' . $cfg['client_secret']);
    $j = json_decode($body, true);
    if ($code !== 200 || empty($j['access_token'])) {
        http_response_code(502);
        // تشخيص بلا أسرار: رمز HTTP وخطأ cURL وخطأ OAuth (مثل invalid_client)
        exit(json_encode(['error' => 'auth_failed', 'env' => $cfg['env'], 'http' => $code, 'curl' => $GLOBALS['curl_err'] ?? '', 'oauth' => is_array($j) ? ($j['error'] ?? null) : null]));
    }
    $tok = ['t' => $j['access_token'], 'exp' => time() + (int)($j['expires_in'] ?? 3600)];
    file_put_contents($tf, json_encode($tok)); @chmod($tf, 0600);
}
$H = ["x-auth-token: {$tok['t']}", "x-client-id: {$cfg['client_id']}"];

// اسم القارئ من قائمة التلاوات الرسمية
[$c1, $b1] = http("$apiHost/content/api/v4/resources/recitations", $H);
$name = '';
foreach ((json_decode($b1, true)['recitations'] ?? []) as $r) if ((int)$r['id'] === $rec) $name = trim(($r['reciter_name'] ?? '') . (empty($r['style']) ? '' : ' — ' . $r['style']));
// ملفات الآيات
// الحدّ الأقصى للصفحة 50 آية حسب الوثائق، فنقرأ الصفحات بالتتابع
$files = []; $c2 = 200; $b2 = '';
for ($page = 1; $page <= 10; $page++) {
    [$c2, $b2] = http("$apiHost/content/api/v4/recitations/$rec/by_chapter/$surah?per_page=50&page=$page", $H);
    if ($c2 !== 200) break;
    $j2 = json_decode($b2, true);
    foreach (($j2['audio_files'] ?? []) as $f) $files[] = $f;
    if (empty($j2['pagination']['next_page'])) break;
}
if ($c1 !== 200 || $c2 !== 200 || !$name || !$files) {
    http_response_code(502);
    // تشخيص بلا أسرار: رمز كل طلب ومقتطف من الرد
    $ids = [];
    foreach ((json_decode($b1, true)['recitations'] ?? []) as $r) $ids[] = ($r['id'] ?? '?') . ':' . ($r['reciter_name'] ?? '');
    exit(json_encode(['error' => 'upstream', 'ver' => 3, 'surah_requested' => $surah, 'env' => $cfg['env'], 'recitations_http' => $c1, 'by_chapter_http' => $c2, 'name_found' => $name !== '',
        'files' => count($files), 'recitation_ids' => array_slice($ids, 0, 60),
        'by_chapter_snippet' => mb_substr((string)$b2, 0, 200)], JSON_UNESCAPED_UNICODE));
}

$out = ['source' => 'Quran.Foundation', 'reciterName' => $name, 'recitationId' => $rec, 'files' => []];
foreach ($files as $f) {
    $u = $f['url'] ?? '';
    if ($u && $u[0] !== 'h') $u = 'https://audio.qurancdn.com/' . ltrim($u, '/');
    $out['files'][] = ['verseKey' => $f['verse_key'], 'url' => $u];
}
$json = json_encode($out, JSON_UNESCAPED_UNICODE);
file_put_contents($mf, $json);
echo $json;
