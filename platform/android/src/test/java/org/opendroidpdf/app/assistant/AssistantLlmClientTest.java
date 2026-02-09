package org.opendroidpdf.app.assistant;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class AssistantLlmClientTest {

    @Test
    public void parseAskResult_includesRelatedQuestions() throws Exception {
        String json = "{"
                + "\"answerText\":\"Hello\","
                + "\"citations\":[1,2],"
                + "\"relatedQuestions\":[\"What is this about?\",\"Where is it discussed?\"]"
                + "}";

        AssistantLlmClient.AskResult r = parseAskResultViaReflection(json);

        assertEquals("Hello", r.answerText);
        assertNotNull(r.citationPages1Based);
        assertArrayEquals(new int[]{1, 2}, r.citationPages1Based);
        assertNotNull(r.relatedQuestions);
        assertArrayEquals(new String[]{"What is this about?", "Where is it discussed?"}, r.relatedQuestions);
    }

    @Test
    public void parseAskResult_keepsRelatedQuestionsWhenCitationsMissing() throws Exception {
        String json = "{"
                + "\"answerText\":\"Hello\","
                + "\"citations\":[],"
                + "\"relatedQuestions\":[\" \",\"What is this about?\",\"what is this about?\",\"Another?\"]"
                + "}";

        AssistantLlmClient.AskResult r = parseAskResultViaReflection(json);

        assertEquals("Hello", r.answerText);
        assertNull(r.citationPages1Based);
        assertNull(r.citationNumbers);
        assertNotNull(r.relatedQuestions);
        assertArrayEquals(new String[]{"What is this about?", "Another?"}, r.relatedQuestions);
    }

    private static AssistantLlmClient.AskResult parseAskResultViaReflection(String content) throws Exception {
        Method m = AssistantLlmClient.class.getDeclaredMethod("parseAskResult", String.class);
        m.setAccessible(true);
        return (AssistantLlmClient.AskResult) m.invoke(null, content);
    }
}

