# ChipID changes to NFCPassportReader

Vendored from https://github.com/AndyQ/NFCPassportReader at 2.3.3 (commit 6e37f1a, 2026-07-28), MIT licence (LICENSE).

- `ChipSession.swift` (new): reading order of the ChipID Android app, returns raw files; verification lives in ChipCore.
- `PACEHandler.swift`: PACE with a card access number (`doPACE(can:)`, key reference 0x02, CAN used as the password without hashing).
- `Logging.swift`: every logger disabled. Upstream logs MRZ keys, session keys and APDUs at debug level.
- `TagReader.swift`: a malformed file header throws instead of crashing (`try!` removed).
- `Package.swift`: test target removed (tests are not vendored).
