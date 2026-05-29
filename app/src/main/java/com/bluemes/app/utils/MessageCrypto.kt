package com.bluemes.app.utils

import android.util.Base64
import com.bluemes.app.utils.Constants
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128/CBC encryption for BlueMes message content.
 *
 * Key derivation:
 *   key = first 16 bytes of HMAC-SHA256(APP_SECRET, sorted(addrA + addrB))
 *
 * This means both devices arrive at the same key without any key exchange,
 * since both know APP_SECRET and both device addresses. It prevents casual
 * eavesdroppers from reading messages and ensures only BlueMes devices with
 * the same build can communicate.
 *
 * Wire format for encrypted payloads: Base64(IV):Base64(ciphertext)
 */
object MessageCrypto {

    private const val AES_ALGO = "AES/CBC/PKCS5Padding"
    private const val HMAC_ALGO = "HmacSHA256"
    private const val KEY_BYTES = 16 // AES-128

    // -------------------------------------------------------------------------
    // Key derivation
    // -------------------------------------------------------------------------

    fun deriveKey(localAddress: String, remoteAddress: String): SecretKeySpec {
        val sorted = listOf(localAddress, remoteAddress).sorted().joinToString("|")
        val mac = Mac.getInstance(HMAC_ALGO)
        val secret = SecretKeySpec(Constants.APP_SECRET.toByteArray(Charsets.UTF_8), HMAC_ALGO)
        mac.init(secret)
        val fullKey = mac.doFinal(sorted.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(fullKey.copyOf(KEY_BYTES), "AES")
    }

    // -------------------------------------------------------------------------
    // Encrypt / Decrypt
    // -------------------------------------------------------------------------

    fun encrypt(plaintext: String, key: SecretKeySpec): String {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivB64:$ctB64"
    }

    fun decrypt(payload: String, key: SecretKeySpec): String? = try {
        val parts = payload.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    // -------------------------------------------------------------------------
    // HMAC challenge-response (used in HANDSHAKE_CHALLENGE / HANDSHAKE_RESPONSE)
    // -------------------------------------------------------------------------

    fun hmacSign(challenge: String): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(Constants.APP_SECRET.toByteArray(Charsets.UTF_8), HMAC_ALGO))
        val result = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    fun hmacVerify(challenge: String, expectedSignature: String): Boolean =
        hmacSign(challenge) == expectedSignature
}
