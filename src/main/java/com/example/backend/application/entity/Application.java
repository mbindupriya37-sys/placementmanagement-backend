package com.example.backend.application.entity;

import com.example.backend.auth.entity.User;
import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.selection.entity.RoundResult;
import com.example.backend.scheduling.entity.InterviewSchedule;
import com.example.backend.finaldecision.entity.FinalDecisionRecord;
import com.example.backend.offer.entity.Offer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_student_drive",
                columnNames = {"student_id", "placement_drive_id"}))
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "placement_drive_id", nullable = false)
    private PlacementDrive placementDrive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "application")
    private List<RoundResult> roundResults = new ArrayList<>();

    @OneToMany(mappedBy = "application")
    private List<InterviewSchedule> interviewSchedules = new ArrayList<>();

    @OneToOne(mappedBy = "application", fetch = FetchType.LAZY)
    private FinalDecisionRecord finalDecisionRecord;

    @OneToOne(mappedBy = "application", fetch = FetchType.LAZY)
    private Offer offer;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        appliedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public PlacementDrive getPlacementDrive() { return placementDrive; }
    public void setPlacementDrive(PlacementDrive placementDrive) { this.placementDrive = placementDrive; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<RoundResult> getRoundResults() { return roundResults; }
    public void setRoundResults(List<RoundResult> roundResults) { this.roundResults = roundResults; }
    public List<InterviewSchedule> getInterviewSchedules() { return interviewSchedules; }
    public void setInterviewSchedules(List<InterviewSchedule> interviewSchedules) {
        this.interviewSchedules = interviewSchedules;
    }
    public FinalDecisionRecord getFinalDecisionRecord() { return finalDecisionRecord; }
    public void setFinalDecisionRecord(FinalDecisionRecord finalDecisionRecord) {
        this.finalDecisionRecord = finalDecisionRecord;
    }
    public Offer getOffer() { return offer; }
    public void setOffer(Offer offer) { this.offer = offer; }
}
