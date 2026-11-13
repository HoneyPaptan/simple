package sh.simple.android.link

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

fun webhookTestErrorMessage(ex: Throwable): String {
  if (ex is LinkDroppedException) {
      when (ex.kind) {
        "webhook_unreachable" -> return "Could not reach that address."
        "timeout" -> return "The webhook did not answer in time."
        "webhook_status" -> {
            val code = ex.message?.takeIf { it.all(Char::isDigit) }
            return if (code != null) {
                "The webhook answered with an error (HTTP $code)."
            } else {
                "The webhook answered with an error."
            }
        }
      }
  }
  return rpcErrorMessage(ex)
}

fun rpcErrorMessage(ex: Throwable): String {
    when (ex) {
        is LinkDroppedException -> return linkDropMessage(ex)
    }
    if (ex is SocketTimeoutException || ex is TimeoutCancellationException) {
        return daemonKindSentence("timeout")
    }
    if (ex is IOException) {
        return "Can't reach the computer right now. It will retry when it is back."
    }
    return daemonKindSentence("internal")
}

fun gitRepoErrorMessage(ex: Throwable): String {
    if (ex is LinkDroppedException) {
        if (ex.kind == "not_found") return "Not a git repository"
        if (ex.kind.contains("not a git repository", ignoreCase = true)) return "Not a git repository"
    }
    return rpcErrorMessage(ex)
}

fun gitCommitResultMessage(commitLine: String, ex: Throwable): String {
    if (ex is LinkDroppedException && ex.kind == "push_failed") {
        return "$commitLine Push did not finish. Push from your computer when you are ready."
    }
    return gitRepoErrorMessage(ex)
}

fun settingsExportError(ex: Throwable): String = "Could not save the export file. Pick another location and try again."

fun settingsImportError(ex: Throwable): String =
    "Could not apply imported settings. Export again from the other phone and retry."

fun voiceDownloadError(ex: Throwable): String {
    if (ex is SocketTimeoutException || ex is TimeoutCancellationException) {
        return "Download timed out. Try again on WiFi."
    }
    if (ex is IOException) {
        return "Could not download the voice model. Check your connection and try again."
    }
    return "Could not download the voice model. Try again."
}

fun linkDropKind(ex: LinkDroppedException): String = ex.kind

private fun linkDropMessage(ex: LinkDroppedException): String = when (ex.kind) {
    "", "disconnected", "send failed", "stopped" ->
        "Can't reach the computer right now. It will retry when it is back."
    "offline" ->
        "This computer is offline on the phone. Open Computers and wait until it connects."
    "timeout" -> daemonKindSentence("timeout")
    "unreachable", "hello send", "hello response" ->
        "Can't reach the computer right now. It will retry when it is back."
    "download failed" -> "Could not download from your computer. Try again."
    "upload failed" -> "Could not upload to your computer. Try again."
    "webhook_unreachable" -> "Could not reach that address."
    "webhook_status" -> {
        val code = ex.message?.takeIf { it.all(Char::isDigit) }
        if (code != null) "The webhook answered with an error (HTTP $code)."
        else "The webhook answered with an error."
    }
    else -> sentenceForWireKind(ex.kind)
}

private fun sentenceForWireKind(kind: String): String {
    if (kind.matches(KindPattern)) return daemonKindSentence(kind)
    return daemonKindSentence("internal")
}

private val KindPattern = Regex("^[a-z][a-z0-9_]*$")

private fun daemonKindSentence(kind: String): String = when (kind) {
    "hc_unavailable" ->
        "Herdr is not running on this computer yet. simpled starts it within a minute; if this keeps happening, open Herdr on the computer, then try again."
    "bad_request" ->
        "That request could not be completed. Check the details and try again."
    "not_found" ->
        "That was not found on your computer."
    "internal" ->
        "Something went wrong on your computer. Try again in a moment."
    "agent_unsupported" ->
        "This agent is not installed on your computer yet. Install it on the computer, then try again."
    "denied" ->
        "That file is outside your home folder."
    "timeout" ->
        "Your computer did not answer in time. Try again."
    "binary" ->
        "This file is not text. Open it on your computer."
    "too_large" ->
        "This file is too large to open on your phone."
    "exists" ->
        "A folder with that name is already here."
    "invalid_name" ->
        "That folder name is not allowed."
    "outside_home" ->
        "That path is outside your home folder."
    "push_failed" ->
        "The commit saved locally but push did not finish. Push from your computer when you are ready."
    else ->
        "Something went wrong on your computer. Try again in a moment."
}

fun CoroutineScope.launchRpc(block: suspend () -> Unit) {
    launch { rpcRun { block() } }
}

suspend inline fun <T> rpcRun(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
