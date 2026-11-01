<p align="center"><img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="96" alt="ChipID"></p>

<h1 align="center">ChipID</h1>

<p align="center">Read the chip in a passport or ID card with an Android phone or an iPhone, and verify that the data and the chip are genuine. Fully offline.</p>

---

ChipID reads ICAO 9303 eMRTDs (biometric passports and ID cards from 100+ countries) over NFC. It
scans the MRZ with the camera, opens the chip, shows the holder data and the photo stored on the
chip, and runs the same cryptographic checks a border e-gate runs on the document.

## Features

- **MRZ scanning** with the camera, on-device OCR. Passports (TD3) and ID cards (TD1). A scan is
  accepted only when every ICAO check digit verifies and two consecutive frames agree.
- **Access control:** PACE (MRZ or 6-digit CAN), falling back to BAC.
- **Reads** EF.SOD and every data group it lists: DG1 (MRZ data), DG2 (face image), DG11/DG12
  (extra holder and document details), DG14, DG15. DG3/DG4 (fingerprints, iris) are never requested.
- **Verification**

  | Check | Standard | Proves |
  |---|---|---|
  | Passive Authentication | ICAO 9303-11 §5.1 | Data group hashes match the signed SOD; SOD signed by the Document Signer; DSC signed by the issuing country's CSCA. The data is the state's and unaltered |
  | Active Authentication | ICAO 9303-11 §6.1 | The chip signs a fresh challenge with the DG15 key (RSA ISO 9796-2 or plain ECDSA). Not a clone |
  | Chip Authentication | ICAO 9303-11 §6.2, BSI TR-03110 | (EC)DH with the DG14 key, then a re-read under the new session keys. Not a clone |

- **Trust list:** the German BSI CSCA master list (600+ certificates, 117 countries), fetched at build
  time. On the phone its signer must chain to a pinned German CSCA key, otherwise the list is
  rejected. Extra anchors can be added at runtime; a document verified only through one of them is
  flagged, never shown as plainly genuine.

## Repository layout

| Path | What |
|---|---|
| `android/` | Android app (Kotlin, Jetpack Compose, JMRTD), Gradle project |
| `ios/` | iOS app (Swift, SwiftUI); `ios/ChipCore` is its platform-independent verification core |
| `ios/Vendor/NFCPassportReader` | Vendored NFC/secure-messaging library with ChipID changes ([CHANGES.md](ios/Vendor/NFCPassportReader/CHANGES.md)) |

## Install (Android)

