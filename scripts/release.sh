#!/usr/bin/env bash
# Release flow (same as Veena):
#   0. Abort unless the working tree is clean (override with ALLOW_DIRTY=1).
#   1. Bump versionCode in app/build.gradle.kts (always +1).
#      Optionally set a new versionName via NAME env var, e.g.
#        NAME=1.1.0 bash scripts/release.sh
#   2. Build signed release APK via Gradle (keystore.properties, gitignored).
#   3. Upload APK to R2 with a versioned key: spendwise-v<name>-<code>.apk.
#   4. Write version.json with the new R2 URL.
#   5. Commit + push.
#
# Run from project root:
#   bash scripts/release.sh                # auto-bump patch: 1.0.0 -> 1.0.1
#   NAME=1.1.0 bash scripts/release.sh     # explicit version name

set -euo pipefail

cd "$(dirname "$0")/.."  # → project root

# --- 0. Refuse to release a dirty tree ------------------------------------
if [[ -z "${ALLOW_DIRTY:-}" ]]; then
  DIRTY=$(git status --porcelain)
  if [[ -n "$DIRTY" ]]; then
    echo "Working tree isn't clean — refusing to release." >&2
    echo "Commit or stash first, or pass ALLOW_DIRTY=1 to override." >&2
    echo "$DIRTY" >&2
    exit 1
  fi
fi

GRADLE_FILE="app/build.gradle.kts"
APK_DIR="app/build/outputs/apk/release"
VERSION_JSON="version.json"
R2_BUCKET="spendwise-releases"
# Requires the custom domain to be bound to the R2 bucket in the Cloudflare
# dashboard (same setup as apk.veena-api.rohithcheryala.dev).
APK_BASE_URL="https://apk.spendwise-api.rohithcheryala.dev"

# --- 1. Bump versions in build.gradle.kts ----------------------------------
read_version_name() {
  grep -E '^[[:space:]]*versionName[[:space:]]*=' "$GRADLE_FILE" | head -n1 \
    | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/'
}
read_version_code() {
  grep -E '^[[:space:]]*versionCode[[:space:]]*=' "$GRADLE_FILE" | head -n1 \
    | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/'
}

CURRENT_CODE=$(read_version_code)
CURRENT_NAME=$(read_version_name)
if [[ -z "${CURRENT_CODE:-}" || -z "${CURRENT_NAME:-}" ]]; then
  echo "Couldn't parse versionName / versionCode from $GRADLE_FILE" >&2
  exit 1
fi

NEW_CODE=$((CURRENT_CODE + 1))

if [[ -n "${NAME:-}" ]]; then
  NEW_NAME="$NAME"
elif [[ "$CURRENT_NAME" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  NEW_NAME="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))"
else
  echo "versionName '${CURRENT_NAME}' isn't in X.Y.Z form — can't auto-bump." >&2
  echo "Pass NAME=X.Y.Z explicitly:  NAME=1.1.0 bash scripts/release.sh" >&2
  exit 1
fi

sed -i.bak -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)([0-9]+)/\1${NEW_CODE}/" "$GRADLE_FILE"
if [[ "$NEW_NAME" != "$CURRENT_NAME" ]]; then
  sed -i.bak -E "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*\")[^\"]+(\".*)/\1${NEW_NAME}\2/" "$GRADLE_FILE"
fi
rm -f "${GRADLE_FILE}.bak"

ACTUAL_CODE=$(read_version_code)
ACTUAL_NAME=$(read_version_name)
if [[ "$ACTUAL_CODE" != "$NEW_CODE" || "$ACTUAL_NAME" != "$NEW_NAME" ]]; then
  echo "build.gradle.kts bump didn't take. Expected ${NEW_NAME}/${NEW_CODE}, got ${ACTUAL_NAME}/${ACTUAL_CODE}." >&2
  exit 1
fi

R2_KEY="spendwise-v${NEW_NAME}-${NEW_CODE}.apk"
R2_URL="${APK_BASE_URL}/${R2_KEY}"

echo "-> Releasing v${NEW_NAME} (code ${CURRENT_CODE} -> ${NEW_CODE})"
echo "  R2 key:  ${R2_KEY}"
echo "  Public:  ${R2_URL}"
echo

# --- 2. Build signed release APK ------------------------------------------
echo "-> Building signed APK"
./gradlew assembleRelease --console=plain
APK_OUT=$(find "$APK_DIR" -name "*-release.apk" -type f 2>/dev/null | head -1)
if [[ -z "$APK_OUT" ]]; then
  echo "No release APK found in $APK_DIR" >&2
  exit 1
fi
APK_BYTES=$(stat -f%z "$APK_OUT" 2>/dev/null || stat -c%s "$APK_OUT")
echo "  APK: ${APK_OUT} (${APK_BYTES} bytes)"

# --- 3. Upload to R2 ------------------------------------------------------
if ! command -v wrangler >/dev/null 2>&1; then
  echo "wrangler not found. Install with: npm i -g wrangler" >&2
  exit 1
fi

echo "-> Uploading to R2 bucket '${R2_BUCKET}' as '${R2_KEY}'"
wrangler r2 object put "${R2_BUCKET}/${R2_KEY}" \
  --file="$APK_OUT" \
  --content-type="application/vnd.android.package-archive" \
  --remote

# --- 4. Write + upload version.json ----------------------------------------
echo "-> Writing $VERSION_JSON"
cat > "$VERSION_JSON" <<EOF
{
  "versionName": "${NEW_NAME}",
  "versionCode": ${NEW_CODE},
  "apkUrl": "${R2_URL}",
  "notes": "",
  "publishedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

echo "-> Uploading version.json to R2"
wrangler r2 object put "${R2_BUCKET}/version.json" \
  --file="$VERSION_JSON" \
  --content-type="application/json" \
  --remote

# --- 5. Commit + push -----------------------------------------------------
git add -A
git commit -m "release v${NEW_NAME} (code ${NEW_CODE})"
echo "-> Pushing to origin"
git push

echo
echo "Done! Released v${NEW_NAME}."
