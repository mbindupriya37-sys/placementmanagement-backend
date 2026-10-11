package com.example.backend.selection.service;

import com.example.backend.application.entity.Application;
import com.example.backend.application.entity.ApplicationStatus;
import com.example.backend.application.repository.ApplicationRepository;
import com.example.backend.common.exception.BadRequestException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.selection.dto.RoundResultRequest;
import com.example.backend.selection.dto.RoundResultResponse;
import com.example.backend.selection.dto.RoundResultUpdateRequest;
import com.example.backend.selection.entity.RoundResult;
import com.example.backend.selection.entity.SelectionRound;
import com.example.backend.selection.repository.RoundResultRepository;
import com.example.backend.selection.repository.SelectionRoundRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoundResultService {

    private final RoundResultRepository roundResultRepository;
    private final SelectionRoundRepository selectionRoundRepository;
    private final ApplicationRepository applicationRepository;

    public RoundResultService(RoundResultRepository roundResultRepository,
                              SelectionRoundRepository selectionRoundRepository,
                              ApplicationRepository applicationRepository) {
        this.roundResultRepository = roundResultRepository;
        this.selectionRoundRepository = selectionRoundRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public RoundResultResponse create(Long roundId, RoundResultRequest request) {
        SelectionRound round = selectionRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Selection round", "id", roundId));
        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", request.getApplicationId()));

        if (!application.getPlacementDrive().getId().equals(round.getPlacementDrive().getId())) {
            throw new BadRequestException("Application does not belong to the placement drive for this round");
        }
        if (application.getStatus() != ApplicationStatus.SHORTLISTED) {
            throw new BadRequestException("Only SHORTLISTED applications can receive round results");
        }
        if (roundResultRepository.existsByApplication_IdAndSelectionRound_Id(application.getId(), roundId)) {
            throw new BadRequestException("A result already exists for this application and round");
        }

        RoundResult result = new RoundResult();
        result.setApplication(application);
        result.setSelectionRound(round);
        result.setResult(request.getResult());
        result.setScore(request.getScore());
        result.setRemarks(trimToNull(request.getRemarks()));
        try {
            return toResponse(roundResultRepository.saveAndFlush(result));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("A result already exists for this application and round");
        }
    }

    @Transactional(readOnly = true)
    public List<RoundResultResponse> getForRound(Long roundId) {
        if (!selectionRoundRepository.existsById(roundId)) {
            throw new ResourceNotFoundException("Selection round", "id", roundId);
        }
        return roundResultRepository.findAllBySelectionRound_IdOrderByApplication_Student_NameAsc(roundId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RoundResultResponse> getForStudentApplication(Long applicationId, String email) {
        applicationRepository.findByIdAndStudent_Email(applicationId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Application", "id", applicationId));
        return roundResultRepository
                .findAllByApplication_IdAndApplication_Student_EmailOrderBySelectionRound_RoundOrderAsc(
                        applicationId, email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoundResultResponse update(Long resultId, RoundResultUpdateRequest request) {
        RoundResult result = roundResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Round result", "id", resultId));
        result.setResult(request.getResult());
        result.setScore(request.getScore());
        result.setRemarks(trimToNull(request.getRemarks()));
        return toResponse(roundResultRepository.saveAndFlush(result));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private RoundResultResponse toResponse(RoundResult result) {
        RoundResultResponse response = new RoundResultResponse();
        response.setResultId(result.getId());
        response.setApplicationId(result.getApplication().getId());
        response.setStudentId(result.getApplication().getStudent().getId());
        response.setStudentName(result.getApplication().getStudent().getName());
        response.setStudentEmail(result.getApplication().getStudent().getEmail());
        response.setRoundId(result.getSelectionRound().getId());
        response.setRoundName(result.getSelectionRound().getRoundName());
        response.setResult(result.getResult());
        response.setScore(result.getScore());
        response.setRemarks(result.getRemarks());
        response.setEvaluatedAt(result.getEvaluatedAt());
        response.setCreatedAt(result.getCreatedAt());
        response.setUpdatedAt(result.getUpdatedAt());
        return response;
    }
}
