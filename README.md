# RAKSHAK (&#x930;&#x915;&#x94d;&#x937;&#x915;) - Personal Safety & Crash Detection Pipeline

RAKSHAK is an advanced Android-based personal safety pipeline designed for high-accuracy, zero-wait crash detection and SOS alerting.

## Core Architecture

RAKSHAK operates a continuous, lock-free sensor ingestion engine driving a multi-stage CrashDetector. The pipeline aggressively prioritizes SMS delivery over non-critical metadata (like high-accuracy location), enforcing a "zero-wait" emergency strategy.

### Component Flow Diagram

`mermaid
graph TD
    subgraph Hardware Layer
        A[Accelerometer] -->|SensorEvent| RingBufA[SensorRingBuffer - Accel]
        G[Gyroscope] -->|SensorEvent| RingBufG[SensorRingBuffer - Gyro]
    end

    subgraph Service Layer
        RingBufA -->|StateFlow| Service[SensorService]
        RingBufG -->|StateFlow| Service
        Service -->|Snapshot| Detector[CrashDetector]
    end

    subgraph Detection Engine
        Detector -->|Monitoring| M[Evaluate Magnitude]
        M -->|> 3G| IC[IMPACT_CANDIDATE]
        IC -->|Calculate Jerk| J[Evaluate Jerk & Gyro]
        J -->|> 100 m/s³ + Gyro Spike| C[CONFIRMING]
        C -->|Evaluate Resting 2s| CF[CONFIRMED]
    end

    subgraph Alert Pipeline
        CF -->|StateFlow Transition| P0[P0Pipeline]
        P0 -->|Exactly-Once Lock| AS[AlertSender]
        
        AS -->|Sync Request| Loc[AndroidLocationController]
        Loc -.->|Cached Only| AS
        
        AS -->|Format SMS| SMS[AndroidSmsController]
        SMS -->|SmsManager| Network[Cellular Network]
        
        AS -->|Async Log| HMAC[HMACIncidentLogger]
    end

    style CF fill:#f96,stroke:#333,stroke-width:2px
    style AS fill:#6f9,stroke:#333,stroke-width:2px
`

## State Machine

The core detection engine (CrashDetector) is built around a rigorous state machine designed to reject false positives (e.g., dropping the phone) while instantaneously confirming continuous-rest impacts.

`mermaid
stateDiagram-v2
    [*] --> MONITORING
    MONITORING --> IMPACT_CANDIDATE : Accel > 3G
    IMPACT_CANDIDATE --> MONITORING : Timeout (0.5s)
    IMPACT_CANDIDATE --> CONFIRMING : Jerk > 100 m/s³ + Gyro > 4.0 rad/s
    CONFIRMING --> MONITORING : Rest Interrupted / Timeout (3s)
    CONFIRMING --> CONFIRMED : 2s Continuous Rest
    CONFIRMED --> ALERTED : SMS Dispatched
    ALERTED --> COOLDOWN : Enter 10s Window
    COOLDOWN --> MONITORING : Cooldown Expired
`

## P0 Alert Priorities

1. **SMS First:** GPS location is fetched synchronously from the cache. If unavailable, the SMS fires immediately with a "Location unavailable" payload. It never blocks.
2. **Exactly-Once Execution:** Handled by a boolean lock in P0Pipeline that guarantees rapid consecutive transitions do not trigger duplicate SMS sends.
3. **Cryptographic Auditing:** HMACIncidentLogger runs asynchronously post-SMS dispatch, securing local incident logs with SHA-256 HMAC for forensic integrity without impacting the critical path.

## Synthetic Trace Testing

RAKSHAK supports physical-device validation without causing hardware damage.
- Send com.rakshak.action.SIMULATE_CRASH via adb to manually advance the state machine to CONFIRMED.
- Send com.rakshak.action.INJECT_TRACE to inject a 100% deterministic, synthetic accelerometer and gyroscope trace perfectly tuned to pass the IMPACT_CANDIDATE and CONFIRMING mathematical thresholds.
