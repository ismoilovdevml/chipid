# Security

## Reporting

Please report vulnerabilities privately through GitHub's "Report a vulnerability" (Security tab)
rather than a public issue.

## Threat model and guarantees

- **No network.** The app declares no `INTERNET` permission (library requests are stripped in the
  manifest), so Android denies every socket. Check any build with
  `aapt2 dump permissions app-debug.apk`; the About screen also shows it at runtime.
- **No persistence of personal data.** The access key (MRZ fields or CAN) and the chip data live in
  memory only; the app has no export feature. Backups and device transfer are excluded. Release
  builds set `FLAG_SECURE` (no screenshots or recents thumbnail) and are not debuggable.
- **Trust list integrity.** The bundled master list is accepted only if its CMS signer certificate is
  issued by a pinned German CSCA key (SPKI SHA-256 pins in `core/MasterList.kt`). A list re-signed by
  anyone else contributes no trust anchors. The pins were taken from the BSI list downloaded over
  HTTPS on 2026-11-01; a new German CSCA key requires an app update.
- **User-added anchors.** Certificates placed in the app's `csca/` folder are honoured but marked;
  a document that verifies only through them gets a warning instead of "genuine".
- **Authentic builds.** Official APKs are signed with the key whose certificate SHA-256 is published
  in the README. Verify it with `apksigner verify --print-certs` before installing.
- **What a green result means.** Passive Authentication proves the data was signed by a CSCA in the
  trust list and was not altered. Chip/Active Authentication proves the chip holds a private key that
  was not copied. Together they detect altered data and cloned chips.
- **What it does not do.** No face matching against the holder, no revocation (CRL) checks, no
  database lookups, no EAC terminal authentication (fingerprints/iris stay unread). A green result is
  not an official identity check.

## Responsible use

Only read documents you hold and are allowed to read. Reading requires the document's MRZ or CAN,
which is printed on the document itself.
