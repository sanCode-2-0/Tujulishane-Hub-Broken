package com.tujulishanehub.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Project {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String projectNo;
    
    @Column(nullable = false)
    private String partner;
    
    @Column(nullable = false)
    private String title;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "project_category", nullable = false)
    private ProjectCategory projectCategory;

    @Column(name = "lacoste_number")
    private String lacosteNumber;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(name = "activity_type", nullable = false, columnDefinition = "TEXT")
    private String activityType;
    
    @Column
    private String county;
    
    // Multiple themes relationship
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ProjectThemeAssignment> themes = new HashSet<>();
    
    // Multiple locations relationship
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ProjectLocation> locations = new HashSet<>();
    
    @Column(name = "contact_person_name", nullable = false)
    private String contactPersonName;
    
    @Column(name = "contact_person_role", nullable = false)
    private String contactPersonRole;
    
    @Column(name = "contact_person_email")
    private String contactPersonEmail;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String objectives;
    
    @Column(precision = 15, scale = 2, nullable = false)
    private java.math.BigDecimal budget;
    
        // Supporting documents (files as blobs) - LAZY loading to avoid performance issues
        @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @JsonIgnore // Never serialize documents in project responses
        private java.util.List<ProjectDocument> supportingDocuments = new java.util.ArrayList<>();
    
    // Additional metadata fields
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    private String status; // e.g., "active", "pending", "stalled", "completed", "abandoned"
    
    @Column(name = "completion_percentage")
    private Integer completionPercentage = 0;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "has_reports")
    private Boolean hasReports = false;
    
    @jakarta.persistence.Convert(converter = com.tujulishanehub.backend.converters.ApprovalStatusConverter.class)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    
    // Two-tier approval workflow status (new system)
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_workflow_status")
    private ApprovalWorkflowStatus approvalWorkflowStatus = ApprovalWorkflowStatus.PENDING_REVIEW;
    
    @Column(name = "approved_by")
    private Long approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    // Two-tier approval workflow fields
    @Column(name = "reviewed_by")
    private Long reviewedBy; // Thematic reviewer who did initial review
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "reviewer_comments")
    private String reviewerComments; // Comments from the thematic reviewer
    
    // Collaboration tracking fields
    @Column(name = "last_modified_by")
    private String lastModifiedBy; // Email of the user who last modified
    
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;
    
    // Role of the user who created the project (e.g. "SUPER_ADMIN" for MoH-created projects)
    @Column(name = "created_by_role")
    private String createdByRole;
    
    // Bidirectional relationship with ProjectReports
    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JsonManagedReference(value = "project-reports")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ProjectReport> reports = new HashSet<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastModifiedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastModifiedAt = LocalDateTime.now();
    }
    
    // Helper method to check if any location has coordinates
    public boolean hasCoordinates() {
        return locations != null && locations.stream().anyMatch(ProjectLocation::hasCoordinates);
    }

    // Helper method to get formatted coordinate strings for all locations
    public Set<String> getCoordinatesStrings() {
        Set<String> coords = new HashSet<>();
        if (locations != null) {
            locations.stream()
                .map(ProjectLocation::getCoordinatesString)
                .filter(coord -> coord != null)
                .forEach(coords::add);
        }
        return coords;
    }

    // Helper method to get primary location coordinates (first location with coordinates)
    public String getPrimaryCoordinatesString() {
        if (locations != null) {
            return locations.stream()
                .filter(ProjectLocation::hasCoordinates)
                .findFirst()
                .map(ProjectLocation::getCoordinatesString)
                .orElse(null);
        }
        return null;
    }

    // Explicit getter and setter methods for important fields
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setProjectNo(String projectNo){
        this.projectNo = projectNo;
    }

    public String getProjectNo(){
        return projectNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContactPersonEmail() {
        return contactPersonEmail;
    }

    public void setContactPersonEmail(String contactPersonEmail) {
        this.contactPersonEmail = contactPersonEmail;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
    
    public ApprovalWorkflowStatus getApprovalWorkflowStatus() {
        return approvalWorkflowStatus;
    }
    
    public void setApprovalWorkflowStatus(ApprovalWorkflowStatus approvalWorkflowStatus) {
        this.approvalWorkflowStatus = approvalWorkflowStatus;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCreatedByRole() {
        return createdByRole;
    }

    public void setCreatedByRole(String createdByRole) {
        this.createdByRole = createdByRole;
    }
    
    public Long getReviewedBy() {
        return reviewedBy;
    }
    
    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
    
    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }
    
    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
    
    public String getReviewerComments() {
        return reviewerComments;
    }
    
    public void setReviewerComments(String reviewerComments) {
        this.reviewerComments = reviewerComments;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ProjectCategory getProjectCategory() {
        return projectCategory;
    }

    public void setProjectCategory(ProjectCategory projectCategory) {
        this.projectCategory = projectCategory;
    }

    public String getLacosteNumber(){
        return lacosteNumber;
    }
    
    public void setLacosteNumber(String lacosteNumber){
        this.lacosteNumber = lacosteNumber;
    }
    
    // Helper methods for project completion
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }
    
    public boolean canCreateReports() {
        return completionPercentage != null && completionPercentage >= 50;
    }
    
    public void markAsCompleted() {
        this.status = "completed";
        this.completionPercentage = 100;
        this.completedAt = LocalDateTime.now();
    }
    
    public Integer getCompletionPercentage() {
        return completionPercentage != null ? completionPercentage : 0;
    }
    
    public void setCompletionPercentage(Integer completionPercentage) {
        this.completionPercentage = completionPercentage;
        if (completionPercentage != null && completionPercentage >= 100) {
            this.status = "completed";
            if (this.completedAt == null) {
                this.completedAt = LocalDateTime.now();
            }
        }
    }
    
    public Boolean getHasReports() {
        return hasReports != null ? hasReports : false;
    }
    
    public void setHasReports(Boolean hasReports) {
        this.hasReports = hasReports;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public Set<ProjectReport> getReports() {
        return reports;
    }
    
    public void setReports(Set<ProjectReport> reports) {
        this.reports = reports;
        this.hasReports = reports != null && !reports.isEmpty();
    }

    // Helper methods for multiple themes
    public void addTheme(ProjectTheme theme) {
        if (themes == null) {
            themes = new HashSet<>();
        }
        ProjectThemeAssignment assignment = new ProjectThemeAssignment();
        assignment.setProject(this);
        assignment.setProjectTheme(theme);
        themes.add(assignment);
    }

    public void removeTheme(ProjectTheme theme) {
        if (themes != null) {
            themes.removeIf(assignment -> assignment.getProjectTheme() == theme);
        }
    }

    public Set<ProjectTheme> getProjectThemes() {
        Set<ProjectTheme> themeSet = new HashSet<>();
        if (themes != null) {
            themes.forEach(assignment -> themeSet.add(assignment.getProjectTheme()));
        }
        return themeSet;
    }

    // Helper methods for multiple locations
    public void addLocation(String county, String subCounty, String mapsAddress, Double latitude, Double longitude) {
        if (locations == null) {
            locations = new HashSet<>();
        }
        ProjectLocation location = new ProjectLocation();
        location.setProject(this);
        location.setCounty(county);
        location.setSubCounty(subCounty);
        location.setMapsAddress(mapsAddress);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        locations.add(location);
    }

    public void removeLocation(ProjectLocation location) {
        if (locations != null) {
            locations.remove(location);
        }
    }

    // Get primary location (first one)
    public ProjectLocation getPrimaryLocation() {
        if (locations != null && !locations.isEmpty()) {
            return locations.iterator().next();
        }
        return null;
    }

    // Get all location strings
    public Set<String> getLocationStrings() {
        Set<String> locationStrings = new HashSet<>();
        if (locations != null) {
            locations.forEach(location -> {
                String locStr = location.getFullLocationString();
                if (locStr != null && !locStr.isEmpty()) {
                    locationStrings.add(locStr);
                }
            });
        }
        return locationStrings;
    }

    // Getter and setter for supporting documents
    public java.util.List<ProjectDocument> getSupportingDocuments() {
        return supportingDocuments;
    }

    public void setSupportingDocuments(java.util.List<ProjectDocument> supportingDocuments) {
        this.supportingDocuments = supportingDocuments;
    }
}