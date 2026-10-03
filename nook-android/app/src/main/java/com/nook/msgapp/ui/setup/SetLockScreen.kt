package com.nook.msgapp.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nook.msgapp.ui.lock.LockSetupFlow
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.Spacing

/** Step 3 of 3: set the app lock. Saving flips LockStore.configured, which moves the stage on by itself. */
@Composable
fun SetLockScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Nook.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.lg),
    ) {
        LockSetupFlow(
            onDone = {},
            title = "Lock your NOOK",
            step = 3,
            modifier = Modifier.weight(1f),
        )
    }
}
