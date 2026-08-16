package com.tujulishanehub.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(exclude = {"thematicAreaAssignments", "parentDonor"})
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = true)  // Nullable since this is a passwordless system using OTP
    private String password;
    
    private String otp;
    
    @Column(name = "otp_expiry")
    private LocalDateTime otpExpiry;
    
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;
    
    @Column(name = "email_verification_token")
    private String emailVerificationToken;
    
    // Legacy field for backward compatibility
    @Column(nullable = false)
    private Boolean verified = false;
    
    private String status = "INACTIVE"; // e.g. INACTIVE, ACTIVE
    
    @Enumerated(EnumType.STRING)
    private Role role = Role.PARTNER;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;
    
    @Column(name = "approved_by")
    private Long approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    @Column(name = "phone")
    private String phone;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    // Organization relationship - Many users can belong to one organization
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id")
    private Organization organization;
    
    // Donor relationship - Partners can be linked to a parent donor account
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_donor_id")
    private User parentDonor;

    @Enumerated(EnumType.STRING)
    @Column(name = "partnership_approval_status")
    private ApprovalStatus partnershipApprovalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "partnership_workflow_status")
    private ApprovalWorkflowStatus partnershipWorkflowStatus;
    
    // Legacy single thematic area assignment for backward compatibility
    @Enumerated(EnumType.STRING)
    @Column(name = "thematic_area")
    private ProjectTheme thematicArea;
    
    // Many-to-many relationship: Reviewers can be assigned to multiple thematic areas
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("user")
    private java.util.Set<ReviewerThematicArea> thematicAreaAssignments = new java.util.HashSet<>();
    
    // Role enum - Five roles system with two-tier SUPER_ADMIN structure
    public enum Role {
        SUPER_ADMIN,            // Legacy - MOH administrators with full system access (backward compatibility)
        SUPER_ADMIN_APPROVER,   // Final approval authority - single user who makes final approval decisions
        SUPER_ADMIN_REVIEWER,   // Thematic area reviewers - review projects in their assigned thematic area
        DONOR,                  // Donor organizations/funding agencies
        PARTNER                 // Partner organizations implementing projects
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods for approval status
    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }
    
    public boolean isPending() {
        return approvalStatus == ApprovalStatus.PENDING;
    }
    
    public boolean isSubmitted() {
        return approvalStatus == ApprovalStatus.SUBMITTED;
    }
    
    public boolean isRejected() {
        return approvalStatus == ApprovalStatus.REJECTED;
    }
    
    // Helper methods for role checks
    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN || role == Role.SUPER_ADMIN_APPROVER || role == Role.SUPER_ADMIN_REVIEWER;
    }
    
    public boolean isSuperAdminApprover() {
        return role == Role.SUPER_ADMIN_APPROVER || role == Role.SUPER_ADMIN; // SUPER_ADMIN has approver rights for backward compatibility
    }
    
    public boolean isSuperAdminReviewer() {
        return role == Role.SUPER_ADMIN_REVIEWER;
    }
    
    public boolean isPartner() {
        return role == Role.PARTNER || role == Role.DONOR;
    }
    
    // Convenience method for email verification
    public boolean isEmailVerified() {
        return emailVerified != null ? emailVerified : false;
    }
    
    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified != null ? emailVerified : false;
    }
    
    // Check if user can create projects
    public boolean canCreateProjects() {
        return isEmailVerified() && isApproved() && "ACTIVE".equals(status);
    }

    // Explicit getter and setter methods for status (in case Lombok doesn't work properly)
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public Boolean isVerified() {
        return verified != null ? verified : false;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified != null ? verified : false;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
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
    
    public ProjectTheme getThematicArea() {
        return thematicArea;
    }
    
    public void setThematicArea(ProjectTheme thematicArea) {
        this.thematicArea = thematicArea;
    }
    
    public java.util.Set<ReviewerThematicArea> getThematicAreaAssignments() {
        return thematicAreaAssignments;
    }
    
    public void setThematicAreaAssignments(java.util.Set<ReviewerThematicArea> thematicAreaAssignments) {
        this.thematicAreaAssignments = thematicAreaAssignments;
    }
    
    /**
     * Get all thematic areas assigned to this reviewer
     */
    public java.util.List<ProjectTheme> getThematicAreas() {
        return thematicAreaAssignments.stream()
            .map(ReviewerThematicArea::getThematicArea)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Check if reviewer is assigned to a specific thematic area
     */
    public boolean hasThematicArea(ProjectTheme thematicArea) {
        // Check new many-to-many relationship first
        if (!thematicAreaAssignments.isEmpty()) {
            return thematicAreaAssignments.stream()
                .anyMatch(assignment -> assignment.getThematicArea() == thematicArea);
        }
        // Fallback to legacy single thematic area for backward compatibility
        return this.thematicArea == thematicArea;
    }
    
    /**
     * Add a thematic area assignment to this reviewer
     */
    public void addThematicArea(ReviewerThematicArea assignment) {
        thematicAreaAssignments.add(assignment);
        assignment.setUser(this);
    }
    
    /**
     * Remove a thematic area assignment from this reviewer
     */
    public void removeThematicArea(ReviewerThematicArea assignment) {
        thematicAreaAssignments.remove(assignment);
        assignment.setUser(null);
    }
    
    /**
     * Get thematic areas as a list of strings for JSON serialization
     * This provides backward-compatible API responses
     */
    @com.fasterxml.jackson.annotation.JsonProperty("thematicAreas")
    public java.util.List<String> getThematicAreasAsStrings() {
        // Return new many-to-many relationship if available
        if (!thematicAreaAssignments.isEmpty()) {
            return thematicAreaAssignments.stream()
                .map(assignment -> assignment.getThematicArea().name())
                .collect(java.util.stream.Collectors.toList());
        }
        // Fallback to legacy single thematic area
        if (thematicArea != null) {
            return java.util.Arrays.asList(thematicArea.name());
        }
        return java.util.Collections.emptyList();
    }
    
    public Organization getOrganization() {
        return organization;
    }
    
    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public ApprovalStatus getPartnershipApprovalStatus() {
        return partnershipApprovalStatus;
    }

    public void setPartnershipApprovalStatus(ApprovalStatus partnershipApprovalStatus) {
        this.partnershipApprovalStatus = partnershipApprovalStatus;
    }

    public ApprovalWorkflowStatus getPartnershipWorkflowStatus() {
        return partnershipWorkflowStatus;
    }

    public void setPartnershipWorkflowStatus(ApprovalWorkflowStatus partnershipWorkflowStatus) {
        this.partnershipWorkflowStatus = partnershipWorkflowStatus;
    }
}
