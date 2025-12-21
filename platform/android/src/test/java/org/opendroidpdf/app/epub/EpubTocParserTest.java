package org.opendroidpdf.app.epub;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpubTocParserTest {

    @Test
    public void parsesEpub2NcxFromOpfSpine() throws Exception {
        String container = ""
                + "<?xml version=\"1.0\"?>"
                + "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">"
                + "  <rootfiles>"
                + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "  </rootfiles>"
                + "</container>";

        String opf = ""
                + "<?xml version=\"1.0\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"BookId\" version=\"2.0\">"
                + "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "    <dc:title>Fixture</dc:title>"
                + "    <dc:identifier id=\"BookId\">urn:uuid:test</dc:identifier>"
                + "  </metadata>"
                + "  <manifest>"
                + "    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
                + "    <item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "    <item id=\"c2\" href=\"sub/chapter2.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "  </manifest>"
                + "  <spine toc=\"ncx\">"
                + "    <itemref idref=\"c1\"/>"
                + "    <itemref idref=\"c2\"/>"
                + "  </spine>"
                + "</package>";

        String ncx = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">"
                + "  <navMap>"
                + "    <navPoint id=\"n1\" playOrder=\"1\">"
                + "      <navLabel><text>Chapter 1</text></navLabel>"
                + "      <content src=\"chapter1.xhtml\"/>"
                + "    </navPoint>"
                + "    <navPoint id=\"n2\" playOrder=\"2\">"
                + "      <navLabel><text>Chapter 2</text></navLabel>"
                + "      <content src=\"sub/chapter2.xhtml#frag\"/>"
                + "    </navPoint>"
                + "  </navMap>"
                + "</ncx>";

        File epub = writeTempEpub(new String[]{
                "META-INF/container.xml", container,
                "OEBPS/content.opf", opf,
                "OEBPS/toc.ncx", ncx,
        });

        List<EpubTocParser.TocEntry> toc = EpubTocParser.parseFromEpubPath(epub.getAbsolutePath());
        assertEquals(2, toc.size());
        assertEquals("Chapter 1", toc.get(0).title);
        assertEquals("OEBPS/chapter1.xhtml", toc.get(0).href);
        assertEquals("Chapter 2", toc.get(1).title);
        assertEquals("OEBPS/sub/chapter2.xhtml#frag", toc.get(1).href);
    }

    @Test
    public void normalizesRelativeHrefAndLevels() throws Exception {
        String container = ""
                + "<?xml version=\"1.0\"?>"
                + "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" version=\"1.0\">"
                + "  <rootfiles>"
                + "    <rootfile full-path=\"OPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                + "  </rootfiles>"
                + "</container>";

        String opf = ""
                + "<?xml version=\"1.0\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\">"
                + "  <manifest>"
                + "    <item id=\"ncx\" href=\"toc/toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
                + "  </manifest>"
                + "  <spine toc=\"ncx\"/>"
                + "</package>";

        String ncx = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">"
                + "  <navMap>"
                + "    <navPoint id=\"n1\" playOrder=\"1\">"
                + "      <navLabel><text>Top</text></navLabel>"
                + "      <content src=\"../Text/ch1.xhtml\"/>"
                + "      <navPoint id=\"n1_1\" playOrder=\"2\">"
                + "        <navLabel><text>Child</text></navLabel>"
                + "        <content src=\"../Text/ch2.xhtml#x\"/>"
                + "      </navPoint>"
                + "    </navPoint>"
                + "  </navMap>"
                + "</ncx>";

        File epub = writeTempEpub(new String[]{
                "META-INF/container.xml", container,
                "OPS/content.opf", opf,
                "OPS/toc/toc.ncx", ncx,
        });

        List<EpubTocParser.TocEntry> toc = EpubTocParser.parseFromEpubPath(epub.getAbsolutePath());
        assertEquals(2, toc.size());
        assertEquals(0, toc.get(0).level);
        assertEquals("Top", toc.get(0).title);
        assertEquals("OPS/Text/ch1.xhtml", toc.get(0).href);
        assertEquals(1, toc.get(1).level);
        assertEquals("Child", toc.get(1).title);
        assertEquals("OPS/Text/ch2.xhtml#x", toc.get(1).href);
    }

    @Test
    public void missingContainer_returnsEmpty() throws Exception {
        File epub = writeTempEpub(new String[]{
                "OEBPS/content.opf", "<package/>",
        });
        assertTrue(EpubTocParser.parseFromEpubPath(epub.getAbsolutePath()).isEmpty());
    }

    private static File writeTempEpub(String[] pathAndContents) throws Exception {
        File out = File.createTempFile("epub_toc_", ".epub");
        out.deleteOnExit();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out, false))) {
            for (int i = 0; i + 1 < pathAndContents.length; i += 2) {
                String path = pathAndContents[i];
                String content = pathAndContents[i + 1];
                ZipEntry e = new ZipEntry(path);
                zos.putNextEntry(e);
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
        }
        return out;
    }
}

