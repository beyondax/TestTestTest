package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Arrays
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            sendRequestWithCustomTrust()
        }


        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }


    private fun sendRequestWithPinning() {
        val certificatePinner = CertificatePinner.Builder()
            .add("cbf43926.strelkapay.ru", "sha256/5X0HpukeHlK1TmnKoT/yohFYRl+1VWFmkxzmclt3AyE=")
            .build()
        val client = OkHttpClient().newBuilder().certificatePinner(certificatePinner).build()

        val request = Request.Builder()
            .addHeader("test_access_token", "test_access_token")
            .url("https://cbf43926.strelkapay.ru/accelerometerdata")
            .post(
                POST.toRequestBody()
            )
            .build()

        try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e("MainActivity", "sendRequest: ${e.message}")
        }

    }

    private fun sendRequestWithCustomTrust() {
        val certificatePinner = CertificatePinner.Builder()
            .add(
                "cbf43926.strelkapay.ru",
                "sha256/OK9orTfjwPLxAci+BwQELHjBRRzpYAkIv/nDQTfy8D0="
//                "sha256/5X0HpukeHlK1TmnKoT/yohFYRl+1VWFmkxzmclt3AyE="
            ).build()
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        val trustManager: X509TrustManager
        val sslSocketFactory: SSLSocketFactory
        try {
            trustManager = trustManagerForCertificates(trustedCertificatesInputStream())
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf(trustManager), null)
            sslSocketFactory = sslContext.socketFactory
        } catch (e: GeneralSecurityException) {
            throw RuntimeException(e)
        }

        val newBuilder = OkHttpClient.Builder()
        newBuilder.sslSocketFactory(sslSocketFactory,
            trustManager
        )
        newBuilder.hostnameVerifier { _: String?, _: SSLSession? -> true }
        newBuilder.addInterceptor(interceptor)
//        newBuilder.certificatePinner(certificatePinner)

        val newClient = newBuilder.build()

        val request = Request.Builder()
            .url("https://cbf43926.strelkapay.ru/accelerometerdata")
            .post(POST.toRequestBody())
            .build()

        try {
            newClient.newCall(request).execute()
            Log.d("MainActivity", "sendRequest: DONE")
        } catch (e: Exception) {
            Log.e("MainActivity", "sendRequest: ${e.message}")
        }
    }

    private fun trustedCertificatesInputStream(): InputStream {
        return resources.openRawResource(com.example.myapplication.R.raw.sber_nko_accelerometer_2024_ca)
    }

    @Throws(GeneralSecurityException::class)
    private fun trustManagerForCertificates(inputStream: InputStream): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates: Collection<Certificate?> = certificateFactory.generateCertificates(inputStream)
        if (certificates.isEmpty()) {
            throw IllegalArgumentException("expected non-empty set of trusted certificates")
        }

        // Put the certificates a key store.
        val keyStore = newEmptyKeyStore()
        for ((index, certificate) in certificates.withIndex()) {
            val certificateAlias = index.toString()
            keyStore.setCertificateEntry(certificateAlias, certificate)
        }

        // Use it to build an X509 trust manager.
        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        )
        keyManagerFactory.init(keyStore, null)
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(keyStore)
        val trustManagers: Array<TrustManager> = trustManagerFactory.trustManagers
        if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
            throw IllegalStateException(
                "Unexpected default trust managers:"
                        + trustManagers.contentToString()
            )
        }
        return trustManagers[0] as X509TrustManager
    }

    @Throws(GeneralSecurityException::class)
    private fun newEmptyKeyStore(): KeyStore {
        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            val inputStream: InputStream? = null // By convention, 'null' creates an empty key store.
            keyStore.load(inputStream, null)
            return keyStore
        } catch (e: IOException) {
            throw AssertionError(e)
        }
    }

    private val trustAllCerts: Array<X509TrustManager> = arrayOf(@SuppressLint("CustomX509TrustManager")
    object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }
    )


    companion object {
        const val POST = " \"type\": \"0\",\n" +
                "                \"data\": [[1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557], [1.8843478, 5.6093745, 7.815557]]"
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}