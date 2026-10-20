package app.quranichorizons.player

import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * مشغّل تلاوة بسيط: ملف واحد لكل آية، فالانتقال بين الآيات يتبع حدود الملفات تمامًا (لا تقدير للتوقيت).
 * يُستعمل فقط حين BuildConfig.AUDIO_ENABLED = true.
 */
class AudioPlayer(
    private val onCompleted: () -> Unit,
    private val onFailed: (String) -> Unit,
) {
    private var mp: MediaPlayer? = null
    private var volume = 1f

    fun play(url: String) {
        release()
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            p.setDataSource(url)
            p.setVolume(volume, volume)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { onCompleted() }
            p.setOnErrorListener { _, what, _ ->
                onFailed("تعذر تشغيل التلاوة (رمز $what)")
                true
            }
            mp = p
            p.prepareAsync()
        } catch (e: Exception) {
            onFailed(e.message ?: "تعذر تشغيل التلاوة")
        }
    }

    fun pause() {
        try { if (mp?.isPlaying == true) mp?.pause() } catch (_: IllegalStateException) { }
    }

    fun resume() {
        try { mp?.start() } catch (_: IllegalStateException) { }
    }

    /** نسبة التقدّم داخل الملف (0..1)، أو 0 إن لم تتوفر المدة بعد. */
    fun fraction(): Float = try {
        val m = mp
        if (m != null && m.duration > 0) (m.currentPosition.toFloat() / m.duration).coerceIn(0f, 1f) else 0f
    } catch (_: IllegalStateException) { 0f }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        try { mp?.setVolume(volume, volume) } catch (_: IllegalStateException) { }
    }

    fun release() {
        try { mp?.release() } catch (_: Exception) { }
        mp = null
    }
}
