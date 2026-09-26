# ?? RAKSHAK: AI-Powered P0 System Readiness & Telemetry

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Tests](https://img.shields.io/badge/tests-48%2F48%20passed-success)
![Tech](https://img.shields.io/badge/tech-Kotlin_|_Compose_|_React-blue)
![AI](https://img.shields.io/badge/AI-On--Device_Gemma-purple)
![Security](https://img.shields.io/badge/security-HMAC%20SHA--256-red)

**Rakshak** (meaning *Protector*) is a high-reliability, deterministic two-wheeler crash detection and emergency alerting system. Designed for real-time edge computing, it processes high-frequency sensor data, corroborates impacts with gyroscopic telemetry, dispatches non-blocking SOS alerts, and provides **On-Device AI Incident Analysis** via Google's Gemma model.

This project was engineered for **Hackathon Excellence**, demonstrating production-grade architecture, hardware lifecycle management, multimodal AI, and a companion React dashboard.

---

## ? Key Features & The "Wow" Factor

1. **Deterministic State Machine**: Eliminates false positives by evaluating Magnitude, Jerk, and Gyroscope rotation across a strict 6-stage lifecycle (`MONITORING` ? `IMPACT_CANDIDATE` ? `CONFIRMING` ? `CONFIRMED` ? `ALERTED` ? `COOLDOWN`).
2. **On-Device AI Copilot (Gemma 2B via MediaPipe)**: Uses RAG-lite context grounding (baking real NHTSA & WHO crash statistics and few-shot examples into the prompt) to generate expert medical/mechanical severity assessments locally. No cloud latency.
3. **Voice Copilot (STT ? LLM ? TTS)**: Hands-free interaction. Riders or bystanders can ask the app questions via speech (e.g., "Is the rider okay?"), which the AI answers contextually using the live crash telemetry.
4. **Multimodal Camera Verification**: Uses Android CameraX for a headless, zero-UI frame capture post-crash, feeding visual context (simulated VLM) into the AI assessment to verify rider separation from the vehicle.
5. **Cryptographic Audit Trail (HMAC-SHA256)**: Every state transition and crash event is hashed and logged into a secure local SQLite vault, creating a tamper-proof evidence chain for insurance claims and forensics.
6. **Live Web Dashboard**: The Android app POSTs real-time JSON payloads to a local React/Express dashboard via `P0Pipeline`, which renders the incident details, AI summary, and allows 1-click PDF exports using `jsPDF`.

---

## ?? System Architecture

### Complete System Flow
```plantuml
@startuml
skinparam componentStyle rectangle
skinparam backgroundColor transparent

package "Android Edge Device (Rakshak App)" {
  [Hardware Sensors\n(Accel, Gyro, GPS)] as Sensors
  [CameraX (Headless)] as Camera
  [Speech (STT/TTS)] as Voice
  
  [SensorService\n(Ring Buffers)] as Service
  [CrashDetector\n(State Machine)] as Detector
  
  [MediaPipe GenAI\n(Gemma 2B SLM)] as AI
  [HMAC Vault\n(SHA-256)] as Logger
  
  Sensors --> Service
  Service --> Detector
  Camera --> AI : Visual Context
  Voice --> AI : Audio Query
  Detector --> AI : Telemetry Data
  Detector --> Logger : State Hashes
}

package "Emergency & Web" {
  [SMS Manager] as SMS
  [React + Express Dashboard] as Dashboard
}

Detector --> SMS : P0 Non-blocking Alert
Detector --> Dashboard : HTTP POST (JSON)
Logger --> Dashboard : Chain Integrity
AI --> Dashboard : Generated Summary
@enduml
```

### The AI Grounding Pipeline
```plantuml
@startuml
scale 600 width
skinparam stateBackgroundColor #E8F5E9
skinparam stateBorderColor #2E7D32

[*] --> IncomingTelemetry : Peak Gs, Jerk, Gyro Rad/s
IncomingTelemetry --> GroundingContext : Append NHTSA/WHO Stats\nAppend Few-Shot Examples
GroundingContext --> Gemma2B : Full Inference Prompt
Gemma2B --> UI : Live Hacker Terminal Output
Gemma2B --> Dashboard : JSON Payload Summary
@enduml
```

---

## ?? Tools & Tech Stack

### Android App (Edge Device)
- **Language & UI**: Kotlin, Jetpack Compose (Material 3, Staggered Animations)
- **AI / ML**: `com.google.mediapipe:tasks-genai` (On-Device SLM Inference targeting Gemma 2B INT4)
- **Sensors & Hardware**: `SensorManager` (Continuous Ring Buffers), CameraX (Headless Capture), `SpeechRecognizer` & `TextToSpeech`
- **Concurrency**: Kotlin Coroutines, StateFlows, and `ForegroundService` with `specialUse` type.
- **Security**: `javax.crypto.Mac` (HMAC-SHA256), Android Keystore, SQLite.

### Web Dashboard (Command Center)
- **Frontend**: React.js, Vite
- **Backend**: Node.js, Express.js (REST API endpoint `POST /api/incident`)
- **Export capabilities**: `jsPDF` for instant incident report generation

---

## ? Testing & Validation

- **Unit Tests**: 48/48 Passing JVM tests covering the State Machine, Summary Generator, HMAC encryption, and Coroutine timeouts. Includes dedicated `LlmInferenceTest` for checking model latency and load times.
- **Hardware Simulation**: The App UI features buttons to inject synthetic high-G traces (Frontal Collision vs. High-Side Ejection) directly into the sensor buffers to trigger accurate state machine transitions.
- **Graceful Fallbacks**: 
  - If the AI model times out (e.g., >3s for Summary, >15s for analysis) or fails, the system instantly falls back to deterministic logic strings. 
  - If GPS fails or times out, SMS dispatches immediately with fallback text to guarantee P0 alerting speed.

---

### Built By
*Designed and Engineered for Hackathon Victory.*
