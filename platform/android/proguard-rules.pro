# R8/ProGuard configuration for OpenDroidPDF
# Keep classes invoked directly from JNI (class names and members must remain stable).
-keep class org.opendroidpdf.MuPDFCore { *; }
-keep class org.opendroidpdf.Annotation { *; }
-keep class org.opendroidpdf.MuPDFAlertInternal { *; }
-keep class org.opendroidpdf.TextChar { *; }
-keep class org.opendroidpdf.Separation { *; }
-keep class org.opendroidpdf.OutlineItem { *; }
-keep class org.opendroidpdf.LinkInfo { *; }
-keep class org.opendroidpdf.LinkInfoInternal { *; }
-keep class org.opendroidpdf.LinkInfoExternal { *; }
-keep class org.opendroidpdf.LinkInfoRemote { *; }

# Preserve enum names used across the native boundary.
-keepclassmembers enum org.opendroidpdf.SignatureState { *; }

# Keep generated resource identifiers and annotations for debugging/analytics.
-keepclassmembers class **.R$* { *; }
-keepattributes *Annotation*,SourceFile,LineNumberTable
