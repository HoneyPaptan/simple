package sh.simple.android.ui.chat

data class ChatTurn(
    val key: String,
    val user: ChatBlock.User?,
    val body: List<ChatBlock>,
    val live: Boolean,
)

fun splitChatTurns(blocks: List<ChatBlock>, working: Boolean): List<ChatTurn> {
    val turns = ArrayList<ChatTurn>()
    var user: ChatBlock.User? = null
    val body = ArrayList<ChatBlock>()
    fun emit(live: Boolean) {
        if (user == null && body.isEmpty()) return
        val key = user?.key ?: body.firstOrNull()?.key ?: "turn:${turns.size}"
        turns += ChatTurn(key, user, body.toList(), live)
        body.clear()
    }
    for (block in blocks) {
        if (block is ChatBlock.User) {
            emit(live = false)
            user = block
        } else {
            body += block
        }
    }
    emit(live = working)
    return turns
}

fun turnHasAgentWork(body: List<ChatBlock>): Boolean =
    body.any { it is ChatBlock.Tools || it is ChatBlock.Thinking }

sealed interface TurnRow {
    val key: String
    val topGap: Int

    data class User(override val key: String, override val topGap: Int, val block: ChatBlock.User) : TurnRow
    data class Header(override val key: String, override val topGap: Int) : TurnRow
    data class Body(override val key: String, override val topGap: Int, val block: ChatBlock) : TurnRow
    data class Worked(override val key: String, override val topGap: Int, val turnKey: String, val label: String, val open: Boolean) : TurnRow
    data class Files(override val key: String, override val topGap: Int, val files: List<TurnFileChange>) : TurnRow
}

data class TurnSummary(val worked: String, val files: List<TurnFileChange>)

fun summarizeTurn(turn: ChatTurn): TurnSummary {
    val tools = turnToolEntries(turn.body)
    return TurnSummary(
        workedPillLabel(turnStepCount(turn.body), turnWorkDurationMs(tools)),
        turnFileChanges(tools),
    )
}

const val TurnGapDp = 20
const val TurnHeaderGapDp = 12
const val TurnBlockGapDp = 8
const val AfterUserGapDp = 8

fun chatTurnRows(
    turns: List<ChatTurn>,
    summaries: Map<String, TurnSummary>,
    openTurns: Set<String>,
): List<TurnRow> {
    val rows = ArrayList<TurnRow>()
    turns.forEachIndexed { index, turn ->
        val turnTop = if (index > 0) TurnGapDp else 0
        turn.user?.let { rows += TurnRow.User(it.key, turnTop, it) }
        val hasWork = turnHasAgentWork(turn.body)
        val showHeader = hasWork || turn.body.any { it is ChatBlock.Assistant }
        if (showHeader) {
            val top = if (turn.user != null) AfterUserGapDp else turnTop
            rows += TurnRow.Header("${turn.key}:header", top)
        }
        var nextTop = when {
            showHeader -> TurnHeaderGapDp
            turn.user != null -> AfterUserGapDp
            else -> turnTop
        }
        fun add(row: (Int) -> TurnRow) {
            rows += row(nextTop)
            nextTop = TurnBlockGapDp
        }
        if (turn.live || !hasWork) {
            turn.body.forEach { block -> add { TurnRow.Body(block.key, it, block) } }
        } else {
            val summary = summaries[turn.key] ?: summarizeTurn(turn)
            val open = turn.key in openTurns
            add { TurnRow.Worked("${turn.key}:worked", it, turn.key, summary.worked, open) }
            if (open) {
                turn.body.filter { it is ChatBlock.Tools || it is ChatBlock.Thinking }
                    .forEach { block -> add { TurnRow.Body(block.key, it, block) } }
            }
            if (summary.files.isNotEmpty()) {
                add { TurnRow.Files("${turn.key}:files", it, summary.files) }
            }
            turn.body.filter { it is ChatBlock.Assistant || it is ChatBlock.Marker }
                .forEach { block -> add { TurnRow.Body(block.key, it, block) } }
        }
    }
    return rows
}
