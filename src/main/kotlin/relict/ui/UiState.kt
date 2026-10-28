package relict.ui

/** What the open GUI page tells the game about itself. */
object UiState {
    /** True while a text field in the page has focus, so keys such as Right Shift (used for capitals) are not taken as shortcuts. */
    @Volatile
    var editing = false
}
