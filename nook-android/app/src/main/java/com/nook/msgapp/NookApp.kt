package com.nook.msgapp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Presence
import com.nook.msgapp.data.Session
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.push.Notifications

class NookApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Offline cache: the chat list and open chats are served from disk on reopen (fewer reads).
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(50L * 1024 * 1024).build())
            .build()
        Prefs.init(this)
        LockStore.init(this)
        CallController.init(this)
        Notifications.ensureChannels(this)
        Session.start()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Presence.resume()
                LockStore.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                Presence.pause()
                LockStore.onBackground()
            }
        })
    }

    /** Coil with GIF/WebP animation support and a disk cache for media. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
