package dev.nekuz.dualspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object AppIcons {

    private const val SIZE_DP = 56

    fun load(context: Context, source: () -> Drawable): Drawable =
        try {
            render(context, source())
        } catch (t: Throwable) {
            fallback(context)
        }

    private fun fallback(context: Context): Drawable =
        try {
            render(context, context.packageManager.defaultActivityIcon)
        } catch (t: Throwable) {
            context.packageManager.defaultActivityIcon
        }

    private fun render(context: Context, drawable: Drawable): Drawable {
        val size = iconSizePx(SIZE_DP, context.resources.displayMetrics.density)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return BitmapDrawable(context.resources, bitmap)
    }
}
