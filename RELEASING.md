<!-- Modified by Lunoco contributors on 2026-11-22 for the stable sideload update protocol. -->
# Stable release process

The permanent asset address is [Lunoco.apk](https://github.com/rgordonSJO/lunoco/releases/latest/download/Lunoco.apk). Downloader code **7639133** targets it. Keep that asset name with each stable release.

1. Review complete corresponding source, modified-file notices, actual shipped native components and artwork licensing. Freeze the tested source and choose an explicit increasing numeric Android versionCode and semantic versionName.
2. Run focused host regressions and approved on-device playback and upgrade checks. Verify the non-debuggable Performance APK, original signer, package, native libraries, permissions, matching R8 mapping and exact SHA-256.
3. Generate `SHA256SUMS.txt` and `Lunoco-update.json` from that sealed APK. The update manifest requires `schema` equal to 1, the application package as `packageName`, the APK’s `versionName`, numeric `versionCode`, and lowercase `sha256`. Verify the tag equals `v` plus versionName and that the release asset byte count and API digest match the APK.
4. After approval, push the reviewed source commit and upload the sealed `Lunoco.apk`, checksum list, update manifest, verified corresponding-source archive and native notice archive to a draft for that commit. After verifying the draft assets, publish a stable release, verify its tag and public assets, then mark it latest. Keep the prior release and source tag for recovery.
5. Independently download the tag and permanent assets, verify hashes and signing, and confirm Downloader still reaches the stable file.

For the initial upgrade from public 10B.2, use Android’s manual update installer. Later stable sideload builds can check the official repository feed, verify the update metadata and signer, and ask Android to show the installation confirmation. Existing builds with a local versionName require the manual bridge. Both semantic version and versionCode must increase for the in-app offer. Keep private keys, local install records and R8 mappings out of public source and release assets.
