OpenDroidPDF — Self‑Hosted F‑Droid Deployment

This project ships updates through a self‑hosted F‑Droid repository. The steps below publish a new version and ensure clients actually see it.

Canonical repo URL
- Use exactly: `https://fdroid.uh-oh.wtf/repo`

Requirements
- `fdroidserver >= 2.2`, `awscli >= 2`, `apksigner` (from Android build-tools)
- Access to the release keystore used for prior versions
- AWS IAM credentials with read/write to the S3 bucket and CloudFront invalidation permissions

Environment (single source)
- Copy `scripts/fdroid.env.sample` to `scripts/fdroid.env` and fill in real paths/secrets.
- The deploy script will source that file and set defaults for buildDir/ABIs if you leave them blank.

```bash
export ANDROID_SDK_ROOT=~/Android/Sdk
export PATH="$ANDROID_SDK_ROOT/build-tools/36.0.0:$PATH"
```

Metadata checkpoint
- Keep `fdroid/metadata/org.opendroidpdf.yml` **and** `fdroid/metadata/org.opendroidpdf.officepack.yml` in sync with every release (versionName/versionCode, summary text, etc.). Treat this repo copy as the canonical truth so scripts and documentation don’t drift.
- The `DEPLOYMENT-FDROID.md` instructions, `fdroid/metadata/`, and any automation scripts should land in the same commit as a release bump to preserve reproducibility.

Release steps
1) Bump version
- Edit `platform/android/gradle.properties`:
 - Increment `opendroidpdf.versionCode` (monotonically) and set `opendroidpdf.versionName`.
 - (Optional, legacy tooling) Keep `platform/android/AndroidManifest.xml` versionName/versionCode in sync.

2) Build, align, sign, and stage in the local F-Droid repo
```bash
 ./scripts/fdroid_build.sh   # uses scripts/fdroid.env for config
```
Output:
- `${ODP_REPO_DIR}/org.opendroidpdf_<versionCode>.apk` (aligned + signed)
- `${ODP_REPO_DIR}/org.opendroidpdf.officepack_<versionCode>.apk` (aligned + signed)
- regenerated index files if `fdroid` is available.

3) Publish to S3 + invalidate CloudFront
```bash
 ./scripts/fdroid_deploy.sh  # uses scripts/fdroid.env for config
```

3a) Changelog
- If you use a separate deploy pipeline, regenerate the changelog in your hosting repo. The legacy `/home/arch/fdroid/scripts/update_and_deploy.sh` still works, but for this repo the canonical configuration lives in `scripts/fdroid.env` and `scripts/fdroid_build.sh`.

3b) Manual publish (fallback)
```bash
mkdir -p ~/fdroid/repo
cp app/build/outputs/apk/release/*.apk ~/fdroid/repo/

# Ensure the APK is signed with the SAME key as prior releases
apksigner verify --print-certs ~/fdroid/repo/*.apk

# Generate/refresh the index files
cd ~/fdroid
fdroid update --create-metadata  # creates index-v1.json, index-v2.json, index.jar
```

4) Publish to S3 (fallback)
```bash
aws s3 sync ~/fdroid/repo "$ODP_S3_BUCKET/repo" --delete --only-show-errors
```

5) Invalidate CloudFront caches (fallback)
```bash
aws cloudfront create-invalidation \
  --distribution-id "$ODP_CF_DIST_MAIN" \
  --paths "/repo/*"

# Only one distribution should serve fdroid.uh-oh.wtf. Do not use any '/fdroid/repo' paths.
```

Verification (server side)
```bash
# Check the repo advertises the new version for OpenDroidPDF + Office Pack
curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.packages["org.opendroidpdf"] | max_by(.versionCode) | "pkg=org.opendroidpdf versionName=\(.versionName) versionCode=\(.versionCode) apk=\(.apkName)"'

curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.packages["org.opendroidpdf.officepack"] | max_by(.versionCode) | "pkg=org.opendroidpdf.officepack versionName=\(.versionName) versionCode=\(.versionCode) apk=\(.apkName)"'

# Verify clients will *recommend* the latest version (Droidify/F-Droid uses suggestedVersionCode).
curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.apps[] | select(.packageName=="org.opendroidpdf") | "pkg=org.opendroidpdf suggestedVersionName=\(.suggestedVersionName) suggestedVersionCode=\(.suggestedVersionCode)"'

curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.apps[] | select(.packageName=="org.opendroidpdf.officepack") | "pkg=org.opendroidpdf.officepack suggestedVersionName=\(.suggestedVersionName) suggestedVersionCode=\(.suggestedVersionCode)"'

# Optional: verify APK signature and checksum
apk=$(curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.packages["org.opendroidpdf"] | max_by(.versionCode) | .apkName')
curl -fsSL -o "/tmp/$apk" "https://fdroid.uh-oh.wtf/repo/$apk"
apksigner verify --print-certs "/tmp/$apk"
sha256sum "/tmp/$apk"

office_apk=$(curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
 | jq -r '.packages["org.opendroidpdf.officepack"] | max_by(.versionCode) | .apkName')
curl -fsSL -o "/tmp/$office_apk" "https://fdroid.uh-oh.wtf/repo/$office_apk"
apksigner verify --print-certs "/tmp/$office_apk"
sha256sum "/tmp/$office_apk"
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
  - Updates exist but are not “recommended” (Droidify doesn’t show them): `suggestedVersionCode` drifted.
    - Fix by regenerating indexes with synced metadata:
      - `./scripts/fdroid_index_refresh.sh`
      - `./scripts/fdroid_deploy.sh`
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
