package sh.simple.android.ui.usage

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import sh.simple.android.model.WireJson
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.simple.android.link.Hosts
import sh.simple.android.link.RemoteGateway
import sh.simple.android.link.rpcErrorMessage
import sh.simple.android.model.UsageBucket
import sh.simple.android.model.UsageDayRow
import sh.simple.android.model.UsageSummaryResult
import sh.simple.android.ui.glass.GlassStage
import sh.simple.android.ui.kit.AppText
import sh.simple.android.ui.kit.EmptyState
import sh.simple.android.ui.kit.ErrorState
import sh.simple.android.ui.kit.HeaderBar
import sh.simple.android.ui.kit.LoadingRow
import sh.simple.android.ui.kit.Icons
import sh.simple.android.ui.kit.ListRow
import sh.simple.android.ui.kit.SectionLabel
import sh.simple.android.ui.kit.listScrollBottomPadding
import sh.simple.android.ui.util.agentKindIcon
import sh.simple.android.ui.util.agentKindLabel
import sh.simple.android.ui.theme.Tokens
import sh.simple.android.ui.theme.Type

@Composable
fun UsageScreen(
    hosts: Hosts,
    hostId: String,
    hostName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var summary by remember { mutableStateOf<UsageSummaryResult?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(hostId) {
        error = ""
        summary = withContext(Dispatchers.IO) { readCachedUsage(context, hostId) }
        loading = summary == null
        runCatching {
            withContext(Dispatchers.IO) { RemoteGateway.usageSummary(hosts, hostId, 7) }
        }.onSuccess {
            summary = it
            loading = false
            withContext(Dispatchers.IO) { writeCachedUsage(context, hostId, it) }
        }.onFailure {
            if (summary == null) error = rpcErrorMessage(it)
            loading = false
        }
    }
    val data = summary
    val hasUsage = data?.agents?.any { it.has_data } == true
    GlassStage(modifier.fillMaxSize().background(Tokens.current.background)) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(listOf(Icons.Back), "Usage", emptyList(), subtitle = hostName, onLeftClick = { _ -> onBack() })
            when {
                loading -> LoadingRow()
                error.isNotEmpty() -> ErrorState(error, onRetry = null)
                data == null -> Unit
                !hasUsage -> EmptyState(
                    Icons.Bolt,
                    "No usage yet",
                    "Claude and Codex sessions report tokens here.",
                )
                else -> UsageBody(data)
            }
        }
    }
}

@Composable
private fun UsageBody(data: UsageSummaryResult) {
    LazyColumn(contentPadding = listScrollBottomPadding(), modifier = Modifier.fillMaxSize()) {
        item {
            SectionLabel("Today", Modifier.padding(start = 20.dp, top = 8.dp))
            UsageTotalsCard(data.today, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SectionLabel("Last ${data.days} days", Modifier.padding(start = 20.dp, top = 8.dp))
            UsageTotalsCard(data.period, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SectionLabel("Daily", Modifier.padding(start = 20.dp, top = 8.dp))
            DailyBars(data.daily, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        item { SectionLabel("Agents", Modifier.padding(start = 20.dp, top = 8.dp)) }
        items(data.agents.filter { it.has_data }, key = { it.id }) { row ->
            val t = row.total
            ListRow(
                agentKindIcon(row.kind),
                row.title,
                secondary = agentKindLabel(row.kind),
                trailing = "In ${formatTokens(t.input)} · out ${formatTokens(t.output)}",
                chevron = false,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun UsageTotalsCard(bucket: UsageBucket, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.current.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UsageStatLine("Input", bucket.input)
        UsageStatLine("Output", bucket.output)
        UsageStatLine("Cache read", bucket.cache_read)
        UsageStatLine("Cache write", bucket.cache_write)
        bucket.cost_usd?.let { UsageStatLine("Cost", it, money = true) }
    }
}

@Composable
private fun UsageStatLine(label: String, value: Any, money: Boolean = false) {
    val text = when {
        money && value is Double -> String.format("$%.4f", value)
        value is Long -> formatTokens(value)
        else -> value.toString()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppText(label, style = Type.meta, color = Tokens.current.muted)
        AppText(text, style = Type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

@Composable
private fun DailyBars(daily: List<UsageDayRow>, modifier: Modifier = Modifier) {
    val maxVal = daily.maxOfOrNull { it.input + it.output }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        daily.forEach { day ->
            val total = day.input + day.output
            val h = ((total.toFloat() / maxVal.toFloat()) * 72f).coerceAtLeast(4f).dp
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .width(12.dp)
                        .height(h)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Tokens.current.add),
                )
                AppText(
                    day.day.takeLast(5),
                    Modifier.padding(top = 4.dp),
                    style = Type.caption,
                    color = Tokens.current.faint,
                    maxLines = 1,
                )
            }
        }
    }
}

fun formatTokens(n: Long): String {
    if (n < 1000) return n.toString()
    if (n < 1_000_000) return String.format("%.1fk", n / 1000.0)
    if (n < 1_000_000_000) return String.format("%.1fM", n / 1_000_000.0)
    return String.format("%.1fB", n / 1_000_000_000.0)
}

private const val USAGE_CACHE = "usage_cache"

private fun readCachedUsage(context: Context, hostId: String): UsageSummaryResult? =
    runCatching {
        context.getSharedPreferences(USAGE_CACHE, Context.MODE_PRIVATE).getString(hostId, null)
            ?.let { WireJson.decodeFromString<UsageSummaryResult>(it) }
    }.getOrNull()

private fun writeCachedUsage(context: Context, hostId: String, summary: UsageSummaryResult) {
    runCatching {
        context.getSharedPreferences(USAGE_CACHE, Context.MODE_PRIVATE).edit()
            .putString(hostId, WireJson.encodeToString(summary)).apply()
    }
}
