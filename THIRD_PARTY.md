# Attribution and dependency sources

Lunoco derives from [Wholphin](https://github.com/damontecres/Wholphin), by damontecres and contributors, under GNU GPL version 2. The full upstream license is retained in LICENSE. Existing source notices, translated strings and in-app About/Licenses acknowledgments remain intact.

This repository contains the modified application source, resources, schemas, protobuf definitions, Seerr API specification/templates, Gradle wrapper/build scripts, and MPV stub used for the initial release. The manifest records its development source identity. No private Git history is required to build the application.

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
