package com.example.backend.selection.dto;

import com.example.backend.selection.entity.SelectionRoundStatus;
import jakarta.validation.constraints.NotNull;

public class SelectionRoundStatusRequest {

    @NotNull
    private SelectionRoundStatus status;

    public SelectionRoundStatus getStatus() { return status; }
    public void setStatus(SelectionRoundStatus status) { this.status = status; }
}
