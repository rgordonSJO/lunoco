# Stable releases and the permanent Downloader address

Permanent asset URL:

https://github.com/rgordonSJO/lunoco/releases/latest/download/Lunoco.apk

GitHub documents this stable latest-release asset format:
https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases

Official Downloader code: **7639133**. It targets the permanent asset URL above. Reuse this code for future stable releases while the owner, repository and asset filename remain the same; do not replace its destination with a versioned download or release HTML page.

## Maintainer checklist

1. Use the reviewed public repository; never push private development history or bulk-copy ignored local files. Keep the original APK and exact matching R8 mapping privately backed up outside build output directories.
2. Audit staged changes, source history, dependency licenses/native source provenance, and all release attachments for secrets. Do not publish signing material, server URLs/accounts, private screenshots, diagnostics or R8 maps.
3. Set explicit defaultConfig.versionCode and versionName in app/build.gradle.kts. The initial APK has versionCode 1. The next release must use at least **2** and each later release must exceed the highest code ever publicly distributed. Replace the current tag-count-derived calculation in the release branch; tags are not a reliable release counter.
4. Build/test the new source locally with the original private signing key. Keep the package com.ryan.lunatv.jellyfin.performance and signer SHA-256 57a0d2a501b9a679311a88aad8d9b41a1b605119ce2cf638694dae2563cf1045. A newly generated debug key will fail this check.
5. Run release/Verify-Apk.ps1 against the finished APK with its computed SHA-256, expected versionCode and the previous public versionCode. Review versionName/label, approved artwork, native payloads and tests. Update RELEASE-IDENTITY.json and preserve the matching R8 map privately.
6. Commit/tag the corresponding complete source. Use a new version tag; never move the prior release tag or overwrite an existing asset. The release must be **published**, normal (not prerelease), and marked latest.
7. Upload the verified APK under the exact filename **Lunoco.apk**, with a SHA256SUMS.txt asset and concise notes. Do not rebuild the file after verifying it.
8. Download the public permanent URL without authentication to a separate file. Require successful HTTP response, APK ZIP content, the exact expected SHA-256 and valid Android signature/package.
9. Reuse the same Downloader code. Existing installations update in place; never ask users to uninstall as an upgrade step.

GitHub CLI example after an intentional local source push/tag:

```powershell
gh release create vNEXT /path/to/Lunoco.apk /path/to/SHA256SUMS.txt --repo rgordonSJO/lunoco --verify-tag --latest --title "Lunoco NEXT" --notes-file /path/to/release-notes.md
```

Replace vNEXT and paths with reviewed real values. For login use `gh auth login --hostname github.com --git-protocol https --web`; never paste a token into chat or a tracked file.

No automatic deployment workflow or signing-secret upload is configured. In-app upstream Wholphin update checks remain disabled.
