package com.aditya.cabletv.saver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aditya.cabletv.home.Fonts
import com.aditya.cabletv.home.Scale
import com.aditya.cabletv.home.StaticFlash
import com.aditya.cabletv.home.style
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scene 2 · Channel surf (design-spec S5, R-18, D-024): full-screen "channels" every 30 s with a
 * static flash between, alternating bundled key art with Aditya's own photos. Photos live in the
 * app's own folder on the TV ([photoDir]), filled from the Mac by tools/push_photos.sh, so no
 * storage permission is needed. Photos play in random order without repeats until all are shown.
 */
class ChannelSurfScene(
    context: Context, private val scale: Scale, fonts: Fonts, private val reduced: Boolean,
    private val showClock: Boolean, private val usePhotos: Boolean, private val weather: () -> String?,
) : Scene(context) {

    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val plateNumber = TextView(context)
    private val plateLabel = TextView(context)
    private val time = TextView(context)
    private val date = TextView(context)
    private val flash = StaticFlash(context)
    private val wallpapers = context.assets.list("wallpapers").orEmpty().sorted().map { "wallpapers/$it" }
    private var photos: MutableList<File> = mutableListOf()
    private var wallIndex = 0
    private var channel = 201
    private var showPhotoNext = false
    private var current: Bitmap? = null

    private val flip = object : Runnable {
        override fun run() {
            next()
            postDelayed(this, 30_000)
        }
    }

    init {
        setBackgroundColor(Color.parseColor("#050806"))
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(View(context).apply { background = scanlines() }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val plate = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.END
            background = darkPlate(); setPadding(scale.px(22), scale.px(14), scale.px(22), scale.px(14))
        }
        plateNumber.style(scale, fonts.osd, 64, Color.parseColor("#a9dd7e"))
        plateLabel.style(scale, fonts.osd, 30, Color.parseColor("#e9efe6"), 2f)
        plate.addView(plateNumber); plate.addView(plateLabel)
        addView(plate, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.TOP).apply {
            marginEnd = scale.px(96); topMargin = scale.px(54)
        })

        val clockBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = darkPlate(); setPadding(scale.px(26), scale.px(18), scale.px(26), scale.px(18))
        }
        time.style(scale, fonts.osd, 120, Color.parseColor("#e9efe6"))
        date.style(scale, fonts.osd, 36, Color.parseColor("#a9dd7e"), 3f)
        clockBox.addView(time); clockBox.addView(date)
        addView(clockBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
            marginStart = scale.px(96); bottomMargin = scale.px(54)
        })
        addView(flash, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        if (usePhotos) photos = photoDir(context).listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.toMutableList()?.apply { shuffle() } ?: mutableListOf()
        show()
        postDelayed(flip, 30_000)
    }

    private fun next() {
        if (reduced) { show(); return }
        flash.play(false) { show(); flash.postDelayed({ flash.reset() }, 80) }
    }

    /** Shows the next channel: a photo when it's the photos' turn and there are any, else key art. */
    private fun show() {
        val wantPhoto = showPhotoNext || wallpapers.isEmpty()
        val photo = if (wantPhoto && photos.isNotEmpty()) photos.removeAt(0).also { if (photos.isEmpty()) refill() } else null
        showPhotoNext = !showPhotoNext
        if (photo == null && wallpapers.isEmpty()) {
            // No bundled wallpapers and no photos pushed yet: an empty channel.
            image.setImageDrawable(null)
            current?.recycle(); current = null
            plateNumber.text = "CH $channel"
            plateLabel.text = "NO SIGNAL"
            return
        }
        val bmp = if (photo != null) decode(photo) else assetBitmap(wallpapers[wallIndex++ % wallpapers.size])
        image.setImageBitmap(bmp)
        current?.recycle() // hold at most two bitmaps at a time (tech-spec caches)
        current = bmp
        plateNumber.text = "CH $channel"
        plateLabel.text = if (photo != null) "YOUR PHOTOS" else "WALLPAPER"
        channel = if (channel >= 299) 201 else channel + 1
    }

    private fun refill() {
        photos = photoDir(context).listFiles()?.filter { it.isFile }?.toMutableList()?.apply { shuffle() } ?: mutableListOf()
    }

    private fun assetBitmap(path: String): Bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }

    /** Decodes a photo near 1920 × 1080 so a 12 MP picture doesn't eat the TV's memory. */
    private fun decode(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    override fun tick(now: Date) {
        (time.parent as View).visibility = if (showClock) VISIBLE else GONE
        time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val d = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(now).uppercase(Locale.ENGLISH)
        date.text = weather()?.let { "$d · $it" } ?: d
    }

    override fun stop() {
        super.stop()
        removeCallbacks(flip)
        flash.reset()
    }

    private fun darkPlate() = GradientDrawable().apply { cornerRadius = scale.pxF(14f); setColor(Color.argb(184, 5, 8, 6)) }

    private fun scanlines() = object : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint().apply { color = Color.argb(40, 0, 0, 0) }
        override fun draw(c: android.graphics.Canvas) {
            val step = scale.pxF(4f)
            var y = 0f
            while (y < bounds.height()) { c.drawRect(0f, y, bounds.width().toFloat(), y + step / 2, paint); y += step }
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(cf: android.graphics.ColorFilter?) = Unit
        @Deprecated("Drawable.getOpacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    companion object {
        /** /sdcard/Android/data/com.aditya.cabletv/files/photos — writable by adb, no permission needed. */
        fun photoDir(context: Context): File = File(context.getExternalFilesDir(null), "photos").apply { mkdirs() }
    }
}
