# Build Instructions

## Prerequisites

### Java

| Requirement | Current status |
|---|---|
| JDK 17 (full, with `javac`) | **Not installed** — only a JRE is available at `D:\softwares\jre\jdk-17.0.20.1+1-jre` |
| `JAVA_HOME` set to JDK 17 root | **Not set** |

Install a full JDK 17 (e.g. Eclipse Temurin 17) and set `JAVA_HOME` before building. The JRE alone is sufficient for running the Gradle wrapper but not for compiling the Android project.

### Android SDK

| Requirement | Current status |
|---|---|
| SDK command-line tools at `D:\softwares\android-sdk\cmdline-tools\latest` | **Present** |
| Platform `android-35` | **Not installed** |
| Build tools `35.0.0` | **Not installed** |

Install the missing SDK components:

```powershell
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" ^
    "platforms;android-35" "build-tools;35.0.0"
```

### NDK (for artifact inspection only)

| Requirement | Current status |
|---|---|
| NDK r27 (27.2.12479018) at `D:\softwares\android-sdk\ndk\27.2.12479018` | **Installed** |
| `llvm-readelf` / `llvm-nm` | **Available** at `D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\` |

The NDK is required for ELF inspection of native payloads (T1). It is not needed to compile the APK.

### Gradle Wrapper

The project ships an official Gradle 8.10.2 wrapper:

| File | Source | SHA-256 |
|---|---|---|
| `gradlew` | `gradle/gradle@v8.10.2` | — |
| `gradlew.bat` | `gradle/gradle@v8.10.2` | — |
| `gradle/wrapper/gradle-wrapper.jar` | `gradle/gradle@v8.10.2` | `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046` |
| Distribution ZIP | `services.gradle.org` | `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26` (in `gradle-wrapper.properties`) |

The wrapper validates the distribution SHA-256 automatically via `distributionSha256Sum` in `gradle-wrapper.properties`.

## Building

After installing the prerequisites:

```powershell
./gradlew --no-daemon clean :app:assembleDebug
```

This produces `app/build/outputs/apk/debug/app-debug.apk`.

## Verifying the wrapper

```powershell
# Wrapper version check
./gradlew --version
# Expected: Gradle 8.10.2, Kotlin 1.9.24, Launcher JVM 17

# Java version check
java -version
# Expected: openjdk version "17.0.x"

# SDK components
& "D:\softwares\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat" --list_installed
# Expected: platforms;android-35, build-tools;35.0.0

# NDK tooling
& "D:\softwares\android-sdk\ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe" --version
# Expected: LLVM version 18.x
```

## Unsupported prerequisites

| Prerequisite | Status |
|---|---|
| Full JDK 17 (with `javac`) | **Not installed** — JRE only available |
| Android SDK platform 35 | **Not installed** |
| Android SDK build-tools 35.0.0 | **Not installed** |

These are required for T3 (minimal APK build) but not for T2 (wrapper verification).
