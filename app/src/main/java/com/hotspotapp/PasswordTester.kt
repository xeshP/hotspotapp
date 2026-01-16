package com.hotspotapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
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
        private const val CONNECTION_TIMEOUT_MS = 3000L
    }

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile private var isCancelled = false
    private var currentCallback: ConnectivityManager.NetworkCallback? = null

    suspend fun testPasswords(
        network: WifiNetwork,
        passwords: List<String>,
        onResult: (TestResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled = false

        // Clear any previous suggestions
        clearSuggestions()

        for ((index, password) in passwords.withIndex()) {
            if (isCancelled) {
                withContext(Dispatchers.Main) {
                    onResult(TestResult.Cancelled)
                }
                clearSuggestions()
                return@withContext
            }

            withContext(Dispatchers.Main) {
                onResult(TestResult.Progress(password, index + 1, passwords.size))
            }

            val success = tryConnectWithSuggestion(network.ssid, network.securityType, password)

            if (success) {
                withContext(Dispatchers.Main) {
                    onResult(TestResult.Success(password))
                }
                return@withContext
            }
        }

        clearSuggestions()

        if (!isCancelled) {
            withContext(Dispatchers.Main) {
                onResult(TestResult.Failed)
            }
        }
    }

    private suspend fun tryConnectWithSuggestion(ssid: String, securityType: String, password: String): Boolean {
        // Remove previous suggestions first
        clearSuggestions()

        // Create suggestion
        val suggestionBuilder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setPriority(Int.MAX_VALUE)

        when {
            securityType.contains("WPA3") -> suggestionBuilder.setWpa3Passphrase(password)
            else -> suggestionBuilder.setWpa2Passphrase(password)
        }

        val suggestion = suggestionBuilder.build()
        val suggestionsList = listOf(suggestion)

        // Add suggestion
        val status = wifiManager.addNetworkSuggestions(suggestionsList)
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            return false
        }

        // Wait and check if connected
        return checkConnection(ssid)
    }

    private suspend fun checkConnection(targetSsid: String): Boolean = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var isResumed = false
        var checkCount = 0
        val maxChecks = 6 // Check 6 times over 3 seconds

        val checkRunnable = object : Runnable {
            override fun run() {
                if (isResumed || isCancelled) return

                val connectionInfo = wifiManager.connectionInfo
                val connectedSsid = connectionInfo?.ssid?.replace("\"", "") ?: ""

                if (connectedSsid == targetSsid) {
                    isResumed = true
                    continuation.resume(true) {}
                    return
                }

                checkCount++
                if (checkCount < maxChecks) {
                    handler.postDelayed(this, 500)
                } else {
                    if (!isResumed) {
                        isResumed = true
                        continuation.resume(false) {}
                    }
                }
            }
        }

        handler.post(checkRunnable)

        continuation.invokeOnCancellation {
            handler.removeCallbacks(checkRunnable)
        }
    }

    private fun clearSuggestions() {
        try {
            val emptySuggestions = wifiManager.networkSuggestions
            if (emptySuggestions.isNotEmpty()) {
                wifiManager.removeNetworkSuggestions(emptySuggestions)
            }
        } catch (_: Exception) {}
    }

    fun cancel() {
        isCancelled = true
        clearSuggestions()
        currentCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        currentCallback = null
    }
}
