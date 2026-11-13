package sh.simple.android.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

fun HostDao.observeForDisplay(): Flow<List<HostEntity>> =
    observeAll().distinctUntilChanged { prev, next ->
        prev.size == next.size && prev.zip(next).all { (a, b) -> a.displaySameAs(b) }
    }

private fun HostEntity.displaySameAs(other: HostEntity): Boolean =
    id == other.id &&
        name == other.name &&
        addresses == other.addresses &&
        fingerprint == other.fingerprint &&
        tokenCipher == other.tokenCipher &&
        epoch == other.epoch

fun <T> Flow<List<T>>.distinctList(): Flow<List<T>> = distinctUntilChanged()
