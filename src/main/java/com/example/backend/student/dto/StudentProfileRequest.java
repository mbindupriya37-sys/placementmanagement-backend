package com.example.backend.student.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class StudentProfileRequest {

    @NotBlank
    @Pattern(regexp = "^\\+?[0-9() .-]{7,20}$", message = "must be a valid phone number")
    private String phoneNumber;

    @NotBlank
    @Size(max = 150)
    private String college;

    @NotBlank
    @Size(max = 100)
    private String branch;

    @NotNull
    @Min(1900)
    @Max(2100)
    private Integer graduationYear;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("10.00")
    private BigDecimal cgpa;

    @NotNull
    @Min(0)
    private Integer backlogs;

    @NotEmpty
    @Size(max = 50)
    private List<@NotBlank @Size(max = 100) String> skills;

    @Size(max = 2048)
    @Pattern(regexp = "^(https?://\\S+|/?[A-Za-z0-9._~!$&'()*+,;=:@%/-]+)$",
            message = "must be an HTTP(S) URL or a relative path")
    private String resumeUrl;

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
    public BigDecimal getCgpa() { return cgpa; }
    public void setCgpa(BigDecimal cgpa) { this.cgpa = cgpa; }
    public Integer getBacklogs() { return backlogs; }
    public void setBacklogs(Integer backlogs) { this.backlogs = backlogs; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
}
