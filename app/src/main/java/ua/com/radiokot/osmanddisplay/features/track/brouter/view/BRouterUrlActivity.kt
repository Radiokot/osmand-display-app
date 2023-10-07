package ua.com.radiokot.osmanddisplay.features.track.brouter.view

import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.base.util.localfile.LocalFile
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.features.track.brouter.logic.GetTrackFromBRouterWebUseCase
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.view.ImportTrackActivity
import java.io.File

class BRouterUrlActivity : BaseActivity() {
    private val logger = kLogger("BRouterUrlActivity")

    private val trackImportLauncher =
        registerForActivityResult(
            ImportTrackActivity.ImportContract(),
            this::onTrackImportResult
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brouter_url)

        val uri = intent.dataString

        if (uri == null) {
            finish()
            return
        }

        get<GetTrackFromBRouterWebUseCase> { parametersOf(uri) }
            .perform()
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .map { geoJsonTrackString ->
                // Write the content to a file, as it may be too big
                // for an activity transaction.
                File(filesDir, "brouter.geojson")
                    .apply { writeText(geoJsonTrackString) }
                    .let(LocalFile.Companion::fromFile)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { geoJsonFile ->
                    trackImportLauncher.launch(
                        ImportTrackActivity.getBundle(
                            file = geoJsonFile,
                            onlinePreviewUrl = uri.toString(),
                        )
                    )
                },
                onError = {
                    logger.error(it) { "error_occurred" }

                    toastManager.short(R.string.error_brouter_track_loading_failed)
                    finish()
                }
            )
            .addTo(compositeDisposable)
    }

    private fun onTrackImportResult(importedTrack: ImportedTrackRecord?) {
        if (importedTrack != null) {
            toastManager.long(R.string.brouter_track_successfully_imported)
        }
        finish()
    }
}
