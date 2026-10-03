package com.nook.msgapp.data

import com.nook.msgapp.BuildConfig

/** Per-deployment service settings, filled in gradle.properties (see README > Keys). */
object AppConfig {
    val workerUrl: String = BuildConfig.WORKER_URL.trimEnd('/')
    val cloudinaryCloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME
    val cloudinaryUploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET
    val giphyApiKey: String = BuildConfig.GIPHY_API_KEY
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val workerConfigured get() = workerUrl.isNotEmpty()
    val cloudinaryConfigured get() = cloudinaryCloudName.isNotEmpty() && cloudinaryUploadPreset.isNotEmpty()
    val giphyConfigured get() = giphyApiKey.isNotEmpty()
}
