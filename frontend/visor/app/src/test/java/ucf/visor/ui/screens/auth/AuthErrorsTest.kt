package ucf.visor.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.R
import ucf.visor.network.ApiResult

/**
 * What Log in and Sign up say when something is wrong: plain words for each
 * way a request fails, and the checks made before one is sent.
 */
class AuthErrorsTest {

    @Test
    fun noConnectionSaysSoInsteadOfTheException() {
        val timeout = ApiResult.Error(
            code = -1,
            message = "failed to connect to /54.173.104.183 (port 8000) from /10.0.2.16 (port 59584) after 10000ms",
        )
        assertEquals(R.string.auth_error_unreachable, authErrorMessage(timeout, AuthRequest.LOG_IN))
        assertEquals(R.string.auth_error_unreachable, authErrorMessage(timeout, AuthRequest.SIGN_UP))
    }

    @Test
    fun aWrongPasswordIsToldApartFromAServerFault() {
        assertEquals(
            R.string.login_error_no_match,
            authErrorMessage(ApiResult.Error(401, "Invalid email or password"), AuthRequest.LOG_IN),
        )
        assertEquals(
            R.string.auth_error_server,
            authErrorMessage(ApiResult.Error(500, "Registration Failed"), AuthRequest.SIGN_UP),
        )
    }

    @Test
    fun aRefusedSignUpSuggestsWhatToTry() {
        assertEquals(
            R.string.sign_up_error_rejected,
            authErrorMessage(ApiResult.Error(400, "Registration Failed"), AuthRequest.SIGN_UP),
        )
    }

    @Test
    fun anAddressTheServerCannotReadAsksForTheDetailsToBeChecked() {
        // FastAPI's 422 detail is a JSON list, which once showed up raw.
        val unreadable = ApiResult.Error(422, """[{"type":"value_error","loc":["body","email"]}]""")
        assertEquals(R.string.auth_error_check_details, authErrorMessage(unreadable, AuthRequest.LOG_IN))
    }

    @Test
    fun emptyFieldsAreCaughtBeforeAnyRequest() {
        val errors = checkLogin(email = "", password = "")
        assertTrue(errors.any)
        assertEquals(R.string.auth_error_email_missing, errors.email)
        assertEquals(R.string.auth_error_password_missing, errors.password)
    }

    @Test
    fun ordinaryAddressesPass() {
        listOf("jane.doe@gmail.com", "first.last+visor@mail.example.org", " padded@example.com ").forEach {
            assertTrue(it, looksLikeEmail(it))
        }
        listOf("jane", "jane@", "@example.com", "jane@example", "jane doe@example.com").forEach {
            assertFalse(it, looksLikeEmail(it))
        }
    }

    @Test
    fun signUpChecksEachFieldOnItsOwn() {
        val errors = checkSignUp(username = "jane", email = "jane@example.com", password = "abc", confirmPassword = "abd")
        assertNull(errors.username)
        assertNull(errors.email)
        assertEquals(R.string.sign_up_error_password_short, errors.password)
        assertEquals(R.string.sign_up_error_confirm_mismatch, errors.confirmPassword)

        val fine = checkSignUp("jane", "jane@example.com", "abcdef", "abcdef")
        assertFalse(fine.any)
    }
}
