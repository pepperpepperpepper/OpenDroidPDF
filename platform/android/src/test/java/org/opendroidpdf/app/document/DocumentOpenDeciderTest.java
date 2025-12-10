package org.opendroidpdf.app.document;

import org.junit.Test;

import static org.junit.Assert.*;

public class DocumentOpenDeciderTest {

    @Test
    public void withIntentAndPermission_openUri() {
        DocumentOpenDecider.Inputs in = new DocumentOpenDecider.Inputs(true, true, false, true);
        assertEquals(DocumentOpenDecider.Action.OPEN_URI, DocumentOpenDecider.decide(in));
    }

    @Test
    public void withIntentWithoutPermission_requestPermission() {
        DocumentOpenDecider.Inputs in = new DocumentOpenDecider.Inputs(true, false, false, true);
        assertEquals(DocumentOpenDecider.Action.REQUEST_STORAGE_PERMISSION, DocumentOpenDecider.decide(in));
    }

    @Test
    public void noIntentRecentExists_openRecent() {
        DocumentOpenDecider.Inputs in = new DocumentOpenDecider.Inputs(false, true, true, true);
        assertEquals(DocumentOpenDecider.Action.OPEN_RECENT, DocumentOpenDecider.decide(in));
    }

    @Test
    public void noIntentNoRecent_showDashboard() {
        DocumentOpenDecider.Inputs in = new DocumentOpenDecider.Inputs(false, true, false, true);
        assertEquals(DocumentOpenDecider.Action.SHOW_DASHBOARD, DocumentOpenDecider.decide(in));
    }
}

