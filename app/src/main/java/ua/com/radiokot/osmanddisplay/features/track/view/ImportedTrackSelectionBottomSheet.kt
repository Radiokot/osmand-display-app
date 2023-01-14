package ua.com.radiokot.osmanddisplay.features.track.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.registerForActivityResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.android.synthetic.main.bottom_sheet_imported_track_selection.*
import mu.KotlinLogging
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.util.localfile.OpenLocalFileContract

class ImportedTrackSelectionBottomSheet :
    BottomSheetDialogFragment(R.layout.bottom_sheet_imported_track_selection) {

    private val logger = KotlinLogging.logger("TracksBottomSheet@${hashCode()}")

    private val itemAdapter = ItemAdapter<ImportedTrackListItem>()

    private val trackFileOpeningLauncher =
        registerForActivityResult(
            OpenLocalFileContract(lazy { requireContext().contentResolver }),
            setOf(
                "application/vnd.geo+json", // Hopeless
                "application/json",
                "application/octet-stream"
            ),
            this::onTrackFileOpened
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            initList()
            initButtons()

            displayTracks(
                listOf(
                    ImportedTrackListItem(
                        name = "Dnipro #1",
                        importedAt = "02.01.2023"
                    ),
                    ImportedTrackListItem(
                        name = "My test track",
                        importedAt = "01.01.2023"
                    ),
                    ImportedTrackListItem(
                        name = "Imported #33242",
                        importedAt = "29.12.2022"
                    )
                )
            )
        }
    }

    private fun initList() {
        val adapter = FastAdapter.with(itemAdapter)
        adapter.onClickListener = { _, _, item, _ ->
            logger.debug {
                "clicked:" +
                        "\n$item=$item"
            }
            false
        }

        tracks_recycler_view.layoutManager = LinearLayoutManager(context)
        tracks_recycler_view.adapter = adapter
    }

    private fun initButtons() {
        import_track_button.setOnClickListener {
            openTrackFile()
        }

        import_track_header_button.setOnClickListener {
            openTrackFile()
        }
    }

    private fun openTrackFile() {
        trackFileOpeningLauncher.launch(Unit)
    }

    private fun displayTracks(tracks: List<ImportedTrackListItem>) {
        itemAdapter.set(tracks)

        if (tracks.isEmpty()) {
            empty_view.visibility = View.VISIBLE
            import_track_header_button.visibility = View.GONE
        } else {
            empty_view.visibility = View.GONE
            import_track_header_button.visibility = View.VISIBLE
        }
    }

    private fun onTrackFileOpened(result: OpenLocalFileContract.Result) {
        if (result is OpenLocalFileContract.Result.Opened) {
            startActivity(
                Intent(requireContext(), ImportTrackActivity::class.java)
                    .putExtras(ImportTrackActivity.getBundle(result.file))
            )
        }
    }

    companion object {
        const val TAG = "imported-tracks"
    }
}