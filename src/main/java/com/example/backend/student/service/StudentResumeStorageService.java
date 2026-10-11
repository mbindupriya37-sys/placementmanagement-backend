package com.example.backend.student.service;

import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class StudentResumeStorageService {

    public static final String RESUME_URL_PREFIX = "/api/student/profile/resume/";
    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[0-9a-fA-F-]{36}-[A-Za-z0-9._-]+\\.pdf$");

    private final Path uploadRoot;

    public StudentResumeStorageService(@Value("${app.upload.dir:uploads}") String uploadDirectory) {
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public StoredResume store(Long studentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Select a non-empty PDF file.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Resume PDF must not exceed 5 MB.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Resume must be a PDF file.");
        }

        byte[] content = file.getBytes();
        if (content.length < 5 || content[0] != '%'
                || content[1] != 'P' || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
            throw new BadRequestException("The uploaded file does not contain a valid PDF header.");
        }

        String safeOriginalFilename = sanitizeFilename(originalFilename);
        String filename = UUID.randomUUID() + "-" + safeOriginalFilename;
        Path ownerDirectory = ownerDirectory(studentId);
        Files.createDirectories(ownerDirectory);
        Path destination = ownerDirectory.resolve(filename).normalize();
        if (!destination.startsWith(ownerDirectory)) {
            throw new BadRequestException("Invalid resume filename.");
        }
        Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new StoredResume(filename, RESUME_URL_PREFIX + filename);
    }

    public Path resolve(Long studentId, String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            throw new ResourceNotFoundException("Resume not found");
        }
        Path ownerDirectory = ownerDirectory(studentId);
        Path resume = ownerDirectory.resolve(filename).normalize();
        if (!resume.startsWith(ownerDirectory) || !Files.isRegularFile(resume, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourceNotFoundException("Resume not found");
        }
        return resume;
    }

    public boolean isManagedResumeUrl(String resumeUrl) {
        if (resumeUrl == null || !resumeUrl.startsWith(RESUME_URL_PREFIX)) {
            return false;
        }
        return SAFE_FILENAME.matcher(resumeUrl.substring(RESUME_URL_PREFIX.length())).matches();
    }

    public void delete(Long studentId, String filename) throws IOException {
        if (filename != null && SAFE_FILENAME.matcher(filename).matches()) {
            Files.deleteIfExists(ownerDirectory(studentId).resolve(filename).normalize());
        }
    }

    private Path ownerDirectory(Long studentId) {
        return uploadRoot.resolve("student-resumes").resolve(studentId.toString()).normalize();
    }

    private String sanitizeFilename(String originalFilename) {
        String basename = originalFilename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        String safe = basename.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        if (safe.length() > 100) {
            safe = safe.substring(safe.length() - 100);
        }
        if (safe.isBlank() || !safe.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return "resume.pdf";
        }
        String filenameWithoutExtension = safe.substring(0, safe.lastIndexOf('.'));
        return filenameWithoutExtension + ".pdf";
    }

    public record StoredResume(String filename, String resumeUrl) { }
}
