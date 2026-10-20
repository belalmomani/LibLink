package com.liblink.app.sync

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// تشفير الـ Token على الجهاز بعبارة مرور قبل رفعه (AES-256-GCM + PBKDF2)
object Crypto {
    private const val ITERATIONS = 120_000

    private fun key(pass: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") // متاح من أندرويد 7
        return SecretKeySpec(f.generateSecret(PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, 256)).encoded, "AES")
    }

    // الناتج: salt:iv:cipher بترميز Base64
    fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(salt, iv, ct).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    // null عند عبارة مرور خاطئة أو بيانات تالفة
    fun decrypt(data: String, pass: String): String? = try {
        val (salt, iv, ct) = data.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (_: Exception) { null }
}
