package com.example.backend.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public class CompanyRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 200)
    private String companyName;

    @Size(max = 10000)
    private String description;

    @Size(max = 2048)
    @URL(regexp = "(?i)^https?://.+$", message = "Website must be a valid HTTP or HTTPS URL")
    private String website;

    @Size(max = 100)
    private String industry;

    @Size(max = 200)
    private String location;

    @Size(max = 100)
    private String companySize;

    @Email
    @Size(max = 254)
    private String contactEmail;

    @Size(max = 30)
    @Pattern(regexp = "^$|^\\+?[0-9() .-]{7,30}$", message = "Contact phone must be a valid phone number")
    private String contactPhone;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCompanySize() { return companySize; }
    public void setCompanySize(String companySize) { this.companySize = companySize; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
