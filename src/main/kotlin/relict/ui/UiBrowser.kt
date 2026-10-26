package relict.ui

import org.cef.browser.CefBrowser

/** The part of a browser the rest of the mod needs, whichever MCEF version supplies it. */
interface UiBrowser {
    val cef: CefBrowser
}
