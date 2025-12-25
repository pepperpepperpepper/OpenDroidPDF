package org.opendroidpdf;

/**
 * Result of hit-testing taps against a page view (links, annotations, widgets, etc).
 *
 * <p>This is public so gesture routers/controllers outside {@code org.opendroidpdf}
 * can route taps without depending on concrete view classes.
 */
public enum Hit {
    Nothing,
    Widget,
    Annotation,
    Link,
    LinkInternal,
    LinkExternal,
    LinkRemote,
    Debug,
    TextAnnotation,
    InkAnnotation
}

