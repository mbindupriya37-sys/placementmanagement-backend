package com.example.backend.student.repository;

import com.example.backend.student.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUserEmail(String email);
    boolean existsByUserEmail(String email);
    List<StudentProfile> findAllByUser_EmailIn(List<String> emails);
}
