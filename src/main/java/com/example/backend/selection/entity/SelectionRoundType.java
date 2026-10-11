package com.example.backend.selection.entity;

public enum SelectionRoundType {
    APTITUDE,
    TECHNICAL,
    HR,
    GROUP_DISCUSSION,
    CODING,
    OTHER;

    public boolean isInterviewType() {
        return this == TECHNICAL || this == HR || this == GROUP_DISCUSSION;
    }
}
