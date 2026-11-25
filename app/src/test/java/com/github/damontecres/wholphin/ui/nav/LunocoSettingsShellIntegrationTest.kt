// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.nav

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import coil3.asImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import com.github.damontecres.wholphin.ui.detail.livetv.DvrScheduleContent
import com.github.damontecres.wholphin.ui.detail.livetv.FetchedPrograms
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialog
import com.github.damontecres.wholphin.ui.detail.livetv.TvChannel
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGridContent
import com.github.damontecres.wholphin.ui.detail.livetv.TvProgram
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.SimpleMediaStream
import com.github.damontecres.wholphin.ui.playback.StreamChoiceBottomDialog
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackControls
import com.github.damontecres.wholphin.ui.theme.LunocoContentTheme
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.data.model.*
import com.github.damontecres.wholphin.preferences.*
import com.github.damontecres.wholphin.services.*
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.components.CollectionFolderState
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewModel
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialogState
import com.github.damontecres.wholphin.ui.search.SearchResult
import com.github.damontecres.wholphin.ui.search.SearchState
import com.github.damontecres.wholphin.ui.search.SearchViewModel
import com.github.damontecres.wholphin.ui.preferences.user.PreferredLanguage
import com.github.damontecres.wholphin.ui.preferences.user.UserPreferencesViewModel
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.main.*
import com.github.damontecres.wholphin.ui.preferences.*
import com.github.damontecres.wholphin.ui.setup.InstallUpdateState
import com.github.damontecres.wholphin.ui.setup.ServerVersionSupported
import com.github.damontecres.wholphin.ui.setup.UpdateViewModel
import com.github.damontecres.wholphin.ui.setup.seerr.SwitchSeerrViewModel
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.*
import dagger.hilt.android.testing.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.jellyfin.sdk.model.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.UUID

