package sh.simple.android.store

data class HostRecord(
    val id: String,
    val name: String,
    val addresses: List<String>,
    val fingerprint: String,
    val tokenCipher: String,
    val epoch: String?,
    val seq: Long,
)

fun HostEntity.toRecord(): HostRecord = HostRecord(
    id = id,
    name = name,
    addresses = addresses.split(',').filter { it.isNotBlank() },
    fingerprint = fingerprint,
    tokenCipher = tokenCipher,
    epoch = epoch,
    seq = seq,
)

fun HostRecord.toEntity(): HostEntity = HostEntity(
    id = id,
    name = name,
    addresses = addresses.joinToString(","),
    fingerprint = fingerprint,
    tokenCipher = tokenCipher,
    epoch = epoch,
    seq = seq,
)
