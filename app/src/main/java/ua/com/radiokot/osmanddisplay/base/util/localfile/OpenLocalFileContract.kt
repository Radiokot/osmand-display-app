package ua.com.radiokot.osmanddisplay.base.util.localfile

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import mu.KotlinLogging

/**
 * A contract that calls [Intent.ACTION_OPEN_DOCUMENT] in order to get a [LocalFile].
 *
 * The input is the mime types to filter by, e.g. `image/\*`.
 */
class OpenLocalFileContract(
    lazyContentResolver: Lazy<ContentResolver>,
) :
    ActivityResultContract<Collection<String>?, OpenLocalFileContract.Result>() {

    private val contentResolver: ContentResolver by lazyContentResolver

    sealed class Result {
        /**
         * Opening has been cancelled by the user.
         */
        object Cancelled : Result()

        /**
         * The file has been opened.
         */
        class Opened(val file: LocalFile) : Result()
    }

    private val logger = KotlinLogging.logger("OpenLocalFileCt@${hashCode()}")

    override fun createIntent(context: Context, input: Collection<String>?): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .apply {
                if (input != null && input.isNotEmpty()) {
                    type = input.joinToString(",")
                    putExtra(Intent.EXTRA_MIME_TYPES, input.toTypedArray())
                } else {
                    type = "*/*"
                }
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        if (resultCode == Activity.RESULT_CANCELED) {
            logger.debug { "parseResult(): cancelled" }

            return Result.Cancelled
        }

        val uri = intent
            ?.data
            ?.toString()
            ?.replace("file%3A/", "")
            ?.let(Uri::parse)

        if (uri == null) {
            logger.warn {
                "parseResult(): got_null_uri:" +
                        "\nintent=$intent"
            }

            return Result.Cancelled
        }

        val localFile = LocalFile.fromUri(uri, contentResolver)

        logger.debug {
            "parseResult(): got_file:" +
                    "\nfile=$localFile"
        }

        return Result.Opened(localFile)
    }
}