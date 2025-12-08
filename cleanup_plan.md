                                                                                                                                                                                                                                                                                                                                                                                                                          Sat15 [824/1547]
  Phase 0 – Housekeeping & Baseline                                                                             
                                                                                                                
  - Audit repo clutter: decide which screenshots, UI dumps, Gradle                                              
    wrapper copies, etc. belong under version control; script removal to                                        
    keep future diffs readable.                                                                                 
  - Capture current behaviour with smoke tests (./gradlew assembleDebug,                                        
    Genymotion pen/color/text workflows) as regression guardrails.                                              
  - Freeze fdroid metadata/automation scripts so renaming & package                                             
    changes are coordinated later.                                                                              
                                                                                                                
  Phase 1 – Rebrand + Build Configuration                                                                       
                                                                                                                
  - Rename applicationId/package namespace from org.opendroidpdf                                          
    → org.opendroidpdf (or agreed reverse-DNS) using Android Studio                                             
    refactor to update manifests, code, XML, and native JNI prefixes.                                           
  - Update Gradle modules (platform/android/build.gradle,                                                       
    settings.gradle) to reflect the new project name, output APK naming,                                        
    and F-Droid metadata.                                                                                       
  - Refresh user-facing branding: app label/strings, launcher icons,                                            
    README, license headers, deployment docs, fdroid scripts →                                                  
    OpenDroidPDF.                                                                                               
                                                                                                                
  Phase 2 – Architectural Decomposition (Java/Kotlin Layer)                                                     
                                                                                                                
  - Split `OpenDroidPDFActivity` (formerly `PenAndPDFActivity`, ~3.2 k lines) into feature fragments/                                              
    controllers:                                                                                                
      - HomeFragment for the dashboard/recents, DocumentFragment (or                                            
        DocumentActivity) hosting the reader, SettingsActivity isolated.                                        
      - Extract helpers (IntentRouter, StoragePermissionHelper,                                                 
        ExportController, PenSettingsController, ToolbarStateController)                                        
        under com.opendroidpdf.app.framework.                                                                                            
  - Break down PageView/MuPDFPageView responsibility: isolate drawing                                                                    
    overlay, annotation manager, gesture handling, undo stack into                                                                       
    dedicated classes in a drawing package.                                                                                              
  - Move persistence/preferences to a central PenPreferences (backed                                                                     
    by DataStore or SharedPreferences) with change listeners decoupled                                                                   
    from UI.                             
  - Convert ad-hoc async tasks (custom CancellableAsyncTask) to Kotlin                                                                                                
    coroutines / LifecycleScope for readability and cancellation safety.                                                                                              
  - Introduce dependency injection lite (e.g., Hilt or manual                      
    providers) to centralize access to MuPDFCore, file providers, and                                                                                                 
    configuration.                       

  Phase 3 – Resource & UI Cleanup              

  - Group layouts/menus/styles by feature (dashboard, reader, pen                              
    settings, text tool); eliminate duplicate dialog definitions.                              
  - Standardize string keys and colors; move pen color palette, slider                                                                                                                         
    limits, text styles into resource XML with clear naming.                                   
  - Replace custom menu inflation paths with toolbar/fragment scoped                                                                                                                           
    menus; ensure gesture bindings live near the UI component they                                                                                                                             
    affect.                                    

  Phase 4 – Native Layer Restructure                                                                                   

  - Split mupdf.c (~3.5 k lines) into focused compilation units: document_io.c,
    render.c, ink.c, text_selection.c, export_share.c, text_annot.c,
    widgets.c, widgets_signature.c, utils.c, each exposing JNI hooks via
    the shared mupdf_native.h header.
  - Align JNI package names with the new namespace (generated macros via                                               
    JNI_FN updates).                                       
  - Document MuPDF API expectations and third-party dependency layout                                                  
    under jni/README.md; ensure Core.mk/ThirdParty.mk map cleanly to the                                               
    restructured files.                                             
  - Add thin Kotlin/Java façade (MuPdfRepository) to shield the rest of                                                                  
    the app from low-level JNI signatures.                                                                                               

  Phase 5 – Configuration & Build Variants                                                                                               

  - Centralize build config (paths, keystore, fdroid deploy options) in                                                                  
    gradle.properties + buildSrc constants; expose ABI selection flags                                                                   
    cleanly (legacy penandpdfAbi → opendroidpdfAbi).                                                                                            
  - Evaluate multi-module split (app, core, feature-drawing, feature-                                                                    
    text) once Java packages are untangled to speed incremental builds                                                                   
    and testing.                                                    
  - Enable R8/ProGuard with curated rules once refactor stabilizes;                                                                      
    verify APK size targets hold.                                   

  Phase 6 – Testing & Tooling                                                          

  - Introduce instrumentation tests for pen/text tools (gesture                        
    simulation, undo, export) using Espresso/UIAutomator with new                      
    fragments.                                                                         
  - Add unit tests around preference storage, undo stack, text                         
    annotation sizing.                                                                 
  - Wire basic CI (GitHub Actions or existing scripts) running lint,                                                                                                          
    assembleRelease, selected emulator tests.                                          
  - Update fdroid deployment script to new package/version paths and                                                                                                          
    automate changelog generation.                                                     

  Phase 7 – Documentation & Transition                                                 

  - Publish developer docs: module map, build instructions, coding                                                                                                                                                   
    conventions, native build notes, release checklist under docs/.                                                                                                                                                  
  - Update LICENSE, CONTRIBUTING, README to reflect OpenDroidPDF                                          
    governance and refactored architecture.                                                               
  - Plan migration steps for downstream consumers (package rename,                                                                                                                                                   
    preference migration). Consider shipping a transitional build that                                                                                                                                               
    handles old shared-pref keys.                                                                         

  Each phase should land with passing builds and minimal behaviour          
