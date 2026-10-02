package com.mqttmobile.app.util

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CertificateSummary(
    val subject: String,
    val issuer: String,
    val validUntil: String,
    val fingerprint: String
)

object CertificateInspector {
    fun inspect(pem: String): CertificateSummary? = runCatching {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
        CertificateSummary(
            subject = certificate.subjectX500Principal.name,
            issuer = certificate.issuerX500Principal.name,
            validUntil = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(certificate.notAfter.time)),
            fingerprint = fingerprint
        )
    }.getOrNull()
}
