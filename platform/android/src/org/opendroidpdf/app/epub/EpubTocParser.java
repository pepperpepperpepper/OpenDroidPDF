package org.opendroidpdf.app.epub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Best-effort EPUB table-of-contents (TOC) parser.
 *
 * <p>Supports EPUB 2 {@code toc.ncx} and EPUB 3 {@code nav.xhtml} (manifest item with
 * {@code properties="nav"}). If neither is present we return an empty list and the UI can fall
 * back to "No table of contents available".</p>
 */
public final class EpubTocParser {

    public static final class TocEntry {
        public final int level;
        @NonNull public final String title;
        @NonNull public final String href;

        TocEntry(int level, @NonNull String title, @NonNull String href) {
            this.level = Math.max(0, level);
            this.title = title;
            this.href = href;
        }
    }

    private static final String CONTAINER_XML = "META-INF/container.xml";
    private static final String OPF_MEDIA_TYPE = "application/oebps-package+xml";
    private static final String NCX_MEDIA_TYPE = "application/x-dtbncx+xml";
    private static final String NAV_PROPERTY = "nav";

    private EpubTocParser() {}

    @NonNull
    public static List<TocEntry> parseFromEpubPath(@NonNull String epubPath) {
        if (epubPath == null || epubPath.trim().isEmpty()) return new ArrayList<>();
        File file = new File(epubPath);
        if (!file.isFile()) return new ArrayList<>();

        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            String opfPath = findOpfPath(zip);
            if (opfPath == null) return new ArrayList<>();

            String tocPath = findNcxPath(zip, opfPath);
            if (tocPath == null) tocPath = firstEntryEndingWith(zip, ".ncx");
            if (tocPath == null) tocPath = findNavXhtmlPath(zip, opfPath);
            if (tocPath == null) return new ArrayList<>();

            ZipEntry tocEntry = zip.getEntry(tocPath);
            if (tocEntry == null) return new ArrayList<>();

            String tocDir = dirOf(tocPath);
            try (InputStream is = zip.getInputStream(tocEntry)) {
                Document doc = parseXml(is);
                if (doc == null) return new ArrayList<>();
                if (tocPath.toLowerCase(Locale.US).endsWith(".ncx")) {
                    return parseNcx(doc, tocDir);
                }
                return parseNavXhtml(doc, tocDir);
            }
        } catch (Throwable t) {
            return new ArrayList<>();
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (Throwable ignore) {}
            }
        }
    }

    @Nullable
    private static String findOpfPath(@NonNull ZipFile zip) {
        ZipEntry container = zip.getEntry(CONTAINER_XML);
        if (container == null) return null;

        try (InputStream is = zip.getInputStream(container)) {
            Document doc = parseXml(is);
            if (doc == null) return null;

            NodeList rootfiles = doc.getElementsByTagNameNS("*", "rootfile");
            for (int i = 0; i < rootfiles.getLength(); i++) {
                Node n = rootfiles.item(i);
                if (!(n instanceof Element)) continue;
                Element e = (Element) n;
                String fullPath = e.getAttribute("full-path");
                String mediaType = e.getAttribute("media-type");
                if (fullPath == null || fullPath.trim().isEmpty()) continue;
                if (mediaType == null || mediaType.trim().isEmpty()) return fullPath;
                if (OPF_MEDIA_TYPE.equalsIgnoreCase(mediaType.trim())) return fullPath;
            }
            // Fallback: accept any rootfile full-path.
            for (int i = 0; i < rootfiles.getLength(); i++) {
                Node n = rootfiles.item(i);
                if (!(n instanceof Element)) continue;
                String fullPath = ((Element) n).getAttribute("full-path");
                if (fullPath != null && !fullPath.trim().isEmpty()) return fullPath;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static String findNcxPath(@NonNull ZipFile zip, @NonNull String opfPath) {
        ZipEntry opfEntry = zip.getEntry(opfPath);
        if (opfEntry == null) return null;

        String opfDir = dirOf(opfPath);
        try (InputStream is = zip.getInputStream(opfEntry)) {
            Document doc = parseXml(is);
            if (doc == null) return null;

            String tocId = null;
            NodeList spineNodes = doc.getElementsByTagNameNS("*", "spine");
            if (spineNodes.getLength() > 0 && spineNodes.item(0) instanceof Element) {
                tocId = ((Element) spineNodes.item(0)).getAttribute("toc");
                if (tocId != null && tocId.trim().isEmpty()) tocId = null;
            }

            String ncxHref = null;
            NodeList itemNodes = doc.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node n = itemNodes.item(i);
                if (!(n instanceof Element)) continue;
                Element item = (Element) n;
                String id = item.getAttribute("id");
                String href = item.getAttribute("href");
                String media = item.getAttribute("media-type");
                if (href == null || href.trim().isEmpty()) continue;

                boolean matchesId = tocId != null && tocId.equals(id);
                boolean isNcx = media != null && NCX_MEDIA_TYPE.equalsIgnoreCase(media.trim());
                if (matchesId || isNcx) {
                    ncxHref = href.trim();
                    break;
                }
            }

            if (ncxHref == null || ncxHref.isEmpty()) return null;
            return normalizeZipHref(opfDir, ncxHref);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static String findNavXhtmlPath(@NonNull ZipFile zip, @NonNull String opfPath) {
        ZipEntry opfEntry = zip.getEntry(opfPath);
        if (opfEntry == null) return null;

        String opfDir = dirOf(opfPath);
        try (InputStream is = zip.getInputStream(opfEntry)) {
            Document doc = parseXml(is);
            if (doc == null) return null;

            NodeList itemNodes = doc.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node n = itemNodes.item(i);
                if (!(n instanceof Element)) continue;
                Element item = (Element) n;
                String href = item.getAttribute("href");
                if (href == null || href.trim().isEmpty()) continue;

                String props = item.getAttribute("properties");
                if (props == null || props.trim().isEmpty()) continue;

                if (hasWhitespaceSeparatedToken(props, NAV_PROPERTY)) {
                    return normalizeZipHref(opfDir, href.trim());
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static String firstEntryEndingWith(@NonNull ZipFile zip, @NonNull String suffixLower) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            String name = e != null ? e.getName() : null;
            if (name == null) continue;
            if (name.toLowerCase(Locale.US).endsWith(suffixLower)) return name;
        }
        return null;
    }

    @NonNull
    private static List<TocEntry> parseNavXhtml(@NonNull Document doc, @NonNull String navDir) {
        List<TocEntry> out = new ArrayList<>();

        NodeList navNodes = doc.getElementsByTagNameNS("*", "nav");
        if (navNodes.getLength() == 0) return out;

        Element tocNav = null;
        for (int i = 0; i < navNodes.getLength(); i++) {
            Node n = navNodes.item(i);
            if (!(n instanceof Element)) continue;
            Element nav = (Element) n;
            String epubType = attributeValueByLocalName(nav, "type");
            if ("toc".equalsIgnoreCase(epubType)) {
                tocNav = nav;
                break;
            }
        }
        if (tocNav == null) {
            // Fallback: first <nav> element.
            Node n = navNodes.item(0);
            if (n instanceof Element) tocNav = (Element) n;
        }
        if (tocNav == null) return out;

        Element ol = firstChildElementByLocalName(tocNav, "ol");
        if (ol == null) return out;

        parseNavOl(ol, 0, navDir, out);
        return out;
    }

    private static void parseNavOl(@NonNull Element ol,
                                   int level,
                                   @NonNull String navDir,
                                   @NonNull List<TocEntry> out) {
        NodeList children = ol.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            if (!"li".equalsIgnoreCase(localName(n))) continue;
            Element li = (Element) n;

            Element a = firstDescendantElementByLocalName(li, "a");
            if (a != null) {
                String href = a.getAttribute("href");
                String title = a.getTextContent();
                if (href != null) href = href.trim();
                if (title != null) title = title.trim();
                if (href != null && !href.isEmpty() && title != null && !title.isEmpty()) {
                    out.add(new TocEntry(level, title, normalizeZipHref(navDir, href)));
                }
            }

            Element childOl = firstChildElementByLocalName(li, "ol");
            if (childOl != null) {
                parseNavOl(childOl, level + 1, navDir, out);
            }
        }
    }

    @NonNull
    private static List<TocEntry> parseNcx(@NonNull Document doc, @NonNull String tocDir) {
        List<TocEntry> out = new ArrayList<>();

        NodeList navMaps = doc.getElementsByTagNameNS("*", "navMap");
        if (navMaps.getLength() == 0) return out;
        Node navMap = navMaps.item(0);
        if (!(navMap instanceof Element)) return out;

        NodeList children = navMap.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            if (!"navPoint".equalsIgnoreCase(localName(child))) continue;
            parseNavPoint((Element) child, 0, tocDir, out);
        }
        return out;
    }

    private static void parseNavPoint(@NonNull Element navPoint,
                                      int level,
                                      @NonNull String tocDir,
                                      @NonNull List<TocEntry> out) {
        String title = null;
        String src = null;

        NodeList children = navPoint.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            String ln = localName(n);
            if ("navLabel".equalsIgnoreCase(ln)) {
                title = extractFirstTextUnder((Element) n, "text");
            } else if ("content".equalsIgnoreCase(ln)) {
                src = ((Element) n).getAttribute("src");
            }
        }

        if (title != null) title = title.trim();
        if (src != null) src = src.trim();
        if (title != null && !title.isEmpty() && src != null && !src.isEmpty()) {
            out.add(new TocEntry(level, title, normalizeZipHref(tocDir, src)));
        }

        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            if (!"navPoint".equalsIgnoreCase(localName(n))) continue;
            parseNavPoint((Element) n, level + 1, tocDir, out);
        }
    }

    @Nullable
    private static String extractFirstTextUnder(@NonNull Element root, @NonNull String localTag) {
        NodeList nodes = root.getElementsByTagNameNS("*", localTag);
        if (nodes.getLength() == 0) return null;
        Node n = nodes.item(0);
        if (!(n instanceof Element)) return null;
        String text = n.getTextContent();
        return text != null ? text : null;
    }

    private static boolean hasWhitespaceSeparatedToken(@NonNull String raw, @NonNull String tokenLower) {
        String[] parts = raw.trim().split("\\s+");
        for (String p : parts) {
            if (p == null) continue;
            if (tokenLower.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    @Nullable
    private static String attributeValueByLocalName(@NonNull Element el, @NonNull String localAttr) {
        // namespace-aware: scan attributes for a matching local name (e.g., epub:type).
        if (el.hasAttribute(localAttr)) {
            String v = el.getAttribute(localAttr);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        if (el.hasAttributes()) {
            for (int i = 0; i < el.getAttributes().getLength(); i++) {
                Node a = el.getAttributes().item(i);
                if (a == null) continue;
                String ln = a.getLocalName();
                if (ln == null) {
                    String nn = a.getNodeName();
                    if (nn != null) {
                        int colon = nn.indexOf(':');
                        ln = colon >= 0 ? nn.substring(colon + 1) : nn;
                    }
                }
                if (ln != null && ln.equalsIgnoreCase(localAttr)) {
                    String v = a.getNodeValue();
                    if (v != null && !v.trim().isEmpty()) return v.trim();
                }
            }
        }
        return null;
    }

    @Nullable
    private static Element firstChildElementByLocalName(@NonNull Element parent, @NonNull String local) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && local.equalsIgnoreCase(localName(n))) {
                return (Element) n;
            }
        }
        return null;
    }

    @Nullable
    private static Element firstDescendantElementByLocalName(@NonNull Element parent, @NonNull String local) {
        // BFS to avoid deep recursion on pathological docs.
        ArrayDeque<Node> q = new ArrayDeque<>();
        q.add(parent);
        while (!q.isEmpty()) {
            Node n = q.removeFirst();
            if (n instanceof Element && local.equalsIgnoreCase(localName(n))) {
                return (Element) n;
            }
            NodeList children = n.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node c = children.item(i);
                if (c != null && c instanceof Element) q.addLast(c);
            }
        }
        return null;
    }

    @NonNull
    private static String normalizeZipHref(@NonNull String baseDir, @NonNull String href) {
        // Preserve fragments (#id), but resolve the path portion relative to baseDir.
        String path = href;
        String fragment = "";
        int hash = href.indexOf('#');
        if (hash >= 0) {
            path = href.substring(0, hash);
            fragment = href.substring(hash);
        }

        // If already absolute-ish, just clean it.
        String combined = (path.startsWith("/") ? path.substring(1) : path);
        if (!combined.contains(":") && baseDir != null && !baseDir.isEmpty() && !combined.startsWith(baseDir)) {
            combined = baseDir + combined;
        }

        return normalizeZipPath(combined) + fragment;
    }

    @NonNull
    private static String normalizeZipPath(@NonNull String path) {
        // Resolve ./ and ../ segments without relying on java.nio.file (Android API level).
        String[] parts = path.replace('\\', '/').split("/");
        ArrayDeque<String> stack = new ArrayDeque<>();
        for (String p : parts) {
            if (p == null || p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!stack.isEmpty()) stack.removeLast();
                continue;
            }
            stack.addLast(p);
        }
        StringBuilder sb = new StringBuilder();
        for (String p : stack) {
            if (sb.length() > 0) sb.append('/');
            sb.append(p);
        }
        return sb.toString();
    }

    @NonNull
    private static String dirOf(@NonNull String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0) return "";
        return path.substring(0, idx + 1);
    }

    @NonNull
    private static String localName(@NonNull Node node) {
        String ln = node.getLocalName();
        if (ln != null) return ln;
        String nn = node.getNodeName();
        if (nn == null) return "";
        int colon = nn.indexOf(':');
        return colon >= 0 ? nn.substring(colon + 1) : nn;
    }

    @Nullable
    private static Document parseXml(@NonNull InputStream is) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // Best-effort XXE hardening for both Android + JVM.
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Throwable ignore) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Throwable ignore) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Throwable ignore) {}
        try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Throwable ignore) {}
        try { f.setExpandEntityReferences(false); } catch (Throwable ignore) {}

        return f.newDocumentBuilder().parse(is);
    }
}
