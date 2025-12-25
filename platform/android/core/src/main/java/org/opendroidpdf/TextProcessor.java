package org.opendroidpdf;

/**
 * Callback used by TextSelector to stream selected words to a consumer.
 */
public interface TextProcessor {
    void onStartLine();
    void onWord(TextWord word);
    void onEndLine();
    void onEndText();
}

