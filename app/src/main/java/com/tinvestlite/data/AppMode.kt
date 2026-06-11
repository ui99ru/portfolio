package com.tinvestlite.data

/** Which T-Invest environment the app currently talks to. */
enum class AppMode {
    /** Virtual money, sandbox endpoints, trading allowed. */
    Sandbox,

    /** Real account data via a read-only token. Trading is disabled. */
    Real,
    ;

    val isReal: Boolean get() = this == Real
}
