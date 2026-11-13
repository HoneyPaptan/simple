package sh.simple.android.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcErrorMessageTest {
    @Test
    fun daemonKindsMapToPlainSentences() {
        val kinds = listOf(
            "agent_unsupported",
            "bad_request",
            "hc_unavailable",
            "internal",
            "not_found",
            "denied",
            "timeout",
            "binary",
            "too_large",
            "push_failed",
        )
        for (kind in kinds) {
            val text = rpcErrorMessage(LinkDroppedException(kind))
            assertTrue("empty for $kind", text.isNotBlank())
            assertFalse("leaked $kind in $text", text.contains(kind))
        }
    }

    @Test
    fun unknownWireKindDoesNotLeakRawToken() {
        val text = rpcErrorMessage(LinkDroppedException("weird_kind"))
        assertFalse(text.contains("weird_kind"))
        assertFalse(text.contains("weird kind"))
    }

    @Test
    fun goErrorDetailDoesNotReachUi() {
        val text = rpcErrorMessage(LinkDroppedException("could not switch the OpenCode model"))
        assertFalse(text.contains("OpenCode"))
        assertFalse(text.contains("switch"))
    }

    @Test
    fun webhookTestErrorsMapToPlainSentences() {
        assertEquals(
            "Could not reach that address.",
            webhookTestErrorMessage(LinkDroppedException("webhook_unreachable")),
        )
        assertEquals(
            "The webhook did not answer in time.",
            webhookTestErrorMessage(LinkDroppedException("timeout")),
        )
        assertEquals(
            "The webhook answered with an error (HTTP 404).",
            webhookTestErrorMessage(LinkDroppedException("webhook_status", "404")),
        )
    }
}
