package com.rakshak.ui.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import com.rakshak.core.alert.TestContactConfig
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import com.rakshak.R
import com.rakshak.core.mode.AppMode
import com.rakshak.core.readiness.ReadinessState
import com.rakshak.core.readiness.ReadinessStatus
import com.rakshak.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * MainActivity — "System Readiness" screen.
 *
 * Shows:
 *  - Live status of 6 P0 components (checkmark / warning + [FIX] button)
 *  - Global DEMO ↔ REAL mode toggle switch
 *  - A Refresh button for manual re-check
 *
 * Architecture: purely observes [MainViewModel]. All logic lives in the ViewModel
 * or below. This Activity contains ZERO business logic.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // Adapter for the readiness RecyclerView
    private val readinessAdapter = ReadinessAdapter { status ->
        onFixRequested(status)
    }

    // Permission launcher — requests all P0+P1 permissions at once on first launch
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            showPermissionRationale(denied)
        }
        viewModel.refresh()
    }

    // Single-permission launcher used by the [FIX] button
    private val fixPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupModeSwitch()
        setupTestControls()
        setupRefreshButton()
        
        // Start SensorService
        val serviceIntent = android.content.Intent(this, com.rakshak.core.sensor.SensorService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun setupTestControls() {
        val etTestContact = findViewById<android.widget.EditText>(R.id.et_test_contact)
                val btnSimulateCrash = findViewById<android.view.View>(R.id.btn_simulate_crash)
        val btnInjectTrace = findViewById<android.view.View>(R.id.btn_inject_trace)

        etTestContact?.setText(com.rakshak.core.alert.TestContactConfig.testContactNumber)

        btnSimulateCrash?.setOnClickListener {
            val contact = etTestContact?.text?.toString()?.trim()
            if (!contact.isNullOrEmpty()) {
                com.rakshak.core.alert.TestContactConfig.testContactNumber = contact
            }
            
            val intent = android.content.Intent(this, com.rakshak.core.sensor.SensorService::class.java).apply {
                action = com.rakshak.core.sensor.SensorService.ACTION_SIMULATE_CRASH
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
                        android.widget.Toast.makeText(this, "Test Triggered", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        btnInjectTrace?.setOnClickListener {
            val intent = android.content.Intent(this, com.rakshak.core.sensor.SensorService::class.java).apply {
                action = com.rakshak.core.sensor.SensorService.ACTION_INJECT_TRACE
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            android.widget.Toast.makeText(this, "Trace Injected", android.widget.Toast.LENGTH_SHORT).show()
        }
        observeViewModel()

        // Request all permissions on first launch
        requestAllPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning from Settings (user may have granted permissions)
        viewModel.refresh()
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.rvReadiness.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = readinessAdapter
        }
    }

    private fun setupModeSwitch() {
        binding.switchMode.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) AppMode.REAL else AppMode.DEMO
            viewModel.setMode(newMode)
            updateModeBanner(newMode)
        }
    }

    private fun setupRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            viewModel.refresh()
        }
    }

    // ── Observation ──────────────────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isServiceRunning.collect { running ->
                        findViewById<android.widget.TextView>(R.id.tv_service_status)?.text = "Service: " + if(running) "Running" else "Stopped"
                    }
                }
                launch {
                    viewModel.detectorState.collect { state ->
                        findViewById<android.widget.TextView>(R.id.tv_detector_state)?.text = "Detector: " + state.name
                    }
                }
                launch {
                    viewModel.lastAlertResult.collect { result ->
                        findViewById<android.widget.TextView>(R.id.tv_last_alert)?.text = "Last Alert: " + (result?.name ?: "None")
                    }
                }
                launch {
                    viewModel.readinessItems.collect { items ->
                        readinessAdapter.submitList(items)
                        updateOverallStatus(items)
                    }
                }
                launch {
                    viewModel.currentMode.collect { mode ->
                        binding.switchMode.isChecked = mode is AppMode.REAL
                        updateModeBanner(mode)
                    }
                }
            }
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private fun updateModeBanner(mode: AppMode) {
        when (mode) {
            is AppMode.DEMO -> {
                binding.chipMode.text = getString(R.string.mode_demo)
                binding.chipMode.setChipBackgroundColorResource(R.color.mode_demo_bg)
            }
            is AppMode.REAL -> {
                binding.chipMode.text = getString(R.string.mode_real)
                binding.chipMode.setChipBackgroundColorResource(R.color.mode_real_bg)
            }
        }
    }

    private fun updateOverallStatus(items: List<ReadinessStatus>) {
        val allOk = items.isNotEmpty() && items.all { it.state == ReadinessState.OK }
        binding.tvOverallStatus.text = if (allOk) {
            getString(R.string.status_all_systems_ready)
        } else {
            getString(R.string.status_action_required)
        }
        binding.tvOverallStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (allOk) R.color.status_ok else R.color.status_warning
            )
        )
    }

    // ── Permission handling ───────────────────────────────────────────────────

    private fun requestAllPermissions() {
        permissionLauncher.launch(ALL_PERMISSIONS)
    }

    private fun onFixRequested(status: ReadinessStatus) {
        val permission = PERMISSION_MAP[status.id]
        if (permission != null) {
            fixPermissionLauncher.launch(permission)
        } else {
            // Non-permission fix — open app settings
            openAppSettings()
        }
    }

    private fun showPermissionRationale(denied: Set<String>) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_rationale_title))
            .setMessage(getString(R.string.permission_rationale_message))
            .setPositiveButton(getString(R.string.open_settings)) { _, _ -> openAppSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    // ── Constants ────────────────────────────────────────────────────────────

    companion object {
        private val ALL_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )

        private val PERMISSION_MAP = mapOf(
            "location_permission" to Manifest.permission.ACCESS_FINE_LOCATION,
            "sms_permission" to Manifest.permission.SEND_SMS,
        )
    }
}

