// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.github.damontecres.wholphin.ui.main.LocalHomeNavigationInset
import com.github.damontecres.wholphin.ui.theme.LocalLunocoGlassBackdrop
import com.github.damontecres.wholphin.ui.theme.LunocoGlassScene
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import java.util.UUID
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.main.LocalJellyLuHomeCards
import com.github.damontecres.wholphin.ui.main.LocalJellyLuNavigationHasFocus
import com.github.damontecres.wholphin.ui.main.LocalJellyLuRailFocus
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** Fixed browsing chrome, also used by Settings; other full-screen destinations bypass it. */
@Composable
fun JellyLuBrowseShell(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    viewModel: NavDrawerViewModel,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.serviceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val entries = remember(state.items, state.moreItems) {
        (state.items + state.moreItems + NavDrawerItem.Favorites)
            .filter { it !is NavDrawerItem.More }.distinctBy { it.id }
    }
    val glassBackdrop = remember(server.id, user.id) { LunocoGlassScene() }
    val homeFocus = remember(user.id) { FocusRequester() }
    val contentFocus = remember(destination) { FocusRequester() }
    var navigationFocused by remember { mutableStateOf(false) }
    val navigationHasFocus = remember { { navigationFocused } }
    var profileMenu by remember { mutableStateOf(false) }

    val links = remember(entries) { jellyLuBrowseLinks(entries) }
    val overflow = remember(entries, links) {
        val primaryIds = links.map { it.library.id }.toSet()
        entries.filter { it.id !in primaryIds && it !is NavDrawerItem.Favorites }
    }
    val selectedLibraryId = viewModel.navigationManager.backStack.asReversed().firstNotNullOfOrNull { route ->
        when (route) {
            is Destination.MediaItem -> entries.filterIsInstance<ServerNavDrawerItem>()
                .firstOrNull { it.itemId == route.itemId }?.itemId
            is Destination.Recordings -> route.itemId
            else -> null
        }
    }
    JellyLuHomeBackFocus(destination is Destination.Home && !navigationFocused && !profileMenu, homeFocus)

    CompositionLocalProvider(
        LocalLunocoGlassBackdrop provides glassBackdrop.takeIf { destination is Destination.Home },
        LocalJellyLuHomeCards provides (destination is Destination.Home),
        LocalHomeNavigationInset provides if (destination is Destination.Home) 56.dp else 0.dp,
        // Retained local name for source compatibility; it now points to top navigation.
        LocalJellyLuRailFocus provides homeFocus,
        LocalJellyLuNavigationHasFocus provides navigationHasFocus,
    ) {
        Box(modifier.fillMaxSize()) {
            // Keep the same navigation instance/focus order, but draw it above Home's hero.
            Box(Modifier.zIndex(1f)) {
            LunocoTopNavigation(
                destination = destination,
                groups = remember(links) { lunocoBrowseGroups(links) },
                selectedLibraryId = selectedLibraryId,
                homeFocus = homeFocus,
                onProfile = { profileMenu = true },
                onHome = { viewModel.navigationManager.selectHome() },
                onOpenLink = { link ->
                    if (destination == link.destination) contentFocus.tryRequestFocus()
                    else viewModel.navigationManager.navigateToFromDrawer(link.destination)
                },
                onSearch = { viewModel.navigationManager.navigateToFromDrawer(Destination.Search()) },
                onSettings = {
                    if (destination !is Destination.Settings) {
                        viewModel.navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.BASIC))
                    }
                },
                onNavigationFocus = { navigationFocused = it },
                downFocus = contentFocus.takeIf { destination is Destination.Home },
            )
            }
            Box(
                Modifier.fillMaxSize().padding(
                    top = if (destination is Destination.Home) 0.dp else 56.dp,
                    start = if (destination is Destination.Home) 0.dp else 24.dp,
                    end = if (destination is Destination.Home) 0.dp else 24.dp,
                )
                    .focusRestorer().focusRequester(contentFocus).focusGroup(),
            ) {
                DestinationContent(destination, preferences, onClearBackdrop, Modifier.fillMaxSize())
            }
        }
    }
    if (profileMenu) {
        val profileLabel = stringResource(R.string.profile)
        val favoritesLabel = stringResource(R.string.favorites)
        val nowPlayingLabel = stringResource(R.string.now_playing)
        val choices = buildList<Pair<String, () -> Unit>> {
            add(profileLabel to {
                profileMenu = false
                viewModel.navigateToSetup(SetupDestination.UserList(server))
            })
            add(favoritesLabel to {
                profileMenu = false
                viewModel.navigationManager.navigateToFromDrawer(Destination.Favorites)
            })
            addAll(overflow.map { entry ->
                entry.name(context) to {
                    profileMenu = false
                    val target = if (entry is ServerNavDrawerItem) {
                        JellyLuBrowseLink(JellyLuBrowseTab.HOME, entry).destination
                    } else null
                    if (target != null) viewModel.navigationManager.navigateToFromDrawer(target)
                    else viewModel.onClickDrawerItem(entries.indexOf(entry), entry)
                }
            })
            if (state.nowPlayingEnabled) add(nowPlayingLabel to {
                profileMenu = false
                viewModel.navigationManager.navigateTo(Destination.NowPlaying)
            })
        }
        BrowseChoiceDialog(stringResource(R.string.brand_name), choices, { profileMenu = false })
    }
}

