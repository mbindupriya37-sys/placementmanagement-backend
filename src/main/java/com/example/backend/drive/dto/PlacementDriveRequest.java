package com.example.backend.drive.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

public class PlacementDriveRequest {

    @NotNull(message = "Company is required")
    @Min(1)
    private Long companyId;

    @NotBlank(message = "Drive title is required")
    @Size(max = 200)
    private String driveTitle;

    @NotBlank(message = "Job role is required")
    @Size(max = 150)
    private String jobRole;

    @Size(max = 20000)
    private String jobDescription;

    @NotBlank
    @Pattern(regexp = "FULL_TIME|INTERNSHIP|CONTRACT",
            message = "Employment type must be FULL_TIME, INTERNSHIP, or CONTRACT")
    private String employmentType;

    @Size(max = 200)
    private String location;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("10.00")
    private BigDecimal minimumCgpa;

    @NotNull
    @Min(0)
    private Integer maximumBacklogs;

    @NotEmpty(message = "At least one eligible branch is required")
    @Size(max = 100)
    private List<@NotBlank @Size(max = 100) String> eligibleBranches;

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer graduationYear;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal packageLpa;

    @NotNull
    private LocalDate applicationStartDate;

    @NotNull
    private LocalDate applicationEndDate;

    @NotNull
    private LocalDate driveDate;

    @NotBlank
    @Pattern(regexp = "DRAFT|OPEN|CLOSED|COMPLETED|CANCELLED",
            message = "Status must be DRAFT, OPEN, CLOSED, COMPLETED, or CANCELLED")
    private String status;

    @AssertTrue(message = "Graduation year must be from 2000 through ten years from the current year")
    public boolean isGraduationYearReasonable() {
        return graduationYear == null
                || (graduationYear >= 2000 && graduationYear <= Year.now().getValue() + 10);
    }

    @AssertTrue(message = "Application start date must be before application end date")
    public boolean isApplicationStartBeforeEnd() {
        return applicationStartDate == null || applicationEndDate == null
                || applicationStartDate.isBefore(applicationEndDate);
    }

    @AssertTrue(message = "Application end date must not be after the drive date")
    public boolean isApplicationEndOnOrBeforeDrive() {
        return applicationEndDate == null || driveDate == null
                || !applicationEndDate.isAfter(driveDate);
    }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String driveTitle) { this.driveTitle = driveTitle; }
    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getMinimumCgpa() { return minimumCgpa; }
    public void setMinimumCgpa(BigDecimal minimumCgpa) { this.minimumCgpa = minimumCgpa; }
    public Integer getMaximumBacklogs() { return maximumBacklogs; }
    public void setMaximumBacklogs(Integer maximumBacklogs) { this.maximumBacklogs = maximumBacklogs; }
    public List<String> getEligibleBranches() { return eligibleBranches; }
    public void setEligibleBranches(List<String> eligibleBranches) { this.eligibleBranches = eligibleBranches; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
    public BigDecimal getPackageLpa() { return packageLpa; }
    public void setPackageLpa(BigDecimal packageLpa) { this.packageLpa = packageLpa; }
    public LocalDate getApplicationStartDate() { return applicationStartDate; }
    public void setApplicationStartDate(LocalDate applicationStartDate) {
        this.applicationStartDate = applicationStartDate;
    }
    public LocalDate getApplicationEndDate() { return applicationEndDate; }
    public void setApplicationEndDate(LocalDate applicationEndDate) { this.applicationEndDate = applicationEndDate; }
    public LocalDate getDriveDate() { return driveDate; }
    public void setDriveDate(LocalDate driveDate) { this.driveDate = driveDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
