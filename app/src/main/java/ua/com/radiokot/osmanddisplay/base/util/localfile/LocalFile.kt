package ua.com.radiokot.osmanddisplay.base.util.localfile

import android.content.ContentResolver
import android.net.Uri
import android.os.Parcelable
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.android.parcel.Parcelize
import java.io.File
import java.util.*

/**
 * Represents a file stored on the device
 */
@Parcelize
data class LocalFile(
    /**
     * 'file://' or 'content://' URI for the file
     */
    val uri: Uri,
    /**
     * MIME type of the file
     */
    val mimeType: String,
    /**
     * Byte size of the file
     */
    val size: Long,
    /**
     * Name of the file with extension
     */
    val name: String
) : Parcelable {
    companion object {
        private fun getMimeTypeOfFile(fileUri: Uri): String {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileUri.toString())
            return MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.ENGLISH))
                ?: ""
        }

        /**
         * @return [LocalFile] instance constructed from 'file://' or 'content://' URI
         */
        fun fromUri(uri: Uri, contentResolver: ContentResolver): LocalFile {
            val mimeType = contentResolver.getType(uri)
                ?: getMimeTypeOfFile(uri)

            val size: Long
            val name: String

            val projection = arrayOf(
                OpenableColumns.SIZE,
                OpenableColumns.DISPLAY_NAME
            )
            val queryCursor = contentResolver
                .query(uri, projection, null, null, null)

            if (queryCursor?.moveToFirst() == true) {
                // Content URI
                val sizeColumnIndex = queryCursor.getColumnIndex(OpenableColumns.SIZE)
                check(sizeColumnIndex >= 0) {
                    "There must be the size column in the content query result"
                }

                val displayNameColumnIndex =
                    queryCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                check(displayNameColumnIndex >= 0) {
                    "There must be the display name column in the content query result"
                }

                size = queryCursor.getLong(sizeColumnIndex)
                name = queryCursor.getString(displayNameColumnIndex)
            } else {
                // File URI
                val file = File(requireNotNull(uri.path) { "Not a file URI, missing path" })
                size = file.length()
                name = file.name
            }

            queryCursor?.close()

            return LocalFile(uri, mimeType, size, name)
        }

        fun fromFile(file: File): LocalFile {
            val uri = Uri.fromFile(file)

            return LocalFile(
                size = file.length(),
                name = file.name,
                mimeType = getMimeTypeOfFile(uri),
                uri = uri
            )
        }
    }
}