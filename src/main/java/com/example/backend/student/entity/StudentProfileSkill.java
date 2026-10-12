package com.example.backend.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profile_skills")
public class StudentProfileSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_student_profile_skill_profile"))
    private StudentProfile studentProfile;

    @Column(name = "skill", nullable = false, length = 100)
    private String skill;

    protected StudentProfileSkill() {
    }

    StudentProfileSkill(StudentProfile studentProfile, String skill) {
        this.studentProfile = studentProfile;
        this.skill = skill;
    }

    public Long getId() { return id; }
    public StudentProfile getStudentProfile() { return studentProfile; }
    public String getSkill() { return skill; }
}
