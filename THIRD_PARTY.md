<!-- Modified by Lunoco contributors on 2026-11-22 to record native source references. -->
# Attribution and dependency sources

Lunoco derives from [Wholphin](https://github.com/damontecres/Wholphin), by damontecres and contributors, under GNU GPL version 2. The full upstream license is retained in LICENSE. Existing source notices, translated strings and in-app About/Licenses acknowledgments remain intact.

This repository contains the modified application source, resources, schemas, protobuf definitions, Seerr API specification/templates, Gradle wrapper/build scripts, and MPV stub used for v0.11.0. The manifest records its development source identity. No private Git history is required to build the application.

Third-party dependencies and exact versions are declared in gradle/libs.versions.toml and app/build.gradle.kts. Maven artifacts are fetched from Google Maven and Maven Central. Their upstream source distributions and licenses remain the governing terms for those components; Lunoco does not relicense third-party work.

Important source projects include:

- Jellyfin Kotlin SDK: https://github.com/jellyfin/jellyfin-sdk-kotlin
- Jellyfin Android TV (device-profile contributions): https://github.com/jellyfin/jellyfin-androidtv
- AndroidX / Media3: https://github.com/androidx/media
- ASS Android integration (io.github.peerless2012:ass-media:0.5.1): https://github.com/peerless2012/libass-android
- HDR utilities (com.suyashbelekar:exoplayerhdrutils:0.4.1): https://github.com/suyash192/ExoplayerHdrUtils
- libass: https://github.com/libass/libass
- FreeType: https://freetype.org/
- FriBidi: https://github.com/fribidi/fribidi
- Roboto fonts: the original font README and SIL Open Font License are retained under app/src/main/assets/font-roboto/.

Additional component notices and license texts are retained in app/config/libraries and app/config/licenses, and in the generated in-app license list. Some inherited notices describe optional components that are not bundled in this release (including MPV and optional FFmpeg/AV1 decoders); their presence in the notice catalog is not a capability claim.

When changing or replacing a native artifact, audit its actual linked components, exact source revision, build instructions and source-distribution obligations before release. Retain required notices and make corresponding modified source/build material available with the release. Never treat a license label alone as proof of complete native-source provenance.

## Native source references for v0.11.0

All 23 Phase 38C native entries were matched byte-for-byte to these declared dependency archives. The same payload was verified in the sealed v0.11.0 APK.

| Package entry | Maven artifact | Source reference |
| --- | --- | --- |
| `libandroidx.graphics.path.so` | `androidx.graphics:graphics-path:1.0.1` | [AndroidX Graphics](https://developer.android.com/jetpack/androidx/releases/graphics#1.0.1) |
| `libdatastore_shared_counter.so` | `androidx.datastore:datastore-core-android:1.2.1` | [AndroidX DataStore](https://developer.android.com/jetpack/androidx/releases/datastore#1.2.1) |
| `libass.so`, `libasskt.so`, `libc++_shared.so` | `io.github.peerless2012:ass-kt:0.5.1` | [ASS Android v0.5.1](https://github.com/peerless2012/libass-android/tree/504f64a325059ab5188c4c9d0eddc99c9356a23a) · [Pinned CMake submodule](https://github.com/peerless2012/libass-cmake/tree/d3f00a43ca66e42a2c34de964b1a7dbbfa9dbc8b) |
| `libdovi_android.so` | `com.suyashbelekar:exoplayerhdrutils:0.4.1` | [HDR Utils v0.4.1](https://github.com/suyash192/ExoplayerHdrUtils/tree/a791274f137b301fd9c0b7eb47d4fd18dbacbe30) |

The ASS Android native build uses static archives from libass, Expat, Fontconfig, FriBidi, FreeType, HarfBuzz and libunibreak. The pinned CMake tree specifies their revisions as Git submodules. Release review must pair the exact APK with the applicable source and notice materials and account for the static FriBidi linking terms. A Maven declaration and notice catalog do not establish a complete redistribution bundle on their own.

The [v0.11.0 corresponding-source archive](https://github.com/rgordonSJO/lunoco/releases/download/v0.11.0/Lunoco-0.11.0-corresponding-source.zip) includes the application source and recipient instructions to rebuild the ASS consumer against a modified FriBidi library. The [native notices archive](https://github.com/rgordonSJO/lunoco/releases/download/v0.11.0/Lunoco-native-notices.zip) includes the applicable license texts, attributions and HDR notice inventory. Both hashes are recorded in the [release checksum list](https://github.com/rgordonSJO/lunoco/releases/download/v0.11.0/SHA256SUMS.txt).

## v0.11.1 native materials

The v0.11.1 APK retains all 23 v0.11.0 native-library entries byte for byte and packages the same native notices. The [v0.11.1 release](https://github.com/rgordonSJO/lunoco/releases/tag/v0.11.1) provides the unchanged notices and the tested ASS/FriBidi relinking kit within the updated corresponding-source archive.
