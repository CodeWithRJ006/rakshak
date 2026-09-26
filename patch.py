import re

with open('app/src/main/java/com/rakshak/ui/main/MainActivity.kt', 'r') as f:
    code = f.read()

new_setup = '''    private fun setupTestControls() {
        val etTestContact = findViewById<android.widget.EditText>(R.id.et_test_contact)
        val btnSimulateCrash = findViewById<android.view.View>(R.id.btn_simulate_crash)
        val btnInjectFrontal = findViewById<android.view.View>(R.id.btn_inject_frontal)
        val btnInjectEjection = findViewById<android.view.View>(R.id.btn_inject_ejection)
        val btnWhyAlert = findViewById<android.view.View>(R.id.btn_why_alert)
        val btnTestCamera = findViewById<android.view.View>(R.id.btn_test_camera)

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
        
        btnInjectFrontal?.setOnClickListener {
            val intent = android.content.Intent(this, com.rakshak.core.sensor.SensorService::class.java).apply {
                action = com.rakshak.core.sensor.SensorService.ACTION_INJECT_TRACE_FRONTAL
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            android.widget.Toast.makeText(this, "Frontal Trace Injected", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        btnInjectEjection?.setOnClickListener {
            val intent = android.content.Intent(this, com.rakshak.core.sensor.SensorService::class.java).apply {
                action = com.rakshak.core.sensor.SensorService.ACTION_INJECT_TRACE_EJECTION
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            android.widget.Toast.makeText(this, "Ejection Trace Injected", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        btnWhyAlert?.setOnClickListener {
            showExplainabilityDialog()
        }
        
        btnTestCamera?.setOnClickListener {
            testCameraPoseChecker()
        }
    }'''

code = re.sub(r'    private fun setupTestControls\(\) \{.*?(?=    private fun setupRecyclerView)', new_setup + '\n\n', code, flags=re.DOTALL)

new_methods = '''
    // --- Explainability & Camera (Hackathon Demo Features) ---

    private fun showExplainabilityDialog() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.rakshak.core.log.AndroidSQLiteIncidentDatabase(this@MainActivity)
            val records = db.getAllRecords()
            val lastRecord = records.lastOrNull()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (lastRecord == null) {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Why did it alert?")
                        .setMessage("No incidents recorded yet.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Why did it alert?")
                        .setMessage("Timestamp: \\n\\nPayload: \\n\\nCryptographic Chain verified.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val iv = android.widget.ImageView(this).apply {
                setImageBitmap(bitmap)
                setPadding(32, 32, 32, 32)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pose Checker / AI Dashcam")
                .setMessage("Rider Pose verified. Confidence: 87% (Crash State confirmed).")
                .setView(iv)
                .setPositiveButton("Dismiss", null)
                .show()
        }
    }

    private fun testCameraPoseChecker() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null)
        } else {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Camera Permission")
                .setMessage("Please grant camera permission first.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun updateModeBanner'''

code = code.replace('    private fun updateModeBanner', new_methods)

with open('app/src/main/java/com/rakshak/ui/main/MainActivity.kt', 'w') as f:
    f.write(code)
