OpenDroidPDF — Self‑Hosted F‑Droid Deployment

This project ships updates through a self‑hosted F‑Droid repository. The steps below publish a new version and ensure clients actually see it.

Canonical repo URL
- Use exactly: `https://fdroid.uh-oh.wtf/repo`

Requirements
- `fdroidserver >= 2.2`, `awscli >= 2`, `apksigner` (from Android build-tools)
- Access to the release keystore used for prior versions
- AWS IAM credentials with read/write to the S3 bucket and CloudFront invalidation permissions

Environment
```bash
# Example; adjust to your environment
export ANDROID_SDK_ROOT=~/Android/Sdk
export PATH="$ANDROID_SDK_ROOT/build-tools/36.0.0:$PATH"

# Keystore (must match already-installed app signature!)
export ODP_KEYSTORE=~/fdroid/release.keystore
export ODP_KEY_ALIAS=uh-oh-fdroid
export ODP_KEY_PASS=changeit

# Hosting
export ODP_S3_BUCKET=s3://fdroid-uh-oh-wtf
export ODP_CF_DIST_MAIN=E1234567890ABC   # CloudFront dist for fdroid.uh-oh.wtf
export ODP_CF_DIST_ALIAS=                # leave empty (no alias host)
```

Metadata checkpoint
- Keep `fdroid/metadata/org.opendroidpdf.yml` in sync with every release (versionName/versionCode, summary text, etc.). Treat this repo copy as the canonical truth so scripts and documentation don’t drift.
- The `DEPLOYMENT-FDROID.md` instructions, `fdroid/metadata/`, and any automation scripts should land in the same commit as a release bump to preserve reproducibility.

Release steps
1) Bump version
- Edit `platform/android/build.gradle` and `AndroidManifest.xml`:
 - Increment `versionCode` (monotonically) and set `versionName`.

2) Build the release APK
```bash
 cd platform/android
  ./gradlew clean assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk` (path may vary if you’ve customized).

3a) Changelog (auto-generated when using `scripts/update_and_deploy.sh`)
- The deploy script now writes `site/changelog/org.opendroidpdf.txt` using the latest
  `versionName`/`versionCode` from `platform/android/build.gradle` and the 15 most recent
  git commit subjects. If you deploy manually, re-run the script or regenerate the
  file similarly so the `Changelog:` URL in metadata stays fresh.

3) Stage in local repo and sign
```bash
mkdir -p ~/fdroid/repo
cp app/build/outputs/apk/release/*.apk ~/fdroid/repo/

# Ensure the APK is signed with the SAME key as prior releases
apksigner verify --print-certs ~/fdroid/repo/*.apk

# Generate/refresh the index files
cd ~/fdroid
fdroid update --create-metadata  # creates index-v1.json, index-v2.json, index.jar
```

4) Publish to S3
```bash
aws s3 sync ~/fdroid/repo "$ODP_S3_BUCKET/repo" --delete --only-show-errors
```

5) Invalidate CloudFront caches
```bash
aws cloudfront create-invalidation \
  --distribution-id "$ODP_CF_DIST_MAIN" \
  --paths "/repo/*"

# Only one distribution should serve fdroid.uh-oh.wtf. Do not use any '/fdroid/repo' paths.
```

Verification (server side)
```bash
# Check the repo advertises the new version for org.opendroidpdf
curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.packages["org.opendroidpdf"] | max_by(.versionCode) | "versionName=\(.versionName) versionCode=\(.versionCode) apk=\(.apkName)"'

# Optional: verify APK signature and checksum
apk=$(curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.packages["org.opendroidpdf"] | max_by(.versionCode) | .apkName')
curl -fsSL -o "/tmp/$apk" "https://fdroid.uh-oh.wtf/repo/$apk"
apksigner verify --print-certs "/tmp/$apk"
sha256sum "/tmp/$apk"
```

Verification (client side)
- In the F‑Droid app: Settings → Repositories → ensure the repo URL is `https://fdroid.uh-oh.wtf/repo`.
- Pull‑to‑refresh on the “Updates” tab, or tap the three‑dot menu → “Refresh”.
- Open OpenDroidPDF’s app page → “Versions”. The top entry should show the new `versionName (versionCode)`.

Troubleshooting
- Still seeing 1.3.12?
  - Repo URL mismatch: Some devices were pointed at the alias `fdroid.uhoh.wtf` (which may fail TLS or serve stale cache). Switch to `https://fdroid.uh-oh.wtf/repo`.
  - Client cache: F‑Droid caches aggressively. Force refresh, or clear cache for the F‑Droid app.
  - CDN cache: Make sure you invalidated CloudFront for `/repo/*` on all distributions serving these hostnames.
  - Signature mismatch: F‑Droid only updates if the installed app is signed with the SAME key as the repo APK.
    * Compare fingerprints:
      ```bash
      # Repo APK
      apksigner verify --print-certs "/tmp/$apk"

      # Installed app on a connected device
      adb shell pm path org.opendroidpdf | sed 's/package://;q' | \
        xargs -I{} adb pull {} /tmp/installed.apk
      apksigner verify --print-certs /tmp/installed.apk
      ```
      If fingerprints differ, uninstall the old app and install from this repo (or re‑sign with the original key).
- 404 on `.../repo/` but files load: S3 returns 404 for the “directory” URL; fetching `index-v1.json`/`index.jar` is the correct check.

Quick checklist for every release
- [ ] Bump `versionCode` and `versionName`
- [ ] Build release APK
- [ ] Sign with the same keystore
- [ ] Run `fdroid update`
- [ ] `aws s3 sync` to `s3://<bucket>/repo`
- [ ] CloudFront invalidation(s) for `/repo/*`
- [ ] Server and client verification
