package com.example.backend.drive.entity;

import com.example.backend.application.entity.Application;
import com.example.backend.company.entity.Company;
import com.example.backend.selection.entity.SelectionRound;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.OneToMany;

@Entity
@Table(name = "placement_drives")
public class PlacementDrive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String driveTitle;

    @Column(nullable = false, length = 150)
    private String jobRole;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal minimumCgpa;

    @Column(nullable = false)
    private Integer maximumBacklogs;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "placement_drive_eligible_branches",
            joinColumns = @JoinColumn(name = "placement_drive_id"))
    @Column(name = "branch", nullable = false, length = 100)
    private List<String> eligibleBranches = new ArrayList<>();

    @Column(nullable = false)
    private Integer graduationYear;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal packageLpa;

    @Column(nullable = false)
    private LocalDate applicationStartDate;

    @Column(nullable = false)
    private LocalDate applicationEndDate;

    @Column(nullable = false)
    private LocalDate driveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlacementDriveStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "placementDrive")
    private List<Application> applications = new ArrayList<>();

    @OneToMany(mappedBy = "placementDrive")
    private List<SelectionRound> selectionRounds = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getDriveTitle() { return driveTitle; }
    public void setDriveTitle(String driveTitle) { this.driveTitle = driveTitle; }
    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getMinimumCgpa() { return minimumCgpa; }
    public void setMinimumCgpa(BigDecimal minimumCgpa) { this.minimumCgpa = minimumCgpa; }
    public Integer getMaximumBacklogs() { return maximumBacklogs; }
    public void setMaximumBacklogs(Integer maximumBacklogs) { this.maximumBacklogs = maximumBacklogs; }
    public List<String> getEligibleBranches() { return eligibleBranches; }
    public void setEligibleBranches(List<String> eligibleBranches) {
        this.eligibleBranches = new ArrayList<>(eligibleBranches);
    }
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
    public PlacementDriveStatus getStatus() { return status; }
    public void setStatus(PlacementDriveStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<Application> getApplications() { return applications; }
    public void setApplications(List<Application> applications) { this.applications = applications; }
    public List<SelectionRound> getSelectionRounds() { return selectionRounds; }
    public void setSelectionRounds(List<SelectionRound> selectionRounds) {
        this.selectionRounds = selectionRounds;
    }
}
