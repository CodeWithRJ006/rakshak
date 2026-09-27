package com.rakshak.ui.main

import android.Manifest
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.rakshak.core.sensor.CameraCaptureHelper
import com.rakshak.core.mode.AppMode
import com.rakshak.core.sensor.SensorService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val ALL_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        permissionLauncher.launch(ALL_PERMISSIONS)
        
        val serviceIntent = Intent(this, SensorService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        setContent {
            RakshakTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F1117)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HomeScreen(viewModel)

                        val seconds by viewModel.countdownSeconds.collectAsState()
                        if (seconds != null) {
                            EmergencyCountdownOverlay(
                                secondsRemaining = seconds ?: 10,
                                onCancel = { viewModel.cancelCountdownByRider() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RakshakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0F1117),
            surface = Color(0xFF1C1F2A),
            primary = Color(0xFFD32F2F),
            secondary = Color(0xFF9AA0B0),
            tertiary = Color(0xFF4CAF50)
        ),
        content = content
    )
}

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val scrollState = rememberScrollState()
    var isVisible by remember { mutableStateOf(false) }
    
    val aiState by viewModel.aiState.collectAsState()
    val aiResultText by viewModel.aiResultText.collectAsState()
    val isModelLoaded by viewModel.isModelLoaded.collectAsState()
    val detectorState by viewModel.detectorState.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val lastTelemetry by viewModel.lastTelemetry.collectAsState()
    val capturedImage by viewModel.capturedImage.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(600))
        ) {
            Column {
                Text(
                    text = "RAKSHAK",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "AI SAFETY COPILOT",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }

        // Status Badge Row
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600, delayMillis = 100)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(600, delayMillis = 100))
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    text = if (isModelLoaded) "ON-DEVICE AI · Gemma · READY" else "ON-DEVICE AI · LOADING...",
                    color = if (isModelLoaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
                StatusBadge(
                    text = if (isServiceRunning) "TELEMETRY · ACTIVE" else "TELEMETRY · OFFLINE",
                    color = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                )
            }
        }

        // Hero AI Card
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600, delayMillis = 200)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(600, delayMillis = 200))
        ) {
            AiCopilotCard(aiState, aiResultText, detectorState.name, capturedImage)
        }

        // Test Controls
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600, delayMillis = 300)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(600, delayMillis = 300))
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.triggerSimulatedCrash() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("💥 TEST INCIDENT (FRONTAL)", fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedButton(
                        onClick = { viewModel.triggerInjectTrace() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9800)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9800))
                    ) {
                        Text("⚡ TEST INCIDENT (EJECTION)", fontWeight = FontWeight.Bold)
                    }
                    
                    if (lastTelemetry != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.explainAlert() },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3140)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("🤔 WHY ALERT?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = { 
                                    coroutineScope.launch {
                                        val bitmap = CameraCaptureHelper.takePicture(context, lifecycleOwner)
                                        viewModel.verifyWithCamera(bitmap)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A5F)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📸 CAM VERIFY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.startVoiceCopilot() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🎤 VOICE COPILOT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AiCopilotCard(aiState: AiState, aiResultText: String, detectorState: String, capturedImage: android.graphics.Bitmap?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Animated pulse dot
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(if (aiState == AiState.ANALYZING) 300 else 1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "pulse"
                )
                
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (aiState == AiState.ANALYZING) Color(0xFFFF9800).copy(alpha = alpha) else Color(0xFF4CAF50).copy(alpha = alpha))
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "AI COPILOT STATUS: ${if(aiState == AiState.ANALYZING) "ANALYZING..." else "IDLE"}",
                    color = if (aiState == AiState.ANALYZING) Color(0xFFFF9800) else Color(0xFF4CAF50),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x3300FF00), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = when(aiState) {
                        AiState.IDLE -> "> SYSTEM READY.\n> WAITING FOR TELEMETRY ANOMALIES...\n> CURRENT STATE: $detectorState"
                        AiState.ANALYZING -> "> INGESTING HIGH-G TELEMETRY...\n> EXTRACTING JERK & GYRO CORROBORATION...\n> RUNNING ON-DEVICE INFERENCE..."
                        AiState.READY -> aiResultText
                        AiState.FAILED -> "> INFERENCE FAILED. FALLBACK TO DETERMINISTIC MODEL."
                    },
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                
                if (capturedImage != null && aiState == AiState.READY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = capturedImage.asImageBitmap(),
                        contentDescription = "Camera Verification",
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencyCountdownOverlay(secondsRemaining: Int, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8B0000).copy(alpha = 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "ARE YOU OKAY?",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "Incident detected. Emergency contacts will be notified automatically in:",
                color = Color(0xFFFFCDD2),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(4.dp, Color(0xFFD32F2F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$secondsRemaining",
                    color = Color(0xFFD32F2F),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("I'M GOOD — CANCEL ALERT", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
