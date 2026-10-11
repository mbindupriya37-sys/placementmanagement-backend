package com.example.backend.finaldecision.dto;

import com.example.backend.finaldecision.entity.FinalDecision;

import java.time.LocalDateTime;

public class FinalDecisionResponse {

    private Long decisionId;
    private Long applicationId;
    private String studentName;
    private String studentEmail;
    private Long driveId;
    private String driveTitle;
    private FinalDecision decision;
    private String remarks;
    private LocalDateTime decidedAt;
    private String decidedByName;
    private String decidedByEmail;

    public Long getDecisionId() { return decisionId; }
    public void setDecisionId(Long decisionId) { this.decisionId = decisionId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    public Long getDriveId() { return driveId; }
    public void setDriveId(Long driveId) { this.driveId = driveId; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String driveTitle) { this.driveTitle = driveTitle; }
    public FinalDecision getDecision() { return decision; }
    public void setDecision(FinalDecision decision) { this.decision = decision; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecidedByName() { return decidedByName; }
    public void setDecidedByName(String decidedByName) { this.decidedByName = decidedByName; }
    public String getDecidedByEmail() { return decidedByEmail; }
    public void setDecidedByEmail(String decidedByEmail) { this.decidedByEmail = decidedByEmail; }
}
