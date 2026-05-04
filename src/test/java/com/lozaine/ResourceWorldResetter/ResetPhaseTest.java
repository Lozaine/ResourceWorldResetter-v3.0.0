package com.lozaine.ResourceWorldResetter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}