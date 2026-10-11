package com.example.backend.student.service;

import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.student.dto.StudentProfileRequest;
import com.example.backend.student.dto.StudentProfileResponse;
import com.example.backend.student.dto.StudentResumeFile;
import com.example.backend.student.entity.StudentProfile;
import com.example.backend.student.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public class StudentProfileService {

    private static final Logger logger = LoggerFactory.getLogger(StudentProfileService.class);

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final StudentResumeStorageService resumeStorageService;

    public StudentProfileService(StudentProfileRepository studentProfileRepository, UserRepository userRepository,
                                 StudentResumeStorageService resumeStorageService) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.resumeStorageService = resumeStorageService;
    }

    @Transactional
    public StudentProfileResponse create(String email, StudentProfileRequest request) {
        if (studentProfileRepository.existsByUserEmail(email)) {
            throw new BadRequestException("Student profile already exists");
        }

        User user = findUser(email);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        user.setStudentProfile(profile);
        profile.setResumeUrl("");
        applyRequest(profile, request);
        return toResponse(studentProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse get(String email) {
        return toResponse(findProfile(email));
    }

    @Transactional
    public StudentProfileResponse update(String email, StudentProfileRequest request) {
        StudentProfile profile = findProfile(email);
        applyRequest(profile, request);
        return toResponse(studentProfileRepository.save(profile));
    }

    @Transactional
    public StudentProfileResponse uploadResume(String email, MultipartFile file) throws IOException {
        StudentProfile profile = findProfile(email);
        Long studentId = profile.getUser().getId();
        String previousResumeUrl = profile.getResumeUrl();
        StudentResumeStorageService.StoredResume storedResume = resumeStorageService.store(studentId, file);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deletePreviousResume(studentId, previousResumeUrl);
                } else {
                    deleteResume(studentId, storedResume.filename());
                }
            }
        });

        profile.setResumeUrl(storedResume.resumeUrl());
        return toResponse(studentProfileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public StudentResumeFile getResume(String email, String filename) {
        StudentProfile profile = findProfile(email);
        String expectedResumeUrl = StudentResumeStorageService.RESUME_URL_PREFIX + filename;
        if (!expectedResumeUrl.equals(profile.getResumeUrl())) {
            throw new ResourceNotFoundException("Resume not found");
        }
        Path resumePath = resumeStorageService.resolve(profile.getUser().getId(), filename);
        return new StudentResumeFile(resumePath, filename.substring(37));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private StudentProfile findProfile(String email) {
        return studentProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private void deletePreviousResume(Long studentId, String previousResumeUrl) {
        if (!resumeStorageService.isManagedResumeUrl(previousResumeUrl)) {
            return;
        }
        String previousFilename = previousResumeUrl.substring(
                StudentResumeStorageService.RESUME_URL_PREFIX.length());
        deleteResume(studentId, previousFilename);
    }

    private void deleteResume(Long studentId, String filename) {
        try {
            resumeStorageService.delete(studentId, filename);
        } catch (IOException exception) {
            logger.warn("Could not clean up resume file for student {}", studentId, exception);
        }
    }

    private void applyRequest(StudentProfile profile, StudentProfileRequest request) {
        profile.setPhoneNumber(request.getPhoneNumber().trim());
        profile.setCollege(request.getCollege().trim());
        profile.setBranch(request.getBranch().trim());
        profile.setGraduationYear(request.getGraduationYear());
        profile.setCgpa(request.getCgpa());
        profile.setBacklogs(request.getBacklogs());
        profile.setSkills(request.getSkills().stream().map(String::trim).toList());
        if (request.getResumeUrl() != null) {
            profile.setResumeUrl(request.getResumeUrl().isBlank() ? "" : request.getResumeUrl().trim());
        }
    }

    private StudentProfileResponse toResponse(StudentProfile profile) {
        StudentProfileResponse response = new StudentProfileResponse();
        response.setId(profile.getId());
        response.setPhoneNumber(profile.getPhoneNumber());
        response.setCollege(profile.getCollege());
        response.setBranch(profile.getBranch());
        response.setGraduationYear(profile.getGraduationYear());
        response.setCgpa(profile.getCgpa());
        response.setBacklogs(profile.getBacklogs());
        response.setSkills(List.copyOf(profile.getSkills()));
        response.setResumeUrl(profile.getResumeUrl());
        return response;
    }
}
