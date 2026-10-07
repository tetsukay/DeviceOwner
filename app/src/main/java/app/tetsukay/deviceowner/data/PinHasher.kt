package app.tetsukay.deviceowner.data

import java.security.MessageDigest
import java.security.SecureRandom

/** PIN を SHA-256 + salt でハッシュ化・検証する。平文は保存しない。 */
object PinHasher {

    private const val SALT_BYTES = 16
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    data class Hashed(val hashHex: String, val saltHex: String)

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray = newSalt()): Hashed =
        Hashed(hashHex = digest(pin, salt).toHex(), saltHex = salt.toHex())

    fun verify(pin: String, hashHex: String, saltHex: String): Boolean {
        val salt = saltHex.hexToBytesOrNull() ?: return false
        val expected = hashHex.hexToBytesOrNull() ?: return false
        // 定数時間比較
        return MessageDigest.isEqual(digest(pin, salt), expected)
    }

    private fun digest(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(salt)
            update(pin.toByteArray(Charsets.UTF_8))
            digest()
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
    }
}
