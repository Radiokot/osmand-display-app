package ua.com.radiokot.osmanddisplay.features.track.brouter.logic

import io.reactivex.Single
import io.reactivex.rxkotlin.toSingle
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Gets a BRouter GeoJSON track from a BRouter Web Client URL.
 * @see <a href="https://brouter.de/brouter-web">BRouter Web</a>
 */
class GetTrackFromBRouterWebUseCase(
    private val url: String,
    private val httpClient: OkHttpClient,
) {
    private lateinit var bRouterUrl: String

    fun perform(): Single<String> {
        return getBRouterUrl()
            .doOnSuccess {
                bRouterUrl = it
            }
            .flatMap {
                getBRouterGeoJson()
            }
    }

    private fun getBRouterUrl(): Single<String> = {
        val hashPart = url.substringAfterLast("lonlats=", "")
            .takeIf(String::isNotEmpty)

        checkNotNull(hashPart) {
            "There must be a hash part"
        }

        val sourceUrl = url.toHttpUrl()

        HttpUrl.Builder()
            .scheme(sourceUrl.scheme)
            .host(sourceUrl.host)
            .addPathSegment("brouter")
            .addQueryParameter("format", "geojson")
            .apply {
                if (!hashPart.contains("alternativeidx")) {
                    addQueryParameter("alternativeidx", "0")
                }
            }
            .build()
            .toString() + "&lonlats=" + hashPart.replace(';', '|')
    }.toSingle()

    private fun getBRouterGeoJson(): Single<String> = {
        val request = Request.Builder()
            .get()
            .url(bRouterUrl)
            .build()

        val response = httpClient
            .newCall(request)
            .execute()

        check(
            response.header("Content-Type", "")
                ?.startsWith("application/vnd.geo+json") == true
        ) {
            "Expected GeoJSON content in the response"
        }

        val body = response.body
        checkNotNull(body) {
            "Expected a body in the response"
        }

        body.string()
    }
        .toSingle()
        .subscribeOn(Schedulers.io())
}