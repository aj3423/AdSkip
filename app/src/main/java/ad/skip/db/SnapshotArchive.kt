package ad.skip.db

import android.content.Context
import android.net.Uri
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SnapshotArchive {
    private const val SNAPSHOT_ENTRY = "snapshot.json"
    private const val SCREENSHOT_ENTRY = "screenshot.webp"

    fun export(ctx: Context, snapshot: Snapshot, uri: Uri) {
        val screenshotBytes = ScreenshotStore.file(ctx, snapshot.screenshotFileName).readBytes()
        val exportSnapshot = snapshot.copy(
            id = 0L,
            screenshotFileName = SCREENSHOT_ENTRY
        )

        ctx.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(SNAPSHOT_ENTRY))
                zip.write(snapshotJson.encodeToString(exportSnapshot).toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(SCREENSHOT_ENTRY))
                zip.write(screenshotBytes)
                zip.closeEntry()
            }
        } ?: error("Unable to open export destination")
    }

    fun import(ctx: Context, uri: Uri): Long {
        var snapshotBytes: ByteArray? = null
        var screenshotBytes: ByteArray? = null

        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val bytes = zip.readCurrentEntryBytes()
                    when (entry.name) {
                        SNAPSHOT_ENTRY -> snapshotBytes = bytes
                        SCREENSHOT_ENTRY -> screenshotBytes = bytes
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Unable to open import source")

        val snapshot = snapshotJson.decodeFromString<Snapshot>(
            snapshotBytes?.decodeToString() ?: error("Missing $SNAPSHOT_ENTRY")
        )
        val savedScreenshotFileName = ScreenshotStore.save(
            ctx,
            screenshotBytes ?: error("Missing $SCREENSHOT_ENTRY")
        )

        return SnapshotTable.addNew(
            ctx,
            snapshot.copy(
                id = 0L,
                screenshotFileName = savedScreenshotFileName
            )
        )
    }

    private fun ZipInputStream.readCurrentEntryBytes(): ByteArray =
        ByteArrayOutputStream().use { output ->
            copyTo(output)
            output.toByteArray()
        }
}
