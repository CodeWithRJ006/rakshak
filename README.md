# 🛡️ RAKSHAK: Automated Crash Detection & SOS System

**RAKSHAK** is a high-reliability Android background service designed to instantly detect vehicular crashes via hardware sensors (Accelerometer & Gyroscope) and dispatch emergency SMS alerts with geolocation, without relying on external cloud dependencies.

Built for robustness, speed, and safety, Rakshak operates locally on the device to ensure maximum privacy and immediate response when every second counts.

---

## 🏗️ System Architecture (10/10)

The core architecture isolates low-level hardware sensor polling from the mathematical detection algorithms and emergency alert dispatcher. This separation of concerns ensures that the SensorService remains responsive while handling rapid asynchronous data streams (50Hz).

`mermaid
flowchart TD
    %% Core Inputs
    A1[Accelerometer] -->|SensorEvent| B1(SensorRingBuffer)
    A2[Gyroscope] -->|SensorEvent| B2(SensorRingBuffer)
    
    subgraph P0 Pipeline Component
        B1 -->|Snapshot Flow| C{CrashDetector}
        B2 -->|Snapshot Flow| C
        C -->|StateFlow| D[P0Pipeline Loop]
        
        %% External Trigger (Testing / Voice / AI)
        ExtTrigger[Physical UI Trigger / Voice SOS] -->|ACTION_SIMULATE_CRASH| C
    end
    
    subgraph Emergency Dispatch
        D -->|on CONFIRMED| E[AlertSender]
        
        %% Adapters
        E -.->|Zero-blocking Request| L[AndroidLocationController]
        L -.->|Cached Location| E
        
        E -->|SMS Dispatch| S[AndroidSmsController]
        S -->|SmsManager| Network((Cellular Network))
    end
    
    %% Output to UI
    D -->|Status Updates| UI[Readiness Dashboard]
`

---

## 🚦 Crash Detector State Machine

Rakshak implements a mathematically sound, time-gated finite state machine to prevent false positives (like dropping the phone or sudden stops) and guarantee exactly-once emergency alert transmission per incident.

`mermaid
stateDiagram-v2
    [*] --> MONITORING
    
    MONITORING --> IMPACT_CANDIDATE : Acceleration Spike \n(> Threshold)
    
    IMPACT_CANDIDATE --> MONITORING : Candidate Timeout \n(No Jerk detected)
    IMPACT_CANDIDATE --> CONFIRMING : High Jerk Detected
    
    CONFIRMING --> MONITORING : Interrupted / Excessive Movement
    CONFIRMING --> CONFIRMED : Sustained Resting \n(Persistence Window met)
    
    CONFIRMED --> ALERTED : SMS/SOS Dispatched
    
    ALERTED --> COOLDOWN : Enter safe delay
    COOLDOWN --> MONITORING : Cooldown Expired
`

---

## 🚀 Key Features Built Thus Far

### 1. Robust P0 Pipeline (Sense → Detect → Alert)
- **Zero-Blocking SOS Path**: The SMS dispatch strictly enforces a zero-wait architectural rule. Acquiring GPS location happens asynchronously; if unavailable instantly, the SMS fires immediately with a "Location unavailable" fallback to prioritize network transmission speed.
- **Memory-Safe Ring Buffers**: Employs fixed-capacity, rolling time-window structures (SensorRingBuffer.kt) to continually record 4-second blocks of hardware events at 50Hz without unconstrained memory expansion or heavy GC pauses.
- **Foreground Service Survival**: Runs under Android 14 FOREGROUND_SERVICE_TYPE_SPECIAL_USE to prevent OS termination during deep sleep or screen-off conditions.

### 2. Hackathon & Testing Safeties
- **Demo Mode vs. Real Mode**: Features a global AppMode switch preventing accidental spam or cost overhead. DEMO_MODE fully bypasses the SmsManager and evaluates exact application logic locally, while logging output.
- **Physical-Device Mocking**: A built-in "Simulate Crash" trigger allows developers and judges to traverse the authentic P0 pipeline state machine on real hardware without physically dropping or crashing the device. 

### 3. Comprehensive Pure-JVM Testing Strategy
- To counter CI network restrictions preventing Robolectric execution, business logic algorithms, exactly-once pipeline routing, and bounded state transitions are built with 100% mocked interfaces using Mockito and custom coroutine dispatchers. 
- Over 46 pure JUnit algorithmic tests protect the rolling buffer mathematics and detection thresholds seamlessly.

---

## 🛠️ Tech Stack & Dependencies
- **Language**: Kotlin 1.9+
- **Architecture**: MVVM, Unidirectional Data Flow (UDF) using Kotlin StateFlow and SharedFlow.
- **Concurrency**: Kotlin Coroutines (Dispatchers.Default for math/algorithms, SupervisorJob for isolated pipeline lifecycles).
- **Core APIs**: SensorManager, SmsManager, Google Play Services FusedLocationProviderClient.
- **UI Framework**: Android ViewBinding, ConstraintLayout, Material Components.
- **Testing**: JUnit4, Mockito-Kotlin, Coroutines Test.

---

## 💻 Getting Started

### Building the Project
Clone the repository and compile using the Gradle wrapper:

`ash
git clone https://github.com/CodeWithRJ006/rakshak.git
cd rakshak
./gradlew assembleDebug
`

### Running Tests
Execute the entire JVM suite (no emulator required):
`ash
./gradlew testDebugUnitTest
`

### Physical Device Validation
1. Deploy to a physical device (./gradlew installDebug).
2. Accept the SMS and Location permission prompts.
3. Observe the "P0 Pipeline Status" on the main dashboard (Service: Running, Detector: MONITORING).
4. Type your cell number into the "Test Contact" field and hit **Simulate Crash**.
5. Observe the live state transitions and immediate delivery of the SOS SMS.
