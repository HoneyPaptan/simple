package sh.simple.android.store

suspend fun AgentDao.upsertChanged(rows: List<AgentEntity>) {
    val changed = rows.filter { row ->
        val existing = get(row.hostId, row.id) ?: return@filter true
        existing.json != row.json
    }
    if (changed.isNotEmpty()) upsertAll(changed)
}

suspend fun ItemDao.upsertChanged(rows: List<ItemEntity>) {
    val changed = rows.filter { row ->
        val existing = get(row.hostId, row.id) ?: return@filter true
        existing.json != row.json
    }
    if (changed.isNotEmpty()) upsertAll(changed)
}

suspend fun WorkspaceDao.upsertChanged(rows: List<WorkspaceEntity>) {
    val changed = rows.filter { row ->
        val existing = get(row.hostId, row.path) ?: return@filter true
        existing.json != row.json
    }
    if (changed.isNotEmpty()) upsertAll(changed)
}

suspend fun MessageDao.upsertChanged(rows: List<MessageEntity>) {
    val changed = rows.filter { row ->
        val existing = get(row.hostId, row.agentId, row.id) ?: return@filter true
        existing.json != row.json
    }
    if (changed.isNotEmpty()) upsertAll(changed)
}
