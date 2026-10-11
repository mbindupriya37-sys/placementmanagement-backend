package com.example.backend.drive.repository;

import com.example.backend.drive.entity.PlacementDrive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, Long> {
    List<PlacementDrive> findAllByCompany_IdOrderByCreatedAtDesc(Long companyId);
}
