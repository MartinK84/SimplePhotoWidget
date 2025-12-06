package com.plonko.simplephotowidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.plonko.simplephotowidget.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var imageUri: Uri? = null

    private val pickImage = 100

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        imageUri = loadImageUri(this, appWidgetId)
        val transparency = loadTransparency(this, appWidgetId)

        imageUri?.let { binding.imagePreview.setImageURI(it) }
        binding.imagePreview.imageAlpha = ((100f - transparency) / 100f * 255).toInt()
        binding.transparencySlider.value = transparency

        binding.transparencySlider.addOnChangeListener { _, value, _ ->
            binding.imagePreview.imageAlpha = ((100f - value) / 100f * 255).toInt()
        }

        binding.selectPhotoButton.setOnClickListener { openGallery() }
        binding.saveButton.setOnClickListener { saveAndFinish() }
    }

    private fun openGallery() {
        val gallery = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(gallery, pickImage)
    }

    private fun saveAndFinish() {
        imageUri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
                saveImageUri(this, appWidgetId, it)
            } catch (e: SecurityException) {
                // This can happen if the user selects an image from a provider that doesn't support persistable URI permissions.
                // We can still use the image for the current session.
            }
        }

        saveTransparency(this, appWidgetId, binding.transparencySlider.value)

        val appWidgetManager = AppWidgetManager.getInstance(this)

        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                updateAppWidget(this@SettingsActivity, appWidgetManager, appWidgetId)
            }

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == pickImage) {
            imageUri = data?.data
            imageUri?.let { binding.imagePreview.setImageURI(it) }
        }
    }
}