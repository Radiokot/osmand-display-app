package ua.com.radiokot.osmanddisplay.features.track.view

import android.view.View
import android.widget.TextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import kotlinx.android.synthetic.main.list_item_imported_track.view.*
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.track.model.ImportedTrack
import java.text.DateFormat

data class ImportedTrackListItem(
    val name: String,
    val importedAt: String,
    val source: ImportedTrack? = null,
) : AbstractItem<ImportedTrackListItem.ViewHolder>() {
    constructor(
        source: ImportedTrack,
        dateFormat: DateFormat,
    ) : this(
        name = source.name,
        importedAt = dateFormat.format(source.importedAt),
        source = source,
    )

    override val type: Int
        get() = R.id.list_item_imported_track

    override val layoutRes: Int
        get() = R.layout.list_item_imported_track

    override fun getViewHolder(v: View): ViewHolder = ViewHolder(v)

    class ViewHolder(view: View) : FastAdapter.ViewHolder<ImportedTrackListItem>(view) {
        private val nameTextView: TextView = view.track_name_text_view
        private val importedAtTextView: TextView = view.track_imported_at_text_view

        override fun bindView(item: ImportedTrackListItem, payloads: List<Any>) {
            nameTextView.text = item.name
            importedAtTextView.text = item.importedAt
        }

        override fun unbindView(item: ImportedTrackListItem) {}
    }
}