package ua.com.radiokot.osmanddisplay.features.track.brouter.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ua.com.radiokot.osmanddisplay.features.track.view.ImportTrackActivity
import java.io.File

class BRouterUrlActivity : BaseActivity() {
    private val logger = kLogger("BRouterUrlActivity")

    private val trackImportLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
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
                        Intent(this, ImportTrackActivity::class.java)
                            .putExtras(ImportTrackActivity.getBundle(geoJsonFile))
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

    private fun onTrackImportResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            toastManager.long(R.string.brouter_track_successfully_imported)
        }
        finish()
    }
}
