package ua.com.radiokot.osmanddisplay.features.track.view

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
                "application/geo+json", // Hopeless
                "application/octet-stream"
            ),
            this::onTrackFileOpened
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            initList()
            initEmptyView()

            displayTracks(emptyList())
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

        itemAdapter.add(
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

    private fun initEmptyView() {
        import_track_button.setOnClickListener {
            trackFileOpeningLauncher.launch(Unit)
        }
    }

    private fun displayTracks(tracks: List<ImportedTrackListItem>) {
        itemAdapter.set(tracks)

        if (tracks.isEmpty()) {
            empty_view.visibility = View.VISIBLE
        } else {
            empty_view.visibility = View.GONE
        }
    }

    private fun onTrackFileOpened(result: OpenLocalFileContract.Result) {
        if (result is OpenLocalFileContract.Result.Opened) {
            import_track_button.text = result.file.name
        }
    }

    companion object {
        const val TAG = "imported-tracks"
    }
}