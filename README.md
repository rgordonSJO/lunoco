<!-- Modified by Lunoco contributors on 2026-11-22 for the 0.11.0 source. -->
# Lunoco — Jellyfin for Google TV

Lunoco brings Jellyfin to Google TV with floating Home controls, movie discovery, and D-pad navigation. Press and hold Left or Right during playback to scrub through a movie. A quick tap uses your skip setting. Release to seek to the previewed position. Trickplay thumbnails remain visible briefly when available.

Series Details groups Season with the usual series actions. Press Up from an episode thumbnail to reach Season, and Down to return to the selected episode. The series overview is compact and can still be opened in full. Shows opened from Recently Added highlight the exact episode and bring its thumbnail and title into view.

## Install on Google TV

In Downloader by AFTVnews, enter code **7639133**. You can also [download Lunoco.apk](https://github.com/rgordonSJO/lunoco/releases/latest/download/Lunoco.apk) from the [official release](https://github.com/rgordonSJO/lunoco/releases/tag/v0.11.6). If Google TV prompts you, allow Downloader to install the APK.

Install over a compatible existing copy of Lunoco to retain your settings and Jellyfin connection. An update requires the same package and signing identity and a compatible version code. Keep the existing installation if Google TV reports a conflict.

For in-app updates, open **Settings → About → Check for updates** in Lunoco. Choose **Update** and confirm in the Google TV installer.

Connect Lunoco to your Jellyfin server. Lunoco supplies no media or server account. Playback uses Media3/ExoPlayer. The optional FFmpeg and AV1 extensions and working MPV are excluded from this release configuration.

## Source and credits

Lunoco is a modified version of [Wholphin](https://github.com/damontecres/Wholphin), by damontecres and contributors, distributed under [GNU GPL version 2](LICENSE). The source retains original author notices, translations and third-party acknowledgments. Thanks to the Jellyfin project, Jellyfin SDK and Android TV contributors.

- [Build the application](BUILDING.md)
- [Release process](RELEASING.md)
- [Third-party notices and source references](THIRD_PARTY.md)
- [Changes from the public base](MODIFICATIONS.md)
- [Corresponding source and native notices](https://github.com/rgordonSJO/lunoco/releases/tag/v0.11.6)
- [Latest released APK identity](RELEASE-IDENTITY.json)
