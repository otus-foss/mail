package com.otus.mail

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.otus.mail.feature.inbox.domain.MessagePrinter
import java.lang.ref.WeakReference

/**
 * Prints a message, or saves it as a PDF, through the system print dialog. The HTML loads into an
 * off-screen [WebView] with scripts and network loads off, and prints once it has drawn. The print
 * dialog needs an activity, so [attach] names the one in front.
 */
class AndroidMessagePrinter : MessagePrinter {
    private var activity = WeakReference<Activity>(null)

    /** Call it from `onCreate`; the printer keeps only a weak reference. */
    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    override fun print(
        title: String,
        html: String,
    ) {
        val host = activity.get()?.takeUnless { it.isFinishing } ?: return
        val view = WebView(host)
        view.settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
        }
        view.webViewClient =
            object : WebViewClient() {
                private var printed = false

                override fun onPageFinished(
                    page: WebView,
                    url: String?,
                ) {
                    if (printed) return
                    printed = true
                    val printing = host.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    printing.print(title, page.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
                }
            }
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
