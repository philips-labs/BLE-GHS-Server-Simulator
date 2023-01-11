package com.philips.btserverapp

import android.os.Build
import android.text.Html
import android.util.Log
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
class AppLogTree: Timber.DebugTree() {
    private val LOG_TAG: String = AppLogTree::class.java.getSimpleName()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")

     override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val displayString =  "<p style=\"background:lightgray;\"><strong " +
                    "style=\"background:lightblue;\">&nbsp&nbsp" +
                    LocalDateTime.now().format(dateFormatter) +
                    " :&nbsp&nbsp</strong><strong>&nbsp&nbsp" +
                    tag +
                    "</strong> - " +
                    message +
                    "</p>"
            val html = Html.fromHtml(displayString, Html.FROM_HTML_MODE_COMPACT)
            AppLog.log(html)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error while logging into file : $e")
        }
    }

}