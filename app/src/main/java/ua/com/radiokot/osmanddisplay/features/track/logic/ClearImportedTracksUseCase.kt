package ua.com.radiokot.osmanddisplay.features.track.logic

import com.mapbox.common.TileStore
import io.reactivex.Completable
import io.reactivex.Single
import ua.com.radiokot.osmanddisplay.features.track.data.storage.ImportedTracksRepository

/**
 * Clears the [importedTracksRepository] and removes all the saved tile regions
 * from the [tileStore].
 */
class ClearImportedTracksUseCase(
    private val importedTracksRepository: ImportedTracksRepository,
    private val tileStore: TileStore,
) {
    fun perform(): Completable {
        return clearRepository()
            .flatMap {
                clearTileStore()
            }
            .ignoreElement()
    }

    private fun clearRepository(): Single<Boolean> =
        importedTracksRepository
            .clear()
            .toSingleDefault(true)

    private fun clearTileStore(): Single<Boolean> = Single.create { emitter ->
        tileStore.getAllTileRegions { allTileRegionsResult ->
            val tileRegions = allTileRegionsResult.value
            if (tileRegions != null) {
                tileRegions.forEach { region ->
                    tileStore.removeTileRegion(region.id)
                }
                emitter.onSuccess(true)
            } else {
                emitter.tryOnError(IllegalStateException("Failed to get tile regions: ${allTileRegionsResult.error?.message}"))
            }
        }
    }
}