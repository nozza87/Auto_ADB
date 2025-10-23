@file:Suppress("unused")

package au.com.inoahguy.autoadb

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

private const val TAG = "ContextExtensions"

/**
 * Show a toast message on the main thread safely.
 * This function ensures the toast is displayed even if called from a background thread.
 */
internal fun Context.showToast(
    text: String,
    duration: Int = Toast.LENGTH_SHORT
) {
    if (text.isBlank()) {
        Log.w(TAG, "Attempted to show empty toast")
        return
    }

    Log.d(TAG, "showToast: $text (duration=$duration)")

    val showToastRunnable = Runnable {
        Toast.makeText(this, text, duration).show()
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
        showToastRunnable.run()
    } else {
        Handler(Looper.getMainLooper()).post(showToastRunnable)
    }
}

/**
 * Show an alert dialog on the main thread safely.
 * This function ensures the dialog is displayed even if called from a background thread.
 *
 * @param title Dialog title
 * @param message Dialog message
 * @param shouldFinish Whether to finish the activity when OK is pressed
 * @param onDismiss Optional callback when dialog is dismissed
 */
internal fun Context.showDialog(
    title: String = "Auto ADB",
    message: String = "",
    shouldFinish: Boolean = true,
    onDismiss: (() -> Unit)? = null
) {
    if (message.isBlank()) {
        Log.w(TAG, "Attempted to show dialog with empty message")
        return
    }

    Log.d(TAG, "showDialog: title=$title, message=$message, shouldFinish=$shouldFinish")

    val showDialogRunnable = Runnable {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setCancelable(false)
            builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()

                if (shouldFinish && this is Activity) {
                    finish()
                }
            }

            val dialog = builder.create()

            // Prevent window leak if activity is finishing
            if (this is Activity && !isFinishing && !isDestroyed) {
                dialog.show()
            } else if (this !is Activity) {
                dialog.show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dialog", e)
        }
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
        showDialogRunnable.run()
    } else {
        Handler(Looper.getMainLooper()).post(showDialogRunnable)
    }
}