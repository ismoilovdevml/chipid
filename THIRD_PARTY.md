# Third-party components

ChipID's own code is MIT-licensed (see `LICENSE`).

## Android (`android/`)

Fetched from Maven by Gradle unless noted.

| Component | Use | License |
|---|---|---|
| [JMRTD](https://jmrtd.org/) 0.8.9 | ICAO 9303 LDS parsing, BAC/PACE, CA, AA | LGPL 3.0 |
| [SCUBA](https://sourceforge.net/projects/scuba/) 0.0.27 | Smart-card I/O over Android `IsoDep` | LGPL 3.0 |
| [Bouncy Castle](https://www.bouncycastle.org/) 1.86 | Crypto provider, CMS, X.509 | MIT-style (Bouncy Castle License) |
| [EJBCA cert-cvc](https://www.ejbca.org/) 1.4.13 | Card-verifiable certificates (JMRTD dependency) | LGPL 2.1 |
| [AndroidX / Jetpack Compose / CameraX](https://developer.android.com/jetpack) | UI, camera | Apache 2.0 |
| [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2) 16.0.1 | On-device MRZ OCR (bundled model) | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) (proprietary) |
| [Inter](https://github.com/rsms/inter) | UI typeface, bundled in `res/font` (Android) and `ios/ChipID/Resources/Fonts` | SIL OFL 1.1 (`android/app/src/main/assets/licenses/OFL-Inter.txt`) |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | Monospace typeface, bundled in `res/font` (Android) and `ios/ChipID/Resources/Fonts` | SIL OFL 1.1 (`android/app/src/main/assets/licenses/OFL-JetBrainsMono.txt`) |
| German CSCA master list | Trust anchors for Passive Authentication | Published by the German Federal Office for Information Security (BSI). Not redistributed here: downloaded at build time by `fetchMasterList` |

LGPL libraries are linked as unmodified, separately replaceable Maven artifacts; the full source of
this app is available, so the application can be rebuilt against modified versions of them.

Because ML Kit is proprietary, this app as built is not fully free software (for example it would
not qualify for F-Droid's main repository without replacing the OCR engine).

## iOS (`ios/`)

| Component | Use | License |
|---|---|---|
| [NFCPassportReader](https://github.com/AndyQ/NFCPassportReader) 2.3.3 | APDU transport, secure messaging, BAC, PACE, Chip Authentication. Vendored with changes listed in `ios/Vendor/NFCPassportReader/CHANGES.md` | MIT (`ios/Vendor/NFCPassportReader/LICENSE`) |
| [OpenSSL](https://www.openssl.org/) 3.6 via [OpenSSL-Package](https://github.com/krzyzanowskim/OpenSSL-Package) | X.509, CMS, RSA/EC, digests (ChipCore and NFCPassportReader), fetched by Swift Package Manager | Apache 2.0 |
| Apple Vision, AVFoundation, Core NFC | On-device MRZ OCR, camera, NFC | Part of iOS |
| Inter, JetBrains Mono, German CSCA master list | As above (the iOS build fetches the list with `ios/scripts/fetch-masterlist.sh`) | As above |
