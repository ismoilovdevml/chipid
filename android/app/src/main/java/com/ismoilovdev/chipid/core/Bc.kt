package com.ismoilovdev.chipid.core

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * The full BouncyCastle provider. Android ships a stripped "BC" provider without brainpool curves,
 * PACE primitives or explicit-parameter EC keys, so the real one is installed at position 1.
 */
object Bc {
    val provider: BouncyCastleProvider = BouncyCastleProvider()

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(provider, 1)
    }

    fun install() = Unit
}
