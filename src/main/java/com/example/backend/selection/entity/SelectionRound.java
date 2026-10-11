package com.example.backend.selection.entity;

import com.example.backend.drive.entity.PlacementDrive;
import com.example.backend.scheduling.entity.InterviewSchedule;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "selection_rounds",
        uniqueConstraints = @UniqueConstraint(name = "uk_selection_round_drive_order",
                columnNames = {"placement_drive_id", "round_order"}))
public class SelectionRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "placement_drive_id", nullable = false)
    private PlacementDrive placementDrive;

    @Column(nullable = false, length = 200)
    private String roundName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SelectionRoundType roundType;

    @Column(name = "round_order", nullable = false)
    private Integer roundOrder;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime scheduledDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SelectionRoundStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "selectionRound")
    private List<RoundResult> results = new ArrayList<>();

    @OneToMany(mappedBy = "selectionRound")
    private List<InterviewSchedule> interviewSchedules = new ArrayList<>();

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
    public PlacementDrive getPlacementDrive() { return placementDrive; }
    public void setPlacementDrive(PlacementDrive placementDrive) { this.placementDrive = placementDrive; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public SelectionRoundType getRoundType() { return roundType; }
    public void setRoundType(SelectionRoundType roundType) { this.roundType = roundType; }
    public Integer getRoundOrder() { return roundOrder; }
    public void setRoundOrder(Integer roundOrder) { this.roundOrder = roundOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getScheduledDateTime() { return scheduledDateTime; }
    public void setScheduledDateTime(LocalDateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }
    public SelectionRoundStatus getStatus() { return status; }
    public void setStatus(SelectionRoundStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<RoundResult> getResults() { return results; }
    public void setResults(List<RoundResult> results) { this.results = results; }
    public List<InterviewSchedule> getInterviewSchedules() { return interviewSchedules; }
    public void setInterviewSchedules(List<InterviewSchedule> interviewSchedules) {
        this.interviewSchedules = interviewSchedules;
    }
}
