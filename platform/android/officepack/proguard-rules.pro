# Keep minimal; the Office Pack is tiny for now.

# pdfbox-android references optional JPEG2000 support classes which are not
# packaged by default; suppress R8 missing-class errors.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
