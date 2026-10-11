package com.example.backend.student;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.student.controller.StudentProfileController;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import com.example.backend.student.service.StudentProfileService;
import com.example.backend.student.service.StudentResumeStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentResumeSecurityTests {

    @TempDir
    private Path temporaryDirectory;

    @Autowired
    private StudentProfileController studentProfileController;

    @Autowired
    private StudentProfileService studentProfileService;

    @Autowired
    private StudentResumeStorageService resumeStorageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Test
    @WithMockUser(username = "resume-owner@example.com", roles = "STUDENT")
    void uploadsAndReadsOnlyTheAuthenticatedStudentsResume() throws IOException {
        createStudentWithProfile("resume-owner@example.com");
        createStudentWithProfile("resume-other@example.com");
        byte[] pdfContent = "%PDF-1.4\nresume".getBytes();

        var uploaded = studentProfileController.uploadResume(
                new MockMultipartFile("resume", "my-resume.pdf", "application/pdf", pdfContent),
                SecurityContextHolder.getContext().getAuthentication()).getData();
        String filename = uploaded.getResumeUrl()
                .substring(StudentResumeStorageService.RESUME_URL_PREFIX.length());
        var response = studentProfileController.getResume(filename,
                SecurityContextHolder.getContext().getAuthentication());

        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertArrayEquals(pdfContent, Files.readAllBytes(((FileSystemResource) response.getBody()).getFile().toPath()));
        assertThrows(ResourceNotFoundException.class,
                () -> studentProfileService.getResume("resume-other@example.com", filename));

        resumeStorageService.delete(userRepository.findByEmail("resume-owner@example.com").orElseThrow().getId(),
                filename);
    }

    @Test
    @WithMockUser(username = "resume-owner@example.com", roles = "STUDENT")
    void profileGetSucceedsWhenResumeUrlIsNull() {
        createStudentWithProfile("resume-owner@example.com");

        var response = studentProfileController.get(SecurityContextHolder.getContext().getAuthentication());

        assertNull(response.getData().getResumeUrl());
        assertEquals(List.of("Java"), response.getData().getSkills());
    }

    @Test
    void resumeStorageDoesNotCreateUploadDirectoryUntilNeeded() {
        Path uploadRoot = temporaryDirectory.resolve("not-created");

        new StudentResumeStorageService(uploadRoot.toString());

        assertFalse(Files.exists(uploadRoot));
    }

    @Test
    @WithMockUser(username = "resume-owner@example.com", roles = "STUDENT")
    void replacementMakesPreviousResumeUnavailable() throws IOException {
        createStudentWithProfile("resume-owner@example.com");
        Long studentId = userRepository.findByEmail("resume-owner@example.com").orElseThrow().getId();

        var first = studentProfileService.uploadResume("resume-owner@example.com",
                pdf("first.pdf", "%PDF-1.4\nfirst"));
        String firstFilename = first.getResumeUrl()
                .substring(StudentResumeStorageService.RESUME_URL_PREFIX.length());
        var replacement = studentProfileService.uploadResume("resume-owner@example.com",
                pdf("replacement.pdf", "%PDF-1.4\nreplacement"));
        String replacementFilename = replacement.getResumeUrl()
                .substring(StudentResumeStorageService.RESUME_URL_PREFIX.length());

        assertThrows(ResourceNotFoundException.class,
                () -> studentProfileService.getResume("resume-owner@example.com", firstFilename));
        assertEquals("replacement.pdf", studentProfileService
                .getResume("resume-owner@example.com", replacementFilename).filename());
        resumeStorageService.delete(studentId, firstFilename);
        resumeStorageService.delete(studentId, replacementFilename);
    }

    @Test
    @WithMockUser(username = "resume-owner@example.com", roles = "STUDENT")
    void rejectsNonPdfFilesAndOversizedUploads() throws IOException {
        createStudentWithProfile("resume-owner@example.com");

        assertThrows(BadRequestException.class, () -> studentProfileService.uploadResume(
                "resume-owner@example.com", pdf("resume.txt", "%PDF-1.4\ncontent")));
        assertThrows(BadRequestException.class, () -> studentProfileService.uploadResume(
                "resume-owner@example.com", pdf("fake.pdf", "not a PDF")));
        assertThrows(BadRequestException.class, () -> studentProfileService.uploadResume(
                "resume-owner@example.com",
                new MockMultipartFile("resume", "large.pdf", "application/pdf",
                        new byte[(int) StudentResumeStorageService.MAX_FILE_SIZE_BYTES + 1])));
    }

    @Test
    @WithMockUser(roles = "TPO_ADMIN")
    void nonStudentRoleCannotUploadAResume() {
        assertThrows(AccessDeniedException.class, () -> studentProfileController.uploadResume(
                pdf("resume.pdf", "%PDF-1.4\ncontent"),
                SecurityContextHolder.getContext().getAuthentication()));
    }

    private User createStudentWithProfile(String email) {
        User user = createStudent(email);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setPhoneNumber("+1 555 123 4567");
        profile.setCollege("Example University");
        profile.setBranch("Computer Science");
        profile.setGraduationYear(2027);
        profile.setCgpa(new BigDecimal("8.00"));
        profile.setBacklogs(0);
        profile.setSkills(List.of("Java"));
        profile.setResumeUrl(null);
        studentProfileRepository.saveAndFlush(profile);
        return user;
    }

    private User createStudent(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName("Resume Test Student");
        user.setPassword("encoded-password");
        user.setRole(Role.STUDENT);
        return userRepository.saveAndFlush(user);
    }

    private MockMultipartFile pdf(String filename, String contents) {
        return new MockMultipartFile("resume", filename, "application/pdf", contents.getBytes());
    }

}
