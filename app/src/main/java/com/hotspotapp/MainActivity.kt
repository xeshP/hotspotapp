package com.hotspotapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hotspotapp.adapter.WifiListAdapter
import com.hotspotapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiScanner: WifiScanner
    private lateinit var passwordTester: PasswordTester
    private lateinit var passwordFileReader: PasswordFileReader
    private lateinit var wifiAdapter: WifiListAdapter

    private var passwords: List<String> = emptyList()
    private var selectedNetwork: WifiNetwork? = null
    private var isTesting = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scanWifiNetworks()
        } else {
            Toast.makeText(this, "Location permission required for WiFi scanning", Toast.LENGTH_LONG).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadPasswordFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initComponents()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun initComponents() {
        wifiScanner = WifiScanner(this)
        passwordTester = PasswordTester(this)
        passwordFileReader = PasswordFileReader(this)
    }

    private fun setupRecyclerView() {
        wifiAdapter = WifiListAdapter { network ->
            selectedNetwork = network
            updateStartButtonState()
        }

        binding.rvNetworks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wifiAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            checkPermissionAndScan()
        }

        binding.btnLoadPasswords.setOnClickListener {
            filePickerLauncher.launch(arrayOf("text/plain"))
        }

        binding.btnStartTest.setOnClickListener {
            if (isTesting) {
                stopTesting()
            } else {
                startTesting()
            }
        }
    }

    private fun checkPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                scanWifiNetworks()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("Location permission is required to scan WiFi networks on Android 10+")
                    .setPositiveButton("Grant") { _, _ ->
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun scanWifiNetworks() {
        if (!wifiScanner.isWifiEnabled()) {
            Toast.makeText(this, "Please enable WiFi first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressScanning.visibility = View.VISIBLE
        binding.btnScan.isEnabled = false
        binding.tvStatus.text = "Scanning for networks..."

        wifiScanner.startScan { networks ->
            runOnUiThread {
                binding.progressScanning.visibility = View.GONE
                binding.btnScan.isEnabled = true

                if (networks.isEmpty()) {
                    binding.tvStatus.text = "No networks found"
                } else {
                    binding.tvStatus.text = "Found ${networks.size} networks"
                }

                wifiAdapter.submitList(networks)
                selectedNetwork = null
                wifiAdapter.clearSelection()
                updateStartButtonState()
            }
        }
    }

    private fun loadPasswordFile(uri: Uri) {
        val result = passwordFileReader.readPasswordsFromUri(uri)
        result.onSuccess { loadedPasswords ->
            passwords = loadedPasswords
            val fileName = passwordFileReader.getFileName(uri)
            binding.tvPasswordFile.text = "$fileName (${passwords.size} passwords)"
            updateStartButtonState()
            Toast.makeText(this, "Loaded ${passwords.size} passwords", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(this, "Error loading file: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStartButtonState() {
        val canStart = selectedNetwork != null && passwords.isNotEmpty() && !isTesting
        binding.btnStartTest.isEnabled = canStart || isTesting
        binding.btnStartTest.text = if (isTesting) "Stop Testing" else "Start Testing"
    }

    private fun startTesting() {
        val network = selectedNetwork ?: return
        if (passwords.isEmpty()) return

        isTesting = true
        updateStartButtonState()
        binding.btnScan.isEnabled = false
        binding.btnLoadPasswords.isEnabled = false
        binding.progressTesting.visibility = View.VISIBLE
        binding.progressTesting.max = passwords.size
        binding.progressTesting.progress = 0

        lifecycleScope.launch {
            passwordTester.testPasswords(network, passwords) { result ->
                when (result) {
                    is TestResult.Progress -> {
                        binding.tvStatus.text = "Testing: ${result.currentPassword} (${result.index}/${result.total})"
                        binding.progressTesting.progress = result.index
                    }
                    is TestResult.Success -> {
                        showSuccessDialog(network.ssid, result.password)
                        resetTestingState()
                    }
                    is TestResult.Failed -> {
                        binding.tvStatus.text = "No password found for ${network.ssid}"
                        resetTestingState()
                    }
                    is TestResult.Cancelled -> {
                        binding.tvStatus.text = "Testing cancelled"
                        resetTestingState()
                    }
                }
            }
        }
    }

    private fun stopTesting() {
        passwordTester.cancel()
    }

    private fun resetTestingState() {
        isTesting = false
        binding.btnScan.isEnabled = true
        binding.btnLoadPasswords.isEnabled = true
        binding.progressTesting.visibility = View.GONE
        updateStartButtonState()
    }

    private fun showSuccessDialog(ssid: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Password Found!")
            .setMessage("Network: $ssid\nPassword: $password")
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("WiFi Password", password)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanner.stopScan()
        passwordTester.cancel()
    }
}
