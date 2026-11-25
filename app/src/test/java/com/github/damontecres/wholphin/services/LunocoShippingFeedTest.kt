package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.BuildConfig
import org.junit.Assert.*
import org.junit.Test

/** Shipping code must never inherit the disposable updater test feed or identity. */
class LunocoShippingFeedTest {
    @Test
    fun releaseApiIsTheOfficialRepository() {
        assertEquals(
            "https://api.github.com/repos/rgordonSJO/lunoco/releases/latest",
            LunocoReleaseProtocol.API,
        )
    }

    @Test
    fun releaseAssetUrlsRemainOnTheOfficialRepository() {
        assertEquals(
            "https://github.com/rgordonSJO/lunoco/releases/download/v1.0.1/Lunoco.apk",
            LunocoReleaseProtocol.assetUrl("v1.0.1", "Lunoco.apk"),
        )
    }

    @Test
    fun isolatedRepositoryIsRejectedByShippingDownloadPolicy() {
        assertFalse(
            LunocoReleaseProtocol.trustedDownload(
                "https://github.com/rgordonSJO/lunoco-updater-isolated-test-07/releases/download/v1.0.1/Lunoco.apk",
            ),
        )
    }

    @Test
    fun shippingBuildConfigCannotBeAnIsolatedTestApplication() {
        assertTrue(BuildConfig.APPLICATION_ID.startsWith("com.ryan.lunatv.jellyfin"))
        assertFalse(BuildConfig.APPLICATION_ID.startsWith("com.ryan.lunoco.updatertest"))
        assertFalse(BuildConfig::class.java.fields.any { it.name == "LUNOCO_ISOLATED_TEST" })
    }
}
