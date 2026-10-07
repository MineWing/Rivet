package dev.rivet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class ModerationProtectionTest {
    @Test
    public void refusesSelfTargetingEvenForOperators() {
        assertEquals(StaffTools.ModerationRefusal.SELF, StaffTools.moderationRefusal(true, true, true, true));
        assertEquals(StaffTools.ModerationRefusal.SELF, StaffTools.moderationRefusal(true, false, false, false));
    }

    @Test
    public void protectsOperatorsFromNonOperators() {
        assertEquals(StaffTools.ModerationRefusal.OPERATOR,
            StaffTools.moderationRefusal(false, false, true, false));
    }

    @Test
    public void protectsExemptPlayersFromEveryone() {
        assertEquals(StaffTools.ModerationRefusal.EXEMPT,
            StaffTools.moderationRefusal(false, true, false, true));
        // Operators hold rivet.moderation.exempt by default, so op-on-op actions are refused too.
        assertEquals(StaffTools.ModerationRefusal.EXEMPT,
            StaffTools.moderationRefusal(false, true, true, true));
    }

    @Test
    public void allowsOrdinaryTargets() {
        assertNull(StaffTools.moderationRefusal(false, false, false, false));
        assertNull(StaffTools.moderationRefusal(false, true, false, false));
        assertNull(StaffTools.moderationRefusal(false, true, true, false));
    }
}
