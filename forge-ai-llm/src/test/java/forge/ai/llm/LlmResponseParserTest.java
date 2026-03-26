package forge.ai.llm;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

public class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser();

    // === Single action choice ===

    @Test
    public void testCleanNumber() {
        assertEquals(3, parser.parseActionChoice("3", 5));
    }

    @Test
    public void testNumberWithExplanation() {
        assertEquals(3, parser.parseActionChoice("I choose action 3 because Dockside is great", 5));
    }

    @Test
    public void testNumberWithNewline() {
        assertEquals(2, parser.parseActionChoice("2\n\nThis plays around removal", 5));
    }

    @Test
    public void testPass() {
        assertEquals(-1, parser.parseActionChoice("PASS", 5));
    }

    @Test
    public void testPassWithExplanation() {
        assertEquals(-1, parser.parseActionChoice("PASS - nothing good to play", 5));
    }

    @Test
    public void testPassLowercase() {
        assertEquals(-1, parser.parseActionChoice("pass", 5));
    }

    @Test
    public void testZeroIndex() {
        assertEquals(0, parser.parseActionChoice("0", 5));
    }

    @Test(expected = LlmResponseParser.LlmParseException.class)
    public void testOutOfBounds() {
        parser.parseActionChoice("99", 5);
    }

    @Test(expected = LlmResponseParser.LlmParseException.class)
    public void testEmptyResponse() {
        parser.parseActionChoice("", 5);
    }

    @Test(expected = LlmResponseParser.LlmParseException.class)
    public void testNullResponse() {
        parser.parseActionChoice(null, 5);
    }

    @Test(expected = LlmResponseParser.LlmParseException.class)
    public void testGarbageResponse() {
        parser.parseActionChoice("I'm not sure what to do here", 5);
    }

    // === Multiple choices (attackers) ===

    @Test
    public void testMultipleNumbers() {
        List<Integer> result = parser.parseMultipleChoices("1,3,5", 6);
        assertEquals(3, result.size());
        assertTrue(result.contains(1));
        assertTrue(result.contains(3));
        assertTrue(result.contains(5));
    }

    @Test
    public void testMultipleWithSpaces() {
        List<Integer> result = parser.parseMultipleChoices("1, 3, 5", 6);
        assertEquals(3, result.size());
    }

    @Test
    public void testNone() {
        List<Integer> result = parser.parseMultipleChoices("NONE", 6);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNoneWithExplanation() {
        List<Integer> result = parser.parseMultipleChoices("NONE - too risky to attack", 6);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testEmptyMultiple() {
        List<Integer> result = parser.parseMultipleChoices("", 6);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNoDuplicates() {
        List<Integer> result = parser.parseMultipleChoices("1,1,1", 6);
        assertEquals(1, result.size());
    }

    @Test
    public void testSkipsOutOfBounds() {
        List<Integer> result = parser.parseMultipleChoices("1,99,3", 6);
        assertEquals(2, result.size());
        assertTrue(result.contains(1));
        assertTrue(result.contains(3));
    }

    // === Block assignments ===

    @Test
    public void testBlockAssignment() {
        Map<Integer, Integer> result = parser.parseBlockAssignments("0->1,2->0", 3, 2);
        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(1), result.get(0));
        assertEquals(Integer.valueOf(0), result.get(2));
    }

    @Test
    public void testBlockNone() {
        Map<Integer, Integer> result = parser.parseBlockAssignments("NONE", 3, 2);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testBlockWithSpaces() {
        Map<Integer, Integer> result = parser.parseBlockAssignments("0 -> 1, 2 -> 0", 3, 2);
        assertEquals(2, result.size());
    }
}
