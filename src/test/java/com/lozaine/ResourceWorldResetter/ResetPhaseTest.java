package com.lozaine.ResourceWorldResetter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetPhaseTest {

    @Test
    void resolveResumePhasePrefersSavedActiveResumePhase() {
        assertEquals(ResetPhase.DELETE,
                ResetPhase.resolveResumePhase(ResetPhase.PRECHECK, ResetPhase.RECREATE, ResetPhase.DELETE));
    }

    @Test
    void resolveResumePhaseFallsBackToFailedPhaseForLegacyFailedState() {
        assertEquals(ResetPhase.TELEPORT,
                ResetPhase.resolveResumePhase(ResetPhase.FAILED, ResetPhase.TELEPORT, ResetPhase.IDLE));
    }

    @Test
    void resolveResumePhaseFallsBackToCurrentActivePhaseOrIdle() {
        assertEquals(ResetPhase.UNLOAD,
                ResetPhase.resolveResumePhase(ResetPhase.UNLOAD, ResetPhase.IDLE, ResetPhase.IDLE));
        assertEquals(ResetPhase.IDLE,
                ResetPhase.resolveResumePhase(ResetPhase.COMPLETE, ResetPhase.DELETE, ResetPhase.IDLE));
    }

    @Test
    void allPhasesAreDefined() {
        ResetPhase[] phases = ResetPhase.values();
        assertEquals(9, phases.length);
        assertTrue(arrayContains(phases, ResetPhase.IDLE));
        assertTrue(arrayContains(phases, ResetPhase.PRECHECK));
        assertTrue(arrayContains(phases, ResetPhase.TELEPORT));
        assertTrue(arrayContains(phases, ResetPhase.UNLOAD));
        assertTrue(arrayContains(phases, ResetPhase.DELETE));
        assertTrue(arrayContains(phases, ResetPhase.RECREATE));
        assertTrue(arrayContains(phases, ResetPhase.VERIFY));
        assertTrue(arrayContains(phases, ResetPhase.COMPLETE));
        assertTrue(arrayContains(phases, ResetPhase.FAILED));
    }

    @Test
    void idlePhaseIsNotTerminal() {
        assertFalse(ResetPhase.IDLE.isTerminal());
    }

    @Test
    void idlePhaseIsNotActive() {
        assertFalse(ResetPhase.IDLE.isActive());
    }

    @Test
    void completPhaseIsTerminal() {
        assertTrue(ResetPhase.COMPLETE.isTerminal());
    }

    @Test
    void failedPhaseIsTerminal() {
        assertTrue(ResetPhase.FAILED.isTerminal());
    }

    @Test
    void allActivePhasesBetweenIdleAndComplete() {
        assertTrue(ResetPhase.PRECHECK.isActive());
        assertTrue(ResetPhase.TELEPORT.isActive());
        assertTrue(ResetPhase.UNLOAD.isActive());
        assertTrue(ResetPhase.DELETE.isActive());
        assertTrue(ResetPhase.RECREATE.isActive());
        assertTrue(ResetPhase.VERIFY.isActive());
    }

    @Test
    void phaseTransitionOrder() {
        assertEquals(ResetPhase.PRECHECK, ResetPhase.IDLE.next());
        assertEquals(ResetPhase.TELEPORT, ResetPhase.PRECHECK.next());
        assertEquals(ResetPhase.UNLOAD, ResetPhase.TELEPORT.next());
        assertEquals(ResetPhase.DELETE, ResetPhase.UNLOAD.next());
        assertEquals(ResetPhase.RECREATE, ResetPhase.DELETE.next());
        assertEquals(ResetPhase.VERIFY, ResetPhase.RECREATE.next());
        assertEquals(ResetPhase.COMPLETE, ResetPhase.VERIFY.next());
    }

    @Test
    void terminalPhaseStaysOnSelf() {
        assertEquals(ResetPhase.COMPLETE, ResetPhase.COMPLETE.next());
        assertEquals(ResetPhase.FAILED, ResetPhase.FAILED.next());
    }

    @Test
    void fromStringCaseInsensitive() {
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("IDLE"));
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("idle"));
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("Idle"));
        assertEquals(ResetPhase.PRECHECK, ResetPhase.fromString("precheck"));
        assertEquals(ResetPhase.PRECHECK, ResetPhase.fromString("PRECHECK"));
        assertEquals(ResetPhase.COMPLETE, ResetPhase.fromString("complete"));
        assertEquals(ResetPhase.FAILED, ResetPhase.fromString("failed"));
    }

    @Test
    void fromStringNullReturnsIdle() {
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString(null));
    }

    @Test
    void fromStringBlankReturnsIdle() {
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString(""));
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("   "));
    }

    @Test
    void fromStringInvalidValueReturnsIdle() {
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("INVALID"));
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("NotAPhase"));
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("123"));
    }

    @Test
    void fromStringTrimsWhitespace() {
        assertEquals(ResetPhase.IDLE, ResetPhase.fromString("   IDLE   "));
        assertEquals(ResetPhase.PRECHECK, ResetPhase.fromString("  PRECHECK  "));
        assertEquals(ResetPhase.COMPLETE, ResetPhase.fromString("\tCOMPLETE\t"));
    }

    @Test
    void allPhasesCanBeConvertedFromString() {
        for (ResetPhase phase : ResetPhase.values()) {
            assertEquals(phase, ResetPhase.fromString(phase.name()));
            assertEquals(phase, ResetPhase.fromString(phase.name().toLowerCase()));
        }
    }

    @Test
    void resolveResumePhaseNullSavedResumePhase() {
        assertEquals(ResetPhase.PRECHECK,
                ResetPhase.resolveResumePhase(ResetPhase.PRECHECK, ResetPhase.IDLE, null));
    }

    @Test
    void resolveResumePhaseInactiveSavedPhaseIgnored() {
        assertEquals(ResetPhase.UNLOAD,
                ResetPhase.resolveResumePhase(ResetPhase.UNLOAD, ResetPhase.IDLE, ResetPhase.IDLE));
    }

    @Test
    void resolveResumePhaseCompletePhaseReturnsIdle() {
        assertEquals(ResetPhase.IDLE,
                ResetPhase.resolveResumePhase(ResetPhase.COMPLETE, ResetPhase.IDLE, ResetPhase.IDLE));
    }

    @Test
    void resolveResumePhaseFailedPhaseWithFailedPhase() {
        // When savedResumePhase is active, it takes precedence
        assertEquals(ResetPhase.DELETE,
                ResetPhase.resolveResumePhase(ResetPhase.FAILED, ResetPhase.TELEPORT, ResetPhase.DELETE));
        // When savedResumePhase is inactive, failedPhase is used
        assertEquals(ResetPhase.TELEPORT,
                ResetPhase.resolveResumePhase(ResetPhase.FAILED, ResetPhase.TELEPORT, ResetPhase.FAILED));
    }

    @Test
    void resolveResumePhaseNullCurrentPhase() {
        assertEquals(ResetPhase.IDLE,
                ResetPhase.resolveResumePhase(null, ResetPhase.IDLE, null));
    }

    @Test
    void isTerminalOnlyForCompleteAndFailed() {
        assertFalse(ResetPhase.IDLE.isTerminal());
        assertFalse(ResetPhase.PRECHECK.isTerminal());
        assertFalse(ResetPhase.TELEPORT.isTerminal());
        assertFalse(ResetPhase.UNLOAD.isTerminal());
        assertFalse(ResetPhase.DELETE.isTerminal());
        assertFalse(ResetPhase.RECREATE.isTerminal());
        assertFalse(ResetPhase.VERIFY.isTerminal());
        assertTrue(ResetPhase.COMPLETE.isTerminal());
        assertTrue(ResetPhase.FAILED.isTerminal());
    }

    @Test
    void isActiveOnlyForMiddlePhases() {
        assertFalse(ResetPhase.IDLE.isActive());
        assertTrue(ResetPhase.PRECHECK.isActive());
        assertTrue(ResetPhase.TELEPORT.isActive());
        assertTrue(ResetPhase.UNLOAD.isActive());
        assertTrue(ResetPhase.DELETE.isActive());
        assertTrue(ResetPhase.RECREATE.isActive());
        assertTrue(ResetPhase.VERIFY.isActive());
        assertFalse(ResetPhase.COMPLETE.isActive());
        assertFalse(ResetPhase.FAILED.isActive());
    }

    private boolean arrayContains(ResetPhase[] array, ResetPhase phase) {
        for (ResetPhase p : array) {
            if (p == phase) return true;
        }
        return false;
    }
}
