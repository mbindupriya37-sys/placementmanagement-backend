package com.example.backend.finaldecision.dto;

import com.example.backend.finaldecision.entity.FinalDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FinalDecisionRequest {

    @NotNull
    private FinalDecision decision;

    @Size(max = 2000)
    private String remarks;

    public FinalDecision getDecision() { return decision; }
    public void setDecision(FinalDecision decision) { this.decision = decision; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
