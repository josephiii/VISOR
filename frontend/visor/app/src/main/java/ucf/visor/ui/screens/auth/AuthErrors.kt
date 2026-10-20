package ucf.visor.ui.screens.auth

import androidx.annotation.StringRes
import ucf.visor.R
import ucf.visor.network.ApiResult

/** Which account request failed: the same HTTP status means different things for each. */
enum class AuthRequest { LOG_IN, SIGN_UP }

/**
 * What went wrong with a log-in or sign-up request, in plain words for the
 * user, as a string resource. The raw error, an exception's message such as
 * "failed to connect to /54.173.104.183 (port 8000) … after 10000ms" or the
 * server's own detail, means nothing to them; it goes to the log instead.
 *
 * The server (backend/routes/AuthRoutes.py) answers 401 to a wrong email or
 * password, 400 when it won't create an account (most often, one already has
 * that email), and 422 when what was sent isn't an email address at all.
 */
@StringRes
fun authErrorMessage(error: ApiResult.Error, request: AuthRequest): Int = when {
    error.code == -1 -> R.string.auth_error_unreachable
    error.code >= 500 -> R.string.auth_error_server
    error.code == 422 -> R.string.auth_error_check_details
    request == AuthRequest.LOG_IN && (error.code == 400 || error.code == 401) -> R.string.login_error_no_match
    request == AuthRequest.SIGN_UP && error.code in 400..499 -> R.string.sign_up_error_rejected
    else -> R.string.auth_error_unknown
}

/**
 * A quick check that [email] has the shape of an address, so an obvious slip
 * is caught at once rather than after a trip to the server, which checks it
 * properly. Deliberately loose: "first.last+tag@mail.example.org" passes.
 */
fun looksLikeEmail(email: String): Boolean = EmailShape.matches(email.trim())

private val EmailShape = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")

/** Supabase's default shortest password, which the backend's sign-up uses. */
const val MinPasswordLength = 6

/** What to say under each log-in field before sending it, or null where it is fine. */
data class LoginFieldErrors(
    @StringRes val email: Int?,
    @StringRes val password: Int?,
) {
    val any: Boolean get() = email != null || password != null
}

fun checkLogin(email: String, password: String) = LoginFieldErrors(
    email = emailError(email),
    password = if (password.isEmpty()) R.string.auth_error_password_missing else null,
)

/** What to say under each sign-up field before sending it, or null where it is fine. */
data class SignUpFieldErrors(
    @StringRes val username: Int?,
    @StringRes val email: Int?,
    @StringRes val password: Int?,
    @StringRes val confirmPassword: Int?,
) {
    val any: Boolean get() = listOf(username, email, password, confirmPassword).any { it != null }
}

fun checkSignUp(username: String, email: String, password: String, confirmPassword: String) =
    SignUpFieldErrors(
        username = if (username.isBlank()) R.string.sign_up_error_username_missing else null,
        email = emailError(email),
        password = when {
            password.isEmpty() -> R.string.auth_error_password_missing
            password.length < MinPasswordLength -> R.string.sign_up_error_password_short
            else -> null
        },
        confirmPassword = when {
            confirmPassword.isEmpty() -> R.string.sign_up_error_confirm_missing
            confirmPassword != password -> R.string.sign_up_error_confirm_mismatch
            else -> null
        },
    )

@StringRes
private fun emailError(email: String): Int? = when {
    email.isBlank() -> R.string.auth_error_email_missing
    !looksLikeEmail(email) -> R.string.auth_error_email_invalid
    else -> null
}
