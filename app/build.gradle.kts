plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val nativeStage = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
val attributionSources = listOf(
    rootProject.file("third_party/llama.cpp-LICENSE"),
    rootProject.file("third_party/NOTICE.md"),
)

val verifyNativeStage by tasks.registering {
    group = "verification"
    description = "Read-only verification of the pinned native stage before Android builds."
    // No cached output: receipts, payload bytes and incomplete-import markers
    // must be checked even when all downstream APK tasks are up to date.
    outputs.upToDateWhen { false }
    doLast {
        val pythonCommand = providers.gradleProperty("nativeVerifier.python").getOrElse("python")
        try {
            for (source in attributionSources) {
                check(source.isFile && source.length() > 0) {
                    "Missing or empty upstream attribution source: $source"
                }
            }
            val verification = providers.exec {
                workingDir(rootProject.projectDir)
                commandLine(
                    pythonCommand,
                    rootProject.file("scripts/import_llama_release.py").absolutePath,
                    "--verify-only", "--staging-dir", nativeStage.asFile.absolutePath,
                )
                isIgnoreExitValue = true
            }
            print(verification.standardOutput.asText.get())
            System.err.print(verification.standardError.asText.get())
            check(verification.result.get().exitValue == 0) { "Read-only native verifier rejected the stage" }
        } catch (failure: Exception) {
            throw GradleException(
                "Native stage verification failed: ${failure.message} " +
                    "(Python executable: $pythonCommand). Stage the pinned archive with " +
                    "python scripts/import_llama_release.py <archive.tar.gz>; " +
                    "use -PnativeVerifier.python=<python-executable> if needed.",
                failure,
            )
        }
    }
}

val stageVerifiedNative by tasks.registering(Sync::class) {
    dependsOn(verifyNativeStage)
    from(nativeStage) {
        include("*.so")
        into("arm64-v8a")
    }
    into(layout.buildDirectory.dir("generated/verifiedNative"))
}

val generateAttributionAssets by tasks.registering(Sync::class) {
    dependsOn(verifyNativeStage)
    from(attributionSources[0]) { rename { "THIRD_PARTY_LICENSE.txt" } }
    from(attributionSources[1]) { rename { "THIRD_PARTY_NOTICES.txt" } }
    into(layout.buildDirectory.dir("generated/attributionAssets"))
}

// This receipt is test-only; the production APK retains only attribution assets.
val generateSmokeTestAssets by tasks.registering(Sync::class) {
    dependsOn(verifyNativeStage)
    from(rootProject.file("third_party/llama.cpp.json")) { rename { "native-provenance.json" } }
    into(layout.buildDirectory.dir("generated/smokeTestAssets"))
}

android {
    namespace = "io.github.greprohan.llamadroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.greprohan.llamadroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only the verified CPU ARM64 payload is packaged.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets.getByName("main") {
        // Package only the verified ABI snapshot, never transaction backups,
        // receipts, orphan candidates or sibling ABI directories from staging.
        jniLibs.setSrcDirs(listOf(stageVerifiedNative))
        assets.srcDir(generateAttributionAssets)
    }
    // Identical pure-JVM probe/fake-Process cases execute on the host and compile
    // for ART instrumentation. They are never part of the production APK.
    sourceSets.getByName("test").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("androidTest") {
        java.srcDir("src/sharedTest/java")
        assets.srcDir(generateSmokeTestAssets)
    }

    packaging {
        jniLibs {
            // Compress native ZIP members and request package-manager extraction.
            // Actual installed permissions/linking still require the S25 test.
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }
}

tasks.named("preBuild") {
    dependsOn(stageVerifiedNative, generateAttributionAssets, generateSmokeTestAssets)
}

dependencies {
    // Platform Views UI; only the approved test dependencies are needed.
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
