package ua.com.radiokot.osmanddisplay.features.track.view

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.list_item_imported_track.view.track_imported_at_text_view
import kotlinx.android.synthetic.main.list_item_imported_track.view.track_name_text_view
import kotlinx.android.synthetic.main.list_item_imported_track.view.track_thumbnail_image_view
import kotlinx.android.synthetic.main.list_item_imported_track.view.view_online_button
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import java.io.File
import java.text.DateFormat

data class ImportedTrackListItem(
    val name: String,
    val importedAt: String,
    val thumbnailImageFile: File?,
    val isViewOnlineButtonVisible: Boolean,
    val source: ImportedTrackRecord? = null,
) : AbstractItem<ImportedTrackListItem.ViewHolder>() {
    constructor(
        source: ImportedTrackRecord,
        dateFormat: DateFormat,
    ) : this(
        name = source.name,
        importedAt = dateFormat.format(source.importedAt),
        thumbnailImageFile = source.thumbnailImageFile,
        isViewOnlineButtonVisible = source.onlinePreviewUrl != null,
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
        private val thumbnailImageView: ImageView = view.track_thumbnail_image_view
        val viewOnlineButton: Button = view.view_online_button

        override fun bindView(item: ImportedTrackListItem, payloads: List<Any>) {
            nameTextView.text = item.name
            importedAtTextView.text = item.importedAt
            viewOnlineButton.isVisible = item.isViewOnlineButtonVisible

            if (item.thumbnailImageFile != null) {
                Picasso.get()
                    .load(item.thumbnailImageFile)
                    .fit()
                    .centerCrop()
                    .into(thumbnailImageView)
            }
        }

        override fun unbindView(item: ImportedTrackListItem) {
            Picasso.get().cancelRequest(thumbnailImageView)
        }
    }
}
