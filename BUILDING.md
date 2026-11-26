<!-- Modified by Lunoco contributors on 2026-11-22 for the stable build. -->
# Build Lunoco

Use the checked-in Gradle 9.8.2 wrapper, a compatible JDK 25 toolchain, Android SDK platform 37 and Android Build Tools 36.0.0. Set ANDROID_HOME to the SDK or use an ignored local.properties. The checked-in version catalog and application build declare dependency and generation inputs. The app source, schemas, resources, license inputs and Seerr API generation files are in this tree.

On Windows PowerShell:

```powershell
.\gradlew.bat :app:testDefaultDebugUnitTest
.\gradlew.bat :app:assembleDefaultPerformance '-Plunoco.releaseVersion=0.11.1' '-Plunoco.releaseVersionCode=5'
```

The second command uses the v0.11.1 release version parameters. The [v0.11.1 release](https://github.com/rgordonSJO/lunoco/releases/tag/v0.11.1) includes corresponding source, a recipient relinking kit and native notices. The corresponding source build uses the committed derived artwork, the MPV stub and public Maven dependencies. Leave optional extension credentials and local FFmpeg, AV1 or MPV AARs out of this configuration.

The Performance variant uses R8 and resource shrinking and has application ID `com.ryan.lunatv.jellyfin.performance`. Its existing release pipeline uses the original maintainer signing identity. A locally generated development key can build a local testing binary. It cannot update the public app signed with the maintainer’s key. Keep signing material outside the source tree. Verify the APK, packaging configuration and source-to-binary record before a release. Build timestamps and a different signer can change the output bytes on another machine.
