package com.example.backend.recruiter.dto;

import com.example.backend.application.entity.ApplicationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RecruiterApplicationResponse {

    private Long applicationId;
    private Long driveId;
    private String driveTitle;
    private String studentName;
    private String branch;
    private BigDecimal cgpa;
    private List<String> skills;
    private boolean resumeAvailable;
    private ApplicationStatus status;
    private LocalDateTime appliedAt;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getDriveId() { return driveId; }
    public void setDriveId(Long driveId) { this.driveId = driveId; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String driveTitle) { this.driveTitle = driveTitle; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public BigDecimal getCgpa() { return cgpa; }
    public void setCgpa(BigDecimal cgpa) { this.cgpa = cgpa; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public boolean isResumeAvailable() { return resumeAvailable; }
    public void setResumeAvailable(boolean resumeAvailable) { this.resumeAvailable = resumeAvailable; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
