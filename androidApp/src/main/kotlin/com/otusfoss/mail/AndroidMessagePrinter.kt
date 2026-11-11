package com.otusfoss.mail

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.otusfoss.core.designsystem.mail.read.html.offlineHtml
import com.otusfoss.mail.feature.read.domain.MessagePrinter
import java.lang.ref.WeakReference

/**
 * Prints a message, or saves it as a PDF, through the system print dialog. The HTML loads into an
 * off-screen [WebView] with scripts and network loads off, sealed by [offlineHtml], never
 * follows a link or refresh, and prints once it has drawn. The print dialog needs an activity, so
 * [attach] names the one in front. The printer holds the loading view until printing starts, since
 * nothing else does, and the print job destroys it when it finishes; a newly attached activity
 * drops a view its old one never printed.
 */
class AndroidMessagePrinter : MessagePrinter {
    private var activity = WeakReference<Activity>(null)
    private var loading: WebView? = null

    /** Call it from `onCreate`; the printer keeps only a weak reference. */
    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
        loading?.destroy()
        loading = null
    }

    override fun print(
        title: String,
        html: String,
    ) {
        val host = activity.get()?.takeUnless { it.isFinishing } ?: return
        loading?.destroy()
        val view = WebView(host)
        loading = view
        view.settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
        }
        view.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = true

                override fun onPageFinished(
                    page: WebView,
                    url: String?,
                ) {
                    if (loading !== page) return
                    loading = null
                    (host.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(
                        title,
                        DestroyingAdapter(page.createPrintDocumentAdapter(title), page),
                        PrintAttributes.Builder().build(),
                    )
                }
            }
        view.loadDataWithBaseURL(null, offlineHtml(html), "text/html", "utf-8", null)
    }
}

/** Prints as [adapter] does and destroys [view], which drew the pages, once the job is done. */
private class DestroyingAdapter(
    private val adapter: PrintDocumentAdapter,
    private val view: WebView,
) : PrintDocumentAdapter() {
    override fun onStart() = adapter.onStart()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) = adapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) = adapter.onWrite(pages, destination, cancellationSignal, callback)

    override fun onFinish() {
        adapter.onFinish()
        view.destroy()
    }
}
