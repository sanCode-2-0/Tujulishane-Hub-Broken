package com.tujulishanehub.backend.services;

import com.tujulishanehub.backend.models.PastProject;
import com.tujulishanehub.backend.models.Project;
import com.tujulishanehub.backend.models.ProjectLocation;
import com.tujulishanehub.backend.models.ProjectTheme;
import com.tujulishanehub.backend.models.ProjectThemeAssignment;
import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.models.ProjectCategory;
import com.tujulishanehub.backend.models.ApprovalStatus;
import com.tujulishanehub.backend.models.ApprovalWorkflowStatus;
import com.tujulishanehub.backend.repositories.ProjectRepository;
import com.tujulishanehub.backend.payload.ProjectCreateRequest;
import com.tujulishanehub.backend.payload.ProjectLocationDto;
import com.tujulishanehub.backend.payload.ProjectResponse;
import com.tujulishanehub.backend.payload.ProjectThemeDto;
import com.tujulishanehub.backend.payload.ProjectUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    
    @Autowired
    private ProjectRepository projectRepository;
    
    @Autowired
    private GeocodingService geocodingService;
    
    @Autowired
    private PastProjectService pastProjectService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private UserService userService;
    
    /**
     * Create a new project with automatic coordinate extraction for locations
     */
    public Project createProject(Project project) {
        logger.info("Creating new project: {}", project.getTitle());
        logger.debug("Project details: partner={}, themes={}, locations={}",
            project.getPartner(),
            project.getThemes() != null ? project.getThemes().size() : 0,
            project.getLocations() != null ? project.getLocations().size() : 0);

        try {
            // Extract coordinates for each location if maps_address is provided but coordinates are missing
            if (project.getLocations() != null) {
                logger.debug("Processing {} locations for coordinate extraction", project.getLocations().size());
                for (ProjectLocation location : project.getLocations()) {
                    if (location.getMapsAddress() != null && !location.getMapsAddress().trim().isEmpty() &&
                        (location.getLatitude() == null || location.getLongitude() == null)) {
                        logger.debug("Extracting coordinates for location: {}", location.getMapsAddress());
                        extractAndSetCoordinatesForLocation(location);
                    }
                }
            }

            // Set default status if not provided
            if (project.getStatus() == null || project.getStatus().trim().isEmpty()) {
                project.setStatus("pending");
                logger.debug("Set default status to 'pending'");
            }

            logger.debug("About to save project to repository");
            Project savedProject = projectRepository.save(project);
            logger.info("Project created successfully with ID: {}", savedProject.getId());
            logger.debug("Saved project details: themes={}, locations={}",
                savedProject.getThemes() != null ? savedProject.getThemes().size() : 0,
                savedProject.getLocations() != null ? savedProject.getLocations().size() : 0);
            return savedProject;
        } catch (Exception e) {
            logger.error("Error in createProject: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Update an existing project
     */
    public Project updateProject(Long id, ProjectUpdateRequest request, String modifiedByEmail) {
        logger.info("Updating project with ID: {} by user: {}", id, modifiedByEmail);
        
        Optional<Project> existingProjectOpt = projectRepository.findById(id);
        if (existingProjectOpt.isEmpty()) {
            throw new RuntimeException("Project not found with ID: " + id);
        }
        
        Project existingProject = existingProjectOpt.get();
        
        // Track who modified the project
        existingProject.setLastModifiedBy(modifiedByEmail);
        existingProject.setLastModifiedAt(java.time.LocalDateTime.now());
        
        // Update fields
        updateProjectFields(existingProject, request);
        replaceThemes(existingProject, request.getThemes());
        replaceLocations(existingProject, request.getLocations());
        
        Project savedProject = projectRepository.save(existingProject);
        logger.info("Project updated successfully: {} by {}", savedProject.getId(), modifiedByEmail);
        return savedProject;
    }
    
    /**
     * Get project by ID
     */
    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }
    
    /**
     * Get all projects
     */
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }
    
    /**
     * Get projects with pagination
     */
    public Page<Project> getProjects(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    /**
     * Get past projects (completed and stalled) with pagination
     */
    public Page<Project> getPastProjects(Pageable pageable) {
        return projectRepository.findByStatusIn(Arrays.asList("completed", "stalled"), pageable);
    }

    /**
     * Delete project
     */
    public void deleteProject(Long id) {
        logger.info("Deleting project with ID: {}", id);
        
        // First try to find by ID
        if (projectRepository.existsById(id)) {
            projectRepository.deleteById(id);
            logger.info("Project deleted successfully by ID: {}", id);
            return;
        }
        
        // If not found by ID, this might be a project number - try to find by project number
        logger.info("Project with ID {} not found, trying to find by project number", id);
        
        // Convert ID to string to search as project number
        String projectNumber = String.valueOf(id);
        Optional<Project> projectByNumber = projectRepository.findByProjectNo(projectNumber);
        
        if (projectByNumber.isPresent()) {
            Project project = projectByNumber.get();
            projectRepository.deleteById(project.getId());
            logger.info("Project deleted successfully by project number: {} (ID: {})", projectNumber, project.getId());
            return;
        }
        
        // If still not found, throw exception
        throw new RuntimeException("Project not found with ID or project number: " + id);
    }
    
    /**
     * Delete project by project number
     */
    public void deleteProjectByProjectNumber(String projectNumber) {
        logger.info("Deleting project with project number: {}", projectNumber);
        
        Optional<Project> project = projectRepository.findByProjectNo(projectNumber);
        if (!project.isPresent()) {
            throw new RuntimeException("Project not found with project number: " + projectNumber);
        }
        
        projectRepository.deleteById(project.get().getId());
        logger.info("Project deleted successfully by project number: {} (ID: {})", projectNumber, project.get().getId());
    }
    
    /**
     * Search projects by various criteria
     */
    public List<Project> searchProjects(String partner, String title, String projectNo, String status, 
                                       String county, String activityType) {
        return projectRepository.searchProjects(partner, title, projectNo, status, county, activityType);
    }
    
    /**
     * Get projects by status
     */
    public List<Project> getProjectsByStatus(String status) {
        return projectRepository.findByStatus(status);
    }
    
    /**
     * Get projects by county
     */
    public List<Project> getProjectsByCounty(String county) {
        return projectRepository.findByCountyContainingIgnoreCase(county);
    }
    
    /**
     * Get projects by partner
     */
    public List<Project> getProjectsByPartner(String partner) {
        return projectRepository.findByPartnerContainingIgnoreCase(partner);
    }
    
    /**
     * Get projects by date range
     */
    public List<Project> getProjectsByDateRange(LocalDate startDate, LocalDate endDate) {
        return projectRepository.findByStartDateBetween(startDate, endDate);
    }
    
    /**
     * Get currently active projects
     */
    public List<Project> getActiveProjects() {
        return projectRepository.findActiveProjects(LocalDate.now());
    }
    
    /**
     * Get projects with coordinates (for map display)
     */
    public List<Project> getProjectsWithCoordinates() {
        return projectRepository.findProjectsWithCoordinates();
    }
    
    /**
     * Get projects within a geographic bounding box
     */
    public List<Project> getProjectsInBoundingBox(Double minLat, Double maxLat, 
                                                 Double minLng, Double maxLng) {
        return projectRepository.findProjectsInBoundingBox(minLat, maxLat, minLng, maxLng);
    }
    
    /**
     * Batch process projects that need geocoding
     */
    public void processProjectsNeedingGeocoding() {
        logger.info("Starting batch geocoding process");
        List<Project> projectsNeedingGeocoding = projectRepository.findProjectsNeedingGeocoding();
        
        logger.info("Found {} projects needing geocoding", projectsNeedingGeocoding.size());
        
        for (Project project : projectsNeedingGeocoding) {
            try {
                extractAndSetCoordinates(project);
                projectRepository.save(project);
                logger.debug("Geocoded project: {}", project.getTitle());
                
                // Add a small delay to be respectful to geocoding services
                Thread.sleep(100);
            } catch (Exception e) {
                logger.error("Failed to geocode project {}: {}", project.getId(), e.getMessage());
            }
        }
        
        logger.info("Batch geocoding process completed");
    }
    
    /**
     * Get project statistics
     */
    public ProjectStatistics getProjectStatistics() {
        List<Object[]> statusCounts = projectRepository.countProjectsByStatus();
        List<Object[]> countyCounts = projectRepository.countProjectsByCounty();
        long totalProjects = projectRepository.count();
        long projectsWithCoordinates = projectRepository.findProjectsWithCoordinates().size();
        
        return new ProjectStatistics(statusCounts, countyCounts, totalProjects, projectsWithCoordinates);
    }
    
    /**
     * Get project statistics for a specific partner (PARTNER/DONOR users)
     */
    public ProjectStatistics getProjectStatisticsByPartner(String partnerEmail) {
        List<Project> userProjects = getProjectsByPartnerEmail(partnerEmail);
        
        // Calculate status counts for user's projects
        Map<String, Long> statusCountsMap = new HashMap<>();
        userProjects.forEach(p -> {
            String status = p.getStatus() != null ? p.getStatus().toLowerCase() : "unknown";
            statusCountsMap.put(status, statusCountsMap.getOrDefault(status, 0L) + 1);
        });
        
        // Convert to Object[] format expected by ProjectStatistics
        List<Object[]> statusCounts = new ArrayList<>();
        statusCountsMap.forEach((status, count) -> {
            statusCounts.add(new Object[]{status, count});
        });
        
        // Calculate county counts for user's projects
        Map<String, Long> countyCountsMap = new HashMap<>();
        userProjects.forEach(p -> {
            if (p.getLocations() != null) {
                p.getLocations().forEach(loc -> {
                    String county = loc.getCounty() != null ? loc.getCounty() : "unknown";
                    countyCountsMap.put(county, countyCountsMap.getOrDefault(county, 0L) + 1);
                });
            }
        });
        
        // Convert to Object[] format
        List<Object[]> countyCounts = new ArrayList<>();
        countyCountsMap.forEach((county, count) -> {
            countyCounts.add(new Object[]{county, count});
        });
        
        long totalProjects = userProjects.size();
        long projectsWithCoordinates = userProjects.stream()
            .filter(p -> p.getLocations() != null && p.getLocations().stream()
                .anyMatch(loc -> loc.getLatitude() != null && loc.getLongitude() != null))
            .count();
        
        return new ProjectStatistics(statusCounts, countyCounts, totalProjects, projectsWithCoordinates);
    }
    
    /**
     * Get project statistics for a thematic area reviewer (SUPER_ADMIN_REVIEWER)
     * Shows only projects in their assigned thematic area
     */
    public ProjectStatistics getProjectStatisticsByThematicArea(ProjectTheme thematicArea) {
        // Get all projects matching the thematic area
        List<Project> thematicProjects = projectRepository.findAll().stream()
            .filter(project -> {
                if (project.getThemes() == null || project.getThemes().isEmpty()) {
                    return false;
                }
                return project.getThemes().stream()
                    .anyMatch(assignment -> assignment.getProjectTheme() == thematicArea);
            })
            .collect(Collectors.toList());
        
        // Calculate status counts for thematic projects
        Map<String, Long> statusCountsMap = new HashMap<>();
        thematicProjects.forEach(p -> {
            String status = p.getStatus() != null ? p.getStatus().toLowerCase() : "unknown";
            statusCountsMap.put(status, statusCountsMap.getOrDefault(status, 0L) + 1);
        });
        
        // Convert to Object[] format expected by ProjectStatistics
        List<Object[]> statusCounts = new ArrayList<>();
        statusCountsMap.forEach((status, count) -> {
            statusCounts.add(new Object[]{status, count});
        });
        
        // Calculate county counts for thematic projects
        Map<String, Long> countyCountsMap = new HashMap<>();
        thematicProjects.forEach(p -> {
            if (p.getLocations() != null) {
                p.getLocations().forEach(loc -> {
                    String county = loc.getCounty() != null ? loc.getCounty() : "unknown";
                    countyCountsMap.put(county, countyCountsMap.getOrDefault(county, 0L) + 1);
                });
            }
        });
        
        // Convert to Object[] format
        List<Object[]> countyCounts = new ArrayList<>();
        countyCountsMap.forEach((county, count) -> {
            countyCounts.add(new Object[]{county, count});
        });
        
        long totalProjects = thematicProjects.size();
        long projectsWithCoordinates = thematicProjects.stream()
            .filter(p -> p.getLocations() != null && p.getLocations().stream()
                .anyMatch(loc -> loc.getLatitude() != null && loc.getLongitude() != null))
            .count();
        
        return new ProjectStatistics(statusCounts, countyCounts, totalProjects, projectsWithCoordinates);
    }
    
    /**
     * Get project statistics for final approver (SUPER_ADMIN_APPROVER)
     * Shows only projects pending final approval or reviewed
     */
    public ProjectStatistics getProjectStatisticsForApprover() {
        // Get all projects awaiting final approval
        List<Project> approverProjects = getProjectsAwaitingFinalApproval();
        
        // Calculate status counts for approver projects
        Map<String, Long> statusCountsMap = new HashMap<>();
        approverProjects.forEach(p -> {
            String status = p.getStatus() != null ? p.getStatus().toLowerCase() : "unknown";
            statusCountsMap.put(status, statusCountsMap.getOrDefault(status, 0L) + 1);
        });
        
        // Convert to Object[] format expected by ProjectStatistics
        List<Object[]> statusCounts = new ArrayList<>();
        statusCountsMap.forEach((status, count) -> {
            statusCounts.add(new Object[]{status, count});
        });
        
        // Calculate county counts for approver projects
        Map<String, Long> countyCountsMap = new HashMap<>();
        approverProjects.forEach(p -> {
            if (p.getLocations() != null) {
                p.getLocations().forEach(loc -> {
                    String county = loc.getCounty() != null ? loc.getCounty() : "unknown";
                    countyCountsMap.put(county, countyCountsMap.getOrDefault(county, 0L) + 1);
                });
            }
        });
        
        // Convert to Object[] format
        List<Object[]> countyCounts = new ArrayList<>();
        countyCountsMap.forEach((county, count) -> {
            countyCounts.add(new Object[]{county, count});
        });
        
        long totalProjects = approverProjects.size();
        long projectsWithCoordinates = approverProjects.stream()
            .filter(p -> p.getLocations() != null && p.getLocations().stream()
                .anyMatch(loc -> loc.getLatitude() != null && loc.getLongitude() != null))
            .count();
        
        return new ProjectStatistics(statusCounts, countyCounts, totalProjects, projectsWithCoordinates);
    }
    
    /**
     * Archive a completed project to past projects repository
     */
    public PastProject archiveProject(Long projectId, String archivedBy, String lessonsLearned,
                                    String successFactors, String challenges, String recommendations) {
        logger.info("Archiving project with ID: {}", projectId);
        
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (!projectOpt.isPresent()) {
            throw new RuntimeException("Project not found with ID: " + projectId);
        }
        
        Project project = projectOpt.get();
        
        // Validate that project is completed
        if (!"completed".equalsIgnoreCase(project.getStatus())) {
            throw new RuntimeException("Only completed projects can be archived");
        }
        
        // Archive the project
        PastProject pastProject = pastProjectService.archiveProject(
            project, archivedBy, lessonsLearned, successFactors, challenges, recommendations);
        
        // Remove the project from active projects (optional - could keep for history)
        // projectRepository.delete(project);
        
        return pastProject;
    }
    
    /**
     * Get projects eligible for archival (completed and older than specified years)
     */
    public List<Project> getProjectsEligibleForArchival(int yearsOld) {
        LocalDate cutoffDate = LocalDate.now().minusYears(yearsOld);
        return projectRepository.findByEndDateBeforeAndStatus(cutoffDate, "completed");
    }
    
    /**
     * Extract coordinates from maps_address and set lat/lng fields
     */
    private void extractAndSetCoordinates(Project project) {
        if (project.getLocations() == null) {
            return;
        }

        for (ProjectLocation location : project.getLocations()) {
            if (location.getMapsAddress() != null && !location.getMapsAddress().trim().isEmpty() &&
                    (location.getLatitude() == null || location.getLongitude() == null)) {
                extractAndSetCoordinatesForLocation(location);
            }
        }
    }
    
    /**
     * Extract and set coordinates for a specific location
     */
    private void extractAndSetCoordinatesForLocation(ProjectLocation location) {
        try {
            GeocodingService.CoordinateResult result = geocodingService.extractCoordinates(location.getMapsAddress());
            
            if (result.isValid()) {
                location.setLatitude(result.getLatitude());
                location.setLongitude(result.getLongitude());
                logger.debug("Extracted coordinates for location '{}': {}", 
                    location.getMapsAddress(), result);
            } else {
                logger.warn("Could not extract coordinates for location '{}': {}", 
                    location.getMapsAddress(), result.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error extracting coordinates for location '{}': {}", 
                location.getMapsAddress(), e.getMessage(), e);
        }
    }
    
    /**
     * Update project fields from another project object
     * Note: Themes and locations are not updated here - they require separate handling
     */
    private void updateProjectFields(Project existing, ProjectUpdateRequest request) {
        if (request.getTitle() != null) existing.setTitle(request.getTitle());
        if (request.getProjectCategory() != null) existing.setProjectCategory(request.getProjectCategory());
        if (request.getStartDate() != null) existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        if (request.getActivityType() != null) existing.setActivityType(request.getActivityType());
        if (request.getContactPersonName() != null) existing.setContactPersonName(request.getContactPersonName());
        if (request.getContactPersonRole() != null) existing.setContactPersonRole(request.getContactPersonRole());
        if (request.getContactPersonEmail() != null) {
            existing.setContactPersonEmail(request.getContactPersonEmail());
        }
        if (request.getObjectives() != null) existing.setObjectives(request.getObjectives());
        if (request.getBudget() != null) existing.setBudget(request.getBudget());
    }

    private void replaceThemes(Project project, List<String> themeCodes) {
        logger.debug("replaceThemes called with themeCodes: {}", themeCodes);

        Set<ProjectThemeAssignment> assignments = project.getThemes();
        if (assignments == null) {
            assignments = new java.util.HashSet<>();
            project.setThemes(assignments);
            logger.debug("Created new assignments set");
        } else {
            assignments.clear();
            logger.debug("Cleared existing assignments set");
        }

        if (themeCodes == null) {
            logger.debug("themeCodes is null, returning");
            return;
        }

        List<String> distinctCodes = themeCodes.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(code -> !code.isEmpty())
            .distinct()
            .collect(Collectors.toList());

        logger.debug("Processing {} distinct theme codes: {}", distinctCodes.size(), distinctCodes);

        for (String themeCode : distinctCodes) {
            try {
                logger.debug("Processing theme code: {}", themeCode);
                ProjectTheme theme = ProjectTheme.fromCode(themeCode);
                logger.debug("Found theme: {}", theme);
                ProjectThemeAssignment assignment = new ProjectThemeAssignment();
                assignment.setProject(project);
                assignment.setProjectTheme(theme);
                assignments.add(assignment);
                logger.debug("Added theme assignment for: {}", themeCode);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid theme code: {}, skipping", themeCode);
            } catch (Exception e) {
                logger.error("Unexpected error processing theme code {}: {}", themeCode, e.getMessage(), e);
            }
        }

        logger.debug("replaceThemes completed, total assignments: {}", assignments.size());
    }

    private void replaceLocations(Project project, List<ProjectCreateRequest.LocationRequest> locationRequests) {
        logger.debug("replaceLocations called with {} location requests", locationRequests != null ? locationRequests.size() : 0);

        Set<ProjectLocation> currentLocations = project.getLocations();
        if (currentLocations == null) {
            currentLocations = new java.util.HashSet<>();
            project.setLocations(currentLocations);
            logger.debug("Created new locations set");
        } else {
            currentLocations.clear();
            logger.debug("Cleared existing locations set");
        }

        if (locationRequests == null) {
            logger.debug("locationRequests is null, returning");
            return;
        }

        List<ProjectCreateRequest.LocationRequest> sanitized = locationRequests.stream()
            .filter(Objects::nonNull)
            .filter(request -> request.getCounty() != null && !request.getCounty().trim().isEmpty())
            .collect(Collectors.toList());

        logger.debug("Processing {} sanitized location requests", sanitized.size());

        for (ProjectCreateRequest.LocationRequest request : sanitized) {
            try {
                logger.debug("Processing location: county={}, subCounty={}, lat={}, lng={}",
                    request.getCounty(), request.getSubCounty(), request.getLatitude(), request.getLongitude());
                ProjectLocation location = new ProjectLocation();
                location.setProject(project);
                location.setCounty(request.getCounty());
                location.setSubCounty(request.getSubCounty());
                location.setMapsAddress(request.getMapsAddress());
                location.setLatitude(request.getLatitude());
                location.setLongitude(request.getLongitude());

                if (location.getMapsAddress() != null && !location.getMapsAddress().trim().isEmpty() &&
                        (location.getLatitude() == null || location.getLongitude() == null)) {
                    logger.debug("Extracting coordinates for location with address: {}", location.getMapsAddress());
                    extractAndSetCoordinatesForLocation(location);
                }

                currentLocations.add(location);
                logger.debug("Added location for county: {}", request.getCounty());
            } catch (Exception e) {
                logger.error("Error processing location request: {}", e.getMessage(), e);
            }
        }

        logger.debug("replaceLocations completed, total locations: {}", currentLocations.size());
    }

    public ProjectResponse toProjectResponse(Project project) {
        logger.debug("Converting project to response: {}", project != null ? project.getTitle() : "null");

        if (project == null) {
            logger.debug("Project is null, returning null response");
            return null;
        }

        try {
            logger.debug("Creating ProjectResponse object");
            ProjectResponse response = new ProjectResponse();
            response.setId(project.getId());
            response.setProjectNo(project.getProjectNo());
            response.setPartner(project.getPartner());
            // Set partner name
            Optional<User> partnerUser = userService.findByEmail(project.getPartner());
            if (partnerUser.isPresent()) {
                response.setPartnerName(partnerUser.get().getName());
                // Use persisted creator role if available (marks MoH-created projects), else fall back to partner's role
                if (project.getCreatedByRole() != null && !project.getCreatedByRole().isEmpty()) {
                    response.setCreatedByRole(project.getCreatedByRole());
                } else {
                    response.setCreatedByRole(partnerUser.get().getRole().name());
                }
                if (partnerUser.get().getOrganization() != null) {
                    response.setOrganizationName(partnerUser.get().getOrganization().getName());
                }
            } else if (project.getCreatedByRole() != null && !project.getCreatedByRole().isEmpty()) {
                response.setCreatedByRole(project.getCreatedByRole());
            }
            response.setTitle(project.getTitle());
            response.setProjectCategory(project.getProjectCategory());
            response.setLacosteNumber(project.getLacosteNumber());
            response.setStartDate(project.getStartDate());
            response.setEndDate(project.getEndDate());
            response.setActivityType(project.getActivityType());
            response.setCounty(project.getCounty());
            response.setContactPersonName(project.getContactPersonName());
            response.setContactPersonRole(project.getContactPersonRole());
            response.setContactPersonEmail(project.getContactPersonEmail());
            response.setObjectives(project.getObjectives());
            response.setBudget(project.getBudget());
            response.setStatus(project.getStatus());
            response.setCompletionPercentage(project.getCompletionPercentage());
            response.setHasReports(project.getHasReports());
            response.setApprovalStatus(project.getApprovalStatus());
            response.setApprovalWorkflowStatus(project.getApprovalWorkflowStatus());
            response.setApprovedBy(project.getApprovedBy());
            response.setApprovedAt(project.getApprovedAt());
            response.setRejectionReason(project.getRejectionReason());
            response.setCreatedAt(project.getCreatedAt());
            response.setUpdatedAt(project.getUpdatedAt());

            logger.debug("Converting themes: {} theme assignments", project.getThemes() != null ? project.getThemes().size() : 0);
            List<ProjectThemeDto> themeDtos = project.getThemes() == null ? List.of() : project.getThemes().stream()
                    .sorted(Comparator.comparing(a -> a.getProjectTheme() != null ? a.getProjectTheme().name() : ""))
                    .map(assignment -> {
                        ProjectTheme theme = assignment.getProjectTheme();
                        if (theme == null) {
                            return null;
                        }
                        return new ProjectThemeDto(theme.getCode(), theme.getDisplayName());
                    })
                    .filter(dto -> dto != null)
                    .collect(Collectors.toList());
            response.setThemes(themeDtos);

            logger.debug("Converting locations: {} location assignments", project.getLocations() != null ? project.getLocations().size() : 0);
            List<ProjectLocationDto> locationDtos = project.getLocations() == null ? List.of() : project.getLocations().stream()
                    .sorted(Comparator.comparing(ProjectLocation::getId, Comparator.nullsLast(Long::compareTo)))
                    .map(location -> new ProjectLocationDto(
                            location.getId(),
                            location.getCounty(),
                            location.getSubCounty(),
                            location.getMapsAddress(),
                            location.getLatitude(),
                            location.getLongitude()
                    ))
                    .collect(Collectors.toList());
            response.setLocations(locationDtos);

            logger.debug("Project response conversion completed successfully");
            return response;
        } catch (Exception e) {
            logger.error("Error converting project to response: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Create a new project from ProjectCreateRequest DTO
     */
    public Project createProjectFromRequest(ProjectCreateRequest request, String userEmail) {
        logger.info("Creating project from request: {} for email: {}", request.getTitle(), userEmail);
        logger.debug("Request details - themes: {}, locations: {}", request.getThemes(), request.getLocations());

        try {
            Project project = new Project();
            project.setProjectNo(generateProjectNo());
            project.setTitle(request.getTitle());
            
            // Set partner field: Only override with userEmail if request doesn't have a partner OR user is not a super admin
            // Super admins can create projects on behalf of partners, so preserve the partner from request
            com.tujulishanehub.backend.models.User currentUser = userService.getUserByEmail(userEmail);
            if (currentUser != null && currentUser.isSuperAdmin() && request.getPartner() != null && !request.getPartner().trim().isEmpty()) {
                // Super admin creating project for a partner - use the partner from request
                project.setPartner(request.getPartner());
                logger.debug("Super admin {} creating project for partner: {}", userEmail, request.getPartner());
            } else {
                // Regular user creating their own project
                project.setPartner(userEmail);
                logger.debug("User {} creating their own project", userEmail);
            }
            
            // Mark who created the project (used to flag MoH-created projects)
            if (currentUser != null) {
                project.setCreatedByRole(currentUser.getRole().name());
                logger.debug("Project created by role: {}", currentUser.getRole().name());
            } else {
                project.setCreatedByRole("PARTNER");
            }
            
            project.setProjectCategory(request.getProjectCategory());

            // Set lacoste number for research projects
            if(request.getLacosteNumber() != null && !request.getLacosteNumber().trim().isEmpty()){
                project.setLacosteNumber(request.getLacosteNumber().trim());
                logger.debug("Set lacoste number: {}",request.getLacosteNumber());
            }
            
            // Special handling for PRIORITY projects created by super admins
            if (project.getProjectCategory() == ProjectCategory.PRIORITY && currentUser != null && currentUser.isSuperAdmin()) {
                project.setApprovalStatus(ApprovalStatus.APPROVED);
                project.setApprovalWorkflowStatus(ApprovalWorkflowStatus.APPROVED);
                project.setStatus("active");
                logger.debug("Super admin creating PRIORITY project - set to immediately active and approved");
            } else {
                // CRITICAL: Ensure ALL projects go through approval workflow, regardless of who creates them
                // Even super admins must have their projects reviewed and approved
                project.setApprovalStatus(ApprovalStatus.PENDING);
                project.setApprovalWorkflowStatus(ApprovalWorkflowStatus.PENDING_REVIEW);
                logger.debug("Set approval status to PENDING and workflow status to PENDING_REVIEW for all new projects");
            }
            
            project.setStartDate(request.getStartDate());
            project.setEndDate(request.getEndDate());
            project.setActivityType(request.getActivityType());
            project.setBudget(request.getBudget());
            project.setObjectives(request.getObjectives());
            project.setContactPersonName(request.getContactPersonName());
            project.setContactPersonRole(request.getContactPersonRole());
            project.setContactPersonEmail(request.getContactPersonEmail());

            logger.debug("About to call replaceThemes with themes: {}", request.getThemes());
            replaceThemes(project, request.getThemes());

            logger.debug("About to call replaceLocations with locations: {}", request.getLocations());
            replaceLocations(project, request.getLocations());

            logger.debug("About to call createProject");
            return createProject(project);
        } catch (Exception e) {
            logger.error("Error in createProjectFromRequest: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Create a project by partner
     */
    public Project createProjectByPartner(Project project, String userEmail) {
        logger.info("Creating project by partner: {} for email: {}", project.getTitle(), userEmail);
        
        // Set partner email for tracking ownership
        project.setPartner(userEmail);
        
        // Set contact person email if not provided
        if (project.getContactPersonEmail() == null) {
            project.setContactPersonEmail(userEmail);
        }
        
        return createProject(project);
    }
    
    /**
     * Approve project (Admin only)
     */
    public boolean approveProject(Long projectId, Long approvedBy) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.APPROVED);
            project.setApprovedBy(approvedBy);
            project.setApprovedAt(java.time.LocalDateTime.now());
            project.setRejectionReason(null);
            project.setStatus("active"); // Make project active when approved
            projectRepository.save(project);
            logger.info("Project {} approved by admin {}", projectId, approvedBy);
            
            // Send notification email to both the contact person and the partner
            try {
                String subject = "Project Approved - " + project.getTitle();
                String body = String.format(
                    "Dear %s,\n\n" +
                    "Congratulations! Your project '%s' has been approved by the MOH administrator.\n\n" +
                    "Project Details:\n" +
                    "- Project Number: %s\n" +
                    "- Title: %s\n" +
                    "- Partner: %s\n" +
                    "- Status: Active\n\n" +
                    "Your project is now active and visible in the system. You can proceed with project activities and reporting.\n\n" +
                    "Best regards,\n" +
                    "RMCAH Hub Team",
                    project.getContactPersonName() != null ? project.getContactPersonName() : "User",
                    project.getTitle(),
                    project.getProjectNo() != null ? project.getProjectNo() : "N/A",
                    project.getTitle(),
                    project.getPartner()
                );
                sendProjectNotificationEmail(project, subject, body);
            } catch (Exception e) {
                logger.error("Failed to send approval notification email for project {}: {}", projectId, e.getMessage(), e);
                // Don't fail the approval if email fails
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Reject project (Admin only)
     */
    public boolean rejectProject(Long projectId, Long rejectedBy, String reason) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.REJECTED);
            project.setApprovedBy(rejectedBy);
            project.setRejectionReason(reason);
            project.setStatus("rejected"); // Set project status to rejected
            projectRepository.save(project);
            logger.info("Project {} rejected by admin {} with reason: {}", projectId, rejectedBy, reason);
            
            // Send notification email to both the contact person and the partner
            try {
                String subject = "Project Rejected - " + project.getTitle();
                String body = String.format(
                    "Dear %s,\n\n" +
                    "We regret to inform you that your project '%s' has been rejected by the MOH administrator.\n\n" +
                    "Project Details:\n" +
                    "- Project Number: %s\n" +
                    "- Title: %s\n" +
                    "- Partner: %s\n\n" +
                    "Rejection Reason:\n" +
                    "%s\n\n" +
                    "If you have any questions or would like to resubmit your project with corrections, " +
                    "please contact the MOH administrator or update your project accordingly.\n\n" +
                    "Best regards,\n" +
                    "RMCAH Hub Team",
                    project.getContactPersonName() != null ? project.getContactPersonName() : "User",
                    project.getTitle(),
                    project.getProjectNo() != null ? project.getProjectNo() : "N/A",
                    project.getTitle(),
                    project.getPartner(),
                    reason != null ? reason : "No reason provided"
                );
                sendProjectNotificationEmail(project, subject, body);
            } catch (Exception e) {
                logger.error("Failed to send rejection notification email for project {}: {}", projectId, e.getMessage(), e);
                // Don't fail the rejection if email fails
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Get projects by approval status (Admin only)
     */
    public List<Project> getProjectsByApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus approvalStatus) {
        return projectRepository.findByApprovalStatus(approvalStatus);
    }
    
    // ==================== TWO-TIER APPROVAL WORKFLOW METHODS ====================
    
    /**
     * Review a project (Thematic Reviewer only)
     * This is the first step in the two-tier approval process
     */
    public boolean reviewProject(Long projectId, Long reviewerId, String comments, boolean approved) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            
            // Validate project is in correct state for review
            if (project.getApprovalWorkflowStatus() != com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_REVIEW &&
                project.getApprovalWorkflowStatus() != com.tujulishanehub.backend.models.ApprovalWorkflowStatus.UNDER_REVIEW) {
                throw new IllegalStateException("Project is not in a state that can be reviewed");
            }
            
            project.setReviewedBy(reviewerId);
            project.setReviewedAt(java.time.LocalDateTime.now());
            project.setReviewerComments(comments);
            
            if (approved) {
                // Reviewer approved - move to next stage (awaiting final approver)
                project.setApprovalWorkflowStatus(com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_FINAL_APPROVAL);
                project.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.SUBMITTED); // Use SUBMITTED to indicate it's been reviewed
            } else {
                // Reviewer rejected
                project.setApprovalWorkflowStatus(com.tujulishanehub.backend.models.ApprovalWorkflowStatus.REJECTED_BY_REVIEWER);
                project.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.REJECTED);
                project.setRejectionReason(comments);
                project.setStatus("rejected");
            }
            
            projectRepository.save(project);
            logger.info("Project {} reviewed by reviewer {} - approved: {}", projectId, reviewerId, approved);
            
            // Send notification email to both the contact person and the partner
            try {
                String subject = approved ? 
                    "Project Reviewed - Awaiting Final Approval: " + project.getTitle() :
                    "Project Review - Revisions Required: " + project.getTitle();
                String body = String.format(
                    "Dear %s,\n\n" +
                    "Your project '%s' has been reviewed by the thematic area reviewer.\n\n" +
                    "Project Details:\n" +
                    "- Project Number: %s\n" +
                    "- Title: %s\n" +
                    "- Partner: %s\n" +
                    "- Review Status: %s\n" +
                    "- Reviewer Comments: %s\n\n" +
                    "%s\n\n" +
                    "Best regards,\n" +
                    "RMCAH Hub Team",
                    project.getContactPersonName() != null ? project.getContactPersonName() : "User",
                    project.getTitle(),
                    project.getProjectNo() != null ? project.getProjectNo() : "N/A",
                    project.getTitle(),
                    project.getPartner(),
                    approved ? "Approved for Final Review" : "Revisions Required",
                    comments != null ? comments : "No comments provided",
                    approved ? 
                        "Your project has passed the thematic review and is now awaiting final approval from the MOH administrator." :
                        "Please address the reviewer's comments and resubmit your project."
                );
                sendProjectNotificationEmail(project, subject, body);
            } catch (Exception e) {
                logger.error("Failed to send review notification email for project {}: {}", projectId, e.getMessage(), e);
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Final approval of a project (SUPER_ADMIN_APPROVER only)
     * This is the second step in the two-tier approval process
     */
    public boolean finalApproveProject(Long projectId, Long approverId, String approverComments) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            
            // Validate project has been reviewed
            if (project.getApprovalWorkflowStatus() != com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_FINAL_APPROVAL &&
                project.getApprovalWorkflowStatus() != com.tujulishanehub.backend.models.ApprovalWorkflowStatus.REVIEWED) {
                throw new IllegalStateException("Project must be reviewed before final approval");
            }
            
            project.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.APPROVED);
            project.setApprovalWorkflowStatus(com.tujulishanehub.backend.models.ApprovalWorkflowStatus.APPROVED);
            project.setApprovedBy(approverId);
            project.setApprovedAt(java.time.LocalDateTime.now());
            project.setRejectionReason(null);
            project.setStatus("active");
            
            // Store approver comments if provided
            if (approverComments != null && !approverComments.isEmpty()) {
                String existingComments = project.getReviewerComments();
                project.setReviewerComments(existingComments != null ? 
                    existingComments + "\n\nFinal Approver: " + approverComments : 
                    "Final Approver: " + approverComments);
            }
            
            projectRepository.save(project);
            logger.info("Project {} finally approved by approver {}", projectId, approverId);
            
            // Send notification email to both the contact person and the partner
            try {
                String subject = "Project Approved - " + project.getTitle();
                String body = String.format(
                    "Dear %s,\n\n" +
                    "Congratulations! Your project '%s' has received final approval from the MOH administrator.\n\n" +
                    "Project Details:\n" +
                    "- Project Number: %s\n" +
                    "- Title: %s\n" +
                    "- Partner: %s\n" +
                    "- Status: Active\n\n" +
                    "Your project is now active and visible in the system. You can proceed with project activities and reporting.\n\n" +
                    "Best regards,\n" +
                    "RMCAH Hub Team",
                    project.getContactPersonName() != null ? project.getContactPersonName() : "User",
                    project.getTitle(),
                    project.getProjectNo() != null ? project.getProjectNo() : "N/A",
                    project.getTitle(),
                    project.getPartner()
                );
                sendProjectNotificationEmail(project, subject, body);
            } catch (Exception e) {
                logger.error("Failed to send final approval notification email for project {}: {}", projectId, e.getMessage(), e);
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Reject a project at final approval stage (SUPER_ADMIN_APPROVER only)
     */
    public boolean finalRejectProject(Long projectId, Long approverId, String reason) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            
            project.setApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.REJECTED);
            project.setApprovalWorkflowStatus(com.tujulishanehub.backend.models.ApprovalWorkflowStatus.REJECTED_BY_APPROVER);
            project.setApprovedBy(approverId);
            project.setRejectionReason(reason);
            project.setStatus("rejected");
            
            projectRepository.save(project);
            logger.info("Project {} rejected at final approval by approver {} with reason: {}", projectId, approverId, reason);
            
            // Send notification email to both the contact person and the partner
            try {
                String subject = "Project Rejected - " + project.getTitle();
                String body = String.format(
                    "Dear %s,\n\n" +
                    "Your project '%s' has been rejected at the final approval stage.\n\n" +
                    "Project Details:\n" +
                    "- Project Number: %s\n" +
                    "- Title: %s\n" +
                    "- Partner: %s\n" +
                    "- Rejection Reason: %s\n\n" +
                    "Please address the issues mentioned and resubmit your project for review.\n\n" +
                    "Best regards,\n" +
                    "RMCAH Hub Team",
                    project.getContactPersonName() != null ? project.getContactPersonName() : "User",
                    project.getTitle(),
                    project.getProjectNo() != null ? project.getProjectNo() : "N/A",
                    project.getTitle(),
                    project.getPartner(),
                    reason
                );
                sendProjectNotificationEmail(project, subject, body);
            } catch (Exception e) {
                logger.error("Failed to send final rejection notification email for project {}: {}", projectId, e.getMessage(), e);
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Get projects that need review by a specific thematic area reviewer
     * Now supports reviewers with multiple thematic areas
     */
    public List<Project> getProjectsForReviewer(com.tujulishanehub.backend.models.ProjectTheme thematicArea) {
        // Get all projects pending review
        List<Project> allPendingProjects = projectRepository.findAll().stream()
            .filter(p -> p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_REVIEW ||
                        p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.UNDER_REVIEW)
            .collect(Collectors.toList());
        
        // Filter by thematic area - check if any of the project's themes match the reviewer's thematic area
        return allPendingProjects.stream()
            .filter(project -> {
                if (project.getThemes() == null || project.getThemes().isEmpty()) {
                    return false;
                }
                return project.getThemes().stream()
                    .anyMatch(assignment -> assignment.getProjectTheme() == thematicArea);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get projects for reviewer with multiple thematic areas (many-to-many)
     */
    public List<Project> getProjectsForReviewerWithThematicAreas(List<com.tujulishanehub.backend.models.ProjectTheme> thematicAreas) {
        if (thematicAreas == null || thematicAreas.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        // Get all projects pending review
        List<Project> allPendingProjects = projectRepository.findAll().stream()
            .filter(p -> p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_REVIEW ||
                        p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.UNDER_REVIEW)
            .collect(Collectors.toList());
        
        // Filter by thematic areas - check if any of the project's themes match any of the reviewer's thematic areas
        return allPendingProjects.stream()
            .filter(project -> {
                if (project.getThemes() == null || project.getThemes().isEmpty()) {
                    return false;
                }
                return project.getThemes().stream()
                    .anyMatch(assignment -> thematicAreas.contains(assignment.getProjectTheme()));
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get projects that need review by a reviewer (supports multiple thematic areas)
     */
    public List<Project> getProjectsForReviewerUser(com.tujulishanehub.backend.models.User reviewer) {
        // Get all projects pending review
        List<Project> allPendingProjects = projectRepository.findAll().stream()
            .filter(p -> p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_REVIEW ||
                        p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.UNDER_REVIEW)
            .collect(Collectors.toList());
        
        // Filter by reviewer's thematic areas (supports multiple)
        return allPendingProjects.stream()
            .filter(project -> {
                if (project.getThemes() == null || project.getThemes().isEmpty()) {
                    return false;
                }
                // Check if any of the project's themes match any of the reviewer's thematic areas
                return project.getThemes().stream()
                    .anyMatch(assignment -> reviewer.hasThematicArea(assignment.getProjectTheme()));
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get projects awaiting final approval
     */
    public List<Project> getProjectsAwaitingFinalApproval() {
        return projectRepository.findAll().stream()
            .filter(p -> p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.PENDING_FINAL_APPROVAL ||
                        p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.REVIEWED ||
                        p.getApprovalWorkflowStatus() == com.tujulishanehub.backend.models.ApprovalWorkflowStatus.REJECTED_BY_REVIEWER)
            .collect(Collectors.toList());
    }
    
    /**
     * Get projects by partner email
     */
    public List<Project> getProjectsByPartnerEmail(String email) {
        logger.info("Fetching projects for partner email: {}", email);
        
        // Try both partner field and contactPersonEmail for backward compatibility
        List<Project> projectsByPartner = projectRepository.findByPartner(email);
        List<Project> projectsByContact = projectRepository.findByContactPersonEmail(email);
        
        // Combine and deduplicate
        java.util.Set<Project> combinedProjects = new java.util.HashSet<>(projectsByPartner);
        combinedProjects.addAll(projectsByContact);
        
        List<Project> result = new java.util.ArrayList<>(combinedProjects);
        logger.info("Found {} projects for email: {}", result.size(), email);
        
        return result;
    }
    
    /**
     * Check if user is the owner of a project
     */
    public boolean isProjectOwner(Long projectId, String userEmail) {
        Optional<Project> project = projectRepository.findById(projectId);
        if (!project.isPresent()) return false;
        Project p = project.get();
        return userEmail.equals(p.getPartner()) || userEmail.equals(p.getContactPersonEmail());
    }
    
    /**
     * Get admin dashboard statistics
     */
    public java.util.Map<String, Object> getAdminDashboardStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        // Project counts by status
        stats.put("totalProjects", projectRepository.count());
        stats.put("pendingProjects", projectRepository.countByApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.PENDING));
        stats.put("submittedProjects", projectRepository.countByApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.SUBMITTED));
        stats.put("approvedProjects", projectRepository.countByApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.APPROVED));
        stats.put("rejectedProjects", projectRepository.countByApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.REJECTED));
        
        // Recent projects
        List<Project> recentProjects = projectRepository.findTop10ByOrderByCreatedAtDesc();
        stats.put("recentProjects", recentProjects);
        
        return stats;
    }

    /**
     * Mark a project as completed
     */
    public Project completeProject(Long projectId, String completedBy) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            throw new RuntimeException("Project not found");
        }

        Project project = projectOpt.get();
        if (!"active".equalsIgnoreCase(project.getStatus())) {
            throw new RuntimeException("Only active projects can be marked as completed");
        }

        project.markAsCompleted();
        projectRepository.save(project);

        logger.info("Project {} marked as completed by {}", projectId, completedBy);
        return project;
    }

    /**
     * Mark a project as stalled
     */
    public Project stallProject(Long projectId, String stalledBy) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            throw new RuntimeException("Project not found");
        }

        Project project = projectOpt.get();
        if (!"active".equalsIgnoreCase(project.getStatus())) {
            throw new RuntimeException("Only active projects can be marked as stalled");
        }

        project.setStatus("stalled");
        projectRepository.save(project);

        logger.info("Project {} marked as stalled by {}", projectId, stalledBy);
        return project;
    }

    /**
     * Generate a unique project number in P-0001 format
     */
    private String generateProjectNo() {
        // Find the highest existing project number
        Optional<String> maxProjectNo = projectRepository.findAll().stream()
            .filter(p -> p.getProjectNo() != null && p.getProjectNo().startsWith("P-"))
            .map(Project::getProjectNo)
            .map(no -> no.substring(2)) // Remove "P-"
            .map(Integer::parseInt)
            .max(Integer::compareTo)
            .map(num -> String.format("P-%04d", num + 1));

        return maxProjectNo.orElse("P-0001");
    }

    /**
     * Send email notifications to both the contact person and the partner
     */
    private void sendProjectNotificationEmail(Project project, String subject, String body) {
        String contactEmail = project.getContactPersonEmail();
        String partnerEmail = project.getPartner();
        
        if (contactEmail != null && !contactEmail.trim().isEmpty()) {
            try {
                emailService.sendEmail(contactEmail, subject, body);
                logger.info("Notification email sent to contact person: {}", contactEmail);
            } catch (Exception e) {
                logger.error("Failed to send notification email to contact person {}: {}", contactEmail, e.getMessage(), e);
            }
        }
        
        if (partnerEmail != null && !partnerEmail.trim().isEmpty() && (contactEmail == null || !partnerEmail.equalsIgnoreCase(contactEmail))) {
            try {
                emailService.sendEmail(partnerEmail, subject, body);
                logger.info("Notification email sent to partner: {}", partnerEmail);
            } catch (Exception e) {
                logger.error("Failed to send notification email to partner {}: {}", partnerEmail, e.getMessage(), e);
            }
        }
    }

    /**
     * Statistics class for project data
     */
    public static class ProjectStatistics {
        private final List<Object[]> statusCounts;
        private final List<Object[]> countyCounts;
        private final long totalProjects;
        private final long projectsWithCoordinates;
        
        public ProjectStatistics(List<Object[]> statusCounts, List<Object[]> countyCounts, 
                               long totalProjects, long projectsWithCoordinates) {
            this.statusCounts = statusCounts;
            this.countyCounts = countyCounts;
            this.totalProjects = totalProjects;
            this.projectsWithCoordinates = projectsWithCoordinates;
        }
        
        public List<Object[]> getStatusCounts() { return statusCounts; }
        public List<Object[]> getCountyCounts() { return countyCounts; }
        public long getTotalProjects() { return totalProjects; }
        public long getProjectsWithCoordinates() { return projectsWithCoordinates; }
        public double getCoordinatesCoveragePercentage() {
            return totalProjects > 0 ? (double) projectsWithCoordinates / totalProjects * 100 : 0;
        }
    }
}