package dev.ronova.randomizer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Фото живут во внутреннем хранилище; при добавлении ужимаются до 1080px и сохраняются в WebP/JPEG. */
class PhotoStore(private val ctx: Context) {
    val dir: File = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun file(name: String) = File(dir, name)

    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            if (maxSide <= 0) return@withContext null
            var sample = 1
            while (maxSide / (sample * 2) >= MAX_SIDE) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            val side = max(bmp.width, bmp.height)
            if (side > MAX_SIDE) {
                val k = MAX_SIDE.toFloat() / side
                m.postScale(k, k)
            }
            if (!m.isIdentity) {
                val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (t !== bmp) bmp.recycle()
                bmp = t
            }

            val name: String
            if (Build.VERSION.SDK_INT >= 30) {
                name = newId() + ".webp"
                FileOutputStream(file(name)).use { bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) }
            } else {
                name = newId() + ".jpg"
                FileOutputStream(file(name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }
            bmp.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val MAX_SIDE = 1080
    }
}
