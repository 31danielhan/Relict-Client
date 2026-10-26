package relict.core

/** A failure the UI should see as an error message; [code] follows HTTP conventions (400 bad input, 404 unknown, 409 conflict). */
class ApiException(val code: Int, message: String) : Exception(message)
