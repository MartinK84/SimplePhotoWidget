package com.plonko.simplephotowidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { appWidgetId ->
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            pendingResult.finish()
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            updateAppWidget(context, appWidgetManager, appWidgetId)
            pendingResult.finish()
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteImageUri(context, appWidgetId)
            deleteTransparency(context, appWidgetId)
        }
    }
}

suspend fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val imageUri = loadImageUri(context, appWidgetId)
    val transparency = loadTransparency(context, appWidgetId)
    val views = RemoteViews(context.packageName, R.layout.photo_widget)

    val intent = Intent(context, SettingsActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)

    if (imageUri != null) {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                decodeSampledBitmapFromUri(context, imageUri, appWidgetManager.getAppWidgetOptions(appWidgetId))
            }
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setInt(R.id.widget_image, "setImageAlpha", ((100f - transparency) / 100f * 255).toInt())
            } else {
                views.setImageViewResource(R.id.widget_image, R.mipmap.ic_launcher)
            }
        } catch (e: Exception) {
            Log.e("PhotoWidgetProvider", "Error updating widget", e)
            views.setImageViewResource(R.id.widget_image, R.mipmap.ic_launcher)
        }
    } else {
        views.setImageViewResource(R.id.widget_image, R.mipmap.ic_launcher)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
}

suspend fun decodeSampledBitmapFromUri(context: Context, imageUri: Uri, options: Bundle): Bitmap? {
    return withContext(Dispatchers.IO) {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val targetWidth = (minWidth * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val targetHeight = (maxHeight * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

        val bitmapOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bitmapOptions) }
        bitmapOptions.inSampleSize = calculateInSampleSize(bitmapOptions, targetWidth, targetHeight)
        bitmapOptions.inJustDecodeBounds = false
        context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bitmapOptions) }
    }
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

internal fun saveImageUri(context: Context, appWidgetId: Int, imageUri: Uri) {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE).edit()
    prefs.putString("widget_image_uri_$appWidgetId", imageUri.toString()).commit()
}

internal fun loadImageUri(context: Context, appWidgetId: Int): Uri? {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
    return prefs.getString("widget_image_uri_$appWidgetId", null)?.let { Uri.parse(it) }
}

internal fun deleteImageUri(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE).edit()
    prefs.remove("widget_image_uri_$appWidgetId").commit()
    prefs.remove("widget_corner_radius_$appWidgetId").commit()
}

internal fun saveTransparency(context: Context, appWidgetId: Int, transparency: Float) {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE).edit()
    prefs.putFloat("widget_transparency_$appWidgetId", transparency).commit()
}

internal fun loadTransparency(context: Context, appWidgetId: Int): Float {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
    return prefs.getFloat("widget_transparency_$appWidgetId", 0f)
}

internal fun deleteTransparency(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE).edit()
    prefs.remove("widget_transparency_$appWidgetId").commit()
}
