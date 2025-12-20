package org.opendroidpdf.app.toolbar;

import org.junit.Test;

import static org.junit.Assert.*;

public class MenuStateEvaluatorTest {

    @Test
    public void noDocument_HidesAndDisablesEverything() {
        MenuStateEvaluator.Inputs in = new MenuStateEvaluator.Inputs(false, false, false, false, false, false);
        MenuState s = MenuStateEvaluator.compute(in);
        assertFalse(s.groupDocumentActionsEnabled);
        assertFalse(s.groupEditorToolsEnabled);
        assertFalse(s.groupEditorToolsVisible);
        assertFalse(s.undoVisible);
        assertFalse(s.undoEnabled);
        assertFalse(s.saveEnabled);
        assertFalse(s.linkBackVisible);
        assertFalse(s.linkBackEnabled);
        assertFalse(s.searchVisible);
        assertFalse(s.searchEnabled);
        assertFalse(s.drawVisible);
        assertFalse(s.drawEnabled);
        assertFalse(s.addTextVisible);
        assertFalse(s.addTextEnabled);
        assertFalse(s.printEnabled);
        assertFalse(s.shareEnabled);
        assertFalse(s.readingSettingsVisible);
        assertFalse(s.readingSettingsEnabled);
    }

    @Test
    public void openDoc_NoUndo_NoUnsaved_NoLink_ShowsBasics() {
        MenuStateEvaluator.Inputs in = new MenuStateEvaluator.Inputs(true, false, false, false, true, false);
        MenuState s = MenuStateEvaluator.compute(in);
        assertTrue(s.groupDocumentActionsEnabled);
        assertTrue(s.groupEditorToolsEnabled);
        assertTrue(s.groupEditorToolsVisible);
        assertTrue(s.undoVisible);
        assertFalse(s.undoEnabled);
        assertTrue(s.saveEnabled);
        assertFalse(s.linkBackVisible);
        assertFalse(s.linkBackEnabled);
        assertTrue(s.searchVisible);
        assertTrue(s.searchEnabled);
        assertTrue(s.drawVisible);
        assertTrue(s.drawEnabled);
        assertTrue(s.addTextVisible);
        assertTrue(s.addTextEnabled);
        assertTrue(s.printEnabled);
        assertTrue(s.shareEnabled);
        assertFalse(s.readingSettingsVisible);
        assertFalse(s.readingSettingsEnabled);
    }

    @Test
    public void openDoc_AllCapabilities_EnableAll() {
        MenuStateEvaluator.Inputs in = new MenuStateEvaluator.Inputs(true, true, true, true, true, false);
        MenuState s = MenuStateEvaluator.compute(in);
        assertTrue(s.undoVisible);
        assertTrue(s.undoEnabled);
        assertTrue(s.saveEnabled);
        assertTrue(s.linkBackVisible);
        assertTrue(s.linkBackEnabled);
    }

    @Test
    public void openDoc_SaveNotSupported_HidesSave() {
        MenuStateEvaluator.Inputs in = new MenuStateEvaluator.Inputs(true, false, false, false, false, true);
        MenuState s = MenuStateEvaluator.compute(in);
        assertFalse(s.saveEnabled);
        // Other basics remain enabled so the reader can still function (EPUB uses sidecar annotations).
        assertTrue(s.searchVisible);
        assertTrue(s.drawVisible);
        assertTrue(s.printEnabled);
        assertTrue(s.shareEnabled);
        assertTrue(s.readingSettingsVisible);
    }
}
