@file:Suppress("SameParameterValue")

package au.com.inoahguy.autoadb

import android.content.Context
import android.os.Build
import android.sun.security.x509.*
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.IOException
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date
import java.util.Random

class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    companion object {
        private const val TAG = "AdbConnectionManager"
        private const val KEY_FILE_NAME = "adb_key"
        private const val PUB_KEY_FILE_NAME = "adb_key.pub"
        private const val CERT_FILE_NAME = "adb_cert"
        private const val KEY_SIZE = 2048
        private const val ALGORITHM_RSA = "RSA"
        private const val ALGORITHM_SIGNATURE = "SHA512withRSA"
        private const val CERT_VALIDITY_DAYS = 20 * 365L // 20 years

        @Volatile
        private var INSTANCE: AbsAdbConnectionManager? = null

        @Synchronized
        fun getInstance(context: Context): AbsAdbConnectionManager {
            return INSTANCE ?: AdbConnectionManager(context.applicationContext).also {
                INSTANCE = it
            }
        }
    }

    private var privateKey: PrivateKey? = null
    private var publicKey: PublicKey? = null
    private var certificate: Certificate? = null

    private val keyFile: File
    private val pubKeyFile: File
    private val certFile: File

    init {
        Log.d(TAG, "Initializing AdbConnectionManager")
        api = Build.VERSION.SDK_INT

        val filesDir = context.filesDir
        keyFile = File(filesDir, KEY_FILE_NAME)
        pubKeyFile = File(filesDir, PUB_KEY_FILE_NAME)
        certFile = File(filesDir, CERT_FILE_NAME)

        Log.i(TAG, "Key files directory: ${filesDir.absolutePath}")

        try {
            loadOrGenerateKeyPair()
            Log.d(TAG, "Initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdbConnectionManager", e)
            throw IllegalStateException("Failed to initialize ADB connection manager", e)
        }
    }

    private fun loadOrGenerateKeyPair() {
        Log.i(TAG, "Loading or generating key pair")

        if (keyFile.exists() && pubKeyFile.exists() && certFile.exists()) {
            try {
                loadExistingKeys()
                Log.i(TAG, "Successfully loaded existing keys and certificate")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load existing keys, generating new ones", e)
                // Delete corrupted files
                safeDeleteFile(keyFile)
                safeDeleteFile(pubKeyFile)
                safeDeleteFile(certFile)
            }
        }

        Log.i(TAG, "Generating new key pair and certificate")
        generateNewKeyPairAndCert()
    }

    private fun loadExistingKeys() {
        // Load private key
        val privateKeyBytes = keyFile.readBytes()
        val privateSpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
        privateKey = keyFactory.generatePrivate(privateSpec)
        Log.i(TAG, "Private key loaded")

        // Load public key
        val publicKeyBytes = pubKeyFile.readBytes()
        val publicSpec = X509EncodedKeySpec(publicKeyBytes)
        publicKey = keyFactory.generatePublic(publicSpec)
        Log.i(TAG, "Public key loaded")

        // Load certificate
        val certBytes = certFile.readBytes()
        val cf = CertificateFactory.getInstance("X.509")
        certificate = cf.generateCertificate(certBytes.inputStream()) as X509Certificate
        Log.i(TAG, "Certificate loaded")
    }

    private fun generateNewKeyPairAndCert() {
        try {
            // Generate RSA key pair
            Log.i(TAG, "Generating new RSA key pair")
            val keyGen = KeyPairGenerator.getInstance(ALGORITHM_RSA)
            keyGen.initialize(KEY_SIZE, SecureRandom.getInstance("SHA1PRNG"))
            val keyPair = keyGen.generateKeyPair()
            privateKey = keyPair.private
            publicKey = keyPair.public
            Log.i(TAG, "Key pair generated")

            // Generate self-signed certificate
            Log.i(TAG, "Generating self-signed certificate")
            generateSelfSignedCertificate()
            Log.i(TAG, "Certificate generated")

            // Save keys atomically
            Log.i(TAG, "Saving keys to files")
            saveKeyToFile(keyFile, privateKey?.encoded)
            saveKeyToFile(pubKeyFile, publicKey?.encoded)
            saveKeyToFile(certFile, certificate?.encoded)
            Log.i(TAG, "Keys and certificate saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate new keys", e)
            // Clean up partial files
            safeDeleteFile(keyFile)
            safeDeleteFile(pubKeyFile)
            safeDeleteFile(certFile)
            throw e
        }
    }

    private fun saveKeyToFile(file: File, data: ByteArray?) {
        if (data == null) {
            throw IllegalStateException("Cannot save null data to file: ${file.name}")
        }

        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            tempFile.writeBytes(data)
            if (!tempFile.renameTo(file)) {
                throw IOException("Failed to rename temp file to ${file.name}")
            }
        } catch (e: Exception) {
            safeDeleteFile(tempFile)
            throw e
        }
    }

    private fun safeDeleteFile(file: File) {
        try {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete file: ${file.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception while deleting file: ${file.name}", e)
        }
    }

    private fun generateSelfSignedCertificate() {
        val subject = "CN=Auto ADB"
        val certificateExtensions = CertificateExtensions()

        val pubKey = publicKey ?: throw IllegalStateException("Public key is null")
        val privKey = privateKey ?: throw IllegalStateException("Private key is null")

        certificateExtensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(pubKey).identifier)
        )

        val x500Name = X500Name(subject)
        val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000) // -24 hours
        val notAfter = Date(System.currentTimeMillis() + CERT_VALIDITY_DAYS * 24 * 60 * 60 * 1000)

        certificateExtensions.set(
            "PrivateKeyUsage",
            PrivateKeyUsageExtension(notBefore, notAfter)
        )

        val certificateValidity = CertificateValidity(notBefore, notAfter)
        val x509CertInfo = X509CertInfo()

        x509CertInfo.set("version", CertificateVersion(2))
        x509CertInfo.set(
            "serialNumber",
            CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE)
        )
        x509CertInfo.set(
            "algorithmID",
            CertificateAlgorithmId(AlgorithmId.get(ALGORITHM_SIGNATURE))
        )
        x509CertInfo.set("subject", CertificateSubjectName(x500Name))
        x509CertInfo.set("key", CertificateX509Key(pubKey))
        x509CertInfo.set("validity", certificateValidity)
        x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
        x509CertInfo.set("extensions", certificateExtensions)

        val x509CertImpl = X509CertImpl(x509CertInfo)
        x509CertImpl.sign(privKey, ALGORITHM_SIGNATURE)
        certificate = x509CertImpl
    }

    override fun getPrivateKey(): PrivateKey {
        return privateKey ?: throw IllegalStateException("Private key not initialized")
    }

    override fun getCertificate(): Certificate {
        return certificate ?: throw IllegalStateException("Certificate not initialized")
    }

    override fun getDeviceName(): String = "Auto-ADB"
}