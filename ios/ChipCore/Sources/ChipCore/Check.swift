/// Outcome of one security check. `unknown` means it could not be decided (e.g. no trust anchor).
public enum Check: String, Sendable {
    case pass = "PASS", fail = "FAIL", unknown = "UNKNOWN", skipped = "SKIPPED"
}

/// Worst-of combination used for overall verdicts: fail > unknown > pass. `skipped` is neutral.
public func combine(_ checks: Check...) -> Check {
    if checks.contains(.fail) { return .fail }
    if checks.contains(.unknown) { return .unknown }
    if checks.contains(.pass) { return .pass }
    return .skipped
}
