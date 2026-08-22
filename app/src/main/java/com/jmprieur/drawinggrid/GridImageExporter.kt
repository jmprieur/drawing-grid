package com.jmprieur.drawinggrid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object GridImageExporter {
    suspend fun suggestedFileName(context: Context, source: Uri, settings: GridSettings): String =
        withContext(Dispatchers.IO) {
            val displayName = runCatching {
                context.contentResolver.query(
                    source,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
            }.getOrNull()
            suggestedFileName(displayName, settings.rows, settings.columns)
        }

    suspend fun export(
        context: Context,
        source: Uri,
        destination: Uri,
        settings: GridSettings,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceBitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, source),
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                val outputBitmap = Bitmap.createBitmap(
                    sourceBitmap.width,
                    sourceBitmap.height,
                    Bitmap.Config.ARGB_8888,
                )
                try {
                    val canvas = Canvas(outputBitmap)
                    canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
                    drawGrid(canvas, outputBitmap.width, outputBitmap.height, settings)
                    context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        if (!outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            throw IOException("Could not encode grid image")
                        }
                    } ?: throw IOException("Could not open destination")
                } finally {
                    outputBitmap.recycle()
                }
            } finally {
                sourceBitmap.recycle()
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun drawGrid(canvas: Canvas, width: Int, height: Int, settings: GridSettings) {
        val sourceColor = settings.color.toInt()
        val sourceAlpha = ((settings.color ushr 24) and 0xFF).toInt()
        val alpha = (sourceAlpha * settings.opacity).toInt().coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, Color.red(sourceColor), Color.green(sourceColor), Color.blue(sourceColor))
            strokeWidth = settings.thickness
            style = Paint.Style.STROKE
        }
        GridGeometry.lines(
            ImageBounds(0f, 0f, width.toFloat(), height.toFloat()),
            settings.rows,
            settings.columns,
        ).forEach { line ->
            canvas.drawLine(line.startX, line.startY, line.endX, line.endY, paint)
        }
    }

    internal fun suggestedFileName(displayName: String?, rows: Int, columns: Int): String {
        val safeName = displayName
            ?.replace('/', '_')
            ?.replace('\\', '_')
            ?.trim()
            .orEmpty()
        val extensionStart = safeName.lastIndexOf('.').takeIf { it > 0 } ?: safeName.length
        val baseName = safeName.substring(0, extensionStart).ifBlank { "drawing" }
        return "$baseName-grid${rows}x${columns}.png"
    }
}
