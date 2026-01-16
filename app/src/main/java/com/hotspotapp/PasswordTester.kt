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
import kotlinx.coroutines.channels.Channel

sealed class TestResult {
    data class Success(val password: String) : TestResult()
    data class Progress(val currentPassword: String, val index: Int, val total: Int) : TestResult()
    object Failed : TestResult()
    object Cancelled : TestResult()
}

class PasswordTester(private val context: Context) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentCallback: ConnectivityManager.NetworkCallback? = null
    private var testingJob: Job? = null
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

            val success = tryConnect(network.ssid, password)

            if (success) {
                withContext(Dispatchers.Main) {
                    onResult(TestResult.Success(password))
                }
                return@withContext
            }
        }

        if (!isCancelled) {
            withContext(Dispatchers.Main) {
                onResult(TestResult.Failed)
            }
        }
    }

    private suspend fun tryConnect(ssid: String, password: String): Boolean = suspendCancellableCoroutine { continuation ->
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val handler = Handler(Looper.getMainLooper())
        var isResumed = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!isResumed) {
                    isResumed = true
                    // Disconnect after successful test
                    connectivityManager.unregisterNetworkCallback(this)
                    continuation.resume(true) {}
                }
            }

            override fun onUnavailable() {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(false) {}
                }
            }
        }

        currentCallback = callback

        // Timeout after 15 seconds
        handler.postDelayed({
            if (!isResumed) {
                isResumed = true
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Callback may already be unregistered
                }
                continuation.resume(false) {}
            }
        }, 15000)

        try {
            connectivityManager.requestNetwork(request, callback, handler, 15000)
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
                } catch (e: Exception) {
                    // Callback may already be unregistered
                }
            }
        }
    }

    fun cancel() {
        isCancelled = true
        currentCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Callback may already be unregistered
            }
        }
        currentCallback = null
        testingJob?.cancel()
    }
}
