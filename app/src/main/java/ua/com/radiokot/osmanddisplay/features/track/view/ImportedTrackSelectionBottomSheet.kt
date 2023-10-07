package ua.com.radiokot.osmanddisplay.features.track.view

import android.os.Bundle
import android.view.View
import androidx.activity.result.registerForActivityResult
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.listeners.addClickListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.bottom_sheet_imported_track_selection.empty_view
import kotlinx.android.synthetic.main.bottom_sheet_imported_track_selection.import_track_button
import kotlinx.android.synthetic.main.bottom_sheet_imported_track_selection.import_track_header_button
import kotlinx.android.synthetic.main.bottom_sheet_imported_track_selection.tracks_recycler_view
import org.koin.android.ext.android.inject
import ua.com.radiokot.osmanddisplay.R
import ua.com.radiokot.osmanddisplay.base.util.localfile.OpenLocalFileContract
import ua.com.radiokot.osmanddisplay.base.view.dateformat.DateFormats
import ua.com.radiokot.osmanddisplay.features.track.data.model.ImportedTrackRecord
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository
import ua.com.radiokot.osmanddisplay.features.track.logic.OpenTrackOnlinePreviewUseCase
import java.text.DateFormat

class ImportedTrackSelectionBottomSheet :
    BottomSheetDialogFragment(R.layout.bottom_sheet_imported_track_selection) {

    private val importedTracksRepository: ImportedTracksRepository by inject()

    private val itemAdapter = ItemAdapter<ImportedTrackListItem>()
    private val trackDateFormat: DateFormat by lazy {
        DateFormats.longTimeOrDate(requireContext())
    }

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
    private val trackImportLauncher =
        registerForActivityResult(
            ImportTrackActivity.ImportContract(),
            this::onTrackImportResult
        )

    private lateinit var compositeDisposable: CompositeDisposable

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        compositeDisposable = CompositeDisposable()

        initList()
        initButtons()

        subscribeToTracks()

        importedTracksRepository.updateIfNotFresh()
    }

    private fun initList() {
        val adapter = FastAdapter.with(itemAdapter).apply {
            addClickListener(
                resolveView = { null },
                resolveViews = { viewHolder: ImportedTrackListItem.ViewHolder ->
                    listOf(viewHolder.itemView, viewHolder.viewOnlineButton)
                },
                onClick = { view, _, _, item ->
                    if (item.source == null) {
                        return@addClickListener
                    }

                    when (view.id) {
                        R.id.view_online_button ->
                            openTrackOnlinePreview(checkNotNull(item.source.onlinePreviewUrl) {
                                "There must be the preview URL the button is visible"
                            })

                        else ->
                            onTrackSelected(item.source)
                    }

                }
            )
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

    private fun subscribeToTracks() {
        importedTracksRepository.itemsSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { importedTracks ->
                displayTracks(importedTracks.map {
                    ImportedTrackListItem(
                        it,
                        trackDateFormat
                    )
                })
            }
            .addTo(compositeDisposable)
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
            trackImportLauncher.launch(
                ImportTrackActivity.getBundle(
                    file = result.file,
                    onlinePreviewUrl = null,
                )
            )
        }
    }

    private fun onTrackSelected(track: ImportedTrackRecord) {
        setFragmentResult(REQUEST_KEY, Bundle().apply {
            putParcelable(RESULT_EXTRA, track)
        })

        dismiss()
    }

    private fun onTrackImportResult(importedTrack: ImportedTrackRecord?) {
        if (importedTrack != null) {
            onTrackSelected(importedTrack)
        }
    }

    private fun openTrackOnlinePreview(onlinePreviewUrl: String) =
        OpenTrackOnlinePreviewUseCase(
            onlinePreviewUrl = onlinePreviewUrl,
            activity = requireActivity(),
        )()

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.dispose()
        compositeDisposable = CompositeDisposable()
    }

    companion object {
        const val TAG = "imported-tracks"
        const val REQUEST_KEY = "imported-track-request"
        private const val RESULT_EXTRA = "result"

        fun getResult(bundle: Bundle): ImportedTrackRecord {
            return bundle.getParcelable(RESULT_EXTRA)!!
        }
    }
}
