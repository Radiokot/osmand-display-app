package ua.com.radiokot.osmanddisplay.features.track.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_import_track.import_track_button
import kotlinx.android.synthetic.main.activity_import_track.thumbnail_preparing_text_view
import kotlinx.android.synthetic.main.activity_import_track.track_name_edit_text
import kotlinx.android.synthetic.main.activity_import_track.track_thumbnail_image_view
import kotlinx.android.synthetic.main.activity_import_track.view_online_button
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.extension.kLogger
import ua.com.radiokot.osmanddisplay.base.util.localfile.LocalFile
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.di.InjectedSnapshotter
import ua.com.radiokot.osmanddisplay.features.map.logic.FriendlySnapshotter
import ua.com.radiokot.osmanddisplay.features.track.data.model.GeoJsonTrackData
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.logic.ImportTrackUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.OpenTrackOnlinePreviewUseCase
import ua.com.radiokot.osmanddisplay.features.track.logic.ReadGeoJsonFileUseCase

class ImportTrackActivity : BaseActivity() {
    private val logger = kLogger("ImportTrackActivity")

    private val importTrackUseCaseFactory: ImportTrackUseCase.Factory by inject()

    @Suppress("DEPRECATION")
    private val file: LocalFile by lazy {
        requireNotNull(intent.getParcelableExtra(FILE_EXTRA)) {
            "No $FILE_EXTRA specified"
        }
    }
    private val onlinePreviewUrl: String? by lazy {
        intent.getStringExtra(ONLINE_PREVIEW_URL_EXTRA)
    }

    private lateinit var geoJsonTrackData: GeoJsonTrackData

    private var trackThumbnail: Bitmap? = null
        set(value) {
            field = value
            validate()
        }

    private var trackName: String? = null
        set(value) {
            field = value
            validate()
        }

    private var isImporting: Boolean = false
        set(value) {
            field = value
            validate()

            runOnUiThread {
                if (value) {
                    showImportProgress()
                } else {
                    hideImportProgress()
                }
            }
        }

    private var canImport: Boolean = false
        set(value) {
            field = value
            runOnUiThread {
                import_track_button.isEnabled = value
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tryToReadFile().also { readData ->
            if (readData == null) {
                toastManager.short(R.string.error_not_a_geojson_track)
                finish()
                return@onCreate
            } else {
                geoJsonTrackData = readData
            }
        }

        setContentView(R.layout.activity_import_track)

        initThumbnail()
        initFields()
        initButtons()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        canImport = false
    }

    private fun tryToReadFile(): GeoJsonTrackData? = try {
        require(file.extension in GEOJSON_EXTENSIONS) {
            "The file has an unsupported extension: ${file.extension}"
        }

        // Damn, I can't remember the rationale for this limit 🤦🏻.
        require(file.size <= MAX_FILE_SIZE_BYTES) {
            "The file is too big: ${file.size} bytes"
        }

        contentResolver.openInputStream(file.uri)!!.use {
            ReadGeoJsonFileUseCase().invoke(it).blockingGet()
        }
    } catch (e: Exception) {
        logger.error(e) { "readFileOrFinish(): check_failed" }
        null
    }

    private fun initThumbnail() {
        val snapshotter: FriendlySnapshotter = get(named(InjectedSnapshotter.TRACK_THUMBNAIL)) {
            parametersOf(
                geoJsonTrackData.track,
                geoJsonTrackData.poi.toJson(),
            )
        }

        snapshotter
            .getSnapshotBitmap()
            .retry(5)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { thumbnail_preparing_text_view.visibility = View.VISIBLE }
            .subscribeBy(
                onSuccess = { bitmap ->
                    trackThumbnail = bitmap
                    track_thumbnail_image_view.setImageBitmap(bitmap)
                    thumbnail_preparing_text_view.visibility = View.GONE
                },
                onError = {
                    logger.error(it) { "initThumbnail(): error_occurred" }
                }
            )
            .addTo(compositeDisposable)
    }

    private fun initFields() {
        track_name_edit_text.addTextChangedListener { text ->
            trackName = text?.toString()
        }
        track_name_edit_text.setText(geoJsonTrackData.name ?: file.name)
    }

    private fun initButtons() {
        import_track_button.setOnClickListener {
            importTrack()
        }

        with(view_online_button) {
            isVisible = onlinePreviewUrl != null
            setOnClickListener {
                onlinePreviewUrl?.also(::openTrackOnlinePreview)
            }
        }
    }

    private fun openTrackOnlinePreview(onlinePreviewUrl: String) =
        OpenTrackOnlinePreviewUseCase(
            onlinePreviewUrl = onlinePreviewUrl,
            activity = this,
        )()

    private fun validate() {
        canImport = !trackName.isNullOrBlank()
                && trackThumbnail != null
                && !isImporting
    }

    private fun importTrack() {
        if (!canImport) {
            return
        }

        importTrackUseCaseFactory
            .get(
                name = trackName!!,
                geometry = geoJsonTrackData.track,
                poi = geoJsonTrackData.poi,
                thumbnail = trackThumbnail!!,
                onlinePreviewUrl = onlinePreviewUrl,
            )
            .invoke()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                isImporting = true
            }
            .doOnEvent { _, _ ->
                isImporting = false
            }
            .subscribeBy(
                onSuccess = this::setResultAndFinish,
                onError = {
                    logger.error(it) { "importTrack(): error_occurred" }

                    toastManager.short(R.string.error_failed_to_import_track)
                }
            )
            .addTo(compositeDisposable)
    }

    private fun showImportProgress() {
        val progressDrawable =
            IndeterminateDrawable.createCircularDrawable(
                this,
                CircularProgressIndicatorSpec(this, null)
            )
        import_track_button.icon = progressDrawable
    }

    private fun hideImportProgress() {
        import_track_button.icon = null
    }

    private fun setResultAndFinish(track: ImportedTrackRecord) {
        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_EXTRA, track))
        finish()
    }

    /**
     * Launch the activity to import the track.
     *
     * - Input – [getBundle]
     * - Result – [ImportedTrackRecord] if the import is successful
     */
    class ImportContract : ActivityResultContract<Bundle, ImportedTrackRecord?>() {
        override fun createIntent(context: Context, input: Bundle): Intent =
            Intent(context, ImportTrackActivity::class.java)
                .putExtras(input)

        @Suppress("DEPRECATION")
        override fun parseResult(resultCode: Int, intent: Intent?): ImportedTrackRecord? =
            intent
                ?.takeIf { resultCode == Activity.RESULT_OK }
                ?.getParcelableExtra(RESULT_EXTRA)
    }

    companion object {
        private const val FILE_EXTRA = "file"
        private const val ONLINE_PREVIEW_URL_EXTRA = "online_preview_url"
        private const val RESULT_EXTRA = "result"

        private const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024
        private val GEOJSON_EXTENSIONS = setOf("geojson", "json")

        // Import is only allowed from a file, as passing the GeoJSON content string
        // in an Intent can easily overcome the activity transaction size limit.
        fun getBundle(
            file: LocalFile,
            onlinePreviewUrl: String?,
        ) = Bundle().apply {
            putParcelable(FILE_EXTRA, file)
            putString(ONLINE_PREVIEW_URL_EXTRA, onlinePreviewUrl)
        }
    }
}
