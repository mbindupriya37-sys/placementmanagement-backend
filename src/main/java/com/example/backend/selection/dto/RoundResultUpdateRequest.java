package com.example.backend.selection.dto;

import com.example.backend.selection.entity.RoundResultStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class RoundResultUpdateRequest {

    @NotNull
    private RoundResultStatus result;

    @DecimalMin("0.00")
    private BigDecimal score;

    @Size(max = 10000)
    private String remarks;

    public RoundResultStatus getResult() { return result; }
    public void setResult(RoundResultStatus result) { this.result = result; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
