package com.pocketoffice.lite.file

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

/** Memilih file lewat Storage Access Framework (tanpa izin penyimpanan). */
class OpenDocContract : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .addFlags(FLAGS)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Membuat file TXT baru lewat Storage Access Framework. */
class CreateDocContract : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, input)
            .addFlags(FLAGS)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
