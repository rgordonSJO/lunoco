# Lunoco — Jellyfin for Google TV

A clean, simple Jellyfin experience designed for Google TV.

Lunoco combines cinematic movie discovery, compact Continue Watching cards and polished D-pad navigation. Bring your own Jellyfin server and media.

### Install on Google TV

**Downloader Code: 7639133**

1. Open the Google Play Store on your Google TV.
2. Install Downloader by AFTVnews.
3. Open Downloader and enter code 7639133.
4. Download the Lunoco APK.
5. If prompted, allow Downloader permission to install unknown apps.
6. Install and open Lunoco.
7. Connect Lunoco to your Jellyfin server.

**Direct APK download:**

https://github.com/rgordonSJO/lunoco/releases/latest/download/Lunoco.apk

**Future updates:** Use the same Downloader code to download the latest stable APK. Install it over the existing Lunoco installation.

## Features

- Movie-only Spotlight with a daily stable lineup and ten-second rotation that pauses while its controls have focus.
- Continue Watching and Recently Added; series entries open their overview with Play focused.
- Curated studio and genre discovery on Home and in Movies/Kids Movies Explore.
- Home, Movies, Shows, Live TV and Home Movies navigation, with family-library and DVR dropdowns.
- Smart Back navigation that restores your browsing position and reveals Spotlight from scrolled Home.
- Simple Playback, Connections and About settings, with retained Advanced options.

Playback uses Media3/ExoPlayer. Working MPV and the optional FFmpeg/AV1 extensions are not included. In-app Wholphin update checks remain disabled; use the Downloader code or direct link above for Lunoco updates.

## Release and compatibility

The current release is [v0.10.2](https://github.com/rgordonSJO/lunoco/releases/tag/v0.10.2). See [release identity](RELEASE-IDENTITY.json) for its checksum, signer and technical Android version information. Package: `com.ryan.lunatv.jellyfin.performance`.

Tested on an onn. 4K Plus Google TV running Android 14 at 1920×1080. Compatibility with every television, codec and server configuration is not established. Rendering performance remains under investigation. Some native screenshot captures intermittently omit top-navigation graphics; a live-display defect has not been confirmed.

## Source, license and credits

Lunoco is a modified version of **Wholphin**, created by damontecres and contributors, and distributed under [GNU GPL version 2](LICENSE). Original source notices, translations and third-party acknowledgments are retained. Jellyfin, its SDK developers, the official Jellyfin Android TV contributors and the libraries listed in the in-app license screen made this work possible.

Upstream project: [Wholphin](https://github.com/damontecres/Wholphin). All installation and release links above point to Lunoco.

The app code/resources correspond to the published APK's verified development source. See [BUILDING.md](BUILDING.md), [RELEASING.md](RELEASING.md) and [THIRD_PARTY.md](THIRD_PARTY.md) for build instructions, the release process and dependency acknowledgments.
