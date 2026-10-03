package com.nook.core

/** First-run state machine. */
enum class AppStage { Loading, Intro, SignIn, ProfileLoading, Username, Permissions, SetLock, App }

enum class AuthStatus { Loading, SignedOut, SignedIn }

/** Profile lookup state: still loading, known missing, or present. */
sealed interface ProfileState {
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Ready(val profile: Profile) : ProfileState
}

object Stage {
    fun compute(
        onboardingSeen: Boolean,
        permissionsPrimed: Boolean,
        auth: AuthStatus,
        profile: ProfileState,
        lockConfigured: Boolean?,
    ): AppStage = when {
        auth == AuthStatus.Loading || lockConfigured == null -> AppStage.Loading
        auth == AuthStatus.SignedOut -> if (onboardingSeen) AppStage.SignIn else AppStage.Intro
        profile is ProfileState.Loading -> AppStage.ProfileLoading
        profile is ProfileState.Missing -> AppStage.Username
        !permissionsPrimed -> AppStage.Permissions
        !lockConfigured -> AppStage.SetLock
        else -> AppStage.App
    }
}
