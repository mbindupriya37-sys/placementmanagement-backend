package com.example.backend.student;

import com.example.backend.auth.entity.Role;
import com.example.backend.auth.entity.User;
import com.example.backend.auth.repository.UserRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.student.dto.StudentProfileRequest;
import com.example.backend.student.dto.StudentProfileResponse;
import com.example.backend.student.service.StudentProfileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentProfileServiceTests {

    @Autowired
    private StudentProfileService studentProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsReadsAndUpdatesProfileForAuthenticatedEmail() {
        String email = createStudent("student@example.com");

        StudentProfileResponse created = studentProfileService.create(email, profileRequest("3.75"));
        assertEquals(email, userRepository.findByEmail(email).orElseThrow().getStudentProfile().getUser().getEmail());
        assertEquals("3.75", created.getCgpa().toPlainString());
        assertEquals(List.of("Java", "Spring"), created.getSkills());
        entityManager.flush();
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(id) FROM student_profile_skills WHERE student_profile_id = ?",
                Integer.class, created.getId()));

        StudentProfileResponse read = studentProfileService.get(email);
        assertEquals(created.getId(), read.getId());

        StudentProfileRequest updateRequest = profileRequest("3.90");
        updateRequest.setSkills(List.of("Kotlin"));
        StudentProfileResponse updated = studentProfileService.update(email, updateRequest);
        assertEquals(created.getId(), updated.getId());
        assertEquals("3.90", updated.getCgpa().toPlainString());
        assertEquals(List.of("Kotlin"), updated.getSkills());
        entityManager.flush();
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(id) FROM student_profile_skills WHERE student_profile_id = ?",
                Integer.class, created.getId()));
    }

    @Test
    void preventsCreatingMoreThanOneProfileForAUser() {
        String email = createStudent("duplicate@example.com");
        studentProfileService.create(email, profileRequest("3.75"));

        assertThrows(BadRequestException.class,
                () -> studentProfileService.create(email, profileRequest("3.90")));
    }

    @Test
    void profileCanBeCreatedReadAndUpdatedWithoutAResume() {
        String email = createStudent("no-resume@example.com");
        StudentProfileRequest request = profileRequest("3.75");
        request.setResumeUrl(null);

        StudentProfileResponse created = studentProfileService.create(email, request);
        assertEquals("", created.getResumeUrl());
        assertEquals("", studentProfileService.get(email).getResumeUrl());

        request.setCgpa(new BigDecimal("3.90"));
        request.setResumeUrl("");
        StudentProfileResponse updated = studentProfileService.update(email, request);
        assertEquals("3.90", updated.getCgpa().toPlainString());
        assertEquals("", updated.getResumeUrl());
        assertEquals("", studentProfileService.get(email).getResumeUrl());
    }

    private String createStudent(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test Student");
        user.setPassword("encoded-password");
        user.setRole(Role.STUDENT);
        return userRepository.save(user).getEmail();
    }

    private StudentProfileRequest profileRequest(String cgpa) {
        StudentProfileRequest request = new StudentProfileRequest();
        request.setPhoneNumber("+1 555 123 4567");
        request.setCollege("Example University");
        request.setBranch("Computer Science");
        request.setGraduationYear(2027);
        request.setCgpa(new BigDecimal(cgpa));
        request.setBacklogs(0);
        request.setSkills(List.of("Java", "Spring"));
        request.setResumeUrl("resumes/student.pdf");
        return request;
    }
}
