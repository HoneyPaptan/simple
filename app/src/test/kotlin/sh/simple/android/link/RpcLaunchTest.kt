package sh.simple.android.link

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcLaunchTest {
    @Test
    fun rpcRunCapturesLinkDropped() = runTest {
        val result = rpcRun { throw LinkDroppedException() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LinkDroppedException)
    }

    @Test
    fun launchRpcDoesNotThrowLinkDropped() = runTest {
        var failed = false
        launchRpc {
            throw LinkDroppedException()
            failed = true
        }
        testScheduler.advanceUntilIdle()
        assertFalse(failed)
    }

    @Test
    fun rpcRunRethrowsCancellation() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { rpcRun { throw CancellationException("gone") } }
        }
    }
}
