@file:Suppress("SameParameterValue")

package au.com.inoahguy.autoadb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class AdbConnectionManager private constructor(@Suppress("unused") context: Context) : AbsAdbConnectionManager() {
    @Suppress("PropertyName")
    val TAG = "AdbConnectionManager"

    private val mPrivateKey: PrivateKey
    private val mCertificate: Certificate

    // Feel free to change the private key
    init {
        Log.d(TAG, "Initializing AdbConnectionManager")
        api = Build.VERSION.SDK_INT
        val privateKeyString = "-----BEGIN PRIVATE KEY-----\n" +
                "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDi5Hh6GEGjQts4\n" +
                "hBnfIa3Y6uMqXsIeBLBqbY7KsIGx6czLohVYC235DFk0/c+8HSymF53tmmkO9tcv\n" +
                "6f1PwiQF5hW+1mcZvMRZHdVR66I64WKFJOgm+BXu12bX4ViZbfnq2SNVeGIo6gPw\n" +
                "PufyMkkWTHSX6ZmtXN3HZVaEFj8ArLsQCnwdSGegOZArrX/FFkUYUflYAOWmHUHw\n" +
                "huDhEXk3OCOMN6EJyPD6qo73TVDDnTk50yiMVfz1YI/IL5XVzE0yo3lq/Kss6C71\n" +
                "+q4/m8Ss8nOdbg2u2FhGnD9C2pgRykeXyhF4L0YZeVtDs7HRP28kf02jcOUdM1aU\n" +
                "4AEDeKmNAgMBAAECggEACKBCkq2/fotDSkXnb+YGe8PFnUUsiU7OmmPILzSjVJA2\n" +
                "6g8u9keMPByPTPHczNZ67ariGYOqjgat9rBrFmpo+evjwx4d45xJ70TfC9MhgAt6\n" +
                "k1mPQ+0aXRfRRefup8teK7Q8eFeeu2JLU5VXNmlZdeZuUIdOjRS0W3TRby1R4oG/\n" +
                "M/bSZSGJoMYEQZgD0DdqtT+qPZJcbLVVnsW9z6NgDPTdRtOzTs04Ywpx/hnQ59Ea\n" +
                "27mZ+kcckfCYzQ0F6kQpUp+Z+8uYKOScQQWobXtpfyYmQFGKwnU5tAmFxCuc0q2Y\n" +
                "QepneZehXCX9Y91j/ifqmaOtOGsRXKcg4LrGcMbX9wKBgQD9dU/bQBDl1Zq2916E\n" +
                "7wvkNoZG59fVkYc3ZUVXycN3aXXZsYyagCT36xx/cMHECak3L7DQLqH0ZVEkH5tH\n" +
                "XUbZ1vgjKpU85JVdmapvwqe7q4f++pkPM9k6qAL6yhL6b7wbxOSjteAg52Yshpq9\n" +
                "i7HduA9KDY8rwPnX1ecEtu8hrwKBgQDlKvU7LvsxK2uGIx4SuCfAtT2ReqrZfMMf\n" +
                "QUGKeysWOuSRS0jF2+1x1H+EynmWVo9KOfwsTPJRTSRFlxC0+MlhMbgxRhWabCFw\n" +
                "KLPCJGcsUzbfoVXdyxj9tDxzzIxs9mPSKJVb8AxUBNqyUcA4UAFI3/JQg3S7y0xZ\n" +
                "tiFOF9ijgwKBgCEX6SZadhpcSogrQlcfEzFoAR5O9Tp4duw/t88fk/sKdQ3IhfBC\n" +
                "XRFVzHHDWjlrfYGsI2z7OcA8XlzWF6M4xaB51gpZbAT4X5xKDRvskZQKcIZVWBjJ\n" +
                "D0r+Vu1B5zp1zlzd13Cctbf2HrwfkyK+k6m8d5qWrKPs3XJWBoTyEcUXAoGACKIu\n" +
                "rPUfJ4IQQfRuvJvNe5gYYrOxXhIyM6o8st/jBqpfVA33BuU7M8+iojkSjZRjP5Oh\n" +
                "qXWYp3F1jV2cloTM6Wl7G/gc9j1eoSAXbZf7fxL/fTtRxdJR9bTlliM9oxlBN3ip\n" +
                "79XCUSQBrTghOr3g3oL5WQkqy6xkCvkulgeV9MsCgYBxz+XCT2uuSTX+FfkGImVJ\n" +
                "QOn5kEFnGRvEAtuchaVAvR7xlhJYB0i3iCG5MoIJHTAenSX26a3SRipQy1GstEZY\n" +
                "72rTIx6JDgysJpx/QyqLdlHFXFQ3AIerIT2rbYyhjExDZyJ4VGA4Ltsh8Um8W3IS\n" +
                "1PE3Au9KrURPRcSDj9fwPA==\n" +
                "-----END PRIVATE KEY-----\n"

        mPrivateKey = try {
            loadPrivateKeyFromString(privateKeyString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load private key", e)
            throw e
        }

        // Feel free to change the certificate
        val pemCert = "-----BEGIN CERTIFICATE-----\n" +
                "MIIDfzCCAmegAwIBAgIUFZ9cxlKtnlMwhp+SF0JNen1SuAkwDQYJKoZIhvcNAQEL\n" +
                "BQAwaDELMAkGA1UEBhMCQVUxDDAKBgNVBAgTA1FMRDETMBEGA1UEBxMKR29sZCBD\n" +
                "b2FzdDERMA8GA1UEChMIaU5vYWhHdXkxEDAOBgNVBAsTB05venphODcxETAPBgNV\n" +
                "BAMTCEF1dG8gQURCMB4XDTI1MDkyMTEwNDEzMloXDTQ1MDkxNjEwNDEzMlowaDEL\n" +
                "MAkGA1UEBhMCQVUxDDAKBgNVBAgTA1FMRDETMBEGA1UEBxMKR29sZCBDb2FzdDER\n" +
                "MA8GA1UEChMIaU5vYWhHdXkxEDAOBgNVBAsTB05venphODcxETAPBgNVBAMTCEF1\n" +
                "dG8gQURCMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4uR4ehhBo0Lb\n" +
                "OIQZ3yGt2OrjKl7CHgSwam2OyrCBsenMy6IVWAtt+QxZNP3PvB0sphed7ZppDvbX\n" +
                "L+n9T8IkBeYVvtZnGbzEWR3VUeuiOuFihSToJvgV7tdm1+FYmW356tkjVXhiKOoD\n" +
                "8D7n8jJJFkx0l+mZrVzdx2VWhBY/AKy7EAp8HUhnoDmQK61/xRZFGFH5WADlph1B\n" +
                "8Ibg4RF5NzgjjDehCcjw+qqO901Qw505OdMojFX89WCPyC+V1cxNMqN5avyrLOgu\n" +
                "9fquP5vErPJznW4NrthYRpw/QtqYEcpHl8oReC9GGXlbQ7Ox0T9vJH9No3DlHTNW\n" +
                "lOABA3ipjQIDAQABoyEwHzAdBgNVHQ4EFgQUieINH0sQPaX+T2Tu7XskhZUIeOYw\n" +
                "DQYJKoZIhvcNAQELBQADggEBANpRXbDWOD7mwPqaEiQGvbhQVMmJbdUy9uw7bd8y\n" +
                "FLP48HS67D4z0RsbDJHYSh1rzA0XOU2Kz1J7GxgDCaAYbSGe2pzUiUAxN75h+ZOi\n" +
                "GCuHKMELNuCh6DXeTb0GNQyH4Kh0rN9gQcoAEF7I1CIEy9U+lPkiC29kvb7oufVx\n" +
                "OTewXbTKYp9ETupr7GI6qMV729tAPhttvDjJtpfzZ3QWp6trE+4iUd2z2hjyIjB6\n" +
                "8NtjXuBhmzRWDvfOgQFtscy23dgXNJ+J2jrXLyHxdo96qWax8E1Ri0FmiUjTJ7SG\n" +
                "GXmyJyJhb1uMpgSrYqPp3IO58h774AunMVGhrx3jf3bWcGE=\n" +
                "-----END CERTIFICATE-----\n"

        mCertificate = try {
            loadCertificateFromString(pemCert)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load certificate", e)
            throw e
        }
        Log.d(TAG, "Initialization complete")
    }

    override fun getPrivateKey(): PrivateKey = mPrivateKey

    override fun getCertificate(): Certificate = mCertificate

    override fun getDeviceName(): String = "nozza87@Auto-ADB"

    companion object {
        @Volatile
        private var INSTANCE: AbsAdbConnectionManager? = null

        @Synchronized
        fun getInstance(context: Context): AbsAdbConnectionManager {
            return INSTANCE ?: AdbConnectionManager(context).also { INSTANCE = it }
        }

        @Throws(Exception::class)
        private fun loadPrivateKeyFromString(privateKeyPem: String): PrivateKey {
            val privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "") // Remove all whitespace (including newlines)
            val decodedKey = Base64.getDecoder().decode(privateKeyContent)
            val keySpec = PKCS8EncodedKeySpec(decodedKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(keySpec)
        }

        @Throws(Exception::class)
        private fun loadCertificateFromString(certificateString: String): Certificate {
            val certInputStream: InputStream = ByteArrayInputStream(certificateString.toByteArray(StandardCharsets.UTF_8))
            val certFactory = CertificateFactory.getInstance("X.509")
            return certFactory.generateCertificate(certInputStream)
        }
    }
}