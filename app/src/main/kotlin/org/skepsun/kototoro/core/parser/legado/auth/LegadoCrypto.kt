package org.skepsun.kototoro.core.parser.legado.auth

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES encryption/decryption compatible with Legado's loginInfo format.
 *
 * Legado uses: SymmetricCryptoAndroid("AES", key).encryptBase64(info)
 * where key = androidId.encodeToByteArray(0, 16) — first 16 bytes of Android ID string.
 *
 * AES defaults in hutool-crypto to AES/ECB/PKCS5Padding.
 */
object LegadoCrypto {

    private const val ALGORITHM = "AES/ECB/PKCS5Padding"

    /**
     * Derive a 16-byte AES key from the Android ID string,
     * matching Legado's `AppConst.androidId.encodeToByteArray(0, 16)`.
     */
    fun keyFromAndroidId(androidId: String): SecretKeySpec {
        val raw = androidId.toByteArray(Charsets.UTF_8).take(16).toByteArray()
        val padded = ByteArray(16)
        raw.copyInto(padded)
        return SecretKeySpec(padded, "AES")
    }

    /**
     * AES-encrypt and Base64-encode (Legado compatible).
     */
    fun encryptBase64(plaintext: String, key: SecretKeySpec): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Base64-decode and AES-decrypt (Legado compatible).
     */
    fun decryptBase64(base64Text: String, key: SecretKeySpec): String {
        val encrypted = Base64.decode(base64Text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
