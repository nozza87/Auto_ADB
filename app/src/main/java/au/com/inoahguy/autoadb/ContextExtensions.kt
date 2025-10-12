@file:Suppress("unused")

package au.com.inoahguy.autoadb

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

private const val TAG = "<ContextExtensions>"

internal fun Context.showToast(text: String = "", duration: Int = Toast.LENGTH_SHORT) {
    Log.d(TAG,"showToast(text= $text, duration = $duration)")
    val handler = Handler(Looper.getMainLooper())
    handler.post { // Make sure to run on main thread
        Toast.makeText(this, text, duration).show()
    }
}

internal fun Context.showDialog(title: String = "Auto ADB", message: String = "", shouldFinish: Boolean = true) {
    Log.d(TAG,"showDialog(title= $title, message = $message, shouldFinish = $shouldFinish)")
    val handler = Handler(Looper.getMainLooper())
    handler.post { // Make sure to run on main thread
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            if (shouldFinish) {
                if (this is Activity) { // Check if Context is an Activity
                    this.finish()       // Call finish if it is
                }
            }
        }
        builder.show()
    }
}