package ua.com.radiokot.osmanddisplay.features.track.view

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_import_track.*
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.util.localfile.LocalFile
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.di.InjectedSnapshotter
import ua.com.radiokot.osmanddisplay.features.map.logic.FriendlySnapshotter
import ua.com.radiokot.osmanddisplay.features.track.data.model.GeoJsonTrackData
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository
import java.io.InputStreamReader

class ImportTrackActivity : BaseActivity() {
    private val logger = KotlinLogging.logger("ImportTrackActivity@${hashCode()}")

    private val importedTracksRepository: ImportedTracksRepository by inject()

    private val file: LocalFile? by lazy {
        intent.getParcelableExtra(FILE_EXTRA)
    }
    private val fileGeoJsonContent: String? by lazy {
        intent.getStringExtra(FILE_CONTENT_EXTRA)
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

    private var canImport: Boolean = false
        set(value) {
            field = value
            import_track_button.isEnabled = value
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

    private fun tryToReadFile(): GeoJsonTrackData? {
        return try {
            val file = this.file
            val fileGeoJsonContent = this.fileGeoJsonContent

            val fileContent: String
            if (file != null) {
                require(file.extension in GEOJSON_EXTENSIONS) {
                    "The file has an unsupported extension: ${file.extension}"
                }

                require(file.size <= MAX_FILE_SIZE_BYTES) {
                    "The file is too big: ${file.size} bytes"
                }

                fileContent = contentResolver.openInputStream(file.uri).use {
                    InputStreamReader(it).readText()
                }
            } else if (fileGeoJsonContent != null) {
                fileContent = fileGeoJsonContent
            } else {
                throw IllegalStateException("There must be a file or a GeoJSON content")
            }

            GeoJsonTrackData.fromFileContent(fileContent)
        } catch (e: Exception) {
            logger.error(e) { "readFileOrFinish(): check_failed" }
            null
        }
    }

    private fun initThumbnail() {
        val snapshotter: FriendlySnapshotter = get(named(InjectedSnapshotter.TRACK_THUMBNAIL)) {
            parametersOf(geoJsonTrackData.geoJsonFeature.toJson(), geoJsonTrackData.geometry)
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
        track_name_edit_text.setText(geoJsonTrackData.name ?: file?.name)
    }

    private fun initButtons() {
        import_track_button.setOnClickListener {
            importTrack()
        }
    }

    private fun validate() {
        canImport = !trackName.isNullOrBlank()
                && trackThumbnail != null
    }

    private fun importTrack() {
        if (!canImport) {
            return
        }

        importedTracksRepository
            .importTrack(
                name = trackName!!,
                geometry = geoJsonTrackData.geometry,
                thumbnail = trackThumbnail!!
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = this::setResultAndFinish,
                onError = {
                    logger.error(it) { "importTrack(): error_occurred" }

                    toastManager.short(R.string.error_failed_to_import_track)
                }
            )
            .addTo(compositeDisposable)
    }

    private fun setResultAndFinish(track: ImportedTrackRecord) {
        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_EXTRA, track))
        finish()

    }

    companion object {
        private const val FILE_EXTRA = "file"
        private const val FILE_CONTENT_EXTRA = "geojson_data"
        private const val RESULT_EXTRA = "result"

        private const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024
        private val GEOJSON_EXTENSIONS = setOf("geojson", "json")

        fun getBundle(file: LocalFile) = Bundle().apply {
            putParcelable(FILE_EXTRA, file)
        }

        fun getBundle(fileGeoJsonContent: String) = Bundle().apply {
            putString(FILE_CONTENT_EXTRA, fileGeoJsonContent)
        }

        fun getResult(intent: Intent): ImportedTrackRecord {
            return intent.getParcelableExtra(RESULT_EXTRA)!!
        }
    }
}