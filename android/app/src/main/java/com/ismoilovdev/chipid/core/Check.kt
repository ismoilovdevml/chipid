package com.ismoilovdev.chipid.core

/** Outcome of one security check. UNKNOWN means it could not be decided (e.g. no trust anchor). */
enum class Check { PASS, FAIL, UNKNOWN, SKIPPED }

/** Worst-of combination used for overall verdicts: FAIL > UNKNOWN > PASS. SKIPPED is neutral. */
fun combine(vararg checks: Check): Check = when {
    checks.any { it == Check.FAIL } -> Check.FAIL
    checks.any { it == Check.UNKNOWN } -> Check.UNKNOWN
    checks.any { it == Check.PASS } -> Check.PASS
    else -> Check.SKIPPED
}
