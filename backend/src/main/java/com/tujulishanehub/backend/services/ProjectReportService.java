package com.tujulishanehub.backend.services;

import com.tujulishanehub.backend.models.Project;
import com.tujulishanehub.backend.models.ProjectReport;
import com.tujulishanehub.backend.repositories.ProjectReportRepository;
import com.tujulishanehub.backend.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectReportService.class);
    
    @Autowired
    private ProjectReportRepository projectReportRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    /**
     * Create a new project report
     */
    public ProjectReport createReport(ProjectReport report, Long projectId, Long userId) {
        logger.info("Creating new report for project ID: {} by user: {}", projectId, userId);
        
        // Validate project exists
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found with ID: " + projectId));
        
        // Allow reports on active or completed projects so partners can submit multiple
        // reports (interim/progress, financial, completion, etc.) across the project lifecycle.
        String projectStatus = project.getStatus() != null ? project.getStatus().toLowerCase() : "";
        if (!("active".equals(projectStatus) || "completed".equals(projectStatus))) {
            throw new RuntimeException("Reports can only be added to active or completed projects");
        }
        
        // Set project and submission details
        report.setProject(project);
        report.setSubmittedBy(userId);
        report.setSubmittedAt(LocalDateTime.now());
        
        // Set default status if not provided
        if (report.getReportStatus() == null) {
            report.setReportStatus(ProjectReport.ReportStatus.DRAFT);
        }
        
        // Set default type if not provided
        if (report.getReportType() == null) {
            report.setReportType(ProjectReport.ReportType.COMPLETION);
        }
        
        ProjectReport savedReport = projectReportRepository.save(report);
        
        // Update project's hasReports flag
        project.setHasReports(true);
        projectRepository.save(project);
        
        logger.info("Report created successfully with ID: {}", savedReport.getId());
        return savedReport;
    }
    
    /**
     * Get report by ID
     */
    public Optional<ProjectReport> getReportById(Long id) {
        return projectReportRepository.findById(id);
    }
    
    /**
     * Get all reports for a project
     */
    public List<ProjectReport> getReportsByProjectId(Long projectId) {
        return projectReportRepository.findByProjectId(projectId);
    }
    
    /**
     * Get reports by project with pagination
     */
    public Page<ProjectReport> getReportsByProjectId(Long projectId, Pageable pageable) {
        return projectReportRepository.findByProjectId(projectId, pageable);
    }
    
    /**
     * Get reports by status
     */
    public List<ProjectReport> getReportsByStatus(ProjectReport.ReportStatus status) {
        return projectReportRepository.findByReportStatus(status);
    }
    
    /**
     * Get reports by type
     */
    public List<ProjectReport> getReportsByType(ProjectReport.ReportType type) {
        return projectReportRepository.findByReportType(type);
    }
    
    /**
     * Get published reports
     */
    public List<ProjectReport> getPublishedReports() {
        return projectReportRepository.findPublishedReports();
    }
    
    /**
     * Get published reports with pagination
     */
    public Page<ProjectReport> getPublishedReports(Pageable pageable) {
        return projectReportRepository.findPublishedReports(pageable);
    }
    
    /**
     * Get reports for review (submitted or under review)
     */
    public List<ProjectReport> getReportsForReview() {
        return projectReportRepository.findReportsForReview();
    }
    
    /**
     * Get reports by user
     */
    public List<ProjectReport> getReportsByUser(Long userId) {
        return projectReportRepository.findBySubmittedBy(userId);
    }
    
    /**
     * Search reports by keyword
     */
    public List<ProjectReport> searchReports(String keyword) {
        return projectReportRepository.searchByTitleContaining(keyword);
    }
    
    /**
     * Search reports with multiple criteria
     */
    public List<ProjectReport> searchReports(Long projectId, ProjectReport.ReportType reportType, 
                                           ProjectReport.ReportStatus reportStatus, String keyword) {
        return projectReportRepository.searchReports(projectId, reportType, reportStatus, keyword);
    }
    
    /**
     * Search reports with pagination
     */
    public Page<ProjectReport> searchReports(Long projectId, ProjectReport.ReportType reportType, 
                                           ProjectReport.ReportStatus reportStatus, String keyword, 
                                           Pageable pageable) {
        return projectReportRepository.searchReports(projectId, reportType, reportStatus, keyword, pageable);
    }
    
    /**
     * Update report
     */
    public ProjectReport updateReport(Long reportId, ProjectReport reportDetails, Long userId) {
        logger.info("Updating report with ID: {} by user: {}", reportId, userId);
        
        ProjectReport existingReport = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        // Check if user can update this report
        if (!existingReport.getSubmittedBy().equals(userId) && !existingReport.isDraft()) {
            throw new RuntimeException("Only draft reports can be updated by the author");
        }
        
        // Update fields
        existingReport.setTitle(reportDetails.getTitle());
        existingReport.setSummary(reportDetails.getSummary());
        existingReport.setContent(reportDetails.getContent());
        existingReport.setOutcomesAchieved(reportDetails.getOutcomesAchieved());
        existingReport.setChallengesFaced(reportDetails.getChallengesFaced());
        existingReport.setLessonsLearned(reportDetails.getLessonsLearned());
        existingReport.setRecommendations(reportDetails.getRecommendations());
        existingReport.setBeneficiariesReached(reportDetails.getBeneficiariesReached());
        existingReport.setBudgetUtilized(reportDetails.getBudgetUtilized());
        existingReport.setBudgetVariance(reportDetails.getBudgetVariance());
        existingReport.setCompletionPercentage(reportDetails.getCompletionPercentage());
        existingReport.setAttachments(reportDetails.getAttachments());
        existingReport.setImages(reportDetails.getImages());
        existingReport.setReportType(reportDetails.getReportType());
        
        return projectReportRepository.save(existingReport);
    }
    
    /**
     * Submit report for review
     */
    public boolean submitReportForReview(Long reportId, Long userId) {
        logger.info("Submitting report {} for review by user: {}", reportId, userId);
        
        ProjectReport report = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        // Check if user can submit this report
        if (!report.getSubmittedBy().equals(userId)) {
            throw new RuntimeException("Only the report author can submit it for review");
        }
        
        if (!report.isDraft()) {
            throw new RuntimeException("Only draft reports can be submitted for review");
        }
        
        // Validate required fields
        if (report.getTitle() == null || report.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Report title is required");
        }
        if (report.getContent() == null || report.getContent().trim().isEmpty()) {
            throw new RuntimeException("Report content is required");
        }
        
        report.setReportStatus(ProjectReport.ReportStatus.SUBMITTED);
        report.setSubmittedAt(LocalDateTime.now());
        projectReportRepository.save(report);
        
        logger.info("Report {} submitted successfully for review", reportId);
        return true;
    }
    
    /**
     * Approve report (Admin only)
     */
    public boolean approveReport(Long reportId, Long adminId) {
        logger.info("Approving report {} by admin: {}", reportId, adminId);
        
        ProjectReport report = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        if (!report.isSubmitted() && !report.isUnderReview()) {
            throw new RuntimeException("Only submitted or under-review reports can be approved");
        }
        
        report.setReportStatus(ProjectReport.ReportStatus.APPROVED);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        projectReportRepository.save(report);
        
        logger.info("Report {} approved successfully", reportId);
        return true;
    }
    
    /**
     * Reject report (Admin only)
     */
    public boolean rejectReport(Long reportId, Long adminId) {
        logger.info("Rejecting report {} by admin: {}", reportId, adminId);
        
        ProjectReport report = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        if (!report.isSubmitted() && !report.isUnderReview()) {
            throw new RuntimeException("Only submitted or under-review reports can be rejected");
        }
        
        report.setReportStatus(ProjectReport.ReportStatus.REJECTED);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        projectReportRepository.save(report);
        
        logger.info("Report {} rejected", reportId);
        return true;
    }
    
    /**
     * Publish report (Admin only)
     */
    public boolean publishReport(Long reportId, Long adminId) {
        logger.info("Publishing report {} by admin: {}", reportId, adminId);
        
        ProjectReport report = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        if (!report.isApproved()) {
            throw new RuntimeException("Only approved reports can be published");
        }
        
        report.setReportStatus(ProjectReport.ReportStatus.PUBLISHED);
        report.setPublishedAt(LocalDateTime.now());
        projectReportRepository.save(report);
        
        logger.info("Report {} published successfully", reportId);
        return true;
    }
    
    /**
     * Set report under review (Admin only)
     */
    public boolean setReportUnderReview(Long reportId, Long adminId) {
        logger.info("Setting report {} under review by admin: {}", reportId, adminId);
        
        ProjectReport report = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        if (!report.isSubmitted()) {
            throw new RuntimeException("Only submitted reports can be set under review");
        }
        
        report.setReportStatus(ProjectReport.ReportStatus.UNDER_REVIEW);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        projectReportRepository.save(report);
        
        return true;
    }
    
    /**
     * Delete report
     */
    public boolean deleteReport(Long reportId, Long userId) {
        logger.info("Deleting report {} by user: {}", reportId, userId);
        
        ProjectReport report = projectReportRepository.findById(reportId)
            .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));
        
        // Check if user can delete this report (only draft reports by author)
        if (!report.getSubmittedBy().equals(userId) || !report.isDraft()) {
            throw new RuntimeException("Only draft reports can be deleted by the author");
        }
        
        projectReportRepository.delete(report);
        
        // Update project's hasReports flag if no more reports exist
        Long remainingReports = projectReportRepository.countByProjectId(report.getProject().getId());
        if (remainingReports == 0) {
            Project project = report.getProject();
            project.setHasReports(false);
            projectRepository.save(project);
        }
        
        logger.info("Report {} deleted successfully", reportId);
        return true;
    }
    
    /**
     * Get completion reports
     */
    public List<ProjectReport> getCompletionReports() {
        return projectReportRepository.findCompletionReports();
    }
    
    /**
     * Get completion reports with pagination
     */
    public Page<ProjectReport> getCompletionReports(Pageable pageable) {
        return projectReportRepository.findCompletionReports(pageable);
    }
    
    /**
     * Check if project has completion report
     */
    public boolean hasCompletionReport(Long projectId) {
        return projectReportRepository.hasCompletionReport(projectId);
    }
    
    /**
     * Get report statistics
     */
    public ReportStats getReportStats() {
        Long totalReports = projectReportRepository.count();
        Long draftReports = projectReportRepository.countByReportStatus(ProjectReport.ReportStatus.DRAFT);
        Long submittedReports = projectReportRepository.countByReportStatus(ProjectReport.ReportStatus.SUBMITTED);
        Long underReviewReports = projectReportRepository.countByReportStatus(ProjectReport.ReportStatus.UNDER_REVIEW);
        Long approvedReports = projectReportRepository.countByReportStatus(ProjectReport.ReportStatus.APPROVED);
        Long publishedReports = projectReportRepository.countByReportStatus(ProjectReport.ReportStatus.PUBLISHED);
        Long rejectedReports = projectReportRepository.countByReportStatus(ProjectReport.ReportStatus.REJECTED);
        
        Long completionReports = projectReportRepository.countByReportType(ProjectReport.ReportType.COMPLETION);
        Long interimReports = projectReportRepository.countByReportType(ProjectReport.ReportType.INTERIM);
        Long financialReports = projectReportRepository.countByReportType(ProjectReport.ReportType.FINANCIAL);
        
        return new ReportStats(totalReports, draftReports, submittedReports, underReviewReports,
                              approvedReports, publishedReports, rejectedReports,
                              completionReports, interimReports, financialReports);
    }
    
    /**
     * Inner class for report statistics
     */
    public static class ReportStats {
        private final Long total;
        private final Long draft;
        private final Long submitted;
        private final Long underReview;
        private final Long approved;
        private final Long published;
        private final Long rejected;
        private final Long completion;
        private final Long interim;
        private final Long financial;
        
        public ReportStats(Long total, Long draft, Long submitted, Long underReview,
                          Long approved, Long published, Long rejected,
                          Long completion, Long interim, Long financial) {
            this.total = total;
            this.draft = draft;
            this.submitted = submitted;
            this.underReview = underReview;
            this.approved = approved;
            this.published = published;
            this.rejected = rejected;
            this.completion = completion;
            this.interim = interim;
            this.financial = financial;
        }
        
        // Getters
        public Long getTotal() { return total; }
        public Long getDraft() { return draft; }
        public Long getSubmitted() { return submitted; }
        public Long getUnderReview() { return underReview; }
        public Long getApproved() { return approved; }
        public Long getPublished() { return published; }
        public Long getRejected() { return rejected; }
        public Long getCompletion() { return completion; }
        public Long getInterim() { return interim; }
        public Long getFinancial() { return financial; }
    }
}