/** Existing destinations/callbacks, presented by the shared lightweight floating lane. */
@Composable
internal fun LunocoTopNavigation(
    destination: Destination,
    groups: List<LunocoBrowseGroup>,
    selectedLibraryId: UUID?,
    homeFocus: FocusRequester,
    onProfile: () -> Unit,
    onHome: () -> Unit,
    onOpenLink: (JellyLuBrowseLink) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNavigationFocus: (Boolean) -> Unit,
    downFocus: FocusRequester? = null,
) {
    // Global destinations must not also select a library merely retained below them.
    val activeLibraryId = selectedLibraryId.takeUnless {
        destination is Destination.Home || destination is Destination.Search || destination is Destination.Settings
    }
    CompositionLocalProvider(LocalFloatingNavigationDown provides downFocus) {
    FloatingNavigationRow(onNavigationFocus) {
        val compact = LocalFloatingNavigationCompact.current
        BrowseButton(onProfile, Modifier.width(if (compact) 108.dp else 132.dp)) {
            Image(painterResource(R.drawable.lunoco_wordmark), stringResource(R.string.brand_name),
                Modifier.width(if (compact) 76.dp else 96.dp).height(26.dp))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, stringResource(R.string.profile), Modifier.size(12.dp))
        }
        BrowseButton(onHome, selected = destination is Destination.Home,
            modifier = Modifier.width(if (compact) 40.dp else 48.dp).focusRequester(homeFocus)) {
            Icon(Icons.Default.Home, stringResource(R.string.home), Modifier.size(22.dp))
        }
        groups.forEach { group ->
            key(group.tab) {
                if (group.hasMenu) {
                    BrowseDropdown(
                        label = stringResource(group.tab.label),
                        choices = group.children.map {
                            stringResource(if (it.tab == JellyLuBrowseTab.LIVE_TV) R.string.lunoco_guide else it.tab.label)
                        },
                        selectedIndex = group.children.indexOfFirst { activeLibraryId == it.library.itemId },
                        onSelect = { onOpenLink(group.children[it]) },
                    )
                } else {
                    val link = group.children.single()
                    BrowseButton({ onOpenLink(link) },
                        selected = activeLibraryId == link.library.itemId,
                        modifier = Modifier.widthIn(min = group.tab.widthDp.dp)) {
                        Text(stringResource(group.tab.label), fontSize = if (compact) 14.sp else 15.sp,
                            lineHeight = if (compact) 18.sp else 20.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BrowseButton(onSearch, selected = destination is Destination.Search,
            modifier = Modifier.width(40.dp)) {
            Icon(Icons.Default.Search, stringResource(R.string.search), Modifier.size(20.dp))
        }
        BrowseButton(onSettings, selected = destination is Destination.Settings,
            modifier = Modifier.width(40.dp)) {
            Icon(Icons.Default.Settings, stringResource(R.string.settings), Modifier.size(20.dp))
        }
    }
    }
}

/** Shared with the Home focus test; preserves the existing Back-to-navigation behavior. */
@Composable
internal fun JellyLuHomeBackFocus(enabled: Boolean, homeFocus: FocusRequester) {
    BackHandler(enabled = enabled) { homeFocus.tryRequestFocus() }
}

@Composable
private fun BrowseChoiceDialog(
    title: String,
    choices: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.width(440.dp).heightIn(max = 400.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .focusGroup(),
        ) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            if (choices.isEmpty()) item { Text(stringResource(R.string.no_results)) }
            itemsIndexed(choices, key = { index, _ -> index }) { index, choice ->
                ListItem(selected = false, onClick = choice.second,
                    headlineContent = { Text(choice.first) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
            }
        }
        LaunchedEffect(Unit) { if (choices.isNotEmpty()) firstFocus.tryRequestFocus() }
    }
}
