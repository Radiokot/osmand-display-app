package ua.com.radiokot.osmanddisplay.features.track.view

import android.os.Bundle
import android.webkit.MimeTypeMap
import kotlinx.android.synthetic.main.activity_import_track.*
import mu.KotlinLogging
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.util.localfile.LocalFile
import ua.com.radiokot.osmanddisplay.base.view.BaseActivity
import ua.com.radiokot.osmanddisplay.features.track.model.GeoJsonTrackData
import java.io.InputStreamReader
import java.net.URLEncoder

class ImportTrackActivity : BaseActivity() {
    private val logger = KotlinLogging.logger("ImportTrackActivity@${hashCode()}")

    private val file: LocalFile by lazy {
        checkNotNull(intent.getParcelableExtra(FILE_EXTRA)) {
            "There must be a $file extra"
        }
    }
    private lateinit var geoJsonTrackData: GeoJsonTrackData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tryToReadFile().also { readData ->
            if (readData == null) {
                toastManager.short(R.string.error_not_a_geojson_track)
                finish()
                return
            } else {
                geoJsonTrackData = readData
            }
        }

        setContentView(R.layout.activity_import_track)

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