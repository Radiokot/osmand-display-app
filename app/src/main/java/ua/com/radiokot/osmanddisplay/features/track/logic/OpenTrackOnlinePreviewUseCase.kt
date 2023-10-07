package ua.com.radiokot.osmanddisplay.features.track.logic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import ua.com.radiokot.osmanddisplay.base.extension.setManifestComponentEnabled
import ua.com.radiokot.osmanddisplay.features.track.brouter.view.BRouterUrlActivity

class OpenTrackOnlinePreviewUseCase(
    private val onlinePreviewUrl: String,
    private val activity: Activity,
) {
    operator fun invoke() = with(activity) {
        // Prevent the link to be opened in the app by temporarily disabling
        // the corresponding activity.
        setManifestComponentEnabled(BRouterUrlActivity::class.java, false)

        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(onlinePreviewUrl)))

        window.decorView.post {
            setManifestComponentEnabled(BRouterUrlActivity::class.java, true)
        }
    }

    fun interface Factory {
        fun get(
            onlinePreviewUrl: String,
        ): OpenTrackOnlinePreviewUseCase
    }
}
