# OpenDroidPDF Licensing Plan  
for the “OpenDroidPDF” Project

## 1. Base Code License

* **Upstream origin**: The project inherits code from the 2015–2016 Android PDF editor authored by Christian Gogolin, which is published under the GNU Affero General Public License version 3 (AGPL-3.0-or-later).  
* **Project license**: OpenDroidPDF must therefore remain under AGPL-3.0-or-later. Every published binary (APK, bundle, etc.) must provide the full corresponding source code and include the original copyright and license notices.  
* **Copyleft scope**: Any derivative or combined work distributed by this project—or by downstream collaborators—must also be released under AGPL-3.0-or-later.

## 2. MuPDF Dependency

* **Dual license**: MuPDF is available under AGPL-3.0 for open projects, or under a paid commercial license for proprietary distribution. Our upstream Android editor uses the AGPL form, so OpenDroidPDF must do the same.  
* **Open-only stack**: When linked against MuPDF’s AGPL edition, the entire application must consist of open-source components. Closed-source SDKs (e.g. Google Play Services, Crashlytics, AdMob) cannot ship in the same build.  
* **Exception route**: Using MuPDF commercially would require negotiating a commercial license directly with Artifex Software; this plan assumes the free AGPL path.

## 3. Distribution Obligations

* **Source availability**: Host the project in a public VCS (GitHub/GitLab/etc.) and reference that URL from the app (About screen, store listing, README). Refresh the repository for every binary release.  
* **Notice preservation**: Retain upstream headers and attributions in all derived source files and documentation. Add OpenDroidPDF maintainers alongside the original author credit.  
* **Contributor terms**: All incoming patches are accepted under AGPL-3.0-or-later; consider lightweight contributor guidelines (e.g. Developer Certificate of Origin) to confirm intent.

## 4. Dependency Intake Policy

* **Preferred licenses**: MIT, BSD, Apache-2.0, MPL-2.0, LGPL-3.0+, GPL-3.0+, and AGPL-3.0+ are compatible with this project.  
* **Disallowed**: GPL-2.0-only (without “or later”), any proprietary EULAs, or binary-only blobs.  
* **Review process**: Track third-party components in `NOTICE` or `THIRD_PARTY.md` with license texts and source links.

## 5. Font Installer Feature

* **Bundled fonts**: Ship only fonts under open licenses—prefer SIL Open Font License (OFL) or Apache 2.0. Include the license file for every font bundle.  
* **User-supplied fonts**: Allow users to import custom fonts, but display guidance reminding them to respect font licensing.  
* **Embedding**: Open fonts (OFL/Apache) permit embedding into PDFs; ensure MuPDF’s embedding respects each font’s `fsType` flags. Set defaults to open-source font families (e.g. Noto, Liberation, URW core 35).  
* **No proprietary fonts**: Do not distribute or preload fonts with restrictive licenses unless separate permission is secured.

## 6. Compliance Checklist per Release

1. Bump `versionCode` / `versionName` and regenerate release notes.  
2. Build clean release binaries with only approved dependencies.  
3. Publish source snapshot matching the release (tagged commit).  
4. Update LICENSE, NOTICE, and font attribution files if anything changed.  
5. Provide download links and source reference in release announcements/store listings.

## 7. Future Considerations

* **Service integrations**: Use self-hosted or open alternatives for telemetry, crash reporting, sync, etc.  
* **Dual builds**: If proprietary integrations ever become necessary, split the codebase: keep OpenDroidPDF purely AGPL, and negotiate separate licensing for any closed variant.  
* **Ongoing audits**: Periodically review dependency trees (Gradle, NDK, fonts) to ensure no closed-source artifacts slip into releases.

By adhering to this plan, OpenDroidPDF remains fully compliant with upstream AGPL obligations, respects MuPDF’s licensing requirements, and provides clear guidelines for collaborators and distributors.  
