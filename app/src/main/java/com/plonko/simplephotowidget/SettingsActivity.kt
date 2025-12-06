package com.plonko.simplephotowidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickImage = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_settings)

        val selectPhotoButton: Button = findViewById(R.id.select_photo_button)
        val abortButton: Button = findViewById(R.id.abort_button)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        selectPhotoButton.setOnClickListener { openGallery() }
        abortButton.setOnClickListener { finish() }
    }

    private fun openGallery() {
        val gallery = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(gallery, pickImage)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == pickImage) {
            data?.data?.also { uri ->
                // Persist permission to read the URI across device reboots
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                saveImageUri(this, appWidgetId, uri)

                // Manually trigger the update so the image appears immediately
                CoroutineScope(Dispatchers.Main).launch {
                    val appWidgetManager = AppWidgetManager.getInstance(this@SettingsActivity)

                    // Call the helper function from PhotoWidgetProvider.kt
                    updateAppWidget(this@SettingsActivity, appWidgetManager, appWidgetId)

                    // Configuration is complete.
                    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(RESULT_OK, resultValue)
                    finish()
                }
            }
        }
    }
}