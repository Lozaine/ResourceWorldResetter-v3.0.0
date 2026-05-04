package com.lozaine.ResourceWorldResetter.regression;

import com.lozaine.ResourceWorldResetter.ResetPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionResetStateMachineRegressionTest {

    @Test
    void regionResumePrefersSavedActiveResumePhase() {
        ResetPhase resolved = ResetPhase.resolveResumePhase(ResetPhase.FAILED, ResetPhase.TELEPORT, ResetPhase.DELETE);

        assertEquals(ResetPhase.DELETE, resolved);
    }

    @Test
    void regionResumeFallsBackToFailedActivePhaseWhenNoSavedResumePhase() {
        ResetPhase resolved = ResetPhase.resolveResumePhase(ResetPhase.FAILED, ResetPhase.UNLOAD, ResetPhase.IDLE);

        assertEquals(ResetPhase.UNLOAD, resolved);
    }

    @Test
    void regionResumeFallsBackToCurrentActivePhaseWhenNoSavedOrFailedPhase() {
        ResetPhase resolved = ResetPhase.resolveResumePhase(ResetPhase.RECREATE, null, null);

        assertEquals(ResetPhase.RECREATE, resolved);
    }

    @Test
    void regionResumeFallsBackToIdleWhenNoActivePhaseExists() {
        ResetPhase resolved = ResetPhase.resolveResumePhase(ResetPhase.IDLE, null, ResetPhase.COMPLETE);

        assertEquals(ResetPhase.IDLE, resolved);
    }
}