/**
 * The real ApplicationContent -> NavDisplay -> NavDrawer -> JellyLuBrowseShell ->
 * DestinationContent -> PreferencesPage path. Only DI/controller data are substituted.
 * No fake navigation, wrapper padding, Settings host or preference rows are rendered.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class LunocoSettingsShellIntegrationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    @get:Rule(order = 3) val diagnostics = object : TestWatcher() {
        override fun starting(description: Description) {
            println("SETTINGS06B_CASE_BEGIN:${description.methodName}")
        }
        override fun finished(description: Description) {
            println("SETTINGS06B_CASE_END:${description.methodName}")
        }
    }
    private val navigation = NavigationManager()
    private val server = JellyfinServer(UUID(7, 1), "Test server", "https://settings.invalid", "10.11.0")
    private val user = JellyfinUser(id = UUID(7, 2), name = "Test profile", serverId = server.id, accessToken = null)
    private val data = MemoryPreferences(AppPreferencesSerializer().defaultValue.updateInterfacePreferences {
        showClock = false
        showLogos = false
    })
    private val preferencesVM = mockk<PreferencesViewModel>(relaxed = true)
    private val updateVM = mockk<UpdateViewModel>(relaxed = true)
    private val seerrVM = mockk<SwitchSeerrViewModel>(relaxed = true)
    private val drawerVM = mockk<NavDrawerViewModel>(relaxed = true)
    private val appVM = mockk<ApplicationContentViewModel>(relaxed = true)
    private val homeVM = mockk<HomeViewModel>(relaxed = true)
    private val movieVM = mockk<com.github.damontecres.wholphin.ui.detail.movie.MovieViewModel>(relaxed = true)
    private val playlistVM = mockk<AddPlaylistViewModel>(relaxed = true)
    private val spotlightVM = mockk<SpotlightViewModel>(relaxed = true)
    private val images = mockk<ImageUrlService>(relaxed = true)
    private val profileVM = mockk<UserPreferencesViewModel>(relaxed = true)
    private val pinsVM = mockk<NavDrawerPreferencesViewModel>(relaxed = true)
    private val libraryVM = mockk<CollectionFolderViewModel>(relaxed = true)
    private val searchVM = mockk<SearchViewModel>(relaxed = true)
    private val exploreVM = mockk<com.github.damontecres.wholphin.ui.discovery.MovieExploreViewModel>(relaxed = true)
    private val connectionsVM = mockk<com.github.damontecres.wholphin.ui.preferences.LunocoConnectionsViewModel>(relaxed = true)
    private val switchUserVM = mockk<com.github.damontecres.wholphin.ui.setup.SwitchUserViewModel>(relaxed = true)
    private val switchServerVM = mockk<com.github.damontecres.wholphin.ui.setup.SwitchServerViewModel>(relaxed = true)
    private val backdrop = mockk<BackdropService>(relaxed = true)
    private var libraryPosition = 0
    private val factoryCounts = mutableMapOf<Class<*>, Int>()
    private val failures = mutableListOf<String>()
    private var leanbackBefore07c: Boolean? = null
    private var inputMode07c: androidx.compose.ui.input.InputModeManager? = null
    private val errorTree = object : timber.log.Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.ERROR) failures.add(message + "\n" + t?.stackTraceToString().orEmpty())
        }
    }
    private val rows by lazy {
        List(3) { row ->
            HomeRowLoadingState.Success(StringStringProvider("Row $row"), List(24) { index ->
                val dto = BaseItemDto(id = UUID(row.toLong() + 1, index.toLong() + 1),
                    type = BaseItemKind.MOVIE, name = "Row $row movie $index")
                mockk<BaseItem>(relaxed = true) {
                    every { id } returns dto.id
                    every { type } returns dto.type
                    every { data } returns dto
                    every { title } returns dto.name!!
                    every { name } returns dto.name!!
                    every { imageUrlOverride } returns null
                    every { destinationOverride } returns null
                    every { destination(any()) } returns Destination.MediaItem(dto.id, dto.type)
                    every { destination(null) } returns Destination.MediaItem(dto.id, dto.type)
                }
            }, rowType = HomeRowConfig.RecentlyAdded(UUID(8, row.toLong())))
        }
    }

    @Before fun setup() {
        hilt.inject()
        timber.log.Timber.plant(errorTree)
        val app: Application = ApplicationProvider.getApplicationContext()
        val resourceFacade = mockk<WholphinApplication>(relaxed = true)
        mockkObject(WholphinApplication.Companion)
        every { resourceFacade.resources } returns app.resources
        every { resourceFacade.getString(any()) } answers { app.getString(firstArg()) }
        every { resourceFacade.getString(any(), *anyVararg()) } answers { app.getString(firstArg(), *secondArg<Array<Any>>()) }
        every { WholphinApplication.instance } returns resourceFacade

        every { preferencesVM.preferenceDataStore } returns data
        every { preferencesVM.navigationManager } returns navigation
        every { preferencesVM.currentUser } returns MutableStateFlow(user)
        every { preferencesVM.externalPlayers } returns MutableStateFlow(listOf(ExternalPlayerApp("System default", null, "")))
        every { preferencesVM.serverSupported } returns MutableStateFlow(null to ServerVersionSupported.SUPPORTED)
        every { preferencesVM.seerrConnection } returns MutableStateFlow(SeerrState())
        every { updateVM.state } returns MutableStateFlow(InstallUpdateState())
        every { updateVM.currentVersion } returns Version.fromString("0.0.0-local")

        val libraries = listOf("Movies" to CollectionType.MOVIES, "Shows" to CollectionType.TVSHOWS,
            "Kids Movies" to CollectionType.MOVIES, "Kids Shows" to CollectionType.TVSHOWS,
            "Live TV" to CollectionType.LIVETV, "Home Movies" to CollectionType.HOMEVIDEOS)
            .mapIndexed { index, (name, type) ->
                val id = UUID(9, index.toLong())
                ServerNavDrawerItem(id, name, Destination.MediaItem(id, BaseItemKind.COLLECTION_FOLDER), type)
            }
        every { drawerVM.serviceState } returns MutableStateFlow(NavDrawerItemState(items = libraries))
        every { drawerVM.navigationManager } returns navigation
        every { appVM.backdropService } returns backdrop
        every { backdrop.backdropFlow } returns MutableStateFlow(BackdropResult.NONE)
        every { profileVM.currentUserDto } returns MutableStateFlow(null)
        every { profileVM.userAppPreferences } returns MutableStateFlow(user.appPreferences)
        every { profileVM.audioLanguage } returns MutableStateFlow(PreferredLanguage())
        every { profileVM.subtitleLanguage } returns MutableStateFlow(PreferredLanguage())
        every { pinsVM.state } returns MutableStateFlow(emptyList())
        val folder = mockk<BaseItem>(relaxed = true) {
            every { title } returns "Movies"
            every { data } returns BaseItemDto(id = UUID(9, 0), name = "Movies",
                type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.MOVIES)
        }
        every { libraryVM.state } returns MutableStateFlow(CollectionFolderState(
            item = DataLoadingState.Success(folder), items = DataLoadingState.Success(rows.first().items),
            backgroundLoading = LoadingState.Success, viewOptions = ViewOptionsPoster))
        every { libraryVM.position } answers { libraryPosition }
        every { libraryVM.position = any() } answers { libraryPosition = firstArg() }
        every { movieVM.state } returns MutableStateFlow(com.github.damontecres.wholphin.ui.detail.movie.MovieState(
            loading = DataLoadingState.Success(BaseItem(rows.first().items.first()!!.data))))
        every { movieVM.serverRepository.currentUserDtoFlow } returns MutableStateFlow(null)
        every { homeVM.openHomeItem(any(), any()) } answers {
            val item = secondArg<BaseItem>()
            every { movieVM.state } returns MutableStateFlow(com.github.damontecres.wholphin.ui.detail.movie.MovieState(
                loading = DataLoadingState.Success(BaseItem(item.data))))
            navigation.navigateTo(item.destinationOverride ?: Destination.MediaItem(item.id, item.type))
        }
        every { homeVM.navigationManager } returns navigation
        every { homeVM.state } returns MutableStateFlow(HomeState(LoadingState.Success, LoadingState.Success, rows, HomePageResolvedSettings.EMPTY))
        every { homeVM.libraryItems } returns MutableStateFlow(libraries)
        every { exploreVM.state } returns MutableStateFlow(com.github.damontecres.wholphin.ui.discovery.MovieExploreState())
        every { playlistVM.playlistState } returns MutableStateFlow(PlaylistLoadingState.Pending)
        every { spotlightVM.available } returns MutableStateFlow(false)
        every { spotlightVM.entryPending } returns MutableStateFlow(false)
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = emptyList()))
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns null

        every { connectionsVM.server } returns MutableStateFlow(server)
        every { connectionsVM.navigation } returns navigation
        every { switchUserVM.state } returns MutableStateFlow(com.github.damontecres.wholphin.ui.setup.SwitchUserState(
            loading = LoadingState.Success, serverVersionSupported = ServerVersionSupported.SUPPORTED,
            users = listOf(com.github.damontecres.wholphin.ui.setup.JellyfinUserAndImage(user, null, true))))
        every { switchUserVM.serverRepository.currentUserFlow } returns MutableStateFlow(user)
        every { switchServerVM.state } returns MutableStateFlow(com.github.damontecres.wholphin.ui.setup.SwitchServerState(
            loading = LoadingState.Success, servers = listOf(com.github.damontecres.wholphin.ui.setup.ServerState(server))))

        val models = mapOf<Class<*>, ViewModel>(
            com.github.damontecres.wholphin.ui.preferences.LunocoConnectionsViewModel::class.java to connectionsVM,
            com.github.damontecres.wholphin.ui.setup.SwitchUserViewModel::class.java to switchUserVM,
            com.github.damontecres.wholphin.ui.setup.SwitchServerViewModel::class.java to switchServerVM,
            com.github.damontecres.wholphin.ui.discovery.MovieExploreViewModel::class.java to exploreVM,
            PreferencesViewModel::class.java to preferencesVM, UpdateViewModel::class.java to updateVM,
            SwitchSeerrViewModel::class.java to seerrVM, NavDrawerViewModel::class.java to drawerVM,
            ApplicationContentViewModel::class.java to appVM, HomeViewModel::class.java to homeVM,
            com.github.damontecres.wholphin.ui.detail.movie.MovieViewModel::class.java to movieVM,
            AddPlaylistViewModel::class.java to playlistVM, SpotlightViewModel::class.java to spotlightVM,
            UserPreferencesViewModel::class.java to profileVM, CollectionFolderViewModel::class.java to libraryVM,
            SearchViewModel::class.java to searchVM,
            NavDrawerPreferencesViewModel::class.java to pinsVM)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = create(modelClass)
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                factoryCounts[modelClass] = (factoryCounts[modelClass] ?: 0) + 1
                return checkNotNull(models[modelClass]) { "Unconfigured data/controller dependency: ${modelClass.name}" } as T
            }
        }
        // Replace the non-composable DI factory only; all application routing/UI stay real.
        mockkStatic("androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory")
        every { androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory(any(), any()) } returns factory
    }

    @After fun teardown() {
        leanbackBefore07c?.let { before ->
            shadowOf(compose.activity.packageManager).setSystemFeature(
                android.content.pm.PackageManager.FEATURE_LEANBACK, before)
        }
        timber.log.Timber.uproot(errorTree)
        unmockkStatic("androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory")
        unmockkObject(WholphinApplication.Companion)
    }

    private fun render(startOnHome: Boolean = false, fontScale: Float = 1f,
        screen: PreferenceScreenOption = PreferenceScreenOption.ADVANCED, origin: Destination? = null,
        updater: com.github.damontecres.wholphin.services.UpdateChecker? = null,
        artworkLoader: coil3.ImageLoader? = null,
        captureInitial: Boolean = true,
        glass: com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant =
            com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced,
        treatment: com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment =
            com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear) {
        if (origin != null) navigation.navigateTo(origin)
        else if (!startOnHome) navigation.navigateTo(Destination.Settings(screen))
        compose.setContent {
            val density = LocalDensity.current
            val capabilityRecorded = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
            val probe = Modifier.drawWithContent {
                if (capabilityRecorded.compareAndSet(false, true)) {
                    println("GLASS30_CANVAS_HARDWARE_ACCELERATED=" + drawContext.canvas.nativeCanvas.isHardwareAccelerated)
                }
                drawContent()
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                LocalImageUrlService provides images,
                LocalSpotlightImageLoader provides artworkLoader,
                com.github.damontecres.wholphin.ui.theme.LocalLunocoGlassVariant provides glass,
                com.github.damontecres.wholphin.ui.theme.LocalLunocoGlassTreatment provides treatment,
                com.github.damontecres.wholphin.ui.setup.LocalLunocoUpdater provides updater) {
                val current by data.state.collectAsState()
                if (leanbackBefore07c != null) {
                    val input = androidx.compose.ui.platform.LocalInputModeManager.current
                    SideEffect { inputMode07c = input }
                }
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    ApplicationContent(server, user, navigation, UserPreferences(current, null),
                        Modifier.fillMaxSize().then(probe), viewModel = appVM)
                }
            }
        }
        compose.waitForIdle()
        if (leanbackBefore07c != null) {
            compose.runOnIdle {
                val input = checkNotNull(inputMode07c)
                println("HOME_UP07C_INITIAL_INPUT_MODE=" + input.inputMode +
                    ";nativeTouch=" + compose.activity.window.decorView.isInTouchMode)
                check(input.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard))
                println("HOME_UP07C_VERIFIED_INPUT_MODE=" + input.inputMode)
            }
            compose.waitForIdle()
        }
        if (captureInitial && (startOnHome || origin != null)) {
            val label = if (startOnHome) "home" else "library"
            capture("settings06b-integrated-$label")
            File("build/reports/lunoco-settings06b/$label-semantics.txt").writeText(compose.onRoot().printToString())
        }
    }
    private fun category(cat: LunocoSettingsCategory): SemanticsNodeInteraction {
        val tag = "settings-category-${cat.name}"
        compose.onNodeWithTag("settings-categories").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun enter(cat: LunocoSettingsCategory) {
        category(cat).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
    private fun row(id: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings-detail-pane").performScrollToNode(hasTestTag("settings-row-$id"))
        return compose.onNodeWithTag("settings-row-$id")
    }
    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun settingsIcon() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings))
    private fun homeTab() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.home))
    private fun key(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    private fun card(row: Int, index: Int) = compose.onNode(
        hasContentDescription("Row $row movie $index") or hasText("Row $row movie $index"))
    private fun focusLibraryArtwork(index: Int) {
        val caption = card(0, index).fetchSemanticsNode().boundsInRoot
        val artwork = compose.onAllNodes(isFocusable()).fetchSemanticsNodes()
            .filter {
                val bounds = it.boundsInRoot
                kotlin.math.abs(bounds.center.x - caption.center.x) < 1f &&
                    bounds.height > 100f && bounds.bottom <= caption.top
            }.minBy { caption.top - it.boundsInRoot.bottom }
        compose.onNode(SemanticsMatcher("library artwork above its caption") { it.id == artwork.id })
            .requestFocus().assertIsFocused()
        compose.waitForIdle()
    }
    private fun capture(name: String, expectedWidth: Int = 1920, expectedHeight: Int = 1080) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(expectedWidth, bitmap.width)
        assertEquals(expectedHeight, bitmap.height)
        val destination = File("build/reports/lunoco-settings06b/$name.png")
        checkNotNull(destination.parentFile).mkdirs()
        destination.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun unchanged() {
        assertEquals(0, data.writes)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }


    private fun discoveryState(library: UUID = UUID(9, 0)) =
        com.github.damontecres.wholphin.ui.discovery.MovieExploreState(
            scope = com.github.damontecres.wholphin.ui.discovery.MovieExploreScope(server.id, user.id, library),
            studios = List(12) { com.github.damontecres.wholphin.ui.discovery.MovieFacet(
                UUID(20, it.toLong()), "Studio $it", com.github.damontecres.wholphin.ui.discovery.MovieFacetKind.STUDIO) },
            genres = List(10) { com.github.damontecres.wholphin.ui.discovery.MovieFacet(
                UUID(21, it.toLong()), "Genre $it", com.github.damontecres.wholphin.ui.discovery.MovieFacetKind.GENRE) })

    private fun renderDiscovery(library: UUID = UUID(9, 0)): MutableStateFlow<com.github.damontecres.wholphin.ui.discovery.MovieExploreState> {
        val state = discoveryState(library)
        val flow = MutableStateFlow(state)
        every { exploreVM.state } returns flow
        every { exploreVM.open(any()) } answers {
            navigation.navigateTo(firstArg<com.github.damontecres.wholphin.ui.discovery.MovieFacet>()
                .destination(checkNotNull(state.scope)))
        }
        render(origin = Destination.MediaItem(library, BaseItemKind.COLLECTION_FOLDER,
            CollectionType.MOVIES, browseGrid = true))
        return flow
    }

    @Test fun navigation10b2MoviesExploreSettingsBackRestoresUnactivatedStudio() {
        exploreSettingsReturn10b2(UUID(9, 0), genres = false)
    }

    @Test fun navigation10b2KidsExploreSettingsBackRestoresUnactivatedGenre() {
        exploreSettingsReturn10b2(UUID(9, 2), genres = true)
    }

    private fun exploreSettingsReturn10b2(library: UUID, genres: Boolean) {
        renderDiscovery(library)
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val facets = if (genres) discoveryState(library).genres else discoveryState(library).studios
        if (genres) compose.onNodeWithTag("movie-explore").performScrollToNode(hasTestTag("explore-genres"))
        compose.onNodeWithTag("explore-${facets.first().key}").requestFocus()
        repeat(4) { key(Key.DirectionRight) }
        val target = compose.onNodeWithTag("explore-${facets[4].key}")
        target.assertIsFocused()
        val before = target.fetchSemanticsNode().boundsInRoot
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("settings-category-PLAYBACK").assertIsFocused()
        back()
        target.assertIsFocused().assertIsDisplayed()
        assertEquals(before, target.fetchSemanticsNode().boundsInRoot)
        verify(exactly = 0) { exploreVM.open(any()) }
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun navigation10b2HomeDetailsBackThenSingleHomeBackRevealsSpotlightWithoutResettingRows() {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        compose.onNodeWithText("Play").assertIsFocused()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(15) { key(Key.DirectionRight) }
        card(1, 15).assertIsFocused()
        val before = card(1, 15).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        back()
        card(1, 15).assertIsFocused()
        assertEquals(before, card(1, 15).fetchSemanticsNode().boundsInRoot)
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        fun offset() = vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(offset() > 0f)
        back()
        homeTab().assertIsFocused()
        assertEquals(0f, offset(), 0.001f)
        assertFullSpotlight07c()
        assertEquals(0L, navigation.homeResetGeneration.value)
        key(Key.DirectionDown)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        card(1, 15).assertIsFocused()
        unchanged()
    }

    private fun exitObserver10b2(): () -> Int {
        var exits = 0
        compose.activity.onBackPressedDispatcher.addCallback(compose.activity,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { exits++ }
            })
        return { exits }
    }

    @Test fun navigation10b2AtAbsoluteTopBackFallsThroughWithoutExtraPress() {
        val exits = exitObserver10b2()
        prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        back()
        assertEquals(1, exits())
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun navigation10b2RapidBackDuringScrollCannotExitAndPartialFirstItemCountsAsScrolled() {
        val exits = exitObserver10b2()
        prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        vertical.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 60f) }
        compose.waitForIdle()
        fun offset() = vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(offset() > 0f)
        compose.runOnUiThread {
            repeat(3) { compose.activity.onBackPressedDispatcher.onBackPressed() }
        }
        compose.waitForIdle()
        assertEquals(0, exits())
        assertEquals(0f, offset(), 0.001f)
        homeTab().assertIsFocused()
        back()
        assertEquals(1, exits())
        unchanged()
    }

    @Test fun navigation10b2SettingsReturnUsesStableFacetIdentityAfterReorder() {
        val flow = renderDiscovery()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val original = flow.value.studios
        compose.onNodeWithTag("explore-${original[0].key}").requestFocus()
        repeat(4) { key(Key.DirectionRight) }
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { flow.value = flow.value.copy(studios = original.reversed()) }
        back()
        compose.onNodeWithTag("explore-${original[4].key}").assertIsFocused().assertIsDisplayed()
        unchanged()
    }

    @Test fun navigation10b2SettingsReturnFallsBackToNearestSurvivingFacet() {
        val flow = renderDiscovery()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val original = flow.value.studios
        compose.onNodeWithTag("explore-${original[0].key}").requestFocus()
        repeat(4) { key(Key.DirectionRight) }
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { flow.value = flow.value.copy(studios = original.filterIndexed { index, _ -> index != 4 }) }
        back()
        compose.onNodeWithTag("explore-${original[5].key}").assertIsFocused().assertIsDisplayed()
        unchanged()
    }

    @Test fun navigation10b2SettingsReturnFallsBackAcrossMissingSection() {
        val flow = renderDiscovery()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("explore-${flow.value.studios.first().key}").requestFocus()
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { flow.value = flow.value.copy(studios = emptyList()) }
        back()
        compose.onNodeWithTag("explore-${flow.value.genres.first().key}").assertIsFocused().assertIsDisplayed()
        unchanged()
    }

    @Test fun navigation10b2EmptyExploreSettingsReturnOffersAllMoviesWithoutActivation() {
        val flow = renderDiscovery()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("explore-${flow.value.studios.first().key}").requestFocus()
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { flow.value = flow.value.copy(studios = emptyList(), genres = emptyList()) }
        back()
        compose.onNodeWithTag("movies-all").assertIsFocused()
        compose.onNodeWithTag("movies-explore").assertIsSelected()
        verify(exactly = 0) { exploreVM.open(any()) }
        unchanged()
    }

    @Test fun navigation10b2AlreadyTopHomeBackPreservesExistingContentOnDown() {
        render(startOnHome = true)
        repeat(4) { key(Key.DirectionRight) }
        key(Key.DirectionDown)
        repeat(5) { key(Key.DirectionRight) }
        card(1, 5).assertIsFocused()
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        assertEquals(0f, vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), .001f)
        back()
        homeTab().assertIsFocused()
        key(Key.DirectionDown)
        card(1, 5).assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun navigation10b2ScrolledHomeWithoutSpotlightBackTopPreservesFirstRowCard() {
        render(startOnHome = true)
        repeat(15) { key(Key.DirectionRight) }
        card(0, 15).assertIsFocused()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(5) { key(Key.DirectionRight) }
        card(2, 5).assertIsFocused()
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        fun offset() = vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("The Back-to-top test must start actually scrolled", offset() > 0f)
        back()
        homeTab().assertIsFocused()
        assertEquals(0f, offset(), .001f)
        key(Key.DirectionDown)
        focusedEvidence("10B2_SCROLLED_NO_SPOTLIGHT_DOWN")
        card(0, 15).assertIsFocused().assertIsDisplayed()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        card(2, 5).assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun navigation10b2ForegroundDoesNotStealExistingTopBarFocus() {
        renderDiscovery()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("explore-${discoveryState().studios.first().key}").requestFocus()
        settingsIcon().requestFocus().assertIsFocused()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        settingsIcon().assertIsFocused()
        assertFalse(navigation.backStack.last() is Destination.Settings)
        verify(exactly = 0) { exploreVM.open(any()) }
        unchanged()
    }

    @Test fun moviesDiscoveryKeepsDefaultGridAndRealShellThenStudioBackRestoresExactCard() {
        renderDiscovery()
        capture("cohesion37-movies-grid")
        compose.onNodeWithTag("movies-all").assertIsSelected()
        compose.onNodeWithText("Row 0 movie 0").assertExists()
        assertNull(factoryCounts[com.github.damontecres.wholphin.ui.discovery.MovieExploreViewModel::class.java])
        homeTab().assertExists()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val facets = discoveryState().studios
        compose.onNodeWithTag("explore-${facets.first().key}").requestFocus()
        repeat(4) { key(Key.DirectionRight) }
        val tag = "explore-${facets[4].key}"
        compose.onNodeWithTag(tag).assertIsFocused()
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        capture("discovery10a-studios-real-shell")
        key(Key.DirectionCenter)
        val filtered = navigation.backStack.last() as Destination.FilteredCollection
        assertEquals(UUID(9, 0), filtered.itemId)
        assertEquals(listOf(facets[4].id), filtered.filter.filter.studios)
        assertEquals(CollectionType.MOVIES, filtered.collectionType)
        back()
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
        val restored = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, restored.left, 1f)
        assertEquals(before.top, restored.top, 1f)
        key(Key.DirectionRight)
        compose.onNodeWithTag("explore-${facets[5].key}").assertIsFocused()
        verify(exactly = 1) { exploreVM.open(any()) }
        unchanged()
    }

    @Test fun phase38ExploreBackReturnsToTheSameMoviesGrid() {
        renderDiscovery()
        val library = navigation.backStack.last()
        focusLibraryArtwork(0)
        repeat(3) { key(Key.DirectionRight) }
        val before = card(0, 3).fetchSemanticsNode().boundsInRoot
        assertEquals(before.center.x, compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("movie-explore").assertIsDisplayed()
        back()
        assertEquals(library, navigation.backStack.last())
        compose.onNodeWithTag("movies-all").assertIsSelected()
        compose.onNodeWithTag("movie-explore").assertDoesNotExist()
        val focus = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        assertEquals(before.center.x, focus.center.x, 1f)
        capture("phase38-movies-return")
        unchanged()
    }

    @Test fun phase38TrickplayOverlayShowsDirectionAndTimestampWithoutArt() {
        val player = mockk<Player>(relaxed = true)
        every { player.duration } returns 300_000L
        every { player.bufferedPosition } returns 120_000L
        var position by mutableLongStateOf(60_000L)
        var direction by mutableStateOf(com.github.damontecres.wholphin.ui.playback.ScrubDirection.FORWARD)
        var visible by mutableStateOf(true)
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                LunocoContentTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF272727))) {
                        if (visible) {
                            com.github.damontecres.wholphin.ui.playback.overlay.DpadSeekOverlay(
                                player = player,
                                seekPositionMs = position,
                                trickplayInfo = null,
                                trickplayUrlFor = { fail("Missing metadata attempted to resolve a sheet"); null },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.95f).padding(bottom = 16.dp),
                                direction = direction,
                            )
                        }
                    }
                }
            }
        }
        val missing = compose.activity.getString(R.string.lunoco_trickplay_unavailable)
        compose.onNodeWithContentDescription(missing).assertIsDisplayed()
        compose.onNodeWithText("Forward", substring = true).assertIsDisplayed()
        capture("phase38-scrub-forward-fallback")
        compose.runOnIdle {
            position = 32_000L
            direction = com.github.damontecres.wholphin.ui.playback.ScrubDirection.BACKWARD
        }
        compose.onNodeWithText("Back ·", substring = true).assertIsDisplayed()
        capture("phase38-scrub-backward-fallback")
        compose.runOnIdle { visible = false }
        compose.onNodeWithContentDescription(missing).assertDoesNotExist()
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun phase38RealTrickplaySheetUpdatesTheTileAt720p() {
        val player = mockk<Player>(relaxed = true)
        every { player.duration } returns 120_000L
        every { player.bufferedPosition } returns 90_000L
        val metadata = mockk<org.jellyfin.sdk.model.api.TrickplayInfo> {
            every { width } returns 64
            every { height } returns 36
            every { tileWidth } returns 2
            every { tileHeight } returns 1
            every { interval } returns 10_000
            every { thumbnailCount } returns 2
        }
        val sheet = Bitmap.createBitmap(128, 36, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.MAGENTA
        canvas.drawRect(0f, 0f, 64f, 36f, paint)
        paint.color = android.graphics.Color.CYAN
        canvas.drawRect(64f, 0f, 128f, 36f, paint)
        val file = File(compose.activity.cacheDir, "phase38-local-sheet.png")
        file.outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val uri = android.net.Uri.fromFile(file).toString()
        var target by mutableLongStateOf(0L)
        val resolutions = mutableListOf<Int>()
        val resolve: (Int) -> String? = { index -> resolutions.add(index); uri }
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                LunocoContentTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF272727))) {
                        com.github.damontecres.wholphin.ui.playback.overlay.DpadSeekOverlay(
                            player, target, metadata, resolve,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth(.95f).padding(bottom = 16.dp),
                            com.github.damontecres.wholphin.ui.playback.ScrubDirection.FORWARD,
                        )
                    }
                }
            }
        }
        fun hasFixtureColor(expected: Int): Boolean {
            val frame = compose.onRoot().captureToImage().asAndroidBitmap()
            assertEquals(1280, frame.width)
            assertEquals(720, frame.height)
            for (y in 400 until frame.height step 16) for (x in 0 until frame.width step 16) {
                if (frame.getPixel(x, y) == expected) return true
            }
            return false
        }
        compose.waitUntil(15_000) { hasFixtureColor(android.graphics.Color.MAGENTA) }
        capture("phase38-scrub-sheet-first-720p", expectedWidth = 1280, expectedHeight = 720)
        compose.runOnIdle { target = 12_000L }
        compose.waitUntil(15_000) { hasFixtureColor(android.graphics.Color.CYAN) }
        capture("phase38-scrub-sheet-second-720p", expectedWidth = 1280, expectedHeight = 720)
        assertEquals(listOf(0), resolutions)
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test fun phase38LegalNoticesKeepTheAttributionAndGplReachable() {
        render(origin = Destination.License, captureInitial = false)
        compose.onNodeWithText("Lunoco is a modified fork of Wholphin", substring = true).assertIsDisplayed()
        capture("phase38-legal-notices")
        val gpl = compose.onNodeWithText(compose.activity.getString(R.string.lunoco_gpl_notice))
        gpl.requestFocus().assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertIsDisplayed()
    }

    @Test fun genreDiscoveryDpadAndFilteredGridBackPreserveIndependentRows() {
        renderDiscovery()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        val facets = discoveryState().genres
        compose.onNodeWithTag("movie-explore").performScrollToNode(hasTestTag("explore-genres"))
        compose.onNodeWithTag("explore-${facets.first().key}").requestFocus()
        repeat(3) { key(Key.DirectionRight) }
        val tag = "explore-${facets[3].key}"
        compose.onNodeWithTag(tag).assertIsFocused()
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        capture("discovery10a-genres-real-shell")
        key(Key.DirectionCenter)
        val filtered = navigation.backStack.last() as Destination.FilteredCollection
        assertEquals(listOf(facets[3].id), filtered.filter.filter.genres)
        assertNull(filtered.filter.filter.studios)
        back()
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
        val restored = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, restored.left, 1f)
        assertEquals(before.top, restored.top, 1f)
        key(Key.DirectionLeft)
        compose.onNodeWithTag("explore-${facets[2].key}").assertIsFocused()
        unchanged()
    }

    @Test fun kidsMoviesExploreUsesActualPermittedLibraryAndCanReturnToGrid() {
        val kids = UUID(9, 2)
        renderDiscovery(kids)
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("movie-explore").assertIsDisplayed()
        verify { exploreVM.load(kids) }
        val state = discoveryState(kids)
        compose.onNodeWithTag("explore-${state.studios.first().key}").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(kids, (navigation.backStack.last() as Destination.FilteredCollection).itemId)
        back()
        compose.onNodeWithTag("movies-all").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Row 0 movie 0").assertExists()
        unchanged()
    }

    @Test fun emptyExploreKeepsRealShellAndAllowsDpadReturnToAllMovies() {
        renderDiscovery()
        every { exploreVM.state } returns MutableStateFlow(
            com.github.damontecres.wholphin.ui.discovery.MovieExploreState(scope = discoveryState().scope))
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.lunoco_explore_empty)).assertIsDisplayed()
        key(Key.DirectionLeft)
        compose.onNodeWithTag("movies-all").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithText("Row 0 movie 0").assertExists()
        homeTab().assertExists()
        unchanged()
    }

    // Exercise the production shell, Home list virtualization and real directional focus search.
    // The test injects only repository/controller data, never a substitute navigation header.
    private fun prepareDeepHome(spotlight: Boolean = true, rowCount: Int = 6): MutableStateFlow<HomeState> {
        val deepRows = rows + List(rowCount - rows.size) { offset ->
            val row = offset + rows.size
            HomeRowLoadingState.Success(StringStringProvider("Row $row"), List(24) { index ->
                val dto = BaseItemDto(id = UUID(row.toLong() + 1, index.toLong() + 1),
                    type = BaseItemKind.MOVIE, name = "Row $row movie $index")
                mockk<BaseItem>(relaxed = true) {
                    every { id } returns dto.id
                    every { type } returns dto.type
                    every { data } returns dto
                    every { title } returns dto.name!!
                    every { name } returns dto.name!!
                    every { imageUrlOverride } returns null
                    every { destinationOverride } returns null
                    every { destination(any()) } returns Destination.MediaItem(dto.id, dto.type)
                    every { destination(null) } returns Destination.MediaItem(dto.id, dto.type)
                }
            }, rowType = HomeRowConfig.RecentlyAdded(UUID(8, row.toLong())))
        }
        val state = MutableStateFlow(HomeState(LoadingState.Success, LoadingState.Success,
            deepRows, HomePageResolvedSettings.EMPTY))
        every { homeVM.state } returns state
        val movies = if (spotlight) listOf(
            BaseItemDto(id = UUID(20, 1), type = BaseItemKind.MOVIE, name = "Up navigation feature"),
        ) else emptyList()
        every { spotlightVM.details(any()) } answers {
            navigation.navigateTo(Destination.MediaItem(firstArg<BaseItemDto>().id, BaseItemKind.MOVIE))
        }
        every { spotlightVM.available } returns MutableStateFlow(spotlight)
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = movies))
        return state
    }

    private fun focusedEvidence(label: String) {
        println("HOME_UP07_$label: " + compose.onNode(isFocused()).printToString())
    }

    private fun prepareSpotlightUp07c(continueWatching: Boolean = true): MutableStateFlow<HomeState> {
        // Foundation chooses its real TV pivot scrolling only when this capability is present.
        // A phone-like Robolectric default concealed the hardware's horizontal focus geometry.
        leanbackBefore07c = compose.activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK)
        shadowOf(compose.activity.packageManager).setSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        assertTrue(compose.activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK))
        val state = prepareDeepHome(rowCount = 8)
        state.value = state.value.copy(homeRows = state.value.homeRows.mapIndexed { index, row ->
            val success = row as HomeRowLoadingState.Success
            if (index == 0) success.copy(
                rowType = HomeRowConfig.ContinueWatching(),
                items = if (continueWatching) success.items.take(2) else emptyList(),
            ) else success
        })
        return state
    }

    private fun assert07cFocusedRow(row: Int, label: String) {
        focusedEvidence("07C_" + label)
        val prefix = "Row $row movie "
        compose.onNode(isFocused() and (hasText(prefix, substring = true) or
            hasContentDescription(prefix, substring = true))).assertExists()
    }

    private fun assertFullSpotlight07c(title: String = "Up navigation feature") {
        compose.onNodeWithText(title).assertIsDisplayed()
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Spotlight must be fully revealed, not only its controls", 0f, vertical.value(), .000001f)
    }

    private fun spotlightUp07cFromMovie(index: Int, continueWatching: Boolean) {
        prepareSpotlightUp07c(continueWatching)
        render(startOnHome = true)
        compose.onNodeWithText("Play").assertIsFocused()
        if (continueWatching) key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(index) { key(Key.DirectionRight) }
        card(1, index).assertIsFocused()
        capture("home-up07c-movies-$index-before")
        key(Key.DirectionUp)
        capture("home-up07c-movies-$index-first-up")
        if (continueWatching) {
            assert07cFocusedRow(0, "MOVIES_TO_RESUME_$index")
            key(Key.DirectionUp)
        }
        focusedEvidence("07C_MOVIES_TO_SPOTLIGHT_$index")
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        val title = compose.onNodeWithText("Up navigation feature").fetchSemanticsNode().boundsInRoot
        assertTrue("Full Spotlight title must be below the real 112px top bar: $title", title.top >= 112f)
        assertFullSpotlight07c()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText("Play").assertIsFocused()
        if (continueWatching) key(Key.DirectionDown)
        key(Key.DirectionDown)
        card(1, index).assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    private fun discoveryLoadingFixture(loading: Boolean = true) =
        MutableStateFlow(com.github.damontecres.wholphin.ui.discovery.MovieExploreState(
            scope = com.github.damontecres.wholphin.ui.discovery.MovieExploreScope(server.id, user.id, UUID(9, 0)),
            loading = loading,
        )).also { discovery ->
            every { exploreVM.state } returns discovery
            every { homeVM.state } returns MutableStateFlow(HomeState(
                LoadingState.Success, LoadingState.Success, rows + rows, HomePageResolvedSettings.EMPTY))
        }

    @Test fun homeDiscoveryStartsOnEntryBeforeAnyScrolling() {
        discoveryLoadingFixture()
        render(startOnHome = true)
        card(0, 0).assertIsDisplayed()
        verify(exactly = 1) { exploreVM.load(UUID(9, 0)) }
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun homeDiscoveryWaitsForUsableHomeAndDoesNotBlockForegroundRows() {
        discoveryLoadingFixture()
        val home = MutableStateFlow(HomeState.EMPTY)
        every { homeVM.state } returns home
        render(startOnHome = true)
        verify(exactly = 0) { exploreVM.load(any()) }
        compose.runOnIdle {
            home.value = HomeState(LoadingState.Success, LoadingState.Success, rows + rows, HomePageResolvedSettings.EMPTY)
        }
        compose.waitForIdle()
        verify(exactly = 1) { exploreVM.load(UUID(9, 0)) }
        // Discovery is still pending: existing rows must already be usable.
        card(0, 0).assertIsDisplayed().requestFocus().assertIsFocused()
        key(Key.DirectionRight)
        card(0, 1).assertIsFocused()
        unchanged()
    }

    @Test fun homeDiscoveryArrivalKeepsSectionGeometryAndNavigationFocus() {
        val discovery = discoveryLoadingFixture()
        render(startOnHome = true)
        homeTab().requestFocus()
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(7) // Spotlight slot, six existing rows, then Studios.
        val studio = compose.onNodeWithTag("lunoco_discovery:studios")
        val genre = compose.onNodeWithTag("lunoco_discovery:genres")
        val studioBefore = studio.getUnclippedBoundsInRoot()
        val genreBefore = genre.getUnclippedBoundsInRoot()
        capture("home-discovery-pending")
        compose.runOnIdle {
            discovery.value = discovery.value.copy(loading = false,
                studios = listOf(com.github.damontecres.wholphin.ui.discovery.MovieFacet(
                    UUID(40, 1), "Studio", com.github.damontecres.wholphin.ui.discovery.MovieFacetKind.STUDIO)),
                genres = listOf(com.github.damontecres.wholphin.ui.discovery.MovieFacet(
                    UUID(40, 2), "Drama", com.github.damontecres.wholphin.ui.discovery.MovieFacetKind.GENRE)))
        }
        compose.waitForIdle()
        val studioAfter = studio.getUnclippedBoundsInRoot()
        val genreAfter = genre.getUnclippedBoundsInRoot()
        assertEquals((studioBefore.bottom - studioBefore.top).value, (studioAfter.bottom - studioAfter.top).value, .5f)
        assertEquals((genreBefore.bottom - genreBefore.top).value, (genreAfter.bottom - genreAfter.top).value, .5f)
        assertEquals(genreBefore.top.value, genreAfter.top.value, .5f)
        homeTab().assertIsFocused()
        capture("home-discovery-ready")
        verify(exactly = 1) { exploreVM.load(UUID(9, 0)) }
        unchanged()
    }

    @Test fun homeDiscoveryScrollingDoesNotReloadOrPrepareOffscreenArtwork() {
        val discovery = discoveryLoadingFixture(loading = false)
        val studio = com.github.damontecres.wholphin.ui.discovery.MovieFacet(UUID(40, 1), "Studio",
            com.github.damontecres.wholphin.ui.discovery.MovieFacetKind.STUDIO,
            com.github.damontecres.wholphin.ui.discovery.MovieExploreImage(UUID(40, 1), ImageType.LOGO, "studio"))
        val artwork = "android.resource://" + compose.activity.packageName + "/" + R.drawable.lunoco_wordmark
        every { images.getItemImageUrl(studio.id, ImageType.LOGO, maxWidth = 448, maxHeight = 240, tag = "studio") } returns artwork
        discovery.value = discovery.value.copy(studios = listOf(studio),
            genres = listOf(com.github.damontecres.wholphin.ui.discovery.MovieFacet(
                UUID(40, 2), "Drama", com.github.damontecres.wholphin.ui.discovery.MovieFacetKind.GENRE)))
        render(startOnHome = true)
        verify(exactly = 1) { exploreVM.load(UUID(9, 0)) }
        verify(exactly = 0) { images.getItemImageUrl(studio.id, ImageType.LOGO, maxWidth = 448, maxHeight = 240, tag = "studio") }
        homeTab().requestFocus()
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(7)
        compose.waitForIdle()
        compose.onNodeWithTag("explore-" + studio.key).assertIsDisplayed().requestFocus()
        key(Key.DirectionDown)
        key(Key.DirectionUp)
        compose.onNodeWithTag("explore-" + studio.key).assertIsFocused()
        verify(exactly = 1) { exploreVM.load(UUID(9, 0)) }
        verify(atLeast = 1) { images.getItemImageUrl(studio.id, ImageType.LOGO, maxWidth = 448, maxHeight = 240, tag = "studio") }
        unchanged()
    }

    @Test fun homeUp07cMoviesRightFourThenUpRestoresSpotlightAndRealTopBar() {
        spotlightUp07cFromMovie(4, continueWatching = true)
    }

    @Test fun homeUp07cMoviesCardFifteenThenUpRestoresSpotlightAndRealTopBar() {
        spotlightUp07cFromMovie(15, continueWatching = true)
    }

    @Test fun homeUp07cWithoutContinueWatchingMoviesUpRestoresSpotlightAndRealTopBar() {
        spotlightUp07cFromMovie(4, continueWatching = false)
    }

    @Test fun homeUp07cOffscreenSpotlightIsRevealedBeforeFocusTransfer() {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) }
        card(1, 4).assertIsFocused()
        // Exercise the real lazy viewport with Spotlight above its placed range.
        // This is a list scroll, not replacement Home UI or a fake focus target.
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(1)
        compose.onNodeWithText("Play").assertIsNotDisplayed()
        key(Key.DirectionUp)
        assert07cFocusedRow(0, "OFFSCREEN_TO_RESUME")
        key(Key.DirectionUp)
        focusedEvidence("07C_OFFSCREEN_TO_SPOTLIGHT")
        capture("home-up07c-offscreen-spotlight-up")
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        unchanged()
    }

    @Test fun homeUp07cImagePostersAndShortResumeRowReachSpotlight() {
        prepareSpotlightUp07c()
        val artwork = "android.resource://" + compose.activity.packageName + "/" + R.drawable.lunoco_wordmark
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } answers {
            if (firstArg<BaseItem>().id.mostSignificantBits == 1L) null else artwork
        }
        render(startOnHome = true)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) }
        val before = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        assertEquals(288f, before.height, 1f)
        key(Key.DirectionUp)
        assert07cFocusedRow(0, "IMAGE_TO_RESUME")
        key(Key.DirectionUp)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        unchanged()
    }

    @Test fun homeUp07cPagedSpotlightNextThenMoviesRightAndUpPreservesSelection() {
        prepareSpotlightUp07c()
        val movies = listOf(
            BaseItemDto(id = UUID(20, 1), type = BaseItemKind.MOVIE, name = "First feature"),
            BaseItemDto(id = UUID(20, 2), type = BaseItemKind.MOVIE, name = "Second feature"),
        )
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = movies))
        render(startOnHome = true)
        repeat(3) { key(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithText("Second feature").assertIsDisplayed()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) }
        card(1, 4).assertIsFocused()
        key(Key.DirectionUp)
        assert07cFocusedRow(0, "PAGED_TO_RESUME")
        key(Key.DirectionUp)
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Second feature").assertIsDisplayed()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        unchanged()
    }

    private fun android07cBurst(intervalMs: Long) {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        val down = android.view.KeyEvent.KEYCODE_DPAD_DOWN
        val right = android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        val up = android.view.KeyEvent.KEYCODE_DPAD_UP
        val keys = listOf(down, down) + List(4) { right } + List(3) { up }
        compose.mainClock.autoAdvance = false
        try {
            keys.forEach { code ->
                compose.runOnUiThread {
                    val time = android.os.SystemClock.uptimeMillis()
                    compose.activity.dispatchKeyEvent(android.view.KeyEvent(time, time,
                        android.view.KeyEvent.ACTION_DOWN, code, 0, 0,
                        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                        android.view.InputDevice.SOURCE_DPAD))
                    compose.activity.dispatchKeyEvent(android.view.KeyEvent(time, time,
                        android.view.KeyEvent.ACTION_UP, code, 0, 0,
                        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                        android.view.InputDevice.SOURCE_DPAD))
                }
                compose.mainClock.advanceTimeBy(intervalMs)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        focusedEvidence("07C_ANDROID_BURST_" + intervalMs)
        capture("home-up07c-android-burst-" + intervalMs)
        homeTab().assertIsFocused()
        assertFullSpotlight07c()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun homeUp07cAndroidDpadAtNormalPaceReachesNavigationWithoutBack() {
        android07cBurst(425)
    }

    @Test fun homeUp07cAndroidDpadAtQuickPaceReachesNavigationWithoutBack() {
        android07cBurst(125)
    }

    @Test fun homeUp07cLateSpotlightAboveShortResumeRowRemainsReachable() {
        prepareSpotlightUp07c()
        val movieState = MutableStateFlow(SpotlightState(loading = true))
        val available = MutableStateFlow(false)
        every { spotlightVM.state } returns movieState
        every { spotlightVM.available } returns available
        every { spotlightVM.entryPending } returns MutableStateFlow(true)
        render(startOnHome = true)
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        card(0, 0).assertIsFocused()
        key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) }
        card(1, 4).assertIsFocused()
        compose.runOnIdle {
            movieState.value = SpotlightState(resolved = true, movies = listOf(
                BaseItemDto(id = UUID(20, 1), type = BaseItemKind.MOVIE, name = "Late feature")))
            available.value = true
        }
        card(1, 4).assertIsFocused()
        key(Key.DirectionUp)
        assert07cFocusedRow(0, "LATE_TO_RESUME")
        key(Key.DirectionUp)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Late feature").assertIsDisplayed()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        unchanged()
    }

    @Test fun homeUp07cHorizontalMoviesKeepsVerticalViewportAndDoesNotReload() {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        val before = card(1, 0).fetchSemanticsNode().boundsInRoot
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        fun offset() = vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val initialOffset = offset()
        clearMocks(homeVM, spotlightVM, answers = false, recordedCalls = true)
        repeat(5) { index ->
            key(Key.DirectionRight)
            card(1, index + 1).assertIsFocused()
            assertEquals("Horizontal step $index must preserve Home vertical offset", initialOffset, offset(), 1f)
            assertEquals(before.top, card(1, index + 1).fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        verify(exactly = 0) { homeVM.init(any()) }
        verify(exactly = 0) { spotlightVM.load(any(), any(), any()) }
        unchanged()
    }

    @Test fun homeUp07cDeepTvRowsRecomposeSpotlightAndEachUpReachesPrecedingRow() {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        for (row in 0..7) {
            key(Key.DirectionDown)
            if (row > 0) repeat(15) { key(Key.DirectionRight) }
            card(row, if (row == 0) 0 else 15).assertIsFocused()
        }
        compose.onNodeWithText("Play").assertIsNotDisplayed()
        println("HOME_UP07C_DEEP_SPOTLIGHT_COMPOSED=" +
            compose.onAllNodesWithText("Play").fetchSemanticsNodes().size)
        for (row in 6 downTo 0) {
            key(Key.DirectionUp)
            assert07cFocusedRow(row, "TV_DEEP_UP_$row")
        }
        key(Key.DirectionUp)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Up navigation feature").assertIsDisplayed()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun homeUp07cDeepTvRapidUpCannotRemainTrappedInContent() {
        prepareSpotlightUp07c()
        val warnings = mutableListOf<String>()
        val standardOut = System.out
        val tracingOut = object : java.io.PrintStream(standardOut) {
            private fun record(message: Any?) {
                if (message?.toString()?.contains("FocusRequester is not initialized") == true) {
                    warnings.add(Throwable("Unplaced focus request in the real Home route").stackTraceToString())
                }
            }
            override fun println(message: String?) { record(message); super.println(message) }
            override fun println(message: Any?) { record(message); super.println(message) }
        }
        System.setOut(tracingOut)
        try {
            println("HOME_UP07C_STAGE_RENDER")
            render(startOnHome = true)
            println("HOME_UP07C_STAGE_DOWN")
            repeat(8) { key(Key.DirectionDown) }
            println("HOME_UP07C_STAGE_RIGHT")
            repeat(15) { key(Key.DirectionRight) }
            card(7, 15).assertIsFocused()
            println("HOME_UP07C_STAGE_UP")
            compose.mainClock.autoAdvance = false
            repeat(12) {
                compose.runOnUiThread {
                    val time = android.os.SystemClock.uptimeMillis()
                    for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
                        compose.activity.dispatchKeyEvent(android.view.KeyEvent(time, time, action,
                            android.view.KeyEvent.KEYCODE_DPAD_UP, 0, 0,
                            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                            android.view.InputDevice.SOURCE_DPAD))
                    }
                }
                compose.mainClock.advanceTimeBy(125)
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        } finally {
            compose.mainClock.autoAdvance = true
            System.setOut(standardOut)
        }
        warnings.forEach { println("HOME_UP07C_UNPLACED_REQUEST_STACK:\n" + it) }
        focusedEvidence("07C_DEEP_RAPID_END")
        assertEquals("Home must not request an unplaced focus target", 0, warnings.size)
        homeTab().assertIsFocused()
        assertFullSpotlight07c()
        unchanged()
    }

    @Test fun homeUp07cBackToNavigationCancelsPendingSpotlightReturn() {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) }
        key(Key.DirectionUp)
        assert07cFocusedRow(0, "BEFORE_UP_AND_BACK")
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        val offset = vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                val time = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
                    compose.activity.dispatchKeyEvent(android.view.KeyEvent(time, time, action,
                        android.view.KeyEvent.KEYCODE_DPAD_UP, 0, 0,
                        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                        android.view.InputDevice.SOURCE_DPAD))
                }
                compose.activity.onBackPressedDispatcher.onBackPressed()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        homeTab().assertIsFocused()
        assertTrue(offset > 0f)
        assertEquals(0f, vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), .000001f)
        assertEquals(0L, navigation.homeResetGeneration.value)
        capture("home-up07c-back-cancelled")
        key(Key.DirectionDown)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        unchanged()
    }

    @Test fun homeUp07cDetailsBackAndConditionalRowKeepMoviesAnchorAndUpRoute() {
        val state = prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        repeat(15) { key(Key.DirectionRight) }
        card(1, 15).assertIsFocused()
        val before = card(1, 15).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        back()
        card(1, 15).assertIsFocused()
        assertEquals(before, card(1, 15).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {
            state.value = state.value.copy(homeRows = state.value.homeRows.mapIndexed { index, row ->
                when (index) {
                    2 -> HomeRowLoadingState.Loading(StringStringProvider("Loading conditional row"))
                    3 -> (row as HomeRowLoadingState.Success).copy(items = emptyList())
                    else -> row
                }
            })
        }
        card(1, 15).assertIsFocused()
        key(Key.DirectionUp)
        assert07cFocusedRow(0, "DETAILS_AND_CONDITIONAL_UP")
        key(Key.DirectionUp)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        unchanged()
    }

    @Test fun deepHomeRepeatedUpFromLaterCardReachesRealTopBarWithoutBack() {
        prepareDeepHome()
        render(startOnHome = true)
        compose.onNodeWithText("Play").assertIsFocused()
        for (row in 0..5) {
            key(Key.DirectionDown)
            card(row, 0).assertIsFocused()
        }
        repeat(7) { key(Key.DirectionRight) }
        card(5, 7).assertIsFocused()
        // Spotlight has left the lazy viewport; its title/controls are not on screen.
        compose.onNodeWithText("Play").assertIsNotDisplayed()
        focusedEvidence("DEEP")
        repeat(8) { step ->
            key(Key.DirectionUp)
            focusedEvidence("UP_$step")
        }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }


    @Test fun homeUp07DeepBrowseWithLaterPositionInEveryRowReachesNavigation() {
        prepareDeepHome()
        render(startOnHome = true)
        for (row in 0..5) {
            key(Key.DirectionDown)
            repeat(24) { key(Key.DirectionRight) }
            focusedEvidence("LATER_ROW_$row")
        }
        repeat(8) { step ->
            key(Key.DirectionUp)
            focusedEvidence("LATER_UP_$step")
        }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun homeUp07DeepBrowseWithNoSpotlightReachesNavigation() {
        prepareDeepHome(spotlight = false)
        render(startOnHome = true)
        for (row in 0..5) {
            if (row > 0) key(Key.DirectionDown)
            repeat(15) { key(Key.DirectionRight) }
            card(row, 15).assertIsFocused()
        }
        repeat(7) { step ->
            key(Key.DirectionUp)
            focusedEvidence("ABSENT_UP_$step")
        }
        homeTab().assertIsFocused()
        unchanged()
    }

    @Test fun homeUp07LoadingFirstRowDoesNotBlockExitToNavigation() {
        val state = prepareDeepHome(spotlight = false)
        state.value = state.value.copy(homeRows = listOf(
            HomeRowLoadingState.Loading(StringStringProvider("Loading leading row")),
        ) + state.value.homeRows)
        render(startOnHome = true)
        card(0, 0).assertIsFocused()
        repeat(5) { key(Key.DirectionDown) }
        repeat(15) { key(Key.DirectionRight) }
        repeat(9) { step ->
            key(Key.DirectionUp)
            focusedEvidence("LOADING_UP_$step")
        }
        homeTab().assertIsFocused()
        unchanged()
    }


    @Test fun homeUp07TwelveRowsWithHorizontalAnchorsReturnUpWithoutSkippingRows() {
        prepareDeepHome(rowCount = 12)
        render(startOnHome = true)
        for (row in 0..11) {
            key(Key.DirectionDown)
            repeat(15) { key(Key.DirectionRight) }
            card(row, 15).assertIsFocused()
        }
        for (row in 10 downTo 0) {
            key(Key.DirectionUp)
            focusedEvidence("TWELVE_UP_$row")
            card(row, 15).assertIsFocused()
        }
        key(Key.DirectionUp)
        compose.onNodeWithText("Play").assertIsFocused()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        unchanged()
    }

    @Test fun homeUp07AfterRealDetailsBackRestoresDeepItemThenReachesTopBar() {
        val state = prepareDeepHome()
        render(startOnHome = true)
        repeat(6) { key(Key.DirectionDown) }
        repeat(15) { key(Key.DirectionRight) }
        card(5, 15).assertIsFocused()
        val bounds = card(5, 15).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        assertEquals((state.value.homeRows[5] as HomeRowLoadingState.Success).items[15]!!.id,
            (navigation.backStack.last() as Destination.MediaItem).itemId)
        back()
        card(5, 15).assertIsFocused()
        assertEquals(bounds, card(5, 15).fetchSemanticsNode().boundsInRoot)
        repeat(8) { step ->
            key(Key.DirectionUp)
            focusedEvidence("DETAILS_UP_$step")
        }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun homeUp07RapidRepeatedUpStillExitsHomeContent() {
        prepareDeepHome()
        render(startOnHome = true)
        repeat(6) { key(Key.DirectionDown) }
        repeat(15) { key(Key.DirectionRight) }
        compose.onNode(isFocused()).performKeyInput {
            repeat(12) { pressKey(Key.DirectionUp); advanceEventTime(80) }
        }
        compose.waitForIdle()
        focusedEvidence("RAPID")
        // Further normal UP must remain functional after the rapid sequence settles.
        repeat(8) { key(Key.DirectionUp) }
        homeTab().assertIsFocused()
        unchanged()
    }


    @Test fun homeUp07LateSpotlightAndChangedOffscreenRowsDoNotTrapUp() {
        val state = prepareDeepHome(spotlight = false)
        val originalRows = state.value.homeRows
        state.value = state.value.copy(homeRows = listOf(
            HomeRowLoadingState.Loading(StringStringProvider("Pending row")),
        ) + originalRows.drop(1))
        val spotlightState = MutableStateFlow(SpotlightState(loading = true))
        val available = MutableStateFlow(false)
        every { spotlightVM.entryPending } returns MutableStateFlow(true)
        every { spotlightVM.available } returns available
        every { spotlightVM.state } returns spotlightState
        render(startOnHome = true)
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        card(1, 0).assertIsFocused()
        repeat(4) { key(Key.DirectionDown) }
        repeat(15) { key(Key.DirectionRight) }
        card(5, 15).assertIsFocused()
        compose.runOnIdle {
            state.value = state.value.copy(homeRows = originalRows.mapIndexed { index, row ->
                if (index == 2) (row as HomeRowLoadingState.Success).copy(items = emptyList()) else row
            })
            spotlightState.value = SpotlightState(resolved = true, movies = listOf(
                BaseItemDto(id = UUID(20, 1), type = BaseItemKind.MOVIE, name = "Late feature")))
            available.value = true
        }
        card(5, 15).assertIsFocused()
        repeat(9) { step ->
            key(Key.DirectionUp)
            focusedEvidence("DYNAMIC_UP_$step")
        }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun homeUp07TopBarDownPreservesCarouselPositionsUntilExplicitHomeOk() {
        prepareDeepHome()
        render(startOnHome = true)
        repeat(6) {
            key(Key.DirectionDown)
            repeat(15) { key(Key.DirectionRight) }
        }
        repeat(7) { key(Key.DirectionUp) }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        key(Key.DirectionDown)
        compose.onNodeWithText("Play").assertIsFocused()
        for (row in 0..5) {
            key(Key.DirectionDown)
            card(row, 15).assertIsFocused()
        }
        repeat(7) { key(Key.DirectionUp) }
        homeTab().assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithText("Play").assertIsFocused()
        for (row in 0..5) {
            key(Key.DirectionDown)
            card(row, 0).assertIsFocused()
        }
        assertEquals(1L, navigation.homeResetGeneration.value)
        unchanged()
    }


    @Test fun homeUp07ImageOnlyPostersReachTopBarAfterDeepBrowsing() {
        val state = prepareDeepHome()
        val artwork = "android.resource://" + compose.activity.packageName + "/" + R.drawable.lunoco_wordmark
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns artwork
        render(startOnHome = true)
        for (row in 0..5) {
            key(Key.DirectionDown)
            repeat(15) { key(Key.DirectionRight) }
            val focused = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
            assertTrue(focused.top >= 112f && focused.height > 100f)
        }
        repeat(8) { step ->
            key(Key.DirectionUp)
            focusedEvidence("IMAGE_ONLY_UP_$step")
        }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        assertEquals(6, state.value.homeRows.size)
        unchanged()
    }


    @Test fun homeUp07MixedResumeAndPosterRowsPreserveAnchorsWhileReachingTopBar() {
        val state = prepareDeepHome()
        state.value = state.value.copy(homeRows = state.value.homeRows.mapIndexed { index, row ->
            val success = row as HomeRowLoadingState.Success
            when (index) {
                0 -> success.copy(rowType = HomeRowConfig.ContinueWatching())
                1 -> success.copy(rowType = HomeRowConfig.NextUp())
                3 -> success.copy(items = emptyList())
                else -> success
            }
        })
        render(startOnHome = true)
        val nonEmpty = listOf(0, 1, 2, 4, 5)
        for (row in nonEmpty) {
            key(Key.DirectionDown)
            repeat(15) { key(Key.DirectionRight) }
            card(row, 15).assertIsFocused()
        }
        for (row in nonEmpty.dropLast(1).asReversed()) {
            key(Key.DirectionUp)
            card(row, 15).assertIsFocused()
        }
        key(Key.DirectionUp)
        compose.onNodeWithText("Play").assertIsFocused()
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText("Play").assertIsFocused()
        for (row in nonEmpty) {
            key(Key.DirectionDown)
            card(row, 15).assertIsFocused()
        }
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }


    // Real ApplicationContent/NavDisplay/HomePage list; no mock header or text overlay.
    private fun readability36Capture(name: String, width: Int, height: Int): Bitmap {
        val b = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(width, b.width); assertEquals(height, b.height)
        val f = File("build/reports/headerless-floating36c/$name.png")
        f.parentFile!!.mkdirs()
        f.outputStream().use { assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return b
    }

    private fun readability36Visual(bright: Boolean = true, width: Int = 1920, height: Int = 1080,
        fontScale: Float = 1f, focus: String = "Home", missing: Boolean = false,
        detail: com.github.damontecres.wholphin.ui.theme.Glass31Artwork? = null) {
        val loader = fullBleed28Loader(bright, multiple = true, patterned = true)
        if (detail != null) coEvery { loader.execute(any()) } coAnswers {
            val bitmap = com.github.damontecres.wholphin.ui.theme.glass31Artwork(detail)
            mockk<coil3.request.SuccessResult> { every { image } returns bitmap.asImage() }
        }
        val description = "White Spotlight description crosses the pinned navigation while the real Home list scrolls. " +
            "Movie details and metadata must never compete with navigation labels."
        val movies = listOf("Cinematic A", "Cinematic B").mapIndexed { i, name ->
            BaseItemDto(id = UUID(28, i.toLong() + 1), type = BaseItemKind.MOVIE, name = name,
                backdropImageTags = if (missing) emptyList() else listOf("synthetic-$i"),
                overview = description, productionYear = 2026, genres = listOf("Adventure"), officialRating = "PG")
        }
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = movies))
        render(startOnHome = true, fontScale = fontScale, artworkLoader = loader, captureInitial = false)
        val focused = when (focus) {
            "Home" -> homeTab()
            "Settings" -> settingsIcon()
            "Search" -> compose.onNodeWithContentDescription("Search")
            else -> compose.onNodeWithText(focus)
        }
        focused.requestFocus().assertIsFocused()
        homeTab().assertIsSelected()
        val navGeometry = homeTab().fetchSemanticsNode().boundsInRoot
        val descriptionTop = compose.onNodeWithText(description).fetchSemanticsNode().boundsInRoot.top
        val metadata = "Adventure  ·  2026  ·  PG"
        val metadataTop = compose.onNodeWithText(metadata).fetchSemanticsNode().boundsInRoot.top
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        val prefix = "headerless36c-$width-font-$fontScale-" +
            (detail?.name ?: if (missing) "missing" else if (bright) "bright" else "dark") + "-" + focus
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
        val top = readability36Capture("$prefix-A-top", width, height)
        val obsoleteBacking = android.graphics.Color.rgb(16, 16, 16)
        val protectedHeight = with(compose.density) { 56.dp.roundToPx() }
        val lastLibrary = compose.onNodeWithText("Home Movies").fetchSemanticsNode().boundsInRoot
        val searchBounds = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        val gapX = ((lastLibrary.right + searchBounds.left) / 2f).toInt()
        assertNotEquals("Absolute top has no opaque backing", obsoleteBacking, top.getPixel(gapX, 1))
        fun scrollTo(offset: Float) {
            vertical.performScrollToIndex(0)
            if (offset > 0) vertical.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, offset) }
            compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
            assertEquals("Pinned control geometry", navGeometry, homeTab().fetchSemanticsNode().boundsInRoot)
            focused.assertIsFocused()
        }
        val buttons = listOf(
            "Lunoco" to compose.onNodeWithContentDescription("Lunoco"), "Home" to homeTab(),
            "Movies" to compose.onNodeWithText("Movies"), "Shows" to compose.onNodeWithText("Shows"),
            "Live TV" to compose.onNodeWithText("Live TV"), "Home Movies" to compose.onNodeWithText("Home Movies"),
            "Search" to compose.onNodeWithContentDescription("Search"), "Settings" to settingsIcon())
        fun interiors(b: Bitmap): Map<String, IntArray> = buttons.mapNotNull { (name, node) ->
            val bounds = node.fetchSemanticsNode().boundsInRoot
            // Sample opaque centers only, excluding anti-aliased capsules/rims and cropped controls.
            if (bounds.left <= 0 || bounds.right >= width || bounds.width < 60f) null else {
                val inset = with(compose.density) { 12.dp.roundToPx() }
                val x = bounds.left.toInt() + inset; val y = bounds.top.toInt() + inset
                val w = bounds.width.toInt() - inset * 2; val h = bounds.height.toInt() - inset * 2
                assertTrue(w > 0 && h > 0)
                val pixels = IntArray(w * h)
                b.getPixels(pixels, 0, w, x, y, w, h)
                name to pixels
            }
        }.toMap().also { assertTrue("Visible control centers covered", it.size >= if (width == 1920) 8 else 4) }
        fun openGap(b: Bitmap): IntArray {
            assertNotEquals("No Phase36 full-width backing in the gap", obsoleteBacking, b.getPixel(gapX, 1))
            return IntArray(protectedHeight) { b.getPixel(gapX, it) }
        }
        scrollTo(1f)
        val first = readability36Capture("$prefix-B-first-pixel", width, height)
        openGap(first)
        val firstInteriors = interiors(first)
        scrollTo(metadataTop - 40f)
        val metadataBounds = compose.onNodeWithText(metadata).fetchSemanticsNode().boundsInRoot
        assertTrue("Actual metadata is underneath navigation", metadataBounds.top < protectedHeight && metadataBounds.bottom > 0)
        val metadataBitmap = readability36Capture("$prefix-C-metadata", width, height)
        val metadataInteriors = interiors(metadataBitmap)
        val metadataGap = openGap(metadataBitmap)
        scrollTo(descriptionTop - 36f)
        val descriptionBounds = compose.onNodeWithText(description).fetchSemanticsNode().boundsInRoot
        assertTrue("Actual description is underneath navigation", descriptionBounds.top < protectedHeight && descriptionBounds.bottom > 0)
        val descriptionBitmap = readability36Capture("$prefix-D-description", width, height)
        val descriptionInteriors = interiors(descriptionBitmap)
        val descriptionGap = openGap(descriptionBitmap)
        for ((name, pixels) in metadataInteriors) {
            assertArrayEquals("Opaque $name center blocks moving white Spotlight text",
                pixels, descriptionInteriors.getValue(name))
            assertArrayEquals("Dark mode is immediate on the first scroll pixel",
                pixels, firstInteriors.getValue(name))
        }
        if (!missing) assertFalse("Open gap transmits scrolling artwork/content",
            metadataGap.contentEquals(descriptionGap))
        File("build/reports/headerless-floating36c/$prefix-overlap.txt").writeText(
            "production shell/list/Spotlight; metadata=$metadataBounds; description=$descriptionBounds; protectedHeight=$protectedHeight; opaqueControlCenters=${metadataInteriors.keys}; openGaps=true")
        val observations = mutableListOf<String>()
        var hiddenContentPixels = 0
        val insetBoundary = with(compose.density) { 230.dp.toPx() }
        scrollTo(insetBoundary - 1f)
        readability36Capture("$prefix-E-before-old-clip", width, height)
        scrollTo(insetBoundary)
        readability36Capture("$prefix-F-old-clip-boundary", width, height)
        scrollTo(insetBoundary + protectedHeight + 1f)
        readability36Capture("$prefix-F2-hero-left-viewport", width, height)

        // Find a real production media surface below each open nav gap. No mock backdrop.
        val gapCandidates = listOf("Movies" to "Shows", "Shows" to "Live TV", "Live TV" to "Home Movies")
            .map { (left, right) ->
                val a = compose.onNodeWithText(left).fetchSemanticsNode().boundsInRoot
                val b = compose.onNodeWithText(right).fetchSemanticsNode().boundsInRoot
                ((a.right + b.left) / 2f).toInt()
            }.filter { it > 80 && it < width - 80 }
        fun rowThroughGap(lazyIndex: Int, stage: String) {
            vertical.performScrollToIndex(lazyIndex)
            compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
            val surfaces = compose.onAllNodes(isFocusable()).fetchSemanticsNodes()
                .map { it.boundsInRoot }.filter { it.height >= 150f && it.width in 100f..(width / 2f) &&
                    it.top >= 0f && it.top < height / 2f }
            val pair = gapCandidates.firstNotNullOfOrNull { x ->
                surfaces.filter { x > it.left + 12f && x < it.right - 12f }
                    .minByOrNull { it.top }?.let { x to it }
            }
            assertNotNull("A real row surface must geometrically cross an actual nav gap: $surfaces", pair)
            val (x, surface) = checkNotNull(pair)
            vertical.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, surface.top - 24f) }
            compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
            val b = readability36Capture("$prefix-$stage", width, height)
            val y = navGeometry.center.y.toInt()
            val referenceY = protectedHeight + 20
            val emptyBackground = JellyLuThemeColors.darkScheme.background.toArgb()
            val expected = b.getPixel(x, referenceY)
            assertNotEquals("The diagnostic must contain visible media, not empty app background",
                emptyBackground, expected)
            val actual = b.getPixel(x, y)
            val firstY = 36
            var mismatches = 0
            // This column lies inside the real poster surface, away from rounded edges.
            // Poster glyphs are legitimate content, so do not assume a uniform fill.
            // The removed clip instead replaced ALL these pixels with emptyBackground.
            for (sampleY in firstY until protectedHeight - 8) {
                if (b.getPixel(x, sampleY) == emptyBackground) mismatches++
            }
            hiddenContentPixels += mismatches
            observations += "$stage gap=($x,$y) actual=${actual.toUInt().toString(16)} reference=($x,$referenceY) expected=${expected.toUInt().toString(16)} hiddenColumnPixels=$mismatches; sourceSurface=$surface"
            for ((name, pixels) in metadataInteriors) {
                assertArrayEquals("Scrolled $name center remains unchanged over real rows",
                    pixels, interiors(b).getValue(name))
            }
            assertEquals("Pinned control geometry over rows", navGeometry, homeTab().fetchSemanticsNode().boundsInRoot)
            coVerify(exactly = if (missing) 0 else 1) { loader.execute(any()) }
        }
        rowThroughGap(1, "G-continue-watching")
        rowThroughGap(2, "H-recently-added")
        rowThroughGap(6, "I-deep-home")
        if (focus != "Play" && focus != "Details") {
            compose.mainClock.advanceTimeBy(10_200); compose.waitForIdle()
        }
        coVerify(exactly = if (missing) 0 else 1) { loader.execute(any()) }
        File("build/reports/headerless-floating36c/$prefix-gap-diagnostics.txt")
            .writeText(observations.joinToString("\n"))
        compose.mainClock.autoAdvance = true
        back()
        homeTab().assertIsFocused().assertIsSelected()
        assertFullSpotlight07c("Cinematic A")
        val returned = readability36Capture("$prefix-J-back-top", width, height)
        assertNotEquals("Back restores artwork mode", obsoleteBacking, returned.getPixel(gapX, 1))
        if (focus == "Home") {
            val returnedCenters = interiors(returned)
            assertFalse("Clear material restored, not stale scrolled glass",
                metadataInteriors.getValue("Home").contentEquals(returnedCenters.getValue("Home")))
        }
        verify(exactly = 0) { spotlightVM.play(any()) }
        verify(exactly = 0) { spotlightVM.details(any()) }
        unchanged()
        assertEquals("Actual row content must remain visible through nav gaps after the hero leaves; " + observations, 0, hiddenContentPixels)
    }


    @Test fun headerless36cLargeFont1080() = readability36Visual(fontScale = 1.4f, focus = "Settings")

    @Test fun headerless36cFocusedCardsStayUsableBelowPinnedControls() {
        val loader = fullBleed28Loader(true, multiple = true)
        render(startOnHome = true, artworkLoader = loader, captureInitial = false)
        compose.onNodeWithText("Play").assertIsFocused()
        key(Key.DirectionDown)
        card(0, 0).assertIsFocused().assertIsDisplayed()
        val navBottom = homeTab().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Focused Continue Watching surface stays readable below navigation",
            card(0, 0).fetchSemanticsNode().boundsInRoot.top >= navBottom)
        key(Key.DirectionDown)
        card(1, 0).assertIsFocused().assertIsDisplayed()
        assertTrue("Focused Recently Added surface stays readable below navigation",
            card(1, 0).fetchSemanticsNode().boundsInRoot.top >= navBottom)
        readability36Capture("headerless36c-1920-media-card-focused", 1920, 1080)
        key(Key.DirectionRight); card(1, 1).assertIsFocused()
        key(Key.DirectionLeft); card(1, 0).assertIsFocused()
        verify(exactly = 0) { spotlightVM.play(any()) }
        unchanged()
    }

    @Test fun headerless36cAbsentHeroDoesNotClearTheNavigationGaps() {
        prepareSpotlightUp07c()
        every { spotlightVM.available } returns MutableStateFlow(false)
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = emptyList()))
        render(startOnHome = true, captureInitial = false)
        homeTab().requestFocus().assertIsFocused()
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        vertical.performScrollToIndex(3); compose.waitForIdle()
        val a = compose.onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithText("Shows").fetchSemanticsNode().boundsInRoot
        val x = ((a.right + b.left) / 2f).toInt()
        val surface = compose.onAllNodes(isFocusable()).fetchSemanticsNodes().map { it.boundsInRoot }
            .filter { it.width in 100f..960f && it.height >= 150f && it.top >= 0f && it.top < 540f &&
                x > it.left + 12f && x < it.right - 12f }.minBy { it.top }
        vertical.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, surface.top - 24f) }
        compose.waitForIdle()
        val image = readability36Capture("headerless36c-1920-absent-hero-row-gap", 1920, 1080)
        val referenceY = with(compose.density) { 56.dp.roundToPx() } + 20
        val expected = image.getPixel(x, referenceY)
        assertNotEquals(JellyLuThemeColors.darkScheme.background.toArgb(), expected)
        assertEquals("Actual row must remain visible even without a hero item",
            expected, image.getPixel(x, a.center.y.toInt()))
        homeTab().assertIsFocused()
        verify(exactly = 0) { spotlightVM.play(any()) }
        unchanged()
    }

    @Test fun readability36BrightHome1080() = readability36Visual()
    @Test fun readability36DarkSettings1080() = readability36Visual(bright = false, focus = "Settings")
    @Test fun readability36DetailedMovies1080() = readability36Visual(focus = "Movies",
        detail = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.HighDetail)
    @Test fun readability36SaturatedShows1080() = readability36Visual(focus = "Shows",
        detail = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Saturated)
    @Test fun readability36MissingArtwork() = readability36Visual(missing = true)
    @Test fun readability36PlayFocused() = readability36Visual(focus = "Play")
    @Test fun readability36DetailsFocused() = readability36Visual(focus = "Details", bright = false)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun readability36Bright720() = readability36Visual(width = 1280, height = 720)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun readability36Dark720() = readability36Visual(bright = false, width = 1280, height = 720, focus = "Settings")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun readability36LargeFont720() = readability36Visual(width = 1280, height = 720, fontScale = 1.4f, focus = "Settings")

    @Test fun readability36SearchFocused() = readability36Visual(focus = "Search")

    private fun fullBleed28Loader(bright: Boolean, multiple: Boolean = false, patterned: Boolean = false): coil3.ImageLoader {
        prepareSpotlightUp07c()
        val movies = listOf(
            BaseItemDto(id = UUID(28, 1), type = BaseItemKind.MOVIE, name = "Cinematic A",
                backdropImageTags = listOf("synthetic-a"), overview = "A controlled landscape for overlay verification."),
            BaseItemDto(id = UUID(28, 2), type = BaseItemKind.MOVIE, name = "Cinematic B",
                backdropImageTags = listOf("synthetic-b"), overview = "The next committed landscape."),
        ).take(if (multiple) 2 else 1)
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = movies))
        every { images.getItemImageUrl(any<UUID>(), ImageType.BACKDROP,
            maxWidth = 1600, maxHeight = 900, tag = any()) } answers {
            "https://artwork.invalid/" + firstArg<UUID>()
        }
        val loader = mockk<coil3.ImageLoader>()
        coEvery { loader.execute(any()) } coAnswers {
            val bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (bright) android.graphics.Color.rgb(240, 210, 130)
                    else android.graphics.Color.rgb(40, 110, 180))
                if (patterned) {
                    val canvas = android.graphics.Canvas(this)
                    val paint = android.graphics.Paint()
                    for (i in 0..11) {
                        paint.color = if (bright) android.graphics.Color.rgb(205 - i * 5, 170 + i * 3, 120 + i * 7)
                            else android.graphics.Color.rgb(12 + i * 3, 30 + i * 4, 60 + i * 6)
                        canvas.drawRect(i * 14f, 0f, i * 14f + 7f, 90f, paint)
                    }
                    paint.color = if (bright) 0xFFFAF0C0.toInt() else 0xFF779EBB.toInt()
                    canvas.drawCircle(128f, 24f, 16f, paint)
                }
            }
            mockk<coil3.request.SuccessResult> { every { image } returns bitmap.asImage() }
        }
        return loader
    }

    private fun fullBleed28Capture(name: String, width: Int = 1920, height: Int = 1080): Bitmap {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
        val file = File("build/reports/full-bleed-home28/$name.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        File(file.parentFile, "$name-semantics.txt").writeText(compose.onRoot().printToString())
        return bitmap
    }

    private fun fullBleed28Visual(bright: Boolean, fontScale: Float = 1f,
        width: Int = 1920, height: Int = 1080) {
        val loader = fullBleed28Loader(bright)
        render(startOnHome = true, fontScale = fontScale, artworkLoader = loader, captureInitial = false)
        compose.onNodeWithText("Play").assertIsFocused()
        key(Key.DirectionUp)
        homeTab().assertIsFocused().assertIsSelected()
        val home = homeTab().fetchSemanticsNode().boundsInRoot
        val homeMovies = compose.onNodeWithText("Home Movies").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Cinematic A").fetchSemanticsNode().boundsInRoot
        val play = compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
        assertTrue("Title must clear pinned navigation", title.top > home.bottom)
        assertTrue("Actions must clear pinned navigation", play.top > home.bottom)
        val bitmap = fullBleed28Capture(
            "full-bleed-" + (if (bright) "bright" else "dark") + "-" + width + "-font-" + fontScale,
            width, height)
        val gapX = ((homeMovies.right + search.left) / 2f).toInt()
        val background = JellyLuThemeColors.darkScheme.background.toArgb()
        assertNotEquals("The existing artwork must reach the screen top", background, bitmap.getPixel(gapX, 1))
        assertNotEquals("Artwork must be visible between pills", background, bitmap.getPixel(gapX, home.center.y.toInt()))
        assertNotEquals("Glass navigation must paint above artwork", background,
            bitmap.getPixel(home.left.toInt() + 12, home.center.y.toInt()))
        val outline = com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors.NavigationFocusOutline.toArgb()
        assertTrue("The original focus outline must remain visible above the glass",
            (0..6).any { bitmap.getPixel(home.left.toInt() + it, home.center.y.toInt()) == outline })
        coVerify(exactly = 1) { loader.execute(any()) }
        verify(exactly = 0) { spotlightVM.play(any()) }
        verify(exactly = 0) { spotlightVM.details(any()) }
        unchanged()
    }

    @Test fun fullBleed28BrightArtworkBehindActualNavigation1080p() = fullBleed28Visual(true)
    @Test fun fullBleed28DarkArtworkBehindActualNavigation1080p() = fullBleed28Visual(false)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun fullBleed28ArtworkBehindActualNavigation720p() =
        fullBleed28Visual(true, width = 1280, height = 720)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun fullBleed28LargeFontKeepsContentBelowNavigation720p() {
        val loader = fullBleed28Loader(true)
        render(startOnHome = true, fontScale = 1.4f, artworkLoader = loader, captureInitial = false)
        key(Key.DirectionUp)
        val labels = listOf("Movies", "Shows", "Live TV", "Home Movies")
        for (label in labels) { key(Key.DirectionRight); compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed() }
        key(Key.DirectionRight)
        compose.onNodeWithContentDescription("Search").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionRight)
        settingsIcon().assertIsFocused().assertIsDisplayed()
        val navBottom = settingsIcon().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(compose.onNodeWithText("Cinematic A").fetchSemanticsNode().boundsInRoot.top > navBottom)
        fullBleed28Capture("full-bleed-720p-large-font-settings-focused", 1280, 720)
        unchanged()
    }

    @Test fun polish28bFullBleedHomeSelectedWhileSettingsFocusedHasNoMarker() {
        val loader = fullBleed28Loader(true)
        render(startOnHome = true, artworkLoader = loader, captureInitial = false)
        settingsIcon().requestFocus().assertIsFocused().assertIsNotSelected()
        homeTab().assertIsSelected().assertIsNotFocused()
        val bitmap = fullBleed28Capture("polish28b-full-bleed-home-selected-settings-focused")
        for (node in listOf(homeTab(), settingsIcon())) {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            for (dx in -12..12) {
                val reference = bitmap.getPixel(bounds.center.x.toInt() - 18, bounds.bottom.toInt() - 8)
                val actual = bitmap.getPixel(bounds.center.x.toInt() + dx, bounds.bottom.toInt() - 8)
                assertEquals(reference ushr 24, actual ushr 24)
                for (shift in listOf(0, 8, 16)) assertTrue("No marker in the glass gradient",
                    kotlin.math.abs(((reference ushr shift) and 255) - ((actual ushr shift) and 255)) <= 2)
            }
        }
        assertNotEquals(JellyLuThemeColors.darkScheme.background.toArgb(), bitmap.getPixel(1450, 1))
        coVerify(exactly = 1) { loader.execute(any()) }
        unchanged()
    }

    private fun native33Capture(name: String, width: Int = 1920, height: Int = 1080): Bitmap {
        val b = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(width, b.width); assertEquals(height, b.height)
        val file = File("build/reports/neutral-glass35/" + name + ".png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return b
    }

    private fun native33Visual(
        strength: com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant,
        bright: Boolean, width: Int = 1920, height: Int = 1080, fontScale: Float = 1f,
        detailed: com.github.damontecres.wholphin.ui.theme.Glass31Artwork? = null,
        treatment: com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment? = null,
    ) {
        val loader = fullBleed28Loader(bright, multiple = true, patterned = true)
        if (detailed != null) coEvery { loader.execute(any()) } coAnswers {
            val bitmap = com.github.damontecres.wholphin.ui.theme.glass31Artwork(detailed)
            mockk<coil3.request.SuccessResult> { every { image } returns bitmap.asImage() }
        }
        render(startOnHome = true, fontScale = fontScale, artworkLoader = loader, captureInitial = false,
            glass = strength, treatment = treatment ?: com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)
        val prefix = (if (treatment == null) "native33-" else "neutral35-" + treatment.name + "-") + strength.name + "-" + (detailed?.name ?: if (bright) "bright" else "dark") +
            "-" + width + (if (fontScale == 1f) "" else "-font-" + fontScale)
        val play = compose.onNodeWithText("Play")
        play.assertIsFocused()
        val geometry = play.fetchSemanticsNode().boundsInRoot
        if (detailed != null) {
            settingsIcon().requestFocus().assertIsFocused()
            homeTab().assertIsSelected()
            native33Capture(prefix + "-settings", width, height)
            coVerify(exactly = 1) { loader.execute(any()) }
            verify(exactly = 0) { spotlightVM.play(any()) }
            unchanged()
            return
        }
        native33Capture(prefix + "-play", width, height)
        compose.onNodeWithText("Details").requestFocus().assertIsFocused()
        native33Capture(prefix + "-details", width, height)
        key(Key.DirectionUp)
        homeTab().assertIsFocused().assertIsSelected()
        native33Capture(prefix + "-home", width, height)
        key(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Lunoco").assertIsFocused()
        key(Key.DirectionRight)
        homeTab().assertIsFocused()
        for (label in listOf("Movies", "Shows", "Live TV", "Home Movies")) {
            key(Key.DirectionRight); compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
        }
        key(Key.DirectionRight); compose.onNodeWithContentDescription("Search").assertIsFocused()
        key(Key.DirectionRight); settingsIcon().assertIsFocused().assertIsNotSelected()
        homeTab().assertIsSelected().assertIsNotFocused()
        val nav = settingsIcon().fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithText("Cinematic A").fetchSemanticsNode().boundsInRoot.top > nav.bottom)
        assertTrue(geometry.top > nav.bottom)
        assertEquals(geometry, play.fetchSemanticsNode().boundsInRoot)
        val movies = compose.onNodeWithText("Home Movies").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        val gapX = ((movies.right + search.left) / 2f).toInt()
        val bitmap = native33Capture(prefix + "-settings", width, height)
        assertNotEquals("Art remains visible in the navigation gap",
            JellyLuThemeColors.darkScheme.background.toArgb(), bitmap.getPixel(gapX, 1))
        coVerify(exactly = 1) { loader.execute(any()) }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_250); compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250); compose.waitForIdle()
        compose.onNodeWithText("Cinematic B").assertIsDisplayed()
        settingsIcon().assertIsFocused()
        coVerify(exactly = 2) { loader.execute(any()) }
        compose.mainClock.autoAdvance = true
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(6)
        val scrolled = native33Capture(prefix + "-scrolled", width, height)
        assertEquals(JellyLuThemeColors.darkScheme.background.toArgb(), scrolled.getPixel(gapX, 1))
        settingsIcon().assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_250); compose.waitForIdle()
        coVerify(exactly = 2) { loader.execute(any()) }
        compose.mainClock.autoAdvance = true
        verify(exactly = 0) { spotlightVM.play(any()) }
        verify(exactly = 0) { spotlightVM.details(any()) }
        unchanged()
    }

    private fun native33Missing(strength: com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant,
        treatment: com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment? = null) {
        val loader = fullBleed28Loader(true)
        every { spotlightVM.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = listOf(
            BaseItemDto(id = UUID(31, 0), type = BaseItemKind.MOVIE, name = "No artwork"))))
        render(startOnHome = true, artworkLoader = loader, captureInitial = false,
            glass = strength, treatment = treatment ?: com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        settingsIcon().requestFocus().assertIsFocused()
        homeTab().assertIsSelected()
        native33Capture((if (treatment == null) "native33-" else "neutral35-" + treatment.name + "-") + strength.name + "-missing-1920-settings")
        coVerify(exactly = 0) { loader.execute(any()) }
        unchanged()
    }

    @Test
    fun native33Lightweightbright1920() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, 1920, 1080)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33Lightweightbright1280() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, 1280, 720)

    @Test
    fun native33Lightweightdark1920() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, false, 1920, 1080)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33Lightweightdark1280() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, false, 1280, 720)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33LightweightLargeFont() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, 1280, 720, 1.4f)

    @Test fun native33LightweightBrightLandscape() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.BrightLandscape)

    @Test fun native33LightweightDarkScene() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.DarkScene)

    @Test fun native33LightweightSaturated() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Saturated)

    @Test fun native33LightweightHighDetail() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.HighDetail)

    @Test fun native33LightweightMixed() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Mixed)

    @Test fun native33LightweightMissing() = native33Missing(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight)

    @Test
    fun native33Balancedbright1920() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33Balancedbright1280() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720)

    @Test
    fun native33Balanceddark1920() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1920, 1080)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33Balanceddark1280() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1280, 720)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33BalancedLargeFont() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720, 1.4f)

    @Test fun native33BalancedBrightLandscape() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.BrightLandscape)

    @Test fun native33BalancedDarkScene() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.DarkScene)

    @Test fun native33BalancedSaturated() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Saturated)

    @Test fun native33BalancedHighDetail() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.HighDetail)

    @Test fun native33BalancedMixed() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Mixed)

    @Test fun native33BalancedMissing() = native33Missing(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced)

    @Test
    fun native33Enhancedbright1920() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, 1920, 1080)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33Enhancedbright1280() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, 1280, 720)

    @Test
    fun native33Enhanceddark1920() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, false, 1920, 1080)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33Enhanceddark1280() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, false, 1280, 720)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun native33EnhancedLargeFont() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, 1280, 720, 1.4f)

    @Test fun native33EnhancedBrightLandscape() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.BrightLandscape)

    @Test fun native33EnhancedDarkScene() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.DarkScene)

    @Test fun native33EnhancedSaturated() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Saturated)

    @Test fun native33EnhancedHighDetail() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.HighDetail)

    @Test fun native33EnhancedMixed() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, detailed = com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Mixed)

    @Test fun native33EnhancedMissing() = native33Missing(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced)

    // Production shell and controls, synthetic artwork. These captures do not certify GPU optics.
    private fun glass30Visual(
        variant: com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant,
        bright: Boolean, width: Int = 1920, height: Int = 1080, fontScale: Float = 1f,
    ) {
        val loader = fullBleed28Loader(bright, multiple = true, patterned = true)
        render(startOnHome = true, fontScale = fontScale, artworkLoader = loader,
            captureInitial = false, glass = variant)
        val prefix = "glass30-" + variant.name + (if (bright) "-bright-" else "-dark-") + width +
            (if (fontScale == 1f) "" else "-font-" + fontScale)
        compose.onNodeWithText("Play").assertIsFocused()
        val originalBounds = compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
        fullBleed28Capture(prefix + "-play", width, height)
        compose.onNodeWithText("Details").requestFocus().assertIsFocused()
        fullBleed28Capture(prefix + "-details", width, height)
        key(Key.DirectionUp)
        homeTab().assertIsFocused().assertIsSelected()
        fullBleed28Capture(prefix + "-home", width, height)
        key(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Lunoco").assertIsFocused()
        key(Key.DirectionRight)
        homeTab().assertIsFocused()
        for (label in listOf("Movies", "Shows", "Live TV", "Home Movies")) {
            key(Key.DirectionRight)
            compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
        }
        key(Key.DirectionRight)
        compose.onNodeWithContentDescription("Search").assertIsFocused()
        key(Key.DirectionRight)
        settingsIcon().assertIsFocused().assertIsNotSelected()
        fullBleed28Capture(prefix + "-settings", width, height)
        homeTab().assertIsSelected().assertIsNotFocused()
        assertEquals(originalBounds, compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot)
        // Advancing while nav has focus must neither move focus nor re-fetch the old backdrop.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_250)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        settingsIcon().assertIsFocused()
        compose.onNodeWithText("Cinematic B").assertIsDisplayed()
        coVerify(exactly = 2) { loader.execute(any()) }
        compose.mainClock.autoAdvance = true
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        vertical.performScrollToIndex(6)
        fullBleed28Capture(prefix + "-scrolled", width, height)
        settingsIcon().assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_250)
        compose.waitForIdle()
        coVerify(exactly = 2) { loader.execute(any()) }
        compose.mainClock.autoAdvance = true
        unchanged()
    }

    @Test fun glass30ClearActionsUseVisibleMovieAfterFocusedRotation() {
        val loader = fullBleed28Loader(true, multiple = true, patterned = true)
        render(startOnHome = true, artworkLoader = loader, captureInitial = false,
            glass = com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced)
        compose.onNodeWithText("Play").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_250)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Cinematic B").assertIsDisplayed()
        key(Key.DirectionCenter)
        verify(exactly = 1) { spotlightVM.play(match { it.id == UUID(28, 2) }) }
        verify(exactly = 0) { spotlightVM.play(match { it.id == UUID(28, 1) }) }
        compose.onNodeWithText("Details").requestFocus()
        key(Key.DirectionCenter)
        verify(exactly = 1) { spotlightVM.details(match { it.id == UUID(28, 2) }) }
        compose.mainClock.autoAdvance = true
        unchanged()
    }

    @Test fun glass30LightBright1080() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true)
    @Test fun glass30LightDark1080() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, false)
    @Test fun glass30ClearBright1080() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true)
    @Test fun glass30ClearDark1080() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false)
    @Test fun glass30FrostedBright1080() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true)
    @Test fun glass30FrostedDark1080() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, false)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30Light720LargeFont() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, 1280, 720, 1.4f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30Clear720() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30Frosted720() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, false, 1280, 720)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30ExtraLight720Bright() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, true, 1280, 720)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30ExtraFrosted720Bright() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, 1280, 720)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30ExtraLight720Dark() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Lightweight, false, 1280, 720)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30ExtraClear720Dark() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1280, 720)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30ExtraClear720LargeFont() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720, 1.4f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun glass30ExtraFrosted720LargeFont() = glass30Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Enhanced, true, 1280, 720, 1.4f)

    @Test fun glass30ExtraClearEveryControlKeepsFocusAndManualPagingRestartsCountdown() {
        val loader = fullBleed28Loader(true, multiple = true, patterned = true)
        render(startOnHome = true, artworkLoader = loader, captureInitial = false,
            glass = com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced)
        val controls = listOf(compose.onNodeWithText("Play"), compose.onNodeWithText("Details"),
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.jellylu_spotlight_previous)),
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.jellylu_spotlight_next)))
        compose.mainClock.autoAdvance = false
        var second = false
        for (control in controls) {
            control.requestFocus().assertIsFocused()
            compose.mainClock.advanceTimeBy(10_250)
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
            second = !second
            control.assertIsFocused()
            compose.onNodeWithText(if (second) "Cinematic B" else "Cinematic A").assertIsDisplayed()
        }
        compose.mainClock.advanceTimeBy(5_000)
        key(Key.DirectionCenter) // Next, never Play; the callbacks remain the original ones.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithText("Cinematic B").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithText("Cinematic B").assertIsDisplayed()
        controls.last().assertIsFocused()
        compose.mainClock.advanceTimeBy(4_500)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithText("Cinematic A").assertIsDisplayed()
        controls.last().assertIsFocused()
        verify(exactly = 0) { spotlightVM.play(any()) }
        verify(exactly = 0) { spotlightVM.details(any()) }
        compose.mainClock.autoAdvance = true
        unchanged()
    }

    @Test fun fullBleed28ScrolledHeroLeavesAppBackgroundAndBackRestoresArtwork() {
        val loader = fullBleed28Loader(true, multiple = true)
        render(startOnHome = true, artworkLoader = loader)
        key(Key.DirectionUp)
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        vertical.performScrollToIndex(6)
        compose.waitForIdle()
        val bitmap = fullBleed28Capture("full-bleed-scrolled-pinned-navigation")
        assertEquals(JellyLuThemeColors.darkScheme.background.toArgb(), bitmap.getPixel(1450, 40))
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_200)
        compose.waitForIdle()
        coVerify(exactly = 1) { loader.execute(any()) }
        compose.mainClock.autoAdvance = true
        back()
        homeTab().assertIsFocused()
        assertFullSpotlight07c("Cinematic A")
        val returned = fullBleed28Capture("full-bleed-back-to-top")
        assertNotEquals(JellyLuThemeColors.darkScheme.background.toArgb(), returned.getPixel(1450, 1))
        unchanged()
    }

    @Test fun fullBleed28SingleArtworkPipelineRotatesWhileNavigationFocused() {
        val loader = fullBleed28Loader(false, multiple = true)
        render(startOnHome = true, artworkLoader = loader)
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_100)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onNodeWithText("Cinematic B").assertIsDisplayed()
        homeTab().assertIsFocused().assertIsSelected()
        coVerify(exactly = 2) { loader.execute(any()) }
        verify(exactly = 0) { spotlightVM.play(any()) }
        verify(exactly = 0) { spotlightVM.details(any()) }
        unchanged()
    }

    @Test fun floating26ActualShellTraversesEveryTopControlWithoutActivatingOrScrollingHome() {
        prepareSpotlightUp07c()
        render(startOnHome = true)
        key(Key.DirectionUp)
        homeTab().assertIsFocused().assertIsSelected()
        val vertical = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        val before = vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        for (label in listOf("Movies", "Shows", "Live TV", "Home Movies")) {
            key(Key.DirectionRight)
            compose.onNodeWithText(label).assertIsFocused().assertIsNotSelected()
            homeTab().assertIsSelected()
        }
        key(Key.DirectionRight)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.search)).assertIsFocused()
        key(Key.DirectionRight)
        settingsIcon().assertIsFocused().assertIsNotSelected()
        homeTab().assertIsSelected()
        assertEquals(before, vertical.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value())
        assertTrue(navigation.backStack.last() is Destination.Home)
        capture("floating26-real-home-settings-focused")
        repeat(6) { key(Key.DirectionLeft) }
        homeTab().assertIsFocused()
        key(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Lunoco").assertIsFocused()
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focusedEvidence("FULL_BLEED28_NAVIGATION_DOWN")
        compose.onNodeWithText("Play").assertIsFocused()
        unchanged()
    }

    @Test fun floating26ContinuousRotationKeepsActualHomeNavigationFocused() {
        prepareSpotlightUp07c()
        val movies = listOf(
            BaseItemDto(id = UUID(26, 100), type = BaseItemKind.MOVIE, name = "Floating feature A"),
            BaseItemDto(id = UUID(26, 101), type = BaseItemKind.MOVIE, name = "Floating feature B"),
        )
        val state = MutableStateFlow(SpotlightState(resolved = true, movies = movies))
        every { spotlightVM.state } returns state
        render(startOnHome = true)
        key(Key.DirectionUp)
        homeTab().assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(10_100)
        compose.waitForIdle()
        compose.onNodeWithText("Floating feature B").assertIsDisplayed()
        homeTab().assertIsFocused().assertIsSelected()
        compose.mainClock.advanceTimeBy(10_100)
        compose.waitForIdle()
        compose.onNodeWithText("Floating feature A").assertIsDisplayed()
        homeTab().assertIsFocused()
        compose.runOnIdle { assertEquals(movies, state.value.movies) }
        verify(exactly = 0) { spotlightVM.play(any()) }
        verify(exactly = 0) { spotlightVM.details(any()) }
        unchanged()
    }

    @Test fun floating26RealShellKeepsMoviesShowsAndHomeMoviesLibraryRoutes() {
        render(startOnHome = true)
        for ((label, id) in listOf("Movies" to UUID(9, 0), "Shows" to UUID(9, 1))) {
            compose.onNodeWithText(label).requestFocus()
            key(Key.DirectionCenter)
            compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle {
                val route = navigation.backStack.last() as Destination.MediaItem
                assertEquals(id, route.itemId)
                assertTrue(route.browseGrid)
            }
            capture("cohesion37-${label.lowercase()}-grid")
        }
        compose.onNodeWithText("Home Movies").requestFocus()
        key(Key.DirectionCenter)
        compose.runOnIdle {
            val route = navigation.backStack.last() as Destination.MediaItem
            assertEquals(UUID(9, 5), route.itemId)
            assertTrue(route.browseGrid)
        }
        capture("cohesion37-home-movies-grid")
        homeTab().requestFocus()
        key(Key.DirectionCenter)
        assertTrue(navigation.backStack.last() is Destination.Home)
        unchanged()
    }

    @Test
    fun cohesion37GuideAndDvrDisplayExistingRecordingState() {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val start = now.minusMinutes(30)
        val channel = TvChannel(UUID(37, 1), "7", "Lunoco fixture", null, false)
        val programs = List(3) { index ->
            TvProgram(
                id = UUID(37, index.toLong() + 2),
                channelId = channel.id,
                start = start.plusHours(index.toLong()),
                end = start.plusHours(index.toLong() + 1),
                startHours = index.toFloat(),
                endHours = index + 1f,
                duration = 60.minutes,
                name = "Guide program ${index + 1}",
                subtitle = "Fixture schedule",
                seasonEpisode = null,
                isRecording = index == 0,
                isSeriesRecording = false,
                isRepeat = false,
                category = null,
            )
        }
        val recording = BaseItem(BaseItemDto(
            id = UUID(37, 7), name = "Fixture recording", type = BaseItemKind.PROGRAM,
            startDate = start, endDate = start.plusHours(1),
        ))
        var showDvr by mutableStateOf(false)
        var focusedProgram by mutableStateOf<TvProgram?>(null)
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                LunocoContentTheme {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (showDvr) {
                            DvrScheduleContent(listOf(recording), emptyMap(), {}, Modifier.fillMaxSize())
                        } else {
                            val requester = remember { FocusRequester() }
                            TvGuideGridContent(
                                preferences = LiveTvPreferences.getDefaultInstance(),
                                refreshing = false,
                                channels = listOf(channel),
                                programs = FetchedPrograms(0..2, programs, mapOf(channel.id to programs)),
                                channelProgramCount = mapOf(channel.id to programs.size),
                                guideTimes = List(4) { start.plusHours(it.toLong()) },
                                onClickChannel = { _, _ -> fail("The fixture must not tune a channel") },
                                onClickProgram = { _, program ->
                                    focusedProgram = program
                                },
                                onFocus = {},
                                focusRequester = requester,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        focusedProgram?.let { program ->
                            ProgramDialog(
                                state = DataLoadingState.Success(BaseItem(BaseItemDto(
                                    id = program.id, name = "Fixture program details", type = BaseItemKind.PROGRAM,
                                    startDate = program.start, endDate = program.end, overview = program.subtitle,
                                ))),
                                canRecord = true,
                                onDismissRequest = { focusedProgram = null },
                                onWatch = { fail("The fixture must not start playback") },
                                onRecord = { _, _ -> fail("The fixture must not start recording") },
                                onCancelRecord = { _, _ -> fail("The fixture must not cancel recording") },
                            )
                        }
                    }
                }
            }
        }
        val current = compose.onNodeWithText("Guide program 1")
        current.requestFocus().assertIsFocused()
        capture("cohesion37-guide-focused-recording")
        current.performKeyInput { pressKey(Key.DirectionRight) }
        val next = compose.onNodeWithText("Guide program 2")
        next.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(programs[1].id, focusedProgram?.id)
        compose.onNodeWithText("Fixture program details").assertIsDisplayed()
        capture("cohesion37-program-dialog")
        compose.runOnIdle {
            // Robolectric's Compose key injection bypasses the dialog window's Back dispatcher.
            val dialog = requireNotNull(ShadowDialog.getLatestDialog())
            check(dialog.isShowing)
            dialog.onBackPressed()
        }
        compose.onNodeWithText("Fixture program details").assertDoesNotExist()
        compose.runOnIdle { showDvr = true }
        compose.onNodeWithText("Fixture recording").assertIsDisplayed()
        capture("cohesion37-dvr")
        unchanged()
    }

    @Test
    fun cohesion37PausedOsdAndAudioSelectionUseLocalFixture() {
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 46_000L
        every { player.bufferedPosition } returns 80_000L
        every { player.duration } returns 185_000L
        var trackMenu by mutableStateOf(false)
        var selection: SimpleMediaStream? = null
        val english = SimpleMediaStream(0, "English fixture", "Stereo")
        val french = SimpleMediaStream(1, "French fixture", "Stereo")
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                LunocoContentTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFFA0A0A0))) {
                        PlaybackControls(
                            player = player,
                            controllerViewState = remember { ControllerViewState(5_000L, true) },
                            onPlaybackActionClick = {},
                            onClickPlaybackDialogType = { trackMenu = true },
                            onSeekProgress = {},
                            showPlay = true,
                            previousEnabled = true,
                            nextEnabled = true,
                            seekEnabled = true,
                            seekBarIntervals = 10,
                            seekBack = 10.seconds,
                            skipBackOnResume = null,
                            seekForward = 30.seconds,
                            currentSegment = null,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        )
                        if (trackMenu) {
                            StreamChoiceBottomDialog(
                                choices = listOf(english, french),
                                onDismissRequest = { trackMenu = false },
                                onSelectChoice = { _, choice -> selection = choice },
                                gravity = android.view.Gravity.END,
                                currentChoice = english.index,
                            )
                        }
                    }
                }
            }
        }
        val audio = compose.onNodeWithText(compose.activity.getString(R.string.fa_volume_high))
        audio.requestFocus().assertIsFocused()
        capture("cohesion37-osd-paused")
        audio.performKeyInput { pressKey(Key.DirectionCenter) }
        val current = compose.onNode(hasText("English fixture") and isSelected())
        current.assertIsFocused()
        capture("cohesion37-audio-track")
        current.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("French fixture").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(french, selection)
        compose.onNodeWithText("French fixture").assertDoesNotExist()
        verify(exactly = 0) { player.play() }
        verify(exactly = 0) { player.pause() }
        unchanged()
    }

    @Test
    fun cohesion37MovieDetailsUsesExistingBrightAndDarkBackdrop() {
        val id = UUID(37, 80)
        val title = "The long way home under an unfamiliar sky"
        val overview = "A long description for the existing Details page. " +
            "The same title, information and actions remain visible while the fixture backdrop changes."
        val movie = BaseItem(BaseItemDto(
            id = id, type = BaseItemKind.MOVIE, name = title, overview = overview,
            productionYear = 2025,
        ))
        fun localArt(kind: com.github.damontecres.wholphin.ui.theme.Glass31Artwork): String {
            val file = File(compose.activity.cacheDir, "cohesion37-$kind.png")
            file.outputStream().use { stream ->
                assertTrue(com.github.damontecres.wholphin.ui.theme.glass31Artwork(kind)
                    .compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            return android.net.Uri.fromFile(file).toString()
        }
        val bright = localArt(com.github.damontecres.wholphin.ui.theme.Glass31Artwork.BrightLandscape)
        val dark = localArt(com.github.damontecres.wholphin.ui.theme.Glass31Artwork.DarkScene)
        val mood = MutableStateFlow(BackdropResult(id.toString(), bright))
        every { backdrop.backdropFlow } returns mood
        every { movieVM.state } returns MutableStateFlow(
            com.github.damontecres.wholphin.ui.detail.movie.MovieState(
                loading = DataLoadingState.Success(movie),
            ),
        )
        compose.runOnIdle {
            data.state.value = data.state.value.updateInterfacePreferences {
                backdropStyle = BackdropStyle.BACKDROP_IMAGE_ONLY
            }
        }
        val route = Destination.MediaItem(id, BaseItemKind.MOVIE)
        render(origin = route, captureInitial = false)
        val titleBounds = compose.onNodeWithText(title).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        // The overview and shell controls are focusable too. Match the compact action row.
        val buttonRole = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role,
            androidx.compose.ui.semantics.Role.Button,
        )
        val firstAction = compose.onAllNodes(isFocusable() and buttonRole and !hasText(overview))
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.top > titleBounds.bottom && it.boundsInRoot.width < 250f }
            .minBy { it.boundsInRoot.left }
        compose.onNode(SemanticsMatcher("first Movie Details action") { it.id == firstAction.id })
            .requestFocus().assertIsFocused()
        val play = compose.onNodeWithText("Play")
        play.assertIsFocused()
        val sample = { android.graphics.Color.red(compose.onRoot().captureToImage().asAndroidBitmap().getPixel(1560, 220)) }
        compose.waitUntil(15_000) { sample() > 65 }
        capture("cohesion37-details-bright")
        play.performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { mood.value = BackdropResult(id.toString(), dark) }
        compose.waitUntil(15_000) { sample() < 60 }
        capture("cohesion37-details-dark-secondary")
        assertEquals(route, navigation.backStack.last())
        unchanged()
    }

    @Test
    fun cohesion37SearchResultsAndDetailsUseProductionRoutes() {
        val state = MutableStateFlow(SearchState(combinedResults = SearchResult.Success(emptyList())))
        every { searchVM.state } returns state
        every { searchVM.programDialogState } returns MutableStateFlow(ProgramDialogState())
        every { searchVM.seerrActive } returns MutableStateFlow(false)
        every { searchVM.position } returns MutableStateFlow(com.github.damontecres.wholphin.ui.data.RowColumn(0, 0))
        every { searchVM.navigationManager } returns navigation
        every { searchVM.userPreferencesService.flow } returns MutableStateFlow(
            UserPreferences(data.state.value.updateSearchPreferences {
                combinedSearchResults = true
                showVoiceSearchButton = false
            }, null),
        )
        render(origin = Destination.Search("Moon"), captureInitial = false)
        compose.onNodeWithText("Moon").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.no_results)).assertIsDisplayed()
        capture("cohesion37-search-no-results")

        val item = BaseItem(rows.first().items.first()!!.data)
        compose.runOnIdle { state.value = SearchState(combinedResults = SearchResult.Success(listOf(item))) }
        compose.onNodeWithText(item.title!!).assertIsDisplayed()
        focusLibraryArtwork(0)
        val labelLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(item.title!!, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(labelLayouts)) }
        val labelColor = labelLayouts.single().layoutInput.style.color
        assertTrue("Search result captions require light text on the dark canvas",
            labelColor.red > .85f && labelColor.green > .85f && labelColor.blue > .85f)
        capture("cohesion37-search-results")
        key(Key.DirectionCenter)
        assertEquals(Destination.MediaItem(item.id, item.type), navigation.backStack.last())
        capture("cohesion37-movie-details")
        back()
        compose.onNodeWithText("Moon").assertIsDisplayed()
        compose.onNodeWithText(item.title!!).assertIsDisplayed()
        unchanged()
    }

    @Test fun floating26ProfileAndLibraryMenusKeepExistingActionsAndFocus() {
        render(startOnHome = true)
        compose.onNodeWithContentDescription("Lunoco").requestFocus()
        key(Key.DirectionCenter)
        compose.onNodeWithText(compose.activity.getString(R.string.profile)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.favorites)).assertExists()
        // Dialog and activity have separate focus roots; target the actual dialog choice.
        compose.onNode(hasText(compose.activity.getString(R.string.profile)) and hasAnyAncestor(isDialog()))
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { drawerVM.navigateToSetup(SetupDestination.UserList(server)) }
        compose.onNodeWithText(compose.activity.getString(R.string.favorites)).assertDoesNotExist()
        compose.onNodeWithContentDescription("Lunoco").assertIsFocused()
        compose.onNodeWithText("Movies").requestFocus()
        key(Key.DirectionCenter)
        compose.onNodeWithText("Kids Movies").assertExists()
        compose.onNode(hasText("Movies") and hasAnyAncestor(isPopup())).assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Kids Movies").assertDoesNotExist()
        compose.onNodeWithText("Movies").assertIsFocused()
        assertTrue(navigation.backStack.last() is Destination.Home)
        unchanged()
    }

    @Test fun actualApplicationRouteIncludesApprovedNavigationAndSingleSafeInset() {
        render()
        compose.onNodeWithContentDescription("Lunoco").assertExists()
        homeTab().assertExists()
        listOf("Movies", "Shows", "Live TV", "Home Movies").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        settingsIcon().assertExists()
        val category = category(LunocoSettingsCategory.PLAYBACK)
        category.assertIsFocused().assertIsSelected()
        val categoryBounds = category.fetchSemanticsNode().boundsInRoot
        assertEquals(56f, categoryBounds.left, 1f)
        assertEquals(234f, categoryBounds.top, 1f)
        val heading = compose.onNodeWithText(compose.activity.getString(R.string.advanced_settings)).fetchSemanticsNode().boundsInRoot
        assertEquals(56f, heading.left, 1f)
        assertEquals(144f, heading.top, 1f)
        val detailTitle = compose.onNode(hasText("Playback") and !hasAnyAncestor(hasTestTag("settings-categories")))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(536f, detailTitle.left, 1f)
        val pane = compose.onNodeWithTag("settings-detail-pane").fetchSemanticsNode().boundsInRoot
        assertEquals(536f, pane.left, 1f)
        assertEquals(1864f, pane.right, 1f)
        assertTrue(row("play.skip_forward").fetchSemanticsNode().boundsInRoot.left >= 536f)
        compose.onAllNodesWithText(compose.activity.getString(R.string.advanced_settings)).assertCountEquals(1)
        capture("settings06b-integrated-initial")
        unchanged()
    }

    @Test fun allEightCategoriesUseRealShellWithoutChangingPreferences() {
        render()
        for (cat in LunocoSettingsCategory.entries) {
            enter(cat)
            compose.onNode(isFocused()).assertHasClickAction()
            key(Key.DirectionLeft)
            category(cat).assertIsFocused().assertIsSelected()
            compose.onNodeWithContentDescription("Lunoco").assertExists()
        }
        unchanged()
    }

    @Test fun categoryDpadScrollingRevealsTheLastCategoryWithoutSelectingIt() {
        render()
        for (cat in LunocoSettingsCategory.entries.drop(1)) {
            key(Key.DirectionDown)
            compose.onNodeWithTag("settings-category-${cat.name}").assertIsFocused()
        }
        val support = compose.onNodeWithTag("settings-category-SUPPORT").fetchSemanticsNode().boundsInRoot
        assertTrue(support.bottom <= 1048f)
        category(LunocoSettingsCategory.PLAYBACK).assertIsSelected()
        unchanged()
    }

    @Test fun topNavigationIsReachableWithDpadAndDownRestoresCategory() {
        render()
        key(Key.DirectionUp)
        assertTrue(compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.top < 112f)
        key(Key.DirectionDown)
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun categoryFocusDoesNotSelectAndRightLeftKeepTheirContract() {
        render()
        category(LunocoSettingsCategory.AUDIO).requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        row("play.skip_forward").assertIsFocused()
        key(Key.DirectionLeft)
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
        enter(LunocoSettingsCategory.AUDIO)
        row("audio.profile").assertIsFocused()
        key(Key.DirectionLeft)
        category(LunocoSettingsCategory.AUDIO).assertIsFocused().assertIsSelected()
        unchanged()
    }

    @Test fun currentChoiceAndDismissRestoreExactIntegratedRow() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.theme").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(isFocused() and isSelected()).assertExists()
        back()
        row("appearance.theme").assertIsFocused()
        capture("settings06b-integrated-appearance")
        unchanged()
    }

    @Test fun multichoiceBackDoesNotWriteAndRestoresOrigin() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.ratings").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("settings-choice-0").assertExists()
        back()
        row("appearance.ratings").assertIsFocused()
        unchanged()
    }

    @Test fun integratedSliderModeBackExitsWithoutPoppingOrChangingValue() {
        render()
        enter(LunocoSettingsCategory.PLAYBACK)
        val before = data.state.value
        row("play.skip_forward").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.lunoco_settings_adjusting)).assertExists()
        back()
        assertTrue(navigation.backStack.last() is Destination.Settings)
        row("play.skip_forward").assertIsFocused()
        key(Key.DirectionLeft)
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
        assertEquals(before, data.state.value)
        unchanged()
    }

    @Test fun legacyNestedSettingsKeepsShellAndBackRestoresOrigin() {
        render()
        enter(LunocoSettingsCategory.PLAYBACK)
        row("play.segments").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(PreferenceScreenOption.SKIP_SEGMENTS, (navigation.backStack.last() as Destination.Settings).screen)
        compose.onNodeWithContentDescription("Lunoco").assertExists()
        row("play.intros").assertIsFocused()
        back()
        row("play.segments").assertIsFocused()
        category(LunocoSettingsCategory.PLAYBACK).assertIsSelected()
        unchanged()
    }

    @Test fun settingsIconReselectionDoesNotPushOrResetAndDownReturnsToContent() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        val route = navigation.backStack.last()
        val size = navigation.backStack.size
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(size, navigation.backStack.size)
        assertSame(route, navigation.backStack.last())
        settingsIcon().assertIsFocused()
        key(Key.DirectionDown)
        row("appearance.theme").assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun settingsEntryDoesNotResetHomeOrReadBackdrop() {
        val home = navigation.backStack.single()
        render()
        assertSame(home, navigation.backStack.first())
        assertEquals(0L, navigation.homeResetGeneration.value)
        assertEquals(0L, navigation.homeSessionGeneration.value)
        verify(exactly = 0) { appVM.backdropService.backdropFlow }
        verify(exactly = 0) { homeVM.init(any()) }
        unchanged()
    }

    @Test fun backFromSettingsRestoresActualHomeCardAndBoundsWithoutRecreatingHomeVm() {
        render(startOnHome = true)
        card(0, 0).assertIsFocused()
        repeat(15) { key(Key.DirectionRight) }
        card(0, 15).assertIsFocused()
        val bounds = card(0, 15).fetchSemanticsNode().boundsInRoot
        val originalHome = navigation.backStack.first()
        clearMocks(homeVM, answers = false, recordedCalls = true)
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
        back()
        card(0, 15).assertIsFocused()
        assertEquals(bounds, card(0, 15).fetchSemanticsNode().boundsInRoot)
        assertSame(originalHome, navigation.backStack.single())
        assertEquals(1, factoryCounts[HomeViewModel::class.java])
        assertEquals(0L, navigation.homeResetGeneration.value)
        verify(exactly = 0) { homeVM.init(true) }
        unchanged()
    }

    @Test fun explicitHomeFromSettingsResetsActualHomeWithoutRecreatingItsEntry() {
        render(startOnHome = true)
        repeat(15) { key(Key.DirectionRight) }
        val originalHome = navigation.backStack.first()
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        homeTab().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        card(0, 0).assertIsFocused()
        assertSame(originalHome, navigation.backStack.single())
        assertEquals(1L, navigation.homeResetGeneration.value)
        assertEquals(1, factoryCounts[HomeViewModel::class.java])
        unchanged()
    }

    @Test fun explicitHomeReselectionStillResetsWhenHomeIsAlreadyActive() {
        render(startOnHome = true)
        repeat(15) { key(Key.DirectionRight) }
        homeTab().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        card(0, 0).assertIsFocused()
        assertEquals(1L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun integratedLargeFontCategoriesScrollWithoutLabelOverflowOrDoublePadding() {
        render(fontScale = 1.6f)
        enter(LunocoSettingsCategory.APPEARANCE)
        for (cat in LunocoSettingsCategory.entries) {
            category(cat)
            val text = compose.onNode(hasText(compose.activity.getString(cat.title)) and
                hasAnyAncestor(hasTestTag("settings-categories")), useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertTrue(layouts.isNotEmpty())
            assertFalse(layouts.single().hasVisualOverflow)
        }
        category(LunocoSettingsCategory.SUPPORT).requestFocus().assertIsFocused()
        val focused = category(LunocoSettingsCategory.SUPPORT).fetchSemanticsNode().boundsInRoot
        assertTrue(focused.left >= 56f && focused.bottom <= 1048f)
        compose.onNodeWithContentDescription("Lunoco").assertExists()
        capture("settings06b-integrated-large-font")
        unchanged()
    }

    @Test fun specializedProfileEditorRemainsFullScreenAndBackRestoresAudioLink() {
        render()
        enter(LunocoSettingsCategory.AUDIO)
        row("audio.profile").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(Destination.UserAppPreferences, navigation.backStack.last())
        compose.onNodeWithContentDescription("Lunoco").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_specific_settings)).assertExists()
        back()
        row("audio.profile").assertIsFocused()
        category(LunocoSettingsCategory.AUDIO).assertIsSelected()
        unchanged()
    }

    @Test fun librarySettingsBackRestoresTheExistingGridEntryWithoutGoingHome() {
        val library = Destination.MediaItem(UUID(9, 0), BaseItemKind.COLLECTION_FOLDER,
            collectionType = CollectionType.MOVIES, browseGrid = true)
        render(origin = library)
        // The library's pre-existing eager-success initialization races its lazy grid.
        // Establish a real focused poster after layout before testing Settings restoration.
        focusLibraryArtwork(0)
        println("DISCOVERY10_GRID_START=" + libraryPosition + ";" + compose.onNode(isFocused()).printToString())
        repeat(3) { key(Key.DirectionRight); println("DISCOVERY10_GRID_RIGHT=" + libraryPosition + ";" + compose.onNode(isFocused()).printToString()) }
        repeat(2) { key(Key.DirectionDown); println("DISCOVERY10_GRID_DOWN=" + libraryPosition + ";" + compose.onNode(isFocused()).printToString()) }
        compose.waitForIdle()
        capture("discovery10a-library-settings-regression")
        println("DISCOVERY10_GRID_TREE=" + compose.onRoot().printToString())
        compose.onNodeWithText("16 / 24").assertExists()
        val focusedBefore = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        // The focused artwork is visible even when its separate caption is below the viewport.
        // Compare the real focus bounds and selected item, not a clipped caption's zero bounds.
        assertTrue(focusedBefore.width > 100f && focusedBefore.height > 100f)
        assertTrue(focusedBefore.top >= 112f && focusedBefore.bottom <= 1080f)
        capture("settings06b-integrated-library-focused")
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
        back()
        assertSame(library, navigation.backStack.last())
        assertEquals(2, navigation.backStack.size)
        assertEquals(15, libraryPosition)
        assertEquals(focusedBefore, compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("16 / 24").assertExists()
        assertEquals(1, factoryCounts[CollectionFolderViewModel::class.java])
        assertEquals(0L, navigation.homeResetGeneration.value)
        verify(exactly = 0) { homeVM.init(any()) }
        unchanged()
    }

    @Test fun onlySettingsOverridesTheLegacyFullScreenMarker() {
        for (screen in PreferenceScreenOption.entries) {
            val route = Destination.Settings(screen)
            assertTrue(route.fullScreen)
            assertTrue(route.usesLunocoBrowseShell)
        }
        val fullScreen = listOf(Destination.Playback(UUID(1, 1), 0),
            Destination.PlaybackList(UUID(1, 2)), Destination.HomeSettings, Destination.SubtitleSettings(false),
            Destination.SubtitleSettings(true), Destination.UserAppPreferences, Destination.NowPlaying,
            Destination.UpdateApp, Destination.License, Destination.Debug,
            Destination.Slideshow(null, 0, CollectionFolderFilter(), SortAndDirection.DEFAULT, true, false))
        fullScreen.forEach { assertTrue(it.fullScreen); assertFalse(it.usesLunocoBrowseShell) }
        listOf(Destination.Home(), Destination.Search(), Destination.Favorites,
            Destination.MediaItem(UUID(1, 3), BaseItemKind.COLLECTION_FOLDER)).forEach {
            assertFalse(it.fullScreen); assertTrue(it.usesLunocoBrowseShell)
        }
    }

    // Phase08 primary experience uses the actual BASIC route; legacy tests above exercise ADVANCED.
    @Test fun minimal08RealPrimaryShellHasThreeSectionsAndOnlyEssentialPlaybackRows() {
        render(screen = PreferenceScreenOption.BASIC)
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
        listOf("AUDIO","HOME","APPEARANCE","SCREENSAVER","MEDIA").forEach {
            compose.onNodeWithTag("settings-category-$it").assertDoesNotExist()
        }
        compose.onNodeWithText("Connections").assertExists()
        compose.onNodeWithText("About").assertExists()
        key(Key.DirectionRight)
        row("play.autoplay").assertIsFocused()
        row("audio.profile").assertExists()
        row("audio.style").assertExists()
        compose.onNodeWithTag("settings-row-play.backend").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-play.skip_forward").assertDoesNotExist()
        capture("settings08-primary-playback")
        unchanged()
    }

    @Test fun minimal08ConnectionsSelectionAndFocusDoNotChangeAccountsOrPreferences() {
        render(screen = PreferenceScreenOption.BASIC)
        enter(LunocoSettingsCategory.PROFILES)
        row("connections.profiles").assertIsFocused()
        row("profile.locale").assertExists()
        row("profile.protection").assertExists()
        key(Key.DirectionLeft)
        category(LunocoSettingsCategory.PROFILES).assertIsFocused().assertIsSelected()
        assertFalse(factoryCounts.containsKey(com.github.damontecres.wholphin.ui.preferences.LunocoConnectionsViewModel::class.java))
        capture("settings08-primary-connections")
        unchanged()
    }

    @Test fun minimal08AdvancedIsReachableAndBackRestoresExactAboutEntry() {
        render(screen = PreferenceScreenOption.BASIC)
        enter(LunocoSettingsCategory.SUPPORT)
        row("about.advanced").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(PreferenceScreenOption.ADVANCED, (navigation.backStack.last() as Destination.Settings).screen)
        for (cat in LunocoSettingsCategory.entries) {
            category(cat).assertExists()
        }
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.theme").assertIsFocused()
        capture("settings08-advanced")
        back()
        row("about.advanced").assertIsFocused()
        category(LunocoSettingsCategory.SUPPORT).assertIsSelected()
        capture("settings08-primary-about")
        unchanged()
    }

    @Test fun minimal08PrimaryLanguageEditorBackRestoresOriginalRow() {
        render(screen = PreferenceScreenOption.BASIC)
        enter(LunocoSettingsCategory.PLAYBACK)
        row("audio.profile").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(Destination.UserAppPreferences, navigation.backStack.last())
        back()
        row("audio.profile").assertIsFocused()
        compose.onNodeWithTag("settings-category-APPEARANCE").assertDoesNotExist()
        assertEquals(0L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun minimal08AboutVersionDialogClosesWithoutWritingAndReturnsToItsRow() {
        render(screen = PreferenceScreenOption.BASIC)
        enter(LunocoSettingsCategory.SUPPORT)
        row("support.version").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(isDialog()).assertExists()
        for (text in listOf(compose.activity.getString(R.string.brand_name), BuildConfig.VERSION_NAME)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(text) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            val color = layouts.single().layoutInput.style.color
            assertTrue("About text must remain readable on the dark dialog", color.red > .8f && color.green > .8f && color.blue > .8f)
        }
        capture("settings08-about-version")
        back()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(PreferenceScreenOption.BASIC, (navigation.backStack.last() as Destination.Settings).screen)
        row("support.version").assertIsFocused()
        unchanged()
    }

    @Test fun minimal08ProtectionChoiceBackRestoresRowWithoutSecurityChanges() {
        render(screen = PreferenceScreenOption.BASIC)
        enter(LunocoSettingsCategory.PROFILES)
        row("profile.protection").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(isFocused() and isSelected()).assertExists()
        capture("settings08-protection-dialog")
        back()
        row("profile.protection").assertIsFocused()
        verify(exactly = 0) { preferencesVM.removeLoginAndPin(any()) }
        verify(exactly = 0) { preferencesVM.setRequireLogin(any()) }
        verify(exactly = 0) { preferencesVM.setPin(any(), any()) }
        unchanged()
    }

    @Test fun minimal08LargeFontPrimaryLabelsFitAndKeepRealShellInsets() {
        render(screen = PreferenceScreenOption.BASIC, fontScale = 1.6f)
        for (cat in LunocoSettingsCatalog.primaryCategories) {
            enter(cat)
            key(Key.DirectionLeft)
            val text = compose.onNode(hasText(compose.activity.getString(
                LunocoSettingsCatalog.categoryTitle(cat, PreferenceScreenOption.BASIC))) and
                hasAnyAncestor(hasTestTag("settings-categories")), useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertFalse(layouts.single().hasVisualOverflow)
            val bounds = category(cat).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 56f && bounds.bottom <= 1048f)
        }
        capture("settings08-primary-large-font")
        unchanged()
    }

    @Test fun minimal08PrimaryBackRestoresHomeCardThenExplicitHomeStillResets() {
        render(startOnHome = true)
        key(Key.DirectionDown)
        repeat(15) { key(Key.DirectionRight) }
        card(1,15).assertIsFocused()
        val bounds = card(1,15).fetchSemanticsNode().boundsInRoot
        settingsIcon().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        enter(LunocoSettingsCategory.PROFILES)
        enter(LunocoSettingsCategory.SUPPORT)
        back()
        card(1,15).assertIsFocused()
        assertEquals(bounds, card(1,15).fetchSemanticsNode().boundsInRoot)
        assertEquals(0L, navigation.homeResetGeneration.value)
        homeTab().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        card(0,0).assertIsFocused()
        assertEquals(1L, navigation.homeResetGeneration.value)
        unchanged()
    }

    @Test fun minimal08ProfilesUsesExistingEditorAndNestedServersBackPreservesSettings() {
        val warnings = mutableListOf<String>()
        val standardOut = System.out
        val tracingOut = object : java.io.PrintStream(standardOut) {
            private fun record(message: Any?) {
                if (message?.toString()?.contains("FocusRequester is not initialized") == true) {
                    warnings.add(Throwable("Settings08 connection focus caller").stackTraceToString())
                }
            }
            override fun println(message: String?) { record(message); super.println(message) }
            override fun println(message: Any?) { record(message); super.println(message) }
        }
        System.setOut(tracingOut)
        try {
            render(screen = PreferenceScreenOption.BASIC)
            enter(LunocoSettingsCategory.PROFILES)
            println("SETTINGS08_PROFILE_STAGE_OPEN")
            row("connections.profiles").performKeyInput { pressKey(Key.DirectionCenter) }
            assertEquals(Destination.ManageConnections(), navigation.backStack.last())
            compose.onNodeWithText(compose.activity.getString(R.string.select_user)).assertExists()
            compose.onNode(isFocused()).assertIsDisplayed()
            println("SETTINGS08_PROFILE_STAGE_SERVERS")
            compose.onNodeWithText(compose.activity.getString(R.string.switch_servers))
                .requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            assertEquals(Destination.ManageConnections(true), navigation.backStack.last())
            println("SETTINGS08_PROFILE_STAGE_BACK_TO_PROFILES")
            back()
            assertEquals(Destination.ManageConnections(), navigation.backStack.last())
            compose.onNodeWithText(compose.activity.getString(R.string.switch_servers)).assertIsFocused()
            println("SETTINGS08_PROFILE_STAGE_BACK_TO_SETTINGS")
            back()
            row("connections.profiles").assertIsFocused()
            verify(exactly = 0) { switchUserVM.trySwitchUser(any()) }
            verify(exactly = 0) { switchUserVM.removeUser(any()) }
            verify(exactly = 0) { switchServerVM.switchServer(any()) }
            unchanged()
        } finally {
            System.setOut(standardOut)
            warnings.forEach { println("SETTINGS08_CONNECTION_FOCUS_STACK:\n" + it) }
        }
        assertEquals("Settings connection navigation must not request an unattached focus target", 0, warnings.size)
    }

    @Test fun minimal08MissingServerOffersSafeBackAndPreservesConnectionEntryFocus() {
        every { connectionsVM.server } returns MutableStateFlow<JellyfinServer?>(null)
        render(screen = PreferenceScreenOption.BASIC)
        enter(LunocoSettingsCategory.PROFILES)
        row("connections.profiles").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.lunoco_settings_no_server)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.lunoco_settings_manage_servers)).assertIsFocused()
        back()
        row("connections.profiles").assertIsFocused()
        unchanged()
    }

    @Test fun discovery10bCuratedExploreUsesRealMoviesShellAndReadableFallbacks() {
        val state = curatedDiscoveryState()
        every { exploreVM.state } returns MutableStateFlow(state)
        render(origin = Destination.MediaItem(UUID(9,0), BaseItemKind.COLLECTION_FOLDER,
            CollectionType.MOVIES, browseGrid = true))
        compose.onNodeWithTag("movies-all").assertIsSelected()
        compose.onNodeWithTag("movies-explore").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("movie-explore").assertIsDisplayed()
        key(Key.DirectionDown)
        compose.onNodeWithTag("explore-${state.studios.first().key}").assertIsFocused()
        compose.onNodeWithText("Walt Disney Pictures").assertExists()
        compose.onNodeWithText("Pixar").assertExists()
        homeTab().assertExists()
        capture("discovery10b-curated-explore-real-shell")
        unchanged()
    }

    private fun curatedDiscoveryState() = discoveryState().let { state ->
        state.copy(studios = state.studios.take(com.github.damontecres.wholphin.ui.discovery.curatedStudios.size)
            .mapIndexed { index, facet ->
                val studio = com.github.damontecres.wholphin.ui.discovery.curatedStudios[index]
                facet.copy(name = studio.label, curatedKey = studio.key)
            })
    }

    @Test fun discovery10bHomeLoadsBeforeFeedTailAndStudioDetailsBackRestoresExactCard() {
        val state = curatedDiscoveryState()
        val longFeed = rows + (3..5).map { index ->
            HomeRowLoadingState.Success(StringStringProvider("Row $index"), List(20) { col ->
                BaseItem(BaseItemDto(id = UUID(index + 1L, col + 1L), name = "Row $index movie $col", type = BaseItemKind.MOVIE))
            }, rowType = HomeRowConfig.RecentlyAdded(UUID(8, index.toLong())))
        }
        every { homeVM.state } returns MutableStateFlow(HomeState(LoadingState.Success, LoadingState.Success,
            longFeed, HomePageResolvedSettings.EMPTY))
        val flow = MutableStateFlow(com.github.damontecres.wholphin.ui.discovery.MovieExploreState())
        every { exploreVM.state } returns flow
        every { exploreVM.load(any()) } answers { flow.value = state }
        render(startOnHome = true)
        verify(exactly = 1) { exploreVM.load(UUID(9, 0)) }
        homeTab().assertExists()
        compose.onAllNodesWithText(compose.activity.getString(R.string.home)).assertCountEquals(0)
        repeat(6) { key(Key.DirectionDown) }
        val facets = state.studios
        val firstTag = "explore-${facets[0].key}"
        compose.onNodeWithTag(firstTag).assertIsFocused()
        repeat(5) { key(Key.DirectionRight) }
        val tag = "explore-${facets[5].key}"
        compose.onNodeWithTag(tag).assertIsFocused()
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        capture("discovery10b-home-studios-real-shell")
        key(Key.DirectionCenter)
        val route = navigation.backStack.last() as Destination.FilteredCollection
        assertEquals(state.scope!!.library, route.itemId)
        assertEquals(facets[5].studioIds, route.filter.filter.studios)
        focusLibraryArtwork(0)
        key(Key.DirectionCenter)
        assertTrue(navigation.backStack.last() is Destination.MediaItem)
        back()
        back()
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
        val after = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
        // Home composition re-enters once on Back. The real controller is idempotent;
        // repository tests separately verify zero additional server requests on cache re-entry.
        verify(exactly = 2) { exploreVM.load(UUID(9,0)) }
        assertEquals(0L, navigation.homeResetGeneration.value)
        key(Key.DirectionUp)
        card(5,0).assertIsFocused()
        homeTab().requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        card(0,0).assertIsFocused()
        repeat(6) { key(Key.DirectionDown) }
        compose.onNodeWithTag(firstTag).assertIsFocused()
        unchanged()
    }

    @Test fun discovery10bHomeGenreBackPreservesStudioRowAndHomeIconRemainsDpadAccessible() {
        val state = curatedDiscoveryState()
        every { exploreVM.state } returns MutableStateFlow(state)
        render(startOnHome = true)
        repeat(3) { key(Key.DirectionDown) }
        repeat(4) { key(Key.DirectionRight) }
        val studio = "explore-${state.studios[4].key}"
        compose.onNodeWithTag(studio).assertIsFocused()
        key(Key.DirectionDown)
        val initial = compose.onNode(isFocused()).fetchSemanticsNode()
        assertTrue(initial.config.toString().contains("GENRE"))
        repeat(3) { key(Key.DirectionRight) }
        val tag = compose.onNode(isFocused()).fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.TestTag]
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        capture("discovery10b-home-genres-real-shell")
        key(Key.DirectionCenter)
        val route = navigation.backStack.last() as Destination.FilteredCollection
        assertNull(route.filter.filter.studios)
        assertNotNull(route.filter.filter.genres)
        back()
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        key(Key.DirectionUp)
        compose.onNodeWithTag(studio).assertIsFocused()
        repeat(4) { key(Key.DirectionUp) }
        homeTab().assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
        key(Key.DirectionDown)
        card(0,0).assertIsFocused()
        unchanged()
    }

    private class MemoryPreferences(initial: AppPreferences) : DataStore<AppPreferences> {
        val state = MutableStateFlow(initial)
        var writes = 0
        private val mutex = Mutex()
        override val data = state
        override suspend fun updateData(transform: suspend (AppPreferences) -> AppPreferences): AppPreferences {
            mutex.lock()
            try { return transform(state.value).also { state.value = it; writes++ } }
            finally { mutex.unlock() }
        }
    }
    @Test fun manualUpdateCheckUsesRealAboutRowWithoutLeavingSettingsOrWritingPreferences() {
        val updater = mockk<com.github.damontecres.wholphin.services.UpdateChecker>(relaxed = true)
        every { updater.settingsAvailable() } returns true
        every { updater.state } returns MutableStateFlow(com.github.damontecres.wholphin.services.LunocoUpdateState())
        render(screen = PreferenceScreenOption.BASIC, updater = updater)
        enter(LunocoSettingsCategory.SUPPORT)
        row("support.update").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        coVerify(exactly = 1) { updater.checkForUpdates(true) }
        assertEquals(Destination.Settings(PreferenceScreenOption.BASIC), navigation.backStack.last())
        row("support.update").assertIsFocused()
        capture("updates-artwork-about")
        unchanged()
    }

    @Test fun homeReadinessAndDpadInputDoNotIssueUpdateRequestsOrInterruptBrowsing() {
        val updater = mockk<com.github.damontecres.wholphin.services.UpdateChecker>(relaxed = true)
        var ready = false
        every { updater.homeReady } answers { ready }
        every { updater.homeReady = any() } answers { ready = firstArg() }
        every { updater.state } returns MutableStateFlow(com.github.damontecres.wholphin.services.LunocoUpdateState())
        val home = MutableStateFlow(HomeState.EMPTY)
        every { homeVM.state } returns home
        render(startOnHome = true, updater = updater)
        assertFalse(ready)
        compose.runOnIdle {
            home.value = HomeState(LoadingState.Success, LoadingState.Success, rows, HomePageResolvedSettings.EMPTY)
        }
        compose.waitForIdle()
        assertTrue(ready)
        card(0,0).requestFocus()
        key(Key.DirectionRight)
        card(0,1).assertIsFocused()
        verify(atLeast = 1) { updater.userInteraction() }
        coVerify(exactly = 0) { updater.checkForUpdates(any()) }
        compose.onNodeWithText("Update Available").assertDoesNotExist()
        unchanged()
    }

    @Test
    fun neutral35NeutralClearBright1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test
    fun neutral35NeutralClearDark1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1920, 1080, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun neutral35NeutralClearBright720() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun neutral35NeutralClearDark720() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1280, 720, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun neutral35NeutralClearLargeFont720() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720, 1.4f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test
    fun neutral35NeutralClearDetailed1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080, 1f, com.github.damontecres.wholphin.ui.theme.Glass31Artwork.HighDetail, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test
    fun neutral35NeutralClearSaturated1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080, 1f, com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Saturated, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)

    @Test fun neutral35NeutralClearMissing() = native33Missing(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.NeutralClear)
    @Test
    fun neutral35FrostedSmokeBright1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test
    fun neutral35FrostedSmokeDark1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1920, 1080, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun neutral35FrostedSmokeBright720() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun neutral35FrostedSmokeDark720() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, false, 1280, 720, 1f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun neutral35FrostedSmokeLargeFont720() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1280, 720, 1.4f, null, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test
    fun neutral35FrostedSmokeDetailed1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080, 1f, com.github.damontecres.wholphin.ui.theme.Glass31Artwork.HighDetail, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test
    fun neutral35FrostedSmokeSaturated1080() = native33Visual(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, true, 1920, 1080, 1f, com.github.damontecres.wholphin.ui.theme.Glass31Artwork.Saturated, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

    @Test fun neutral35FrostedSmokeMissing() = native33Missing(com.github.damontecres.wholphin.ui.theme.LunocoGlassVariant.Balanced, com.github.damontecres.wholphin.ui.theme.LunocoGlassTreatment.FrostedSmoke)

}