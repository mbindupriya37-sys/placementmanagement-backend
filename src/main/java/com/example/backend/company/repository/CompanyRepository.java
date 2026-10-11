package com.example.backend.company.repository;

import com.example.backend.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByCompanyNameIgnoreCase(String companyName);
    boolean existsByCompanyNameIgnoreCaseAndIdNot(String companyName, Long id);
}
