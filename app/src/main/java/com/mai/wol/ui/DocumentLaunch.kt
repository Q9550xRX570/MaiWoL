package com.mai.wol.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher

/**
 * Opens or creates a document without requiring DocumentsUI.
 * Stripped Android builds often have no ACTION_OPEN_DOCUMENT handler, so the
 * system file picker never appears. GET_CONTENT covers file managers that
 * still register a picker, and the caller handles the case where neither exists.
 */
object DocumentLaunch {

    fun open(
        context: Context,
        mimeTypes: Array<String>,
        openDocument: ActivityResultLauncher<Array<String>>,
        getContent: ActivityResultLauncher<String>
    ): Boolean {
        if (resolves(context, openDocumentIntent(mimeTypes)) &&
            launchSafely { openDocument.launch(mimeTypes) }
        ) {
            return true
        }
        if (resolves(context, getContentIntent()) &&
            launchSafely { getContent.launch("*/*") }
        ) {
            return true
        }
        // resolveActivity is a false negative on API 30+ when package visibility
        // hides the handler. Try the launch itself before reporting that no picker exists.
        if (launchSafely { openDocument.launch(mimeTypes) }) return true
        if (launchSafely { getContent.launch("*/*") }) return true
        return false
    }

    fun create(
        context: Context,
        title: String,
        mimeType: String,
        createDocument: ActivityResultLauncher<String>
    ): Boolean {
        if (resolves(context, createDocumentIntent(title, mimeType)) &&
            launchSafely { createDocument.launch(title) }
        ) {
            return true
        }
        return launchSafely { createDocument.launch(title) }
    }

    private fun launchSafely(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    private fun resolves(context: Context, intent: Intent): Boolean {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            ) != null
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }

    private fun openDocumentIntent(mimeTypes: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }

    private fun getContentIntent(): Intent =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

    private fun createDocumentIntent(title: String, mimeType: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, title)
        }
}
