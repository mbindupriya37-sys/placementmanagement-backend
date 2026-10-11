package com.example.backend.scheduling.dto;

import com.example.backend.scheduling.entity.InterviewMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

public class InterviewScheduleRequest {

    @NotNull
    @Positive
    private Long applicationId;

    @NotNull
    @FutureOrPresent
    private LocalDateTime scheduledStartTime;

    @NotNull
    private LocalDateTime scheduledEndTime;

    @NotNull
    private InterviewMode mode;

    @Size(max = 2048)
    @URL(regexp = "(?i)^https?://.+$", message = "Meeting link must be a valid HTTP or HTTPS URL")
    private String meetingLink;

    @Size(max = 500)
    private String venue;

    @Size(max = 200)
    private String interviewerName;

    @Email
    @Size(max = 254)
    private String interviewerEmail;

    @Size(max = 10000)
    private String notes;

    @AssertTrue(message = "Scheduled start time must be before scheduled end time")
    public boolean isStartBeforeEnd() {
        return scheduledStartTime == null || scheduledEndTime == null
                || scheduledStartTime.isBefore(scheduledEndTime);
    }

    @AssertTrue(message = "Online schedules require a meeting link")
    public boolean isOnlineMeetingLinkPresent() {
        return mode != InterviewMode.ONLINE || (meetingLink != null && !meetingLink.isBlank());
    }

    @AssertTrue(message = "Offline schedules require a venue")
    public boolean isOfflineVenuePresent() {
        return mode != InterviewMode.OFFLINE || (venue != null && !venue.isBlank());
    }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public LocalDateTime getScheduledStartTime() { return scheduledStartTime; }
    public void setScheduledStartTime(LocalDateTime scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }
    public LocalDateTime getScheduledEndTime() { return scheduledEndTime; }
    public void setScheduledEndTime(LocalDateTime scheduledEndTime) { this.scheduledEndTime = scheduledEndTime; }
    public InterviewMode getMode() { return mode; }
    public void setMode(InterviewMode mode) { this.mode = mode; }
    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getInterviewerName() { return interviewerName; }
    public void setInterviewerName(String interviewerName) { this.interviewerName = interviewerName; }
    public String getInterviewerEmail() { return interviewerEmail; }
    public void setInterviewerEmail(String interviewerEmail) { this.interviewerEmail = interviewerEmail; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
