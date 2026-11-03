package com.tsutsen.platformplayer.core.datastore.model

import android.net.Uri

data class StoragePreferences(
    /** SAF tree uri of the general external directory (auto-backups etc.). */
    val storageGeneral: String? = null,
    /** SAF tree uri of the external download-export directory. */
    val storageDownload: String? = null,
    /** Whether the persisted general directory still holds usable grants. */
    val isStorageMainValid: Boolean = true,
    /** Legacy stub getter returned a constant null (never derived from [storageGeneral]). */
    val storageGeneralUri: Uri? = null,
)
