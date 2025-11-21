# F-Droid Automation – Rebrand Checklist

Current deployment workflow relies on `/home/arch/fdroid/scripts/update_and_deploy.sh` plus the manual steps captured in `DEPLOYMENT-FDROID.md`. Before renaming the app to **OpenDroidPDF**, we need to update the automation to reflect the new package name and branding.

## Touchpoints to Review

1. **Package Identifier**  
   - `DEPLOYMENT-FDROID.md` and all verification snippets currently reference `org.opendroidpdf`.  
   - `update_and_deploy.sh` implicitly syncs whatever APKs live in `repo/`. When the package ID changes, ensure old APKs move to `archive/` and that the deploy script doesn’t republish mixed packages.

2. **Environment Variables**  
   - `.env` file (loaded by the script) should gain OpenDroidPDF-specific variables if bucket/keys/paths change.  
   - Decide whether to rename existing vars (`FDROID_*`) or keep them generic; document expectations.

3. **Repo Paths & Branding Assets**  
   - Script autogenerates `repo/icons/icon.png`. Replace with OpenDroidPDF artwork to avoid placeholder regeneration.  
   - If S3 bucket prefix changes (e.g., `s3://fdroid.opendroidpdf.org/repo`), update `FDROID_AWS_BUCKET`.

4. **CloudFront Invalidation**  
   - Verify `FDROID_AWS_CF_DISTRIBUTION_ID` targets the new domain once DNS/branding moves.

5. **Metadata**  
   - Update `/home/arch/fdroid/metadata/org.opendroidpdf.yml` (or rename file to match new package).  
   - Regenerate changelog entries using the OpenDroidPDF name.

6. **Release Notes & Docs**  
   - Align `DEPLOYMENT-FDROID.md` instructions, README release section, and internal runbooks with the new app name and repo URL.

## Immediate Actions for Phase 0
- Capture current script behaviour (done) and keep snapshot for diffing after edits.  
- Plan renaming steps in tandem with package refactor to avoid broken updates.

## Next Steps
1. Draft new `.env` template including OpenDroidPDF defaults.  
2. Decide on archival strategy for legacy PenAndPDF builds.  
3. Schedule a dry run after package rename to confirm `fdroid update` still generates valid metadata.

