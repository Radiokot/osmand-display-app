package ua.com.radiokot.osmanddisplay.features.track.view

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_import_track.*
import mu.KotlinLogging
import org.koin.android.ext.android.get
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.util.localfile.LocalFile
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.di.InjectedSnapshotter
import ua.com.radiokot.osmanddisplay.features.map.logic.FriendlySnapshotter
import ua.com.radiokot.osmanddisplay.features.track.model.GeoJsonTrackData
import java.io.InputStreamReader

class ImportTrackActivity : BaseActivity() {
    private val logger = KotlinLogging.logger("ImportTrackActivity@${hashCode()}")

    private val file: LocalFile by lazy {
        checkNotNull(intent.getParcelableExtra(FILE_EXTRA)) {
            "There must be a $file extra"
        }
    }

    private lateinit var geoJsonTrackData: GeoJsonTrackData
    private var trackThumbnail: Bitmap? = null

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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun tryToReadFile(): GeoJsonTrackData? {
        return try {
            require(file.extension in GEOJSON_EXTENSIONS) {
                "The file has an unsupported extension: ${file.extension}"
            }

            require(file.size <= MAX_FILE_SIZE_BYTES) {
                "The file is too big: ${file.size} bytes"
            }

            val fileContent = contentResolver.openInputStream(file.uri).use {
                InputStreamReader(it).readText()
            }

            GeoJsonTrackData.fromFileContent(fileContent)
        } catch (e: Exception) {
            logger.error(e) { "readFileOrFinish(): check_failed" }
            null
        }
    }

    private fun initThumbnail() {
        val snapshotter: FriendlySnapshotter = get(named(InjectedSnapshotter.TRACK_THUMBNAIL)) {
            parametersOf(geoJsonTrackData.geoJsonFeature, geoJsonTrackData.geometry)
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
        track_name_edit_text.setText(geoJsonTrackData.name ?: file.name)
    }

    companion object {
        private const val FILE_EXTRA = "file"

        private const val MAX_FILE_SIZE_BYTES = 1 * 1024 * 1024
        private val GEOJSON_EXTENSIONS = setOf("geojson", "json")

        fun getBundle(file: LocalFile) = Bundle().apply {
            putParcelable(FILE_EXTRA, file)
        }
    }
}