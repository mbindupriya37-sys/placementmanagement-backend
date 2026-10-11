package com.example.backend.offer.dto;

import com.example.backend.drive.entity.EmploymentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OfferUpdateRequest {

    @NotBlank
    @Size(max = 200)
    private String jobTitle;
    @NotNull
    private EmploymentType employmentType;
    @Size(max = 200)
    private String location;
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal annualPackageLpa;
    @NotNull
    private LocalDate joiningDate;
    @NotNull
    private LocalDate expiryDate;
    @Size(max = 10000)
    private String notes;

    @AssertTrue(message = "Expiry date must be on or after the offer date")
    public boolean isExpiryDateValid() {
        return expiryDate == null || !expiryDate.isBefore(LocalDate.now());
    }

    @AssertTrue(message = "Joining date must be on or after the offer date")
    public boolean isJoiningDateValid() {
        return joiningDate == null || !joiningDate.isBefore(LocalDate.now());
    }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getAnnualPackageLpa() { return annualPackageLpa; }
    public void setAnnualPackageLpa(BigDecimal annualPackageLpa) { this.annualPackageLpa = annualPackageLpa; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
