package com.nook.msgapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nook.core.AppStage
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.calls.CallPhase
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Session
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.nav.DeepLink
import com.nook.msgapp.nav.DeepLinks
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.calls.CallScreen
import com.nook.msgapp.ui.chat.ChatScreen
import com.nook.msgapp.ui.chatinfo.ChatInfoScreen
import com.nook.msgapp.ui.components.NookLogo
import com.nook.msgapp.ui.components.ToastHost
import com.nook.msgapp.ui.groups.NewGroupScreen
import com.nook.msgapp.ui.home.HomeScreen
import com.nook.msgapp.ui.lock.LockScreen
import com.nook.msgapp.ui.onboarding.OnboardingScreen
import com.nook.msgapp.ui.onboarding.SignInScreen
import com.nook.msgapp.ui.settings.AppLockSettingsScreen
import com.nook.msgapp.ui.settings.AppearanceScreen
import com.nook.msgapp.ui.settings.BlockedScreen
import com.nook.msgapp.ui.settings.DisappearingScreen
import com.nook.msgapp.ui.settings.EditProfileScreen
import com.nook.msgapp.ui.settings.HelpScreen
import com.nook.msgapp.ui.settings.NotificationsScreen
import com.nook.msgapp.ui.settings.PrivacyScreen
import com.nook.msgapp.ui.setup.PermissionsScreen
import com.nook.msgapp.ui.setup.ProfileLoadingScreen
import com.nook.msgapp.ui.setup.SetLockScreen
import com.nook.msgapp.ui.setup.UsernameScreen
import com.nook.msgapp.ui.stickers.PackScreen
import com.nook.msgapp.ui.theme.Nook

/** The activity, for APIs that need one (Google sign-in, biometrics). */
val LocalActivity = staticCompositionLocalOf<FragmentActivity> { error("No activity") }

/** Root: picks the first-run stage, then the signed-in app with the lock and call overlays on top. */
@Composable
fun NookRoot(activity: FragmentActivity) {
    val stage by Session.stage.collectAsState()
    CompositionLocalProvider(LocalActivity provides activity) {
        Box(Modifier.fillMaxSize().background(Nook.colors.background)) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
                label = "stage",
            ) { s ->
                when (s) {
                    AppStage.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NookLogo(size = 72.dp) }
                    AppStage.Intro -> OnboardingScreen(onDone = { Prefs.setOnboardingSeen(true) })
                    AppStage.SignIn -> SignInScreen()
                    AppStage.ProfileLoading -> ProfileLoadingScreen()
                    AppStage.Username -> UsernameScreen()
                    AppStage.Permissions -> PermissionsScreen(onDone = { Prefs.setPermissionsPrimed(true) })
                    AppStage.SetLock -> SetLockScreen()
                    AppStage.App -> SignedInApp()
                }
            }
            ToastHost()
        }
    }
}

@Composable
private fun SignedInApp() {
    val nav = rememberNavController()
    val locked by LockStore.locked.collectAsState()
    val call by CallController.state.collectAsState()
    val pending by DeepLinks.pending.collectAsState()

    // Notification taps and incoming calls.
    LaunchedEffect(pending, locked) {
        when (val link = pending) {
            is DeepLink.Chat -> if (!locked) {
                DeepLinks.consume()
                nav.navigate(Routes.chat(link.chatId)) { launchSingleTop = true }
            }
            is DeepLink.IncomingCall -> {
                DeepLinks.consume()
                CallController.showIncoming(link.callId, link.accept)
            }
            null -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        MainNav(nav)
        AnimatedVisibility(
            visible = call.phase != CallPhase.Idle,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.98f),
        ) {
            CallScreen(onOpenChat = { chatId -> nav.navigate(Routes.chat(chatId)) { launchSingleTop = true } })
        }
        // An incoming call shows over the lock screen; everything else waits for unlock.
        AnimatedVisibility(
            visible = locked && call.phase != CallPhase.Incoming,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(260)) + slideOutVertically(tween(300)) { -it / 8 },
        ) {
            LockScreen()
        }
    }
}

@Composable
private fun MainNav(nav: NavHostController) {
    val id = navArgument("chatId") { type = NavType.StringType }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize().background(Nook.colors.background),
        enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220)) },
        exitTransition = { slideOutHorizontally(tween(280)) { -it / 6 } + fadeOut(tween(180)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(180)) },
    ) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.CHAT, arguments = listOf(id)) { e ->
            ChatScreen(e.arguments?.getString("chatId").orEmpty(), nav)
        }
        composable(Routes.CHAT_INFO, arguments = listOf(id)) { e ->
            ChatInfoScreen(e.arguments?.getString("chatId").orEmpty(), nav)
        }
        composable(
            Routes.NEW_GROUP,
            enterTransition = { slideInVertically(tween(320)) { it / 4 } + fadeIn(tween(220)) },
            popExitTransition = { slideOutVertically(tween(260)) { it / 4 } + fadeOut(tween(180)) },
        ) { NewGroupScreen(nav) }
        composable(Routes.PACK, arguments = listOf(navArgument("packId") { type = NavType.StringType })) { e ->
            PackScreen(e.arguments?.getString("packId").orEmpty(), nav)
        }
        composable(Routes.EDIT_PROFILE) { EditProfileScreen(nav) }
        composable(Routes.APP_LOCK) { AppLockSettingsScreen(nav) }
        composable(Routes.BLOCKED) { BlockedScreen(nav) }
        composable(Routes.DISAPPEARING) { DisappearingScreen(nav) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
        composable(Routes.APPEARANCE) { AppearanceScreen(nav) }
        composable(Routes.HELP) { HelpScreen(nav) }
        composable(Routes.PRIVACY) { PrivacyScreen(nav) }
    }
}
