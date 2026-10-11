package com.example.backend.recruiter.repository;

import com.example.backend.recruiter.entity.CandidateRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateRecommendationRepository extends JpaRepository<CandidateRecommendation, Long> {
    boolean existsByApplication_IdAndRecruiter_Email(Long applicationId, String email);
    List<CandidateRecommendation> findAllByRecruiter_EmailOrderBySubmittedAtDesc(String email);
    List<CandidateRecommendation> findAllByOrderBySubmittedAtDesc();
}
