package com.plonko.simplephotowidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
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
        val job = CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { appWidgetId ->
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
        job.invokeOnCompletion { pendingResult.finish() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        val pendingResult = goAsync()
        val job = CoroutineScope(Dispatchers.IO).launch {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        job.invokeOnCompletion { pendingResult.finish() }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteImageUri(context, appWidgetId)
        }
    }
}

suspend fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val imageUri = loadImageUri(context, appWidgetId) ?: return
    val views = RemoteViews(context.packageName, R.layout.photo_widget)

    try {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val targetWidth = (minWidth * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val targetHeight = (maxHeight * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

        val bitmap = withContext(Dispatchers.IO) {
            val bitmapOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, bitmapOptions)
            }

            bitmapOptions.inSampleSize = calculateInSampleSize(bitmapOptions, targetWidth, targetHeight)
            bitmapOptions.inJustDecodeBounds = false

            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, bitmapOptions)
            }
        }

        views.setImageViewBitmap(R.id.widget_image, bitmap)
    } catch (e: Exception) {
        Log.e("PhotoWidgetProvider", "Error updating widget", e)
        views.setImageViewResource(R.id.widget_image, R.mipmap.ic_launcher)
    }

    appWidgetManager.updateAppWidget(appWidgetId, views)
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
    prefs.putString("widget_image_uri_$appWidgetId", imageUri.toString()).apply()
}

internal fun loadImageUri(context: Context, appWidgetId: Int): Uri? {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
    return prefs.getString("widget_image_uri_$appWidgetId", null)?.let { Uri.parse(it) }
}

internal fun deleteImageUri(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE).edit()
    prefs.remove("widget_image_uri_$appWidgetId").apply()
}
