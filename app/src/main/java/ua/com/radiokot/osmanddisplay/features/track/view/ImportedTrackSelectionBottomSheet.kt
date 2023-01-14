package ua.com.radiokot.osmanddisplay.features.track.view

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.android.synthetic.main.bottom_sheet_imported_track_selection.*
import kotlinx.android.synthetic.main.list_item_imported_track.*
import mu.KotlinLogging
import ua.com.radiokot.osmanddisplay.R

class ImportedTrackSelectionBottomSheet(context: Context) : BottomSheetDialog(context) {
    private val logger = KotlinLogging.logger("TracksBottomSheet@${hashCode()}")

    init {
        initView()
    }

    private fun initView() {
        setContentView(R.layout.bottom_sheet_imported_track_selection)

        initList()
    }

    private fun initList() {
        val itemAdapter = ItemAdapter<ImportedTrackListItem>()

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
}