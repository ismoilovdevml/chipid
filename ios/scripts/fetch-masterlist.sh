#!/bin/sh
# Puts the German BSI CSCA master list at ChipID/Resources/masterlist.ml if it is missing.
# Not committed: the BSI publishes it without stated redistribution terms (same as the Android build).
set -eu
here="$(cd "$(dirname "$0")/.." && pwd)"
target="$here/ChipID/Resources/masterlist.ml"
[ -s "$target" ] && exit 0
android="$here/../android/app/src/main/assets/masterlist.ml"
if [ -s "$android" ]; then
  cp "$android" "$target"
  echo "Copied master list from the Android assets"
  exit 0
fi
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL -o "$tmp/ml.zip" "https://www.bsi.bund.de/SharedDocs/Downloads/DE/BSI/ElekAusweise/CSCA/GermanMasterList.zip?__blob=publicationFile"
entry="$(unzip -Z1 "$tmp/ml.zip" | grep '\.ml$' | head -1)"
[ -n "$entry" ] || { echo "error: GermanMasterList.zip contains no .ml file" >&2; exit 1; }
unzip -p "$tmp/ml.zip" "$entry" > "$target"
echo "Fetched $entry ($(wc -c < "$target") bytes)"
