package com.example.backend.drive;

import com.example.backend.drive.controller.PlacementDriveController;
import com.example.backend.drive.dto.PlacementDriveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class PlacementDriveSecurityTests {

    @Autowired
    private PlacementDriveController placementDriveController;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanListDrives() {
        placementDriveController.getAll(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotCreateDrive() {
        assertThrows(AccessDeniedException.class,
                () -> placementDriveController.create(new PlacementDriveRequest()));
    }
}
