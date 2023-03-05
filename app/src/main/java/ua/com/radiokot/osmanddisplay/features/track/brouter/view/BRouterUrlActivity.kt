package ua.com.radiokot.osmanddisplay.features.track.brouter.view

import android.content.Intent
import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.features.track.brouter.logic.GetTrackFromBRouterWebUseCase
import ua.com.radiokot.osmanddisplay.features.track.view.ImportTrackActivity

class BRouterUrlActivity : BaseActivity() {
    private val logger = kLogger("BRouterUrlActivity")

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
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { geoJsonTrackData ->
                    startActivity(
                        Intent(this, ImportTrackActivity::class.java)
                            .putExtras(ImportTrackActivity.getBundle(geoJsonTrackData))
                    )
                    finish()
                },
                onError = {
                    logger.error(it) { "error_occurred" }

                    toastManager.short(R.string.error_brouter_track_loading_failed)
                    finish()
                }
            )
            .addTo(compositeDisposable)
    }
}