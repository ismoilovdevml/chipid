# Security

## Reporting

Please report vulnerabilities privately through GitHub's "Report a vulnerability" (Security tab)
rather than a public issue.

## Threat model and guarantees

- **No network.** The app declares no `INTERNET` permission (library requests are stripped in the
  manifest), so Android denies every socket. Check any build with
  `aapt2 dump permissions app-debug.apk`; the About screen also shows it at runtime.
- **No persistence of personal data.** The access key (MRZ fields or CAN) and the chip data live in
  memory. Files are written only when the user taps "Export raw files", to the app's private external
  directory, and "Delete exported files" removes them. Backups and device transfer are excluded.
- **What a green result means.** Passive Authentication proves the data was signed by a CSCA in the
  trust list and was not altered. Chip/Active Authentication proves the chip holds a private key that
  was not copied. Together they detect altered data and cloned chips.
- **What it does not do.** No face matching against the holder, no revocation (CRL) checks, no
  database lookups, no EAC terminal authentication (fingerprints/iris stay unread). A green result is
  not an official identity check.

## Responsible use

Only read documents you hold and are allowed to read. Reading requires the document's MRZ or CAN,
which is printed on the document itself.
