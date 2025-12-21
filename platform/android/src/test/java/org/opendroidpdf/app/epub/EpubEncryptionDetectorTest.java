package org.opendroidpdf.app.epub;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;

public class EpubEncryptionDetectorTest {

    @Test
    public void noEncryptionXml_none() throws Exception {
        byte[] epub = zip();
        assertEquals(
                EpubEncryptionDetector.EncryptionKind.NONE,
                EpubEncryptionDetector.detectFromEpubZipStream(new ByteArrayInputStream(epub)));
    }

    @Test
    public void fontObfuscationOnly_allowed() throws Exception {
        String xml = ""
                + "<?xml version=\"1.0\"?>"
                + "<encryption>"
                + "  <EncryptedData>"
                + "    <EncryptionMethod Algorithm=\"http://www.idpf.org/2008/embedding\"/>"
                + "    <CipherData><CipherReference URI=\"OEBPS/fonts/MyFont.ttf\"/></CipherData>"
                + "  </EncryptedData>"
                + "</encryption>";
        byte[] epub = zip("META-INF/encryption.xml", xml);
        assertEquals(
                EpubEncryptionDetector.EncryptionKind.FONT_OBFUSCATION_ONLY,
                EpubEncryptionDetector.detectFromEpubZipStream(new ByteArrayInputStream(epub)));
    }

    @Test
    public void encryptedNonFont_resourceIsDrmOrUnknown() throws Exception {
        String xml = ""
                + "<encryption>"
                + "  <EncryptedData>"
                + "    <EncryptionMethod Algorithm=\"http://www.idpf.org/2008/embedding\"/>"
                + "    <CipherData><CipherReference URI=\"OEBPS/chapter1.xhtml\"/></CipherData>"
                + "  </EncryptedData>"
                + "</encryption>";
        byte[] epub = zip("META-INF/encryption.xml", xml);
        assertEquals(
                EpubEncryptionDetector.EncryptionKind.DRM_OR_UNKNOWN,
                EpubEncryptionDetector.detectFromEpubZipStream(new ByteArrayInputStream(epub)));
    }

    @Test
    public void strongEncryption_algorithmIsDrmOrUnknown() throws Exception {
        String xml = ""
                + "<encryption>"
                + "  <EncryptedData>"
                + "    <EncryptionMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#aes256-cbc\"/>"
                + "    <CipherData><CipherReference URI=\"OEBPS/content.opf\"/></CipherData>"
                + "  </EncryptedData>"
                + "</encryption>";
        byte[] epub = zip("META-INF/encryption.xml", xml);
        assertEquals(
                EpubEncryptionDetector.EncryptionKind.DRM_OR_UNKNOWN,
                EpubEncryptionDetector.detectFromEpubZipStream(new ByteArrayInputStream(epub)));
    }

    private static byte[] zip(String... pathAndContentPairs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        for (int i = 0; i + 1 < pathAndContentPairs.length; i += 2) {
            String path = pathAndContentPairs[i];
            String content = pathAndContentPairs[i + 1];
            ZipEntry e = new ZipEntry(path);
            zos.putNextEntry(e);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        zos.finish();
        zos.close();
        return baos.toByteArray();
    }
}

