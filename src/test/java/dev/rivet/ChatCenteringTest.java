package dev.rivet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ChatCenteringTest {
    @Test
    public void computesPixelWidthFromKnownGlyphBuckets() {
        assertEquals(0, ChatCentering.pixelWidth(""));
        assertEquals(8, ChatCentering.pixelWidth("Hi"));
        assertEquals(2, ChatCentering.pixelWidth("i"));
    }

    @Test
    public void centersShorterMessagesWithMoreLeadingSpacesThanLongerOnes() {
        int shortSpaces = ChatCentering.centeringSpaces("Hi");
        int longSpaces = ChatCentering.centeringSpaces(
            "This is a considerably longer message than the other one");
        assertEquals(37, shortSpaces);
        assertEquals(true, longSpaces < shortSpaces);
        assertEquals(0, ChatCentering.centeringSpaces(
            "This message is deliberately long enough to exceed the centered chat box width entirely"
                + " so that no padding is needed at all"));
    }
}
