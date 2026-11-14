# Build Lunoco

## Requirements

Use the checked-in Gradle wrapper (9.8.2). The daemon toolchain file requests JDK 25; install a compatible JDK or allow Gradle toolchain provisioning. Install Android SDK platform 37 and its build tools. Set ANDROID_HOME to your SDK, or create your own ignored local.properties containing sdk.dir. Network access is needed for declared Maven dependencies and generated Seerr API tooling.

Keep the dependency versions in gradle/libs.versions.toml. This tree uses Android Gradle Plugin 9.5.0, Kotlin 2.4.21 and Media3 1.11.1. Do not upgrade them when checking release correspondence.

## Commands

On Windows PowerShell (omit .bat on Linux/macOS):

```powershell
./gradlew.bat :app:testDefaultDebugUnitTest
./gradlew.bat :app:assembleDefaultPerformance -Plunatv.performanceBuildId=lunoco-navigation-10b2
```

The optimized Performance variant uses package com.ryan.lunatv.jellyfin.performance, resource shrinking and R8. Debug has a different package and is not an update for the public APK.

The five approved derived Lunoco image resources are committed; asset regeneration and the private original artwork are not needed to build. Do not replace the artwork.

No optional FFmpeg/AV1/MPV extension credentials or AARs are required for this release's configuration. The MPV module is an API stub, not a working player. Do not advertise optional extensions as included. Android/Media3 and other declared native dependencies still ship in the verified APK.

## Signing and reproduction

The Performance variant uses Gradle's local debug signing configuration. A fresh machine generates a different key and **cannot** produce an official in-place update. Only the maintainer's original private signing key produces the expected signer. Never commit, upload or share that key.

The public repository is a clean snapshot, not the private development history. Git-derived version metadata and build timestamps will differ on a fresh build; a byte-for-byte reproducible APK is not claimed. RELEASE-IDENTITY.json records the original binary. Production Kotlin, resources, schemas, protobufs, dependency declarations and application build script match development commit 7268ce4a1a0b62b4d9a1fc152159ae4e2693d786. Only a test address and publication documentation differ; the private developer-only asset verifier is omitted.

For future releases, explicitly set monotonically increasing versionCode and the intended versionName in app/build.gradle.kts before building; do not rely on the count of local Git tags. Follow RELEASING.md and verify the finished manifest/signer.