// ── RecyclerView Adapter ─────────────────────────────────────────────────────

/**
 * ReadinessAdapter — displays each [ReadinessStatus] as a row with:
 *  - An icon (✓ or ⚠)
 *  - Label text
 *  - Detail text (optional)
 *  - [FIX] button (shown only when [ReadinessStatus.isFixable] is true)
 */
private class ReadinessAdapter(
    private val onFixClick: (ReadinessStatus) -> Unit,
) : ListAdapter<ReadinessStatus, ReadinessAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_readiness, parent, false)
        return ViewHolder(view as ViewGroup, onFixClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val root: ViewGroup,
        private val onFixClick: (ReadinessStatus) -> Unit,
    ) : RecyclerView.ViewHolder(root) {

        private val tvLabel: TextView = root.findViewById(R.id.tv_label)
        private val tvDetail: TextView = root.findViewById(R.id.tv_detail)
        private val tvIcon: TextView = root.findViewById(R.id.tv_icon)
        private val btnFix: MaterialButton = root.findViewById(R.id.btn_fix)

        fun bind(status: ReadinessStatus) {
            tvLabel.text = status.label
            tvDetail.text = status.detail
            tvDetail.visibility = if (status.detail.isBlank()) android.view.View.GONE
                                   else android.view.View.VISIBLE

            when (status.state) {
                ReadinessState.OK -> {
                    tvIcon.text = "✓"
                    tvIcon.setTextColor(ContextCompat.getColor(root.context, R.color.status_ok))
                }
                ReadinessState.WARNING -> {
                    tvIcon.text = "⚠"
                    tvIcon.setTextColor(ContextCompat.getColor(root.context, R.color.status_warning))
                }
                ReadinessState.CHECKING -> {
                    tvIcon.text = "…"
                    tvIcon.setTextColor(ContextCompat.getColor(root.context, R.color.status_checking))
                }
            }

            btnFix.visibility = if (status.isFixable) android.view.View.VISIBLE
                                 else android.view.View.GONE
            btnFix.setOnClickListener { onFixClick(status) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ReadinessStatus>() {
        override fun areItemsTheSame(old: ReadinessStatus, new: ReadinessStatus) =
            old.id == new.id

        override fun areContentsTheSame(old: ReadinessStatus, new: ReadinessStatus) =
            old == new
    }
}





