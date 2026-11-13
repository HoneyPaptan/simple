package sh.simple.android.store

import sh.simple.android.link.PairResult

suspend fun savePairedHost(db: SimpleDatabase, vault: TokenVault, result: PairResult) {
    db.hosts().upsert(
        HostEntity(
            id = result.hostId,
            name = result.hostName,
            addresses = result.addresses.joinToString(","),
            fingerprint = result.fingerprint,
            tokenCipher = vault.encrypt(result.token),
            epoch = null,
            seq = 0,
        ),
    )
}
