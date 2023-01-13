package ua.com.radiokot.osmanddisplay.features.map.track.view

import android.content.Context
import com.google.android.material.bottomsheet.BottomSheetDialog
import ua.com.radiokot.osmanddisplay.R

class ImportedTrackSelectionBottomSheet(context: Context) : BottomSheetDialog(context) {
    init {
        initView()
    }

    private fun initView() {
        setContentView(R.layout.bottom_sheet_imported_track_selection)
    }
}