package com.example.backend.drive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "placement_drive_eligible_branches")
public class PlacementDriveEligibleBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "placement_drive_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_drive_eligible_branch_drive"))
    private PlacementDrive placementDrive;

    @Column(name = "branch", nullable = false, length = 100)
    private String branch;

    protected PlacementDriveEligibleBranch() {
    }

    PlacementDriveEligibleBranch(PlacementDrive placementDrive, String branch) {
        this.placementDrive = placementDrive;
        this.branch = branch;
    }

    public Long getId() { return id; }
    public PlacementDrive getPlacementDrive() { return placementDrive; }
    public String getBranch() { return branch; }
}
