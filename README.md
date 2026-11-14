# Lunoco

A simplicity-first Jellyfin client for Android TV and Google TV, based on [Wholphin](https://github.com/damontecres/Wholphin).

Lunoco combines a cinematic movie-only Spotlight, compact Continue Watching, curated studio and genre discovery, and D-pad navigation. Bring your own Jellyfin server and media; this project supplies neither content nor server credentials.

## Install or update

Download [Lunoco.apk](https://github.com/rgordonSJO/lunoco/releases/latest/download/Lunoco.apk) using Downloader or a browser on your TV. Permit that installer to install unknown apps when Android asks, then confirm installation.

For a numeric Downloader code, use the official [AFTVnews shortener](https://go.aftvnews.com/) with the exact download link above. No code has been assigned by this repository.

For existing Lunoco installations, install as an update and keep app data. Do not uninstall to upgrade. Package: `com.ryan.lunatv.jellyfin.performance`. Future official updates must retain the signing certificate.

The initial release is `v0.10.2`; its Android version name remains `0.0.0-local-perf-lunoco-navigation-10b2`, versionCode 1. See [release identity](RELEASE-IDENTITY.json) for the checksum and signer. The existing verified binary was published without rebuilding.

## What is included

- Movie-only Spotlight with a daily stable lineup and ten-second automatic rotation that pauses while its controls have focus.
- Continue Watching and Recently Added; series entries open their normal overview with Play focused.
- Movies and Kids Movies Explore, studio/genre filtering, and discovery rows on Home.
- Home, Movies, Shows, Live TV and Home Movies navigation, with family-library and DVR dropdowns.
- Smart Back: Details restores your browsing position; Back on scrolled Home reveals Spotlight while preserving horizontal carousel positions.
- Playback, Connections and About settings, with retained Advanced options.

Playback uses the existing Media3/ExoPlayer implementation. This binary does **not** include working MPV or the optional FFmpeg/AV1 extensions. In-app Wholphin update checks remain disabled; update through this repository's stable download.

## Status and limits

Tested on an onn. 4K Plus Google TV running Android 14 at 1920×1080. Compatibility with every television, codec and server configuration is not established. Historical rendering performance remains under investigation. Some native screenshot captures intermittently omit top-navigation graphics; a live-display defect has not been confirmed. No bundled studio artwork is supplied outside the user's server metadata.

## Source, license and credits

Lunoco is a modified version of **Wholphin**, created by damontecres and contributors, and distributed under [GNU GPL version 2](LICENSE). Original source notices, translations and third-party acknowledgments are retained. Jellyfin, its SDK developers, the official Jellyfin Android TV contributors and the libraries listed in the in-app license screen made this work possible.

The app code/resources here correspond to the published APK's verified development source. Publication changes are confined to documentation, ignore rules, a documentation-IP test fixture and omission of a private developer-only asset verifier. No production Kotlin, Android resources, dependency versions or application build script were changed for publication.

See [BUILDING.md](BUILDING.md), [RELEASING.md](RELEASING.md) and [THIRD_PARTY.md](THIRD_PARTY.md). No signing key, account database, private device report or development history is included.
