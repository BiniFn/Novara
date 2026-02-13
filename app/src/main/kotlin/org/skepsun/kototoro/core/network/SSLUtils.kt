package org.skepsun.kototoro.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

@SuppressLint("CustomX509TrustManager")
fun OkHttpClient.Builder.disableCertificateVerification() = also { builder ->
	runCatching {
		val trustAllCerts = object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

			override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

			override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
		}
		val sslContext = SSLContext.getInstance("SSL")
		sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())
		val sslSocketFactory: SSLSocketFactory = SniBypassSSLSocketFactory(sslContext.socketFactory)
		builder.sslSocketFactory(sslSocketFactory, trustAllCerts)
		builder.hostnameVerifier { _, _ -> true }
	}.onFailure {
		it.printStackTraceDebug()
	}
}

fun OkHttpClient.Builder.installExtraCertificates(context: Context) = also { builder ->
	val certificatesBuilder = HandshakeCertificates.Builder()
		.addPlatformTrustedCertificates()
	val assets = context.assets.list("").orEmpty()
	for (path in assets) {
		if (path.endsWith(".pem")) {
			val cert = loadCert(context, path) ?: continue
			certificatesBuilder.addTrustedCertificate(cert)
		}
	}
	val certificates = certificatesBuilder.build()
	builder.sslSocketFactory(SniBypassSSLSocketFactory(certificates.sslSocketFactory()), certificates.trustManager)
}

private fun loadCert(context: Context, path: String): X509Certificate? = runCatching {
	val cf = CertificateFactory.getInstance("X.509")
	context.assets.open(path, AssetManager.ACCESS_STREAMING).use {
		cf.generateCertificate(it)
	} as X509Certificate
}.onFailure { e ->
	e.printStackTraceDebug()
}.onSuccess {
	if (BuildConfig.DEBUG) {
		Log.i("ExtraCerts", "Loaded cert $path")
	}
}.getOrNull()

internal class SniBypassSSLSocketFactory(
	private val delegate: SSLSocketFactory,
) : SSLSocketFactory() {

	override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

	override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

	override fun createSocket(): java.net.Socket = configure(delegate.createSocket(), null)

	override fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean): java.net.Socket {
		return configure(delegate.createSocket(s, host, port, autoClose), host)
	}

	override fun createSocket(host: String, port: Int): java.net.Socket {
		return configure(delegate.createSocket(host, port), host)
	}

	override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket {
		return configure(delegate.createSocket(host, port, localHost, localPort), host)
	}

	override fun createSocket(host: java.net.InetAddress, port: Int): java.net.Socket {
		return configure(delegate.createSocket(host, port), host.hostName)
	}

	override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int): java.net.Socket {
		return configure(delegate.createSocket(address, port, localAddress, localPort), address.hostName)
	}

	private fun configure(socket: java.net.Socket, host: String?): java.net.Socket {
		if (socket is SSLSocket && host?.contains('_') == true) {
			val safeHost = host.replace('_', '-')
			runCatching {
				val params = socket.sslParameters
				params.serverNames = listOf(SNIHostName(safeHost))
				socket.sslParameters = params
			}.onFailure { e ->
				Log.w("SSLUtils", "SNI workaround failed: host=$host", e)
			}
		}
		return socket
	}
}
