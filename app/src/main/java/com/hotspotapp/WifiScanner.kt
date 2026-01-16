package com.hotspotapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val securityType: String,
    val frequency: Int
)

class WifiScanner(private val context: Context) {

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scanResultsListener: ((List<WifiNetwork>) -> Unit)? = null
    private var scanReceiver: BroadcastReceiver? = null

    fun startScan(onResults: (List<WifiNetwork>) -> Unit): Boolean {
        if (!hasLocationPermission()) {
            return false
        }

        scanResultsListener = onResults

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    processScanResults()
                } else {
                    // Use cached results if scan failed
                    processScanResults()
                }
            }
        }

        context.registerReceiver(
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        return wifiManager.startScan()
    }

    fun stopScan() {
        scanReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered
            }
        }
        scanReceiver = null
        scanResultsListener = null
    }

    private fun processScanResults() {
        val results = wifiManager.scanResults
        val networks = results
            .filter { it.SSID.isNotEmpty() }
            .distinctBy { it.SSID }
            .map { scanResult ->
                WifiNetwork(
                    ssid = scanResult.SSID,
                    bssid = scanResult.BSSID,
                    signalStrength = WifiManager.calculateSignalLevel(scanResult.level, 5),
                    securityType = getSecurityType(scanResult),
                    frequency = scanResult.frequency
                )
            }
            .sortedByDescending { it.signalStrength }

        scanResultsListener?.invoke(networks)
    }

    private fun getSecurityType(scanResult: ScanResult): String {
        val capabilities = scanResult.capabilities
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("OWE") -> "OWE"
            else -> "Open"
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled
}
