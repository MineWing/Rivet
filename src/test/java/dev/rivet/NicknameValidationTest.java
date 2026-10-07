package dev.rivet;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class NicknameValidationTest {
    private static final Pattern DEFAULT = Pattern.compile(NicknameModule.DEFAULT_ALLOWED_PATTERN);
    private static final List<String> BLOCKED = NicknameModule.DEFAULT_BLOCKED_WORDS;

    @Test
    public void acceptsAnOrdinaryNickname() {
        assertNull(NicknameModule.validate("Sunny Day_2", 24, DEFAULT, BLOCKED, Set.of("notch")));
    }

    @Test
    public void rejectsAnotherPlayersNameCaseInsensitivelyEvenForStaff() {
        Set<String> taken = Set.of(NicknameModule.normalizeName("Notch"), NicknameModule.normalizeName("Builder Bob"));
        assertEquals(NicknameModule.Rejection.TAKEN,
            NicknameModule.validate("nOtCh", 24, DEFAULT, BLOCKED, taken));
        assertEquals(NicknameModule.Rejection.TAKEN,
            NicknameModule.validate(" builder bob ", 24, DEFAULT, BLOCKED, taken));
        // Staff (no pattern, no blocked words) still cannot impersonate.
        assertEquals(NicknameModule.Rejection.TAKEN,
            NicknameModule.validate("NOTCH", 24, null, List.of(), taken));
    }

    @Test
    public void enforcesTheAllowedPatternUnlessBypassed() {
        assertEquals(NicknameModule.Rejection.PATTERN,
            NicknameModule.validate("Nоtch", 24, DEFAULT, BLOCKED, Set.of()));
        assertEquals(NicknameModule.Rejection.PATTERN,
            NicknameModule.validate("star★", 24, DEFAULT, BLOCKED, Set.of()));
        assertNull(NicknameModule.validate("star★", 24, null, BLOCKED, Set.of()));
    }

    @Test
    public void blocksConfiguredWordsAsSubstringsIncludingSeparatedSpellings() {
        assertEquals(NicknameModule.Rejection.BLOCKED_WORD,
            NicknameModule.validate("TheAdmin", 24, DEFAULT, BLOCKED, Set.of()));
        assertEquals(NicknameModule.Rejection.BLOCKED_WORD,
            NicknameModule.validate("Server Owner", 24, DEFAULT, BLOCKED, Set.of()));
        assertEquals(NicknameModule.Rejection.BLOCKED_WORD,
            NicknameModule.validate("a_d m i n", 24, DEFAULT, BLOCKED, Set.of()));
        assertNull(NicknameModule.validate("TheAdmin", 24, DEFAULT, List.of(), Set.of()));
        assertTrue(NicknameModule.containsBlockedWord("STAFF", List.of(" Staff ")));
        assertFalse(NicknameModule.containsBlockedWord("Builder", List.of("", "admin")));
    }

    @Test
    public void keepsTheExistingLengthAndControlCharacterRules() {
        assertEquals(NicknameModule.Rejection.INVALID,
            NicknameModule.validate("   ", 24, null, List.of(), Set.of()));
        assertEquals(NicknameModule.Rejection.INVALID,
            NicknameModule.validate("abcdef", 5, DEFAULT, BLOCKED, Set.of()));
        assertEquals(NicknameModule.Rejection.INVALID,
            NicknameModule.validate("bad\nname", 24, null, List.of(), Set.of()));
    }
}
