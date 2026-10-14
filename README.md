# Audio Transcriber — Advanced Audio Transcription Powered by Gemini 3.5 Transcribe

**Developer:** Sujan Rai • Nepal  
**Platform:** Android (API 24+)  
**UI Framework:** Jetpack Compose + Material Design 3  
**Architecture:** MVVM + Clean Architecture (Data / Domain / Presentation)  

---

## 🎯 Overview

**Audio Transcriber** is a flagship Android application delivering speech-to-text intelligence with Google's **Gemini 3.5 Transcribe** models:
* **Live Streaming:** Real-time, sub-second latency streaming transcription over bidirectional WebSockets using `gemini-3.5-transcribe-live` (4.0% Word Error Rate).
* **Pre-Recorded Audio:** High-fidelity audio transcription using `gemini-3.5-transcribe` (2.6% Word Error Rate) with speaker diarization for up to 8 distinct speakers and word-level timestamps.
* **Smart Transcription:** Automatic filler-word elimination ("uh", "um"), stutter removal, self-correction handling, and sentence punctuation.
* **Multilingual:** 85+ supported languages with automatic detection and custom terminology vocabulary injection (up to 1,000 terms).
* **Deep Accessibility:** WCAG AAA contrast ratio compliance, TalkBack live region announcements (`LiveRegionMode.Polite`), expandable 56dp touch targets, and color-blind safe palettes.

---

## 🏗️ Architecture

Audio Transcriber adheres strictly to **Clean Architecture** and **MVVM** principles:

```
app/src/main/java/com/blindtechnexus/audiotranscriber/
├── AudioTranscriberApplication.kt       # Application setup, notification channels & dependency tree
├── MainActivity.kt               # Jetpack Navigation host, edge-to-edge insets & runtime permissions
├── domain/                       # Core domain models (Transcripts, Settings, Audio Configs)
│   └── model/
├── data/                         # Data layer (Remote, Local DB, Audio Capture)
│   ├── local/
│   │   ├── database/             # Room Database (AudioTranscriberDatabase)
│   │   ├── dao/                  # TranscriptDao with reactive Kotlin Flow
│   │   ├── entity/               # TranscriptEntity with JSON serialization
│   │   ├── preferences/          # UserPreferencesManager for theme & transcription settings
│   │   └── security/             # SecureKeyStorage via EncryptedSharedPreferences (AES-256)
│   ├── remote/
│   │   ├── GeminiWebSocketClient.kt # BidiGenerateContent live audio streaming client
│   │   ├── GeminiTranscribeApiClient.kt # REST Interactions API for files & AI summarization
│   │   └── model/                # Protocol schemas for live media chunks & events
│   ├── audio/
│   │   ├── AudioRecordManager.kt # 16 kHz 16-bit Mono PCM circular buffer audio capture
│   │   └── VoiceActivityDetector.kt # RMS energy voice activity detection (Low/Med/High/Auto)
│   └── repository/
│       └── TranscriptRepository.kt # Room queries, search, and TXT/SRT/VTT export
├── service/
│   └── TranscriptionForegroundService.kt # Android 14+ FOREGROUND_SERVICE_MICROPHONE service
└── ui/                           # Presentation layer (Jetpack Compose M3)
    ├── theme/                    # Dynamic Color, 10+ preset palettes, Typography & Shapes
    ├── accessibility/            # Accessible touch targets, live region & heading semantics
    ├── components/               # Canvas Waveform Visualizer, Speaker Diarization Timeline
    ├── welcome/                  # First-launch onboarding with API key verification
    ├── main/                     # Hub screen with Live, Files, and History tabs
    └── settings/                 # Customization engine (VAD, Themes, Key rotation)
```

---

## 🔑 Gemini API Key Security

Audio Transcriber never hardcodes API keys in the APK. User keys are handled securely:
1. **Hardware-Backed Cryptography:** Keys are encrypted on-device using AndroidX Security Crypto's `EncryptedSharedPreferences` with an AES-256-GCM `MasterKey`.
2. **Lightweight Validation:** On input, the key is verified against the Gemini models endpoint (`GET /v1beta/models`) with immediate UI feedback.
3. **Masked Display:** Keys are displayed masked (e.g. `AIza...xYz9`).
4. **Key Rotation:** Users can update or clear their key anytime in Settings without altering saved transcript history.

---

## 🎙️ Live Transcription (WebSocket Protocol)

Audio Transcriber connects to:
```
wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key={API_KEY}
```
1. **Setup Phase:** Emits setup payload configuring `models/gemini-3.5-transcribe-live`, `responseModalities=["TEXT"]`, and optional language code and custom vocabulary.
2. **Audio Streaming:** Captures raw 16,000 Hz, 16-bit mono PCM via `AudioRecord`, encodes chunks in Base64 `mediaChunks`, and streams them continuously.
3. **Voice Activity Detection (VAD):** Emits audio chunks actively while speech is detected, conserving bandwidth during silence.
4. **Session Protection:** Monitors 10-minute session limits with a warning at 9 minutes and auto-save at 10 minutes.

---

## 📁 File Transcription & Speaker Diarization

* **Formats:** MP3, WAV, M4A, OGG, FLAC, AAC (up to 1 hour, or 30 minutes with diarization).
* **Diarization:** Distinguishes up to 8 speakers with color-coded badges and interactive timeline bar charts.
* **Export Options:** Plain Text (TXT), SubRip Subtitles (SRT), and WebVTT (VTT).
* **AI Enhancements:** One-tap Gemini summarization (action items, decisions) and real-time translation into 10+ target languages.

---

## ♿ Flagship Android Accessibility

* **Screen Readers:** Transcript feed incorporates `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` so new segments are announced smoothly.
* **Heading Semantics:** Speaker changes are tagged with `heading()` for TalkBack jump navigation.
* **Touch Targets:** Standardized to at least 48dp × 48dp, with an "Expanded Touch Targets" mode increasing interactive components to 56dp.
* **Contrast & Color-Blind Modes:** Contrast ratios exceed WCAG AA/AAA with dedicated palettes for Protanopia, Deuteranopia, and Tritanopia.

---

## 📦 Building & CI Releases

**App ID:** `com.blindtechnexus.audiotranscriber` • **App name:** Audio Transcriber

GitHub Actions (`.github/workflows/android-ci.yml`) builds the production
release on every push / pull request:

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleRelease     # 64-bit + 32-bit + universal APKs in one run
./ci/package_release_archives.sh app/build/outputs/apk/release release-artifacts
```

ABI splits produce one unsigned APK per architecture plus a universal APK
(`app-arm64-v8a-release-unsigned.apk`, `app-armeabi-v7a-release-unsigned.apk`,
`app-universal-release-unsigned.apk`). No signing configuration is kept in the
build: CI packages each APK into its own downloadable artifact —
`Audio-Transcriber-x64-release.zip`, `Audio-Transcriber-32-bit-release.zip`,
and `Audio-Transcriber-universal-release.zip` (with `SHA256SUMS.txt`) —
retained for 30 days. Sign an APK with your own keystore (e.g. `apksigner
sign`) before distributing it.
