package com.lozaine.ResourceWorldResetter;

public enum ResetPhase {
    IDLE,
    PRECHECK,
    TELEPORT,
    UNLOAD,
    DELETE,
    RECREATE,
    VERIFY,
    COMPLETE,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETE || this == FAILED;
    }

    public boolean isActive() {
        return !isTerminal() && this != IDLE;
    }

    public ResetPhase next() {
        return switch (this) {
            case IDLE -> PRECHECK;
            case PRECHECK -> TELEPORT;
            case TELEPORT -> UNLOAD;
            case UNLOAD -> DELETE;
            case DELETE -> RECREATE;
            case RECREATE -> VERIFY;
            case VERIFY -> COMPLETE;
            case COMPLETE, FAILED -> this;
        };
    }

    public static ResetPhase fromString(String value) {
        if (value == null || value.isBlank()) {
            return IDLE;
        }

        for (ResetPhase phase : values()) {
            if (phase.name().equalsIgnoreCase(value.trim())) {
                return phase;
            }
        }

        return IDLE;
    }

    public static ResetPhase resolveResumePhase(ResetPhase currentPhase, ResetPhase failedPhase, ResetPhase savedResumePhase) {
        if (savedResumePhase != null && savedResumePhase.isActive()) {
            return savedResumePhase;
        }

        if (currentPhase == FAILED && failedPhase != null && failedPhase.isActive()) {
            return failedPhase;
        }

        return currentPhase != null && currentPhase.isActive() ? currentPhase : IDLE;
    }
}
