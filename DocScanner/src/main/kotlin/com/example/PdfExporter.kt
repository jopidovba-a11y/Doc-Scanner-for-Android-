package com.example.docscanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    private const val PAGE_W = 595 // A4 in points (1/72 inch)
    private const val PAGE_H = 842
    private const val DPI = 200f   // enough for documents, keeps the PDF small

    fun export(context: Context, photos: List<Photo>, pages: List<PdfPage>, splitPages: Boolean): List<Uri> {
        val nonEmpty = pages.filter { it.items.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return if (splitPages) {
            nonEmpty.mapIndexed { i, p -> write(context, "scan_${stamp}_${i + 1}", listOf(p), photos) }
        } else {
            listOf(write(context, "scan_$stamp", nonEmpty, photos))
        }
    }

    private fun write(context: Context, name: String, pages: List<PdfPage>, photos: List<Photo>): Uri {
        val doc = PdfDocument()
        pages.forEachIndexed { i, page ->
            val pdfPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
            drawPage(pdfPage.canvas, page, photos)
            doc.finishPage(pdfPage)
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create file in Downloads")
        resolver.openOutputStream(uri)!!.use { doc.writeTo(it) }
        doc.close()
        return uri
    }

    private fun drawPage(canvas: Canvas, page: PdfPage, photos: List<Photo>) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(Color.WHITE)
        for (item in page.items) {
            val photo = photos.firstOrNull { it.id == item.photoId } ?: continue
            val wPt = item.width * PAGE_W

            val src = BitmapUtils.load(File(photo.path), 2500, item.rotation)
            // Downscale to the resolution actually needed at the printed size
            val targetPx = (wPt / 72f * DPI).toInt().coerceAtLeast(50)
            val bmp = if (targetPx < src.width) {
                Bitmap.createScaledBitmap(src, targetPx, targetPx * src.height / src.width, true)
            } else src

            val hPt = wPt * bmp.height / bmp.width
            val left = item.cx * PAGE_W - wPt / 2
            val top = item.cy * PAGE_H - hPt / 2
            canvas.drawBitmap(bmp, null, RectF(left, top, left + wPt, top + hPt), paint)

            if (bmp !== src) bmp.recycle()
            src.recycle()
        }
    }
}