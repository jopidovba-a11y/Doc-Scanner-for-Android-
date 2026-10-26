package com.example.docscanner

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import kotlin.math.max

data class Photo(val id: Long, val path: String)

/**
 * An image placed on a page. Position and size are normalized so the same data
 * works for the on-screen preview and for the PDF:
 * cx, cy = center as a fraction of page width / height; width = fraction of page width.
 */
data class PlacedImage(
    val id: Long,
    val photoId: Long,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val rotation: Int // 0, 90, 180, 270
)

data class PdfPage(val id: Long, val items: List<PlacedImage> = emptyList())

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private var nextId = System.currentTimeMillis()
    private fun newId() = nextId++

    val photos = mutableStateListOf<Photo>()
    val pages = mutableStateListOf(PdfPage(newId()))
    var currentPage by mutableIntStateOf(0)
    var selectedId by mutableStateOf<Long?>(null)

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val selectedItem: PlacedImage?
        get() = pages.getOrNull(currentPage)?.items?.firstOrNull { it.id == selectedId }

    // ---------- Photos ----------

    fun importUri(uri: Uri) {
        val ctx = getApplication<Application>()
        val file = File(ctx.filesDir, "scan_${newId()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        photos.add(Photo(newId(), file.path))
    }

    fun removePhoto(photo: Photo) {
        photos.remove(photo)
        for (i in pages.indices) {
            pages[i] = pages[i].copy(items = pages[i].items.filter { it.photoId != photo.id })
        }
        File(photo.path).delete()
        selectedId = null
    }

    fun bitmap(photoId: Long, rotation: Int, maxSide: Int): Bitmap? {
        val key = "$photoId-$rotation-$maxSide"
        cache.get(key)?.let { return it }
        val photo = photos.firstOrNull { it.id == photoId } ?: return null
        val bmp = BitmapUtils.load(File(photo.path), maxSide, rotation)
        cache.put(key, bmp)
        return bmp
    }

    // ---------- Pages ----------

    fun goToPage(index: Int) {
        currentPage = index.coerceIn(0, pages.lastIndex)
        selectedId = null
    }

    fun addPage() {
        pages.add(PdfPage(newId()))
        goToPage(pages.lastIndex)
    }

    fun deletePage() {
        if (pages.size <= 1) return
        pages.removeAt(currentPage)
        goToPage(currentPage)
    }

    // ---------- Items ----------

    fun placeOnCurrentPage(photo: Photo) {
        val page = pages[currentPage]
        val index = page.items.size
        val item = PlacedImage(
            id = newId(),
            photoId = photo.id,
            cx = 0.5f,
            cy = (0.18f + 0.30f * index).coerceAtMost(0.85f), // stack below existing ones
            width = 0.5f,
            rotation = 0
        )
        pages[currentPage] = page.copy(items = page.items + item)
        selectedId = item.id
    }

    fun updateItem(item: PlacedImage) {
        val pi = pages.indexOfFirst { p -> p.items.any { it.id == item.id } }
        if (pi < 0) return
        pages[pi] = pages[pi].copy(items = pages[pi].items.map { if (it.id == item.id) item else it })
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        pages[currentPage] = pages[currentPage].copy(items = pages[currentPage].items.filter { it.id != id })
        selectedId = null
    }
}

object BitmapUtils {
    /** Loads a JPEG downsampled so its larger side is >= maxSide, applying EXIF + extra rotation. */
    fun load(file: File, maxSide: Int, extraRotation: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        val exif = when (ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        val total = (exif + extraRotation) % 360
        if (total == 0) return raw
        val m = Matrix().apply { postRotate(total.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }
}