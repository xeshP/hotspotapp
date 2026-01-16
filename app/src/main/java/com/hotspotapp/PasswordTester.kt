package com.hotspotapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*

sealed class TestResult {
    data class Success(val password: String) : TestResult()
    data class Progress(val currentPassword: String, val index: Int, val total: Int) : TestResult()
    object Failed : TestResult()
    object Cancelled : TestResult()
}

class PasswordTester(private val context: Context) {

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 4000L // 4 seconds per attempt
    }

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentCallback: ConnectivityManager.NetworkCallback? = null
    private var isCancelled = false

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
                withContext(Dispatchers.Main) {
                    onResult(TestResult.Success(password))
                }
                return@withContext
            }

            // Small delay between attempts to avoid rate limiting
            delay(100)
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

        // Set password based on security type
        when {
            securityType.contains("WPA3") -> specifierBuilder.setWpa3Passphrase(password)
            securityType.contains("WPA2") || securityType.contains("WPA") -> specifierBuilder.setWpa2Passphrase(password)
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
                if (!isResumed) {
                    isResumed = true
                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                    } catch (_: Exception) {}
                    continuation.resume(true) {}
                }
            }

            override fun onUnavailable() {
                if (!isResumed) {
                    isResumed = true
                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                    } catch (_: Exception) {}
                    continuation.resume(false) {}
                }
            }

            override fun onLost(network: Network) {
                if (!isResumed) {
                    isResumed = true
                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                    } catch (_: Exception) {}
                    continuation.resume(false) {}
                }
            }
        }

        currentCallback = callback

        // Faster timeout
        handler.postDelayed({
            if (!isResumed) {
                isResumed = true
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
                continuation.resume(false) {}
            }
        }, CONNECTION_TIMEOUT_MS)

        try {
            connectivityManager.requestNetwork(request, callback, handler, CONNECTION_TIMEOUT_MS.toInt())
        } catch (e: Exception) {
            if (!isResumed) {
                isResumed = true
                continuation.resume(false) {}
            }
        }

        continuation.invokeOnCancellation {
            if (!isResumed) {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
            }
        }
    }

    fun cancel() {
        isCancelled = true
        currentCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        currentCallback = null
    }
}
