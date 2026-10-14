#!/usr/bin/env bash
# Package each ABI-split release APK (and the universal APK) into its OWN
# release archive, mirroring the per-architecture artifacts uploaded by CI.
#
# Usage:
#   ci/package_release_archives.sh <apk-output-dir> <archive-output-dir>
#
# Example:
#   ci/package_release_archives.sh app/build/outputs/apk/release release-artifacts
#
# Produces in <archive-output-dir>:
#   Audio-Transcriber-x64-release.zip        (arm64-v8a APK)
#   Audio-Transcriber-32-bit-release.zip     (armeabi-v7a APK)
#   Audio-Transcriber-universal-release.zip  (universal APK, all ABIs)
#   SHA256SUMS.txt                           (sha256 of every archive above)
#
# The release APKs are UNSIGNED (no signingConfig in app/build.gradle.kts), so
# the files produced by `assembleRelease` are named e.g.
# app-arm64-v8a-release-unsigned.apk. Lookup is by ABI token in the file name,
# so signed/local names without the "-unsigned" suffix are accepted too.
# Any missing APK is a hard error: CI must never publish a partial set.
set -euo pipefail

APK_DIR="${1:-app/build/outputs/apk/release}"
OUT_DIR="${2:-release-artifacts}"

fail() {
  echo "::error title=Release packaging failed::$*" >&2
  echo "ERROR: $*" >&2
  exit 1
}

[ -d "$APK_DIR" ] || fail "APK output directory not found: $APK_DIR (did assembleRelease run?)"

mkdir -p "$OUT_DIR"

# Print every APK present to make CI logs self-explanatory, then resolve one
# APK per architecture by the ABI token AGP embeds in split output names.
echo "=== APKs found in $APK_DIR ==="
find "$APK_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort || true

find_apk() {
  # $1 = ABI token that must appear in the file name (e.g. arm64-v8a)
  local token="$1" match
  match="$(find "$APK_DIR" -maxdepth 1 -type f -name "*${token}*.apk" -print | sort | head -n 1)"
  [ -n "$match" ] || return 1
  printf '%s\n' "$match"
}

ARM64_APK="$(find_apk "arm64-v8a")" || fail "No arm64-v8a release APK found in $APK_DIR"
ARM32_APK="$(find_apk "armeabi-v7a")" || fail "No armeabi-v7a release APK found in $APK_DIR"
UNIVERSAL_APK="$(find_apk "universal")" || fail "No universal release APK found in $APK_DIR (isUniversalApk must be true)"

echo "arm64-v8a   -> $ARM64_APK"
echo "armeabi-v7a -> $ARM32_APK"
echo "universal   -> $UNIVERSAL_APK"

make_archive() {
  # $1 = source APK, $2 = archive file name (created inside $OUT_DIR)
  local apk="$1" archive="$2"
  local dest="$OUT_DIR/$archive"
  rm -f "$dest"
  if command -v zip >/dev/null 2>&1; then
    # -j: store just the APK, no directory prefix. APKs are already
    # compressed, so this is effectively a labelled container.
    zip -j -9 "$dest" "$apk" >/dev/null
  else
    # Graceful fallback for minimal environments without `zip`.
    python3 - "$apk" "$dest" <<'PY'
import sys, zipfile
src, dest = sys.argv[1], sys.argv[2]
name = src.replace("\\", "/").rsplit("/", 1)[-1]
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.write(src, arcname=name)
PY
  fi
  [ -s "$dest" ] || fail "Failed to create archive: $dest"
  echo "Created $dest"
}

make_archive "$ARM64_APK" "Audio-Transcriber-x64-release.zip"
make_archive "$ARM32_APK" "Audio-Transcriber-32-bit-release.zip"
make_archive "$UNIVERSAL_APK" "Audio-Transcriber-universal-release.zip"

# Checksums of the archives (not the loose APKs) so anyone can verify a
# downloaded artifact against the CI job summary.
(
  cd "$OUT_DIR"
  rm -f SHA256SUMS.txt
  sha256sum \
    Audio-Transcriber-x64-release.zip \
    Audio-Transcriber-32-bit-release.zip \
    Audio-Transcriber-universal-release.zip > SHA256SUMS.txt
)

echo "=== Release archives in $OUT_DIR ==="
ls -lh "$OUT_DIR"
cat "$OUT_DIR/SHA256SUMS.txt"
