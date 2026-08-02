package io.github.joelkanyi.peek.runtime

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Zero-config auto-init: the app's manifest merge picks this provider up (from a
 * debug-only dependency), and Android calls [onCreate] during app startup, before
 * any Activity. It only starts the agent; it serves no content. Remove it with
 * `tools:node="remove"` to opt out.
 */
internal class PeekInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { PeekRuntime.start(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