Download `chipid-<version>.apk` from [Releases](https://github.com/ismoilovdevml/chipid/releases) and
check that it is the official build before installing. Anyone can rebuild an open-source app, add
code that leaks data and publish it under the same name; the signing certificate cannot be faked.

Official signing certificate SHA-256:

```
D5:3F:C6:E3:5A:9E:31:91:B8:9C:7A:D3:04:92:2A:7D:AB:1F:F5:58:13:B6:05:8A:A0:03:35:F5:2C:1A:02:3A
```

```bash
apksigner verify --print-certs chipid-<version>.apk | grep SHA-256
shasum -a 256 chipid-<version>.apk        # compare with the release notes
```

Android also refuses to update an installed ChipID with an APK signed by a different key.

## Privacy

- **No network access.** The app has no `INTERNET` permission (libraries that ask for it are
  overridden in the manifest), so Android blocks every connection. Verify any build with
  `aapt2 dump permissions app-debug.apk`: only `NFC` and `CAMERA`.
- Camera frames are recognised on the phone and never stored.
- The access key and the chip data stay in memory only; nothing is written to storage. Release
  builds block screenshots and are not debuggable. Excluded from backups.

See [SECURITY.md](SECURITY.md) for the threat model and what a green result does and does not mean.

## Build: Android

Requirements: JDK 17+, Android SDK (compileSdk 36), an NFC phone (Android 8+).

```bash
cd android
./gradlew :app:testDebugUnitTest   # JVM tests: PA chain, AA (RSA ISO 9796-2, ECDSA), MRZ TD1/TD3, master list
./gradlew :app:assembleDebug       # first build downloads the BSI master list into assets/
./gradlew :app:assembleRelease     # signed only if ~/.config/chipid/keystore.properties exists
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Add trust anchors without rebuilding by copying `.ml`, `.der`, `.cer`, `.crt` or `.pem` files to
`/sdcard/Android/data/com.ismoilovdev.chipid/files/csca/`.

## Build: iOS

Requirements: Xcode 26+, [XcodeGen](https://github.com/yonaskolb/XcodeGen), an iPhone XS or newer.
Reading chips needs the *NFC Tag Reading* capability, which Apple grants only to paid
Apple Developer Program teams; a free personal team can build and run the app, but not read chips.

```bash
cd ios
swift test --package-path ChipCore   # core tests on macOS: PA/AA against BouncyCastle-made vectors, BSI master list, MRZ, LDS
xcodegen generate                     # creates ChipID.xcodeproj (not committed)
open ChipID.xcodeproj                 # set your team, run on an iPhone
```

The first build copies the master list from `android/app/src/main/assets/` or downloads it from the
BSI (`ios/scripts/fetch-masterlist.sh`). Debug and simulator builds have a "Try a demo document"
button that opens the ICAO specimen signed with throwaway keys, so the result screens can be seen
without a chip. Extra trust anchors go in Files → On My iPhone → ChipID → csca.

**Status:** the iOS app builds and its core is tested on macOS, but it has not yet read a real
document on an iPhone.

## How the Android app is built

| Path (`android/app/src/main/java/.../chipid/`) | What |
|---|---|
| `core/Mrz.kt` | MRZ finder and parser with per-field OCR repair and check-digit validation |
| `core/PassiveAuth.kt` | SOD hash check, CMS signature, DSC → CSCA chain |
| `core/ActiveAuth.kt` | AA signature verification (ISO 9796-2 scheme 1, plain ECDSA) |
| `core/MasterList.kt` | ICAO CSCA master list parser and signature check |
| `reader/ChipReader.kt` | NFC session: PACE/BAC, file reads, AA, CA, progress |
| `scan/MrzAnalyzer.kt` | CameraX frames → on-device OCR → MRZ |
| `ui/` | Jetpack Compose screens |

Stack: Kotlin, Jetpack Compose, CameraX, ML Kit text recognition (bundled), JMRTD, SCUBA, Bouncy
Castle (installed as provider #1 because Android's stripped "BC" lacks brainpool curves and
explicit EC parameters).

### Notes from real devices

- On Samsung phones, opening the camera stops NFC polling, and closing it restarts the system's
  default discovery, which silently drops an app's reader mode. ChipID re-arms reader mode when the
  camera is released.

## How the iOS app is built

| Path (`ios/`) | What |
|---|---|
| `ChipCore/` | Swift package on OpenSSL: MRZ, LDS (DG1/2/11/12), master list with pinned signer, Passive and Active Authentication, report model. Same rules and pins as the Android core |
| `Vendor/NFCPassportReader/.../ChipSession.swift` | NFC session in the Android app's order: PACE (MRZ or CAN) / BAC, SOD, data groups, AA, CA + DG1 re-read |
| `ChipID/` | SwiftUI screens, camera MRZ scanning with Vision, trust store |

## Limits

- Android: JPEG 2000 face images are not decoded on the phone yet (the photo is still hash-verified).
  iOS decodes them with ImageIO.
- iOS: PACE Integrated Mapping is not supported by the vendored library (Generic Mapping is).
- No face matching, no CRL/revocation checks, no EAC terminal authentication, no PACE-CAM.

## Disclaimer

ChipID is an independent project, not affiliated with ICAO, the BSI or any issuing state. Use it only
with documents you hold and are allowed to read. A green result is a technical check of the chip,
not an official identity verification.

## License

MIT, see [LICENSE](LICENSE). Third-party components and their licenses: [THIRD_PARTY.md](THIRD_PARTY.md).
