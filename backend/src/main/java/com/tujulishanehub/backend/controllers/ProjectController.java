package com.tujulishanehub.backend.controllers;

import com.tujulishanehub.backend.payload.ProjectCreateRequest;
import com.tujulishanehub.backend.models.ProjectDocument;
import com.tujulishanehub.backend.models.ProjectReportDocument;
import com.tujulishanehub.backend.repositories.ProjectDocumentRepository;
import com.tujulishanehub.backend.repositories.ProjectReportDocumentRepository;
import com.tujulishanehub.backend.repositories.ProjectRepository;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import com.tujulishanehub.backend.payload.ProjectUpdateRequest;
import com.tujulishanehub.backend.payload.ProjectResponse;
import com.tujulishanehub.backend.payload.ApiResponse;
import com.tujulishanehub.backend.models.Project;
import com.tujulishanehub.backend.models.PastProject;
import com.tujulishanehub.backend.models.ProjectLocation;
import com.tujulishanehub.backend.models.User;
import com.tujulishanehub.backend.models.ApprovalStatus;
import com.tujulishanehub.backend.models.ApprovalWorkflowStatus;
import com.tujulishanehub.backend.models.ProjectCategory;
import com.tujulishanehub.backend.models.ProjectTheme;
import com.tujulishanehub.backend.services.ProjectService;
import com.tujulishanehub.backend.services.ProjectCollaboratorService;
import com.tujulishanehub.backend.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;
    
    @Autowired
    private ProjectReportDocumentRepository projectReportDocumentRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);
    
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final List<String> ALLOWED_REPORT_TYPES = Arrays.asList(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
        "application/msword", // DOC
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // XLSX
        "application/vnd.ms-excel", // XLS
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", // PPTX
        "application/vnd.ms-powerpoint", // PPT
        "text/csv",
        "text/plain"
    );
    
    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ProjectCollaboratorService projectCollaboratorService;
    
    @GetMapping("/partners/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<com.tujulishanehub.backend.payload.UserProfileDTO>>> getAvailablePartners() {
        try {
            List<com.tujulishanehub.backend.payload.UserProfileDTO> partners = userService.getUsersByRole(User.Role.PARTNER).stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()) && ApprovalStatus.APPROVED.equals(p.getApprovalStatus()))
                .map(com.tujulishanehub.backend.payload.UserProfileDTO::new)
                .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(new ApiResponse<>(200, "Available partners retrieved successfully", partners));
        } catch (Exception e) {
            logger.error("Error retrieving available partners: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new ApiResponse<>(500, "Failed to retrieve available partners", null));
        }
    }

    /**
     * Create a new project (Partners/Admins)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN', 'SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
        @RequestPart("project") String projectJson,
        @RequestPart(value = "supporting_documents", required = false) List<MultipartFile> files
    ) {
        try {
            // Parse the JSON string to ProjectCreateRequest
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            ProjectCreateRequest request = objectMapper.readValue(projectJson, ProjectCreateRequest.class);

            // Validate request fields before processing
            List<String> validationErrors = validateProjectRequest(request);
            if (!validationErrors.isEmpty()) {
                String errorMessage = "Validation failed: " + String.join(", ", validationErrors);
                logger.warn("Controller: Validation errors: {}", errorMessage);
                ApiResponse<ProjectResponse> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    errorMessage,
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Log brief info to console
            logger.info("Controller: Creating new project: {}", request.getTitle());
            logger.debug("Controller: Full request data - title: {}, themes: {}, locations: {}, partner: {}",
                request.getTitle(), request.getThemes(), request.getLocations(), request.getPartner());

            // Log files info
            if (files != null && !files.isEmpty()) {
                logger.info("Controller: Received {} supporting documents", files.size());
                for (MultipartFile file : files) {
                    logger.info("Controller: File - name: {}, size: {}, type: {}", 
                        file.getOriginalFilename(), file.getSize(), file.getContentType());
                }
            } else {
                logger.info("Controller: No supporting documents received");
            }

            // Get authenticated user email
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();

            // Check if priority project is being created by non-admin
            if (request.getProjectCategory() == ProjectCategory.PRIORITY) {
                User currentUser = userService.findByEmail(userEmail).orElse(null);
                if (currentUser == null || !currentUser.getRole().name().startsWith("SUPER_ADMIN")) {
                    throw new RuntimeException("Only admin users can create priority projects");
                }
            }

            // Save project as usual
            Project createdProject = projectService.createProjectFromRequest(request, userEmail);

            // Save files as blobs
            if (files != null) {
                logger.info("Controller: Processing {} files for project {}", files.size(), createdProject.getId());
                for (MultipartFile file : files) {
                    logger.info("Controller: Saving file: {}", file.getOriginalFilename());
                    ProjectDocument doc = new ProjectDocument();
                    doc.setFileName(file.getOriginalFilename());
                    doc.setFileType(file.getContentType());
                    doc.setFileSize(file.getSize()); // Store file size
                    doc.setData(file.getBytes());
                    doc.setProject(createdProject);
                    
                    // Set additional fields
                    User currentUser = userService.findByEmail(userEmail).orElse(null);
                    doc.setUploadedBy(currentUser);
                    LocalDateTime now = LocalDateTime.now();
                    doc.setUploadDate(now);
                    doc.setCreatedAt(now);
                    
                    projectDocumentRepository.save(doc);
                    logger.info("Controller: Successfully saved document: {} for project {}", 
                        file.getOriginalFilename(), createdProject.getId());
                }
                logger.info("Controller: All documents saved for project {}", createdProject.getId());
            }
            logger.debug("Controller: Request validation passed, user authenticated");
            logger.debug("Controller: Authenticated user: {}", userEmail);
            logger.debug("Controller: Project created, converting to response");
            ProjectResponse projectResponse = projectService.toProjectResponse(createdProject);
            logger.debug("Controller: Response created successfully");

            logger.debug("Controller: Creating API response");
            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Project created successfully",
                projectResponse
            );
            logger.info("Controller: Project creation completed successfully");
            logger.debug("Controller: About to return ResponseEntity");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            logger.error("Controller: Error creating project: {}", e.getMessage(), e);
            // Attempt to append the stacktrace to a dedicated error log file for deeper inspection
            try {
                Path logDir = Paths.get("logs");
                if (!Files.exists(logDir)) {
                    try { Files.createDirectories(logDir); } catch (Exception ignore) { }
                }
                Path errFile = logDir.resolve("api-projects-errors.log");
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                String entry = String.format("%s - Exception during /api/projects POST:\n%s\n---\n", java.time.LocalDateTime.now(), sw.toString());
                try { Files.writeString(errFile, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ioe) { logger.warn("Could not write error log: {}", ioe.getMessage()); }
            } catch (Exception logErr) {
                logger.warn("Failed to write exception to error log: {}", logErr.getMessage());
            }

            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to create project: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all projects with pagination
     * Approved projects are shown to everyone (public).
     * Pending/rejected projects are only shown to admin users (SUPER_ADMIN, SUPER_ADMIN_APPROVER, SUPER_ADMIN_REVIEWER).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);
            
            // Only admins see non-approved (pending/review) projects. Everyone else sees approved projects only.
            boolean isAdmin = false;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                User currentUser = userService.getUserByEmail(auth.getName());
                isAdmin = currentUser != null && currentUser.isSuperAdmin();
            }

            Page<Project> projectPage = isAdmin
                ? projectService.getProjects(pageable)
                : projectRepository.findByApprovalWorkflowStatus(ApprovalWorkflowStatus.APPROVED, pageable);

            List<ProjectResponse> projectResponses = projectPage.getContent().stream()
                .map(projectService::toProjectResponse)
                .collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("projects", projectResponses);
            data.put("currentPage", projectPage.getNumber());
            data.put("totalItems", projectPage.getTotalElements());
            data.put("totalPages", projectPage.getTotalPages());
            data.put("hasNext", projectPage.hasNext());
            data.put("hasPrevious", projectPage.hasPrevious());
            
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Projects retrieved successfully", 
                data
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving projects: {}", e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all available thematic areas
     */
    @GetMapping("/thematic-areas")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getThematicAreas() {
        try {
            List<Map<String, String>> themes = Arrays.stream(ProjectTheme.values())
                .map(theme -> {
                    Map<String, String> themeMap = new HashMap<>();
                    themeMap.put("code", theme.getCode());
                    themeMap.put("displayName", theme.getDisplayName());
                    themeMap.put("name", theme.name());
                    return themeMap;
                })
                .collect(Collectors.toList());
            
            ApiResponse<List<Map<String, String>>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Thematic areas retrieved successfully", 
                themes
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving thematic areas: {}", e.getMessage(), e);
            ApiResponse<List<Map<String, String>>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve thematic areas: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get project by ID
     * Pending/rejected projects are only visible to their owners and admin users.
     */
    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(@PathVariable Long id) {
        try {
            Optional<Project> project = projectService.getProjectById(id);
            
            if (project.isPresent()) {
                Project p = project.get();
                
                // Hide pending/rejected projects from the public and non-owner non-admin users
                boolean visible = p.getApprovalWorkflowStatus() == ApprovalWorkflowStatus.APPROVED
                    || p.getApprovalStatus() == ApprovalStatus.APPROVED;
                if (!visible) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.isAuthenticated()) {
                        User currentUser = userService.getUserByEmail(auth.getName());
                        boolean isOwner = currentUser != null && (
                            currentUser.isSuperAdmin()
                            || (p.getPartner() != null && p.getPartner().equalsIgnoreCase(auth.getName()))
                            || (p.getContactPersonEmail() != null && p.getContactPersonEmail().equalsIgnoreCase(auth.getName()))
                        );
                        visible = isOwner;
                    }
                }
                
                if (!visible) {
                    ApiResponse<ProjectResponse> response = new ApiResponse<>(
                        HttpStatus.NOT_FOUND.value(), 
                        "Project not found", 
                        null
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                ApiResponse<ProjectResponse> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Project found", 
                    projectService.toProjectResponse(p)
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<ProjectResponse> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "Project not found with ID: " + id, 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving project {}: {}", id, e.getMessage(), e);
            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve project: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get a project by its project number
     * Pending/rejected projects are only visible to their owners and admin users.
     */
    @GetMapping("/by-number/{projectNo}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectByNumber(@PathVariable String projectNo) {
        try {
            Optional<Project> project = projectRepository.findByProjectNo(projectNo);

            if (project.isPresent()) {
                Project p = project.get();
                
                // Hide pending/rejected projects from non-owner non-admin users
                boolean visible = p.getApprovalWorkflowStatus() == ApprovalWorkflowStatus.APPROVED
                    || p.getApprovalStatus() == ApprovalStatus.APPROVED;
                if (!visible) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    User currentUser = userService.getUserByEmail(auth.getName());
                    boolean isOwner = currentUser != null && (
                        currentUser.isSuperAdmin()
                        || (p.getPartner() != null && p.getPartner().equalsIgnoreCase(auth.getName()))
                        || (p.getContactPersonEmail() != null && p.getContactPersonEmail().equalsIgnoreCase(auth.getName()))
                    );
                    visible = isOwner;
                }
                
                if (!visible) {
                    ApiResponse<ProjectResponse> response = new ApiResponse<>(
                        HttpStatus.NOT_FOUND.value(),
                        "Project not found with number: " + projectNo,
                        null
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                ApiResponse<ProjectResponse> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Project found",
                    projectService.toProjectResponse(p)
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<ProjectResponse> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found with number: " + projectNo,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            logger.error("Error retrieving project by number {}: {}", projectNo, e.getMessage(), e);
            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve project: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get public statistics (no authentication required)
     */
    @GetMapping("/public/statistics")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Get all projects with locations eagerly loaded
            List<Project> allProjects = projectRepository.findAllWithLocations();

            // Filter to only include APPROVED projects for public statistics
            List<Project> approvedProjects = allProjects.stream()
                .filter(p -> p.getApprovalWorkflowStatus() == ApprovalWorkflowStatus.APPROVED)
                .collect(Collectors.toList());

            // Get total projects count (only approved projects)
            stats.put("totalProjects", (long) approvedProjects.size());

            // Get unique counties count
            Set<String> uniqueCounties = new HashSet<>();
            for (Project project : approvedProjects) {
                if (project.getLocations() != null && !project.getLocations().isEmpty()) {
                    for (ProjectLocation location : project.getLocations()) {
                        if (location.getCounty() != null && !location.getCounty().trim().isEmpty()) {
                            uniqueCounties.add(location.getCounty());
                        }
                    }
                }
            }
            stats.put("totalCounties", uniqueCounties.size());

            // Get unique partners/stakeholders count
            Set<String> uniquePartners = new HashSet<>();
            for (Project project : approvedProjects) {
                if (project.getPartner() != null && !project.getPartner().trim().isEmpty()) {
                    uniquePartners.add(project.getPartner());
                }
            }
            stats.put("totalStakeholders", uniquePartners.size());

            // Get counts by category
            Map<String, Long> categoryCount = new HashMap<>();
            for (ProjectCategory category : ProjectCategory.values()) {
                long count = approvedProjects.stream()
                    .filter(p -> p.getProjectCategory() == category)
                    .count();
                categoryCount.put(category.name(), count);
            }
            stats.put("byCategory", categoryCount);
            
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Public statistics retrieved successfully",
                stats
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving public statistics: {}", e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve statistics: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Update project (Owner, Admin, or Collaborator with EDITOR/CO_OWNER role)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long id,
            @RequestPart("project") String projectJson,
            @RequestPart(value = "supporting_documents", required = false) List<MultipartFile> files) {

        try {
            // Parse the JSON string to ProjectUpdateRequest
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            ProjectUpdateRequest request = objectMapper.readValue(projectJson, ProjectUpdateRequest.class);

            // Log brief info to console
            logger.info("Controller: Updating project: {}", request.getTitle());
            logger.debug("Controller: Full request data - title: {}, themes: {}, locations: {}",
                request.getTitle(), request.getThemes(), request.getLocations());

            // Log files info
            if (files != null && !files.isEmpty()) {
                logger.info("Controller: Received {} supporting documents for update", files.size());
                for (MultipartFile file : files) {
                    logger.info("Controller: File - name: {}, size: {}, type: {}",
                        file.getOriginalFilename(), file.getSize(), file.getContentType());
                }
            } else {
                logger.info("Controller: No supporting documents received for update");
            }

            // Get authenticated user email
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();

            // Check if user is admin, super admin, owner, or collaborator with edit permission
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                                   a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                                   a.getAuthority().equals("ROLE_SUPER_ADMIN_REVIEWER") ||
                                   a.getAuthority().equals("ROLE_SUPER_ADMIN_APPROVER"));
            boolean isOwner = projectService.isProjectOwner(id, userEmail);
            boolean canEdit = projectCollaboratorService.canEditProject(id, userEmail);

            if (!isAdmin && !isOwner && !canEdit) {
                logger.warn("User {} attempted to edit project {} without permission", userEmail, id);
                ApiResponse<ProjectResponse> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(),
                    "You don't have permission to edit this project",
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Update the project with tracking
            Project updatedProject = projectService.updateProject(id, request, userEmail);

            // Save files as blobs if provided
            if (files != null) {
                logger.info("Controller: Processing {} files for project update {}", files.size(), id);
                for (MultipartFile file : files) {
                    logger.info("Controller: Saving file: {}", file.getOriginalFilename());
                    ProjectDocument doc = new ProjectDocument();
                    doc.setFileName(file.getOriginalFilename());
                    doc.setFileType(file.getContentType());
                    doc.setFileSize(file.getSize()); // Store file size
                    doc.setData(file.getBytes());
                    doc.setProject(updatedProject);

                    // Set additional fields
                    User currentUser = userService.findByEmail(userEmail).orElse(null);
                    doc.setUploadedBy(currentUser);
                    LocalDateTime now = LocalDateTime.now();
                    doc.setUploadDate(now);
                    doc.setCreatedAt(now);

                    projectDocumentRepository.save(doc);
                    logger.info("Controller: Successfully saved document: {} for project {}",
                        file.getOriginalFilename(), id);
                }
                logger.info("Controller: All documents saved for project update {}", id);
            }

            ProjectResponse projectResponse = projectService.toProjectResponse(updatedProject);

            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Project updated successfully",
                projectResponse
            );
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error updating project {}: {}", id, e.getMessage());
            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            logger.error("Error updating project {}: {}", id, e.getMessage(), e);
            ApiResponse<ProjectResponse> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to update project: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Delete project
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Object>> deleteProject(@PathVariable String id) {
        try {
            // Try to parse as Long first (database ID)
            try {
                Long projectId = Long.parseLong(id);
                projectService.deleteProject(projectId);
            } catch (NumberFormatException e) {
                // If not a valid Long, treat as project number
                projectService.deleteProjectByProjectNumber(id);
            }
            
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Project deleted successfully", 
                null
            );
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error deleting project {}: {}", id, e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.NOT_FOUND.value(), 
                e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            
        } catch (Exception e) {
            logger.error("Error deleting project {}: {}", id, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to delete project: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Search projects
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> searchProjects(
            @RequestParam(required = false) String partner,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String projectNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String county,
            @RequestParam(required = false) String activityType) {
        
        try {
            List<Project> projects = projectService.searchProjects(partner, title, projectNo, status, county, activityType);
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Search completed successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error searching projects: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to search projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects by status
     * Role-based: MoH users see all projects, PARTNER/DONOR see only their own
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsByStatus(@PathVariable String status) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            User currentUser = userService.getUserByEmail(userEmail);
            
            List<Project> projects;
            
            // Check if user is MoH (can see all projects)
            boolean isMoH = currentUser != null && 
                (currentUser.getRole() == User.Role.SUPER_ADMIN ||
                 currentUser.getRole() == User.Role.SUPER_ADMIN_REVIEWER ||
                 currentUser.getRole() == User.Role.SUPER_ADMIN_APPROVER);
            
            if (isMoH) {
                // MoH users see all projects with this status
                projects = projectService.getProjectsByStatus(status);
            } else {
                // PARTNER/DONOR users see only their own projects with this status
                List<Project> userProjects = projectService.getProjectsByPartnerEmail(userEmail);
                projects = userProjects.stream()
                    .filter(p -> status.equalsIgnoreCase(p.getStatus()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Projects retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving projects by status {}: {}", status, e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects with coordinates (for map display)
     */
    @GetMapping("/with-coordinates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsWithCoordinates() {
        try {
            List<Project> projects = projectService.getProjectsWithCoordinates();
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Projects with coordinates retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving projects with coordinates: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all projects (for priority projects display)
     * Filters out rejected/inactive projects for non-admin users
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getAllProjects() {
        try {
            List<Project> projects = projectRepository.findAll();

            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "All projects retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving all projects: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects within a geographic bounding box
     */
    @GetMapping("/in-bounds")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsInBoundingBox(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng) {
        
        try {
            List<Project> projects = projectService.getProjectsInBoundingBox(minLat, maxLat, minLng, maxLng);
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Projects in bounding box retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving projects in bounding box: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            List<Project> projects = projectService.getProjectsByDateRange(startDate, endDate);
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Projects in date range retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving projects by date range: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get currently active projects
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getActiveProjects() {
        try {
            List<Project> projects = projectService.getActiveProjects();
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Active projects retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving active projects: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve active projects: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get project statistics
     * Role-based: 
     * - SUPER_ADMIN: all projects
     * - SUPER_ADMIN_REVIEWER: projects in their thematic area
     * - SUPER_ADMIN_APPROVER: projects pending final approval
     * - PARTNER/DONOR: only their own projects
     */
    @GetMapping("/statistics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProjectStatistics() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            User currentUser = userService.getUserByEmail(userEmail);
            
            ProjectService.ProjectStatistics stats;
            
            if (currentUser != null) {
                switch (currentUser.getRole()) {
                    case SUPER_ADMIN:
                        // SUPER_ADMIN sees all project statistics
                        stats = projectService.getProjectStatistics();
                        break;
                        
                    case SUPER_ADMIN_REVIEWER:
                        // SUPER_ADMIN_REVIEWER sees statistics for their thematic area
                        if (currentUser.getThematicArea() != null) {
                            stats = projectService.getProjectStatisticsByThematicArea(currentUser.getThematicArea());
                        } else {
                            // Reviewer without thematic area sees all (should not happen)
                            stats = projectService.getProjectStatistics();
                        }
                        break;
                        
                    case SUPER_ADMIN_APPROVER:
                        // SUPER_ADMIN_APPROVER sees statistics for projects pending final approval
                        stats = projectService.getProjectStatisticsForApprover();
                        break;
                        
                    default:
                        // PARTNER/DONOR users see only their own project statistics
                        stats = projectService.getProjectStatisticsByPartner(userEmail);
                        break;
                }
            } else {
                // No user found, return empty statistics
                stats = projectService.getProjectStatisticsByPartner(userEmail);
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("totalProjects", stats.getTotalProjects());
            data.put("projectsWithCoordinates", stats.getProjectsWithCoordinates());
            data.put("coordinatesCoveragePercentage", stats.getCoordinatesCoveragePercentage());
            data.put("statusCounts", stats.getStatusCounts());
            data.put("countyCounts", stats.getCountyCounts());
            
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Statistics retrieved successfully", 
                data
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving project statistics: {}", e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve statistics: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Batch process projects that need geocoding
     */
    @PostMapping("/geocode-batch")
    public ResponseEntity<ApiResponse<Object>> batchGeocodeProjects() {
        try {
            projectService.processProjectsNeedingGeocoding();
            
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Batch geocoding process completed successfully", 
                null
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error in batch geocoding process: {}", e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to complete batch geocoding: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== ADMIN ENDPOINTS ====================
    
    /**
     * Approve a project (Admin only)
     */
    @PostMapping("/admin/approve/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approveProject(@PathVariable Long projectId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth.getName();
            User admin = userService.getUserByEmail(adminEmail);
            
            boolean success = projectService.approveProject(projectId, admin.getId());
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "Project approved successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "Project not found", 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error approving project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to approve project", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Reject a project (Admin only)
     */
    @PostMapping("/admin/reject/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> rejectProject(
            @PathVariable Long projectId, 
            @RequestBody Map<String, String> payload) {
        try {
            String reason = payload.getOrDefault("reason", "No reason provided");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminEmail = auth.getName();
            User admin = userService.getUserByEmail(adminEmail);
            
            boolean success = projectService.rejectProject(projectId, admin.getId(), reason);
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(), 
                    "Project rejected successfully", 
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(), 
                    "Project not found", 
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error rejecting project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to reject project", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects by approval status (Admin only)
     */
    @GetMapping("/admin/approval-status/{status}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsByApprovalStatus(@PathVariable String status) {
        try {
            ApprovalStatus approvalStatus = ApprovalStatus.valueOf(status.toUpperCase());
            List<Project> projects = projectService.getProjectsByApprovalStatus(approvalStatus);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Projects retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(), 
                "Invalid approval status. Valid values: PENDING, APPROVED, REJECTED", 
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error retrieving projects by approval status {}: {}", status, e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get admin dashboard statistics (Admin only)
     */
    @GetMapping("/admin/dashboard-stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminDashboardStats() {
        try {
            Map<String, Object> stats = projectService.getAdminDashboardStats();
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Admin dashboard statistics retrieved successfully", 
                stats
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving admin dashboard stats: {}", e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve dashboard statistics", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ==================== TWO-TIER APPROVAL WORKFLOW ENDPOINTS ====================
    
    /**
     * Review a project (Thematic Reviewer only)
     * First step in two-tier approval process
     */
    @PostMapping("/admin/review/{projectId}")
    @PreAuthorize("hasRole('SUPER_ADMIN_REVIEWER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> reviewProject(
            @PathVariable Long projectId,
            @RequestBody Map<String, Object> payload) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String reviewerEmail = auth.getName();
            User reviewer = userService.getUserByEmail(reviewerEmail);
            
            // Validate reviewer has thematic area assigned (unless legacy SUPER_ADMIN)
            if (reviewer.getRole() == User.Role.SUPER_ADMIN_REVIEWER) {
                // Check both new many-to-many and legacy single assignment
                boolean hasAnyThematicArea = !reviewer.getThematicAreaAssignments().isEmpty() || 
                                            reviewer.getThematicArea() != null;
                
                if (!hasAnyThematicArea) {
                    ApiResponse<Object> response = new ApiResponse<>(
                        HttpStatus.FORBIDDEN.value(),
                        "Reviewer must have at least one thematic area assigned",
                        null
                    );
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            }
            
            // Validate project belongs to reviewer's thematic area
            if (reviewer.getRole() == User.Role.SUPER_ADMIN_REVIEWER) {
                Optional<Project> projectOpt = projectService.getProjectById(projectId);
                if (!projectOpt.isPresent()) {
                    ApiResponse<Object> response = new ApiResponse<>(
                        HttpStatus.NOT_FOUND.value(),
                        "Project not found",
                        null
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                Project project = projectOpt.get();
                // Check if any of the project's themes match any of the reviewer's thematic areas
                boolean hasMatchingTheme = project.getThemes().stream()
                    .anyMatch(assignment -> reviewer.hasThematicArea(assignment.getProjectTheme()));
                
                if (!hasMatchingTheme) {
                    ApiResponse<Object> response = new ApiResponse<>(
                        HttpStatus.FORBIDDEN.value(),
                        "This project is not in your assigned thematic areas",
                        null
                    );
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            }
            
            String comments = (String) payload.getOrDefault("comments", "");
            Boolean approved = (Boolean) payload.getOrDefault("approved", false);
            
            boolean success = projectService.reviewProject(projectId, reviewer.getId(), comments, approved);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    approved ? "Project reviewed and approved for final approval" : "Project review completed - revisions required",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalStateException e) {
            logger.error("Invalid state for reviewing project {}: {}", projectId, e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error reviewing project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to review project",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Final approval of a project (SUPER_ADMIN_APPROVER only)
     * Second step in two-tier approval process
     */
    @PostMapping("/admin/final-approve/{projectId}")
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> finalApproveProject(
            @PathVariable Long projectId,
            @RequestBody(required = false) Map<String, String> payload) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String approverEmail = auth.getName();
            User approver = userService.getUserByEmail(approverEmail);
            
            String comments = payload != null ? payload.getOrDefault("comments", "") : "";
            
            boolean success = projectService.finalApproveProject(projectId, approver.getId(), comments);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Project finally approved successfully",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (IllegalStateException e) {
            logger.error("Invalid state for final approval of project {}: {}", projectId, e.getMessage());
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error finally approving project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to finally approve project",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Final rejection of a project (SUPER_ADMIN_APPROVER only)
     */
    @PostMapping("/admin/final-reject/{projectId}")
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Object>> finalRejectProject(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> payload) {
        try {
            String reason = payload.getOrDefault("reason", "No reason provided");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String approverEmail = auth.getName();
            User approver = userService.getUserByEmail(approverEmail);
            
            boolean success = projectService.finalRejectProject(projectId, approver.getId(), reason);
            
            if (success) {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.OK.value(),
                    "Project rejected at final approval stage",
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                ApiResponse<Object> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found",
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Error rejecting project at final approval {}: {}", projectId, e.getMessage(), e);
            ApiResponse<Object> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to reject project",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects for review (filtered by reviewer's thematic area)
     */
    @GetMapping("/admin/projects-for-review")
    @PreAuthorize("hasRole('SUPER_ADMIN_REVIEWER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsForReview() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String reviewerEmail = auth.getName();
            User reviewer = userService.getUserByEmail(reviewerEmail);
            
            List<Project> projects;
            
            // Legacy SUPER_ADMIN can see all projects
            if (reviewer.getRole() == User.Role.SUPER_ADMIN) {
                projects = projectService.getProjectsByApprovalStatus(com.tujulishanehub.backend.models.ApprovalStatus.PENDING);
            } else {
                // Check for many-to-many thematic areas (new system)
                if (reviewer.getThematicAreas() != null && !reviewer.getThematicAreas().isEmpty()) {
                    // getThematicAreas() already returns List<ProjectTheme>
                    List<com.tujulishanehub.backend.models.ProjectTheme> thematicAreas = reviewer.getThematicAreas();
                    projects = projectService.getProjectsForReviewerWithThematicAreas(thematicAreas);
                } else if (reviewer.getThematicArea() != null) {
                    // Legacy single thematic area support
                    projects = projectService.getProjectsForReviewer(reviewer.getThematicArea());
                } else {
                    ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                        HttpStatus.FORBIDDEN.value(),
                        "Reviewer must have a thematic area assigned",
                        null
                    );
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            }
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Projects for review retrieved successfully",
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving projects for review: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve projects for review",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get projects awaiting final approval (SUPER_ADMIN_APPROVER only)
     */
    @GetMapping("/admin/projects-awaiting-final-approval")
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getProjectsAwaitingFinalApproval() {
        try {
            List<Project> projects = projectService.getProjectsAwaitingFinalApproval();
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Projects awaiting final approval retrieved successfully",
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving projects awaiting final approval: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve projects awaiting final approval",
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get user's own projects (Authenticated users)
     * PARTNER: Returns projects they created
     * DONOR: Returns projects they created plus projects of their linked partner organizations
     * SUPER_ADMIN_REVIEWER: Returns projects in their thematic area
     * SUPER_ADMIN / SUPER_ADMIN_APPROVER: Returns all projects
     */
    @GetMapping("/my-projects")
    @PreAuthorize("hasAnyRole('PARTNER', 'DONOR', 'SUPER_ADMIN_REVIEWER', 'SUPER_ADMIN', 'SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getMyProjects() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            User currentUser = userService.getUserByEmail(userEmail);
            
            List<Project> projects;
            
            if (currentUser != null && currentUser.getRole() == User.Role.SUPER_ADMIN_REVIEWER) {
                // Reviewers see projects in their thematic area(s) done by MoH
                List<Project> rawProjects;
                if (currentUser.getThematicAreas() != null && !currentUser.getThematicAreas().isEmpty()) {
                    List<com.tujulishanehub.backend.models.ProjectTheme> thematicAreas = currentUser.getThematicAreas();
                    rawProjects = projectService.getProjectsForReviewerWithThematicAreas(thematicAreas);
                } else if (currentUser.getThematicArea() != null) {
                    rawProjects = projectService.getProjectsForReviewer(currentUser.getThematicArea());
                } else {
                    rawProjects = new java.util.ArrayList<>();
                }
                
                projects = rawProjects.stream()
                    .filter(p -> p.getCreatedByRole() != null && (
                        "SUPER_ADMIN".equalsIgnoreCase(p.getCreatedByRole()) ||
                        "SUPER_ADMIN_APPROVER".equalsIgnoreCase(p.getCreatedByRole()) ||
                        "SUPER_ADMIN_REVIEWER".equalsIgnoreCase(p.getCreatedByRole())
                    ))
                    .collect(java.util.stream.Collectors.toList());
            } else if (currentUser != null && (currentUser.getRole() == User.Role.SUPER_ADMIN || currentUser.getRole() == User.Role.SUPER_ADMIN_APPROVER)) {
                // Super admins and final approvers see all projects done by MoH
                projects = projectService.getAllProjects().stream()
                    .filter(p -> p.getCreatedByRole() != null && (
                        "SUPER_ADMIN".equalsIgnoreCase(p.getCreatedByRole()) ||
                        "SUPER_ADMIN_APPROVER".equalsIgnoreCase(p.getCreatedByRole()) ||
                        "SUPER_ADMIN_REVIEWER".equalsIgnoreCase(p.getCreatedByRole())
                    ))
                    .collect(java.util.stream.Collectors.toList());
            } else if (currentUser != null && currentUser.getRole() == User.Role.DONOR) {
                // Donors see their own projects plus projects from partner organizations linked to them
                java.util.Set<Project> donorProjects = new java.util.HashSet<>(
                    projectService.getProjectsByPartnerEmail(userEmail)
                );
                List<User> linkedPartners = userService.getUsersByParentDonorId(currentUser.getId());
                for (User partner : linkedPartners) {
                    donorProjects.addAll(projectService.getProjectsByPartnerEmail(partner.getEmail()));
                }
                projects = new java.util.ArrayList<>(donorProjects);
            } else {
                // PARTNER (and fallback) see their own projects
                projects = projectService.getProjectsByPartnerEmail(userEmail);
            }
            
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Your projects retrieved successfully", 
                mapProjects(projects)
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving user projects: {}", e.getMessage(), e);
            ApiResponse<List<ProjectResponse>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve your projects", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ========== SUPER-ADMIN ENDPOINTS ==========

    /**
     * Get all projects (Super-Admin only)
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPER_ADMIN_APPROVER','SUPER_ADMIN_REVIEWER')")
    public ResponseEntity<ApiResponse<List<Project>>> getAllProjectsAdmin() {
        try {
            List<Project> projects = projectService.getAllProjects();
            ApiResponse<List<Project>> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "All projects retrieved successfully", 
                projects
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving all projects: {}", e.getMessage(), e);
            ApiResponse<List<Project>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to retrieve projects", 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Archive a completed project to past projects repository (Admin only)
     */
    @PostMapping("/{projectId}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PastProject>> archiveProject(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> archivalData) {
        
        try {
            String archivedBy = "admin"; // TODO: Get from authentication context
            String lessonsLearned = archivalData.get("lessonsLearned");
            String successFactors = archivalData.get("successFactors");
            String challenges = archivalData.get("challenges");
            String recommendations = archivalData.get("recommendations");
            
            PastProject pastProject = projectService.archiveProject(
                projectId, archivedBy, lessonsLearned, successFactors, challenges, recommendations);
            
            ApiResponse<PastProject> response = new ApiResponse<>(
                HttpStatus.OK.value(), 
                "Project archived successfully", 
                pastProject
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error archiving project: {}", e.getMessage(), e);
            ApiResponse<PastProject> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Failed to archive project: " + e.getMessage(), 
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mark project as completed (Owner, Admin, or Collaborator with EDITOR/CO_OWNER role)
     */
    @PostMapping("/admin/complete/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Project>> completeProject(@PathVariable Long id) {
        try {
            // Get authenticated user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();

            // Check if user is admin, super admin, owner, or collaborator with edit permission
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            boolean isOwner = projectService.isProjectOwner(id, userEmail);
            boolean canEdit = projectCollaboratorService.canEditProject(id, userEmail);

            if (!isAdmin && !isOwner && !canEdit) {
                logger.warn("User {} attempted to complete project {} without permission", userEmail, id);
                ApiResponse<Project> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(),
                    "You don't have permission to complete this project",
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Mark project as completed
            Project completedProject = projectService.completeProject(id, userEmail);

            ApiResponse<Project> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Project marked as completed successfully",
                completedProject
            );
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error completing project {}: {}", id, e.getMessage());
            ApiResponse<Project> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            logger.error("Error completing project {}: {}", id, e.getMessage(), e);
            ApiResponse<Project> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to complete project: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mark project as stalled (Owner, Admin, or Collaborator with EDITOR/CO_OWNER role)
     */
    @PostMapping("/admin/stall/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Project>> stallProject(@PathVariable Long id) {
        try {
            // Get authenticated user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();

            // Check if user is admin, super admin, owner, or collaborator with edit permission
            boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            boolean isOwner = projectService.isProjectOwner(id, userEmail);
            boolean canEdit = projectCollaboratorService.canEditProject(id, userEmail);

            if (!isAdmin && !isOwner && !canEdit) {
                logger.warn("User {} attempted to stall project {} without permission", userEmail, id);
                ApiResponse<Project> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(),
                    "You don't have permission to stall this project",
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Mark project as stalled
            Project stalledProject = projectService.stallProject(id, userEmail);

            ApiResponse<Project> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Project marked as stalled successfully",
                stalledProject
            );
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error stalling project {}: {}", id, e.getMessage());
            ApiResponse<Project> response = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            logger.error("Error stalling project {}: {}", id, e.getMessage(), e);
            ApiResponse<Project> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to stall project: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Helper method to map Project entities to ProjectResponse DTOs
     */
    private List<ProjectResponse> mapProjects(List<Project> projects) {
        return projects.stream()
                .map(projectService::toProjectResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get list of documents for a project (metadata only, no binary data)
     * OPTIMIZED: Uses direct query to avoid loading LOB data
     */
    @GetMapping("/{projectId}/documents")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProjectDocuments(@PathVariable Long projectId) {
        try {
            // Verify project exists first
            Optional<Project> project = projectService.getProjectById(projectId);
            
            if (project.isEmpty()) {
                ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found with ID: " + projectId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Use optimized query that ONLY fetches metadata (id, fileName, fileType, size)
            // This avoids loading the LOB data field which can be very slow
            List<Map<String, Object>> documentMetadata = projectDocumentRepository.findMetadataByProjectId(projectId);

            logger.info("Retrieved {} document(s) metadata for project {} (optimized query)", 
                        documentMetadata.size(), projectId);

            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Documents retrieved successfully",
                documentMetadata
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving documents for project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve documents: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * View a specific document (inline display)
     */
    @GetMapping("/{projectId}/documents/{documentId}/view")
    public ResponseEntity<?> viewDocument(@PathVariable Long projectId, @PathVariable Long documentId) {
        try {
            Optional<ProjectDocument> documentOpt = projectDocumentRepository.findById(documentId);
            
            if (documentOpt.isEmpty()) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Document not found with ID: " + documentId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            ProjectDocument document = documentOpt.get();
            
            // Verify the document belongs to the specified project
            if (!document.getProject().getId().equals(projectId)) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Document does not belong to the specified project",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Return the file for inline viewing (not as attachment)
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "inline; filename=\"" + document.getFileName() + "\"")
                .body(document.getData());

        } catch (Exception e) {
            logger.error("Error viewing document {} for project {}: {}", documentId, projectId, e.getMessage(), e);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to view document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download a specific document
     */
    @GetMapping("/{projectId}/documents/{documentId}")
    public ResponseEntity<?> downloadDocument(@PathVariable Long projectId, @PathVariable Long documentId) {
        try {
            Optional<ProjectDocument> documentOpt = projectDocumentRepository.findById(documentId);
            
            if (documentOpt.isEmpty()) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Document not found with ID: " + documentId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            ProjectDocument document = documentOpt.get();
            
            // Verify the document belongs to the specified project
            if (!document.getProject().getId().equals(projectId)) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Document does not belong to the specified project",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Return the file with appropriate headers
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + document.getFileName() + "\"")
                .body(document.getData());

        } catch (Exception e) {
            logger.error("Error downloading document {} for project {}: {}", documentId, projectId, e.getMessage(), e);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to download document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== REPORT DOCUMENT ENDPOINTS ====================

    /**
     * Upload report document to a completed project (Owner only)
     */
    @PostMapping("/{projectId}/reports/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadReportDocument(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        
        try {
            // Get authenticated user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            // Get project
            Optional<Project> projectOpt = projectService.getProjectById(projectId);
            if (projectOpt.isEmpty()) {
                ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found with ID: " + projectId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Project project = projectOpt.get();
            
            // Validation 1: Only project owner can upload
            if (!project.getPartner().equals(userEmail)) {
                ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(),
                    "Only the project owner can upload report documents",
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // Validation 2: Project must be completed
            if (!"completed".equalsIgnoreCase(project.getStatus())) {
                ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Report documents can only be uploaded to completed projects. Current status: " + project.getStatus(),
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // Validation 3: Check file size
            if (file.getSize() > MAX_FILE_SIZE) {
                ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "File size exceeds maximum limit of 20MB. File size: " + (file.getSize() / (1024 * 1024)) + "MB",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // Validation 4: Check file type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_REPORT_TYPES.contains(contentType)) {
                ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Invalid file type. Allowed types: PDF, DOCX, XLSX, PPTX, CSV, TXT. Received: " + contentType,
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // Save report document
            ProjectReportDocument reportDoc = new ProjectReportDocument();
            reportDoc.setFileName(file.getOriginalFilename());
            reportDoc.setFileType(contentType);
            reportDoc.setFileSize(file.getSize());
            reportDoc.setData(file.getBytes());
            reportDoc.setProject(project);
            reportDoc.setUploadedBy(userEmail);
            
            ProjectReportDocument saved = projectReportDocumentRepository.save(reportDoc);
            
            // Update project's hasReports flag if this is the first report
            if (!project.getHasReports()) {
                project.setHasReports(true);
                projectRepository.save(project);
            }
            
            // Prepare response
            Map<String, Object> data = new HashMap<>();
            data.put("id", saved.getId());
            data.put("fileName", saved.getFileName());
            data.put("fileType", saved.getFileType());
            data.put("fileSize", saved.getFileSize());
            data.put("uploadedBy", saved.getUploadedBy());
            data.put("uploadedAt", saved.getUploadedAt());
            
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.CREATED.value(),
                "Report document uploaded successfully",
                data
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error uploading report document for project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to upload report document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get list of report documents for a project (metadata only)
     */
    @Transactional(readOnly = true)
    @GetMapping("/{projectId}/reports/documents")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProjectReportDocuments(@PathVariable Long projectId) {
        try {
            Optional<Project> project = projectService.getProjectById(projectId);
            
            if (project.isEmpty()) {
                ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found with ID: " + projectId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            List<ProjectReportDocument> documents = projectReportDocumentRepository.findByProjectId(projectId);
            List<Map<String, Object>> documentMetadata = documents.stream()
                .map(doc -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", doc.getId());
                    metadata.put("fileName", doc.getFileName());
                    metadata.put("fileType", doc.getFileType());
                    metadata.put("fileSize", doc.getFileSize());
                    metadata.put("uploadedBy", doc.getUploadedBy());
                    metadata.put("uploadedAt", doc.getUploadedAt());
                    return metadata;
                })
                .collect(Collectors.toList());

            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Report documents retrieved successfully",
                documentMetadata
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving report documents for project {}: {}", projectId, e.getMessage(), e);
            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve report documents: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download a specific report document
     */
    @GetMapping("/{projectId}/reports/documents/{documentId}")
    public ResponseEntity<?> downloadReportDocument(@PathVariable Long projectId, @PathVariable Long documentId) {
        try {
            Optional<ProjectReportDocument> documentOpt = projectReportDocumentRepository.findById(documentId);
            
            if (documentOpt.isEmpty()) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Report document not found with ID: " + documentId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            ProjectReportDocument document = documentOpt.get();
            
            // Verify the document belongs to the specified project
            if (!document.getProject().getId().equals(projectId)) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Report document does not belong to the specified project",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Return the file with appropriate headers
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getFileType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + document.getFileName() + "\"")
                .body(document.getData());

        } catch (Exception e) {
            logger.error("Error downloading report document {} for project {}: {}", documentId, projectId, e.getMessage(), e);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to download report document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Delete a report document (Owner only)
     */
    @DeleteMapping("/{projectId}/reports/documents/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteReportDocument(@PathVariable Long projectId, @PathVariable Long documentId) {
        try {
            // Get authenticated user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            
            // Get project
            Optional<Project> projectOpt = projectService.getProjectById(projectId);
            if (projectOpt.isEmpty()) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Project not found with ID: " + projectId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Project project = projectOpt.get();
            
            // Only project owner can delete
            if (!project.getPartner().equals(userEmail)) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(),
                    "Only the project owner can delete report documents",
                    null
                );
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            // Get document
            Optional<ProjectReportDocument> documentOpt = projectReportDocumentRepository.findById(documentId);
            if (documentOpt.isEmpty()) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Report document not found with ID: " + documentId,
                    null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            ProjectReportDocument document = documentOpt.get();
            
            // Verify the document belongs to the specified project
            if (!document.getProject().getId().equals(projectId)) {
                ApiResponse<Void> response = new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Report document does not belong to the specified project",
                    null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // Delete the document
            projectReportDocumentRepository.delete(document);
            
            // Check if there are any remaining reports for this project
            long remainingReports = projectReportDocumentRepository.countByProjectId(projectId);
            if (remainingReports == 0 && project.getHasReports()) {
                project.setHasReports(false);
                projectRepository.save(project);
            }
            
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Report document deleted successfully",
                null
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error deleting report document {} for project {}: {}", documentId, projectId, e.getMessage(), e);
            ApiResponse<Void> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to delete report document: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get project counts summary
     * Role-based: MoH users see all projects, PARTNER/DONOR users see only their own
     */
    @GetMapping("/counts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProjectCounts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();
            User currentUser = userService.getUserByEmail(userEmail);
            
            Map<String, Object> counts = new HashMap<>();
            
            // Check if user is MoH (can see all counts)
            boolean isMoH = currentUser != null && 
                (currentUser.getRole() == User.Role.SUPER_ADMIN ||
                 currentUser.getRole() == User.Role.SUPER_ADMIN_REVIEWER ||
                 currentUser.getRole() == User.Role.SUPER_ADMIN_APPROVER);
            
            if (isMoH) {
                // MoH users see all project counts
                long totalProjects = projectRepository.count();
                counts.put("total", totalProjects);
                
                // Count by approval status
                long pendingCount = projectRepository.countByApprovalStatus(ApprovalStatus.PENDING);
                long approvedCount = projectRepository.countByApprovalStatus(ApprovalStatus.APPROVED);
                long rejectedCount = projectRepository.countByApprovalStatus(ApprovalStatus.REJECTED);
                
                counts.put("pending", pendingCount);
                counts.put("approved", approvedCount);
                counts.put("rejected", rejectedCount);
                counts.put("active", approvedCount);
                
                // Count by category
                Map<String, Long> categoryCounts = new HashMap<>();
                for (ProjectCategory cat : ProjectCategory.values()) {
                    long count = projectRepository.countByProjectCategory(cat);
                    categoryCounts.put(cat.name(), count);
                }
                counts.put("byCategory", categoryCounts);
            } else {
                // PARTNER/DONOR users see only their own project counts
                List<Project> userProjects = projectService.getProjectsByPartnerEmail(userEmail);
                
                long totalProjects = userProjects.size();
                counts.put("total", totalProjects);
                
                // Count by approval status for user's projects
                long pendingCount = userProjects.stream()
                    .filter(p -> p.getApprovalStatus() == ApprovalStatus.PENDING)
                    .count();
                long approvedCount = userProjects.stream()
                    .filter(p -> p.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .count();
                long rejectedCount = userProjects.stream()
                    .filter(p -> p.getApprovalStatus() == ApprovalStatus.REJECTED)
                    .count();
                
                counts.put("pending", pendingCount);
                counts.put("approved", approvedCount);
                counts.put("rejected", rejectedCount);
                counts.put("active", approvedCount);
                
                // Count by category for user's projects
                Map<String, Long> categoryCounts = new HashMap<>();
                for (ProjectCategory cat : ProjectCategory.values()) {
                    long count = userProjects.stream()
                        .filter(p -> p.getProjectCategory() == cat)
                        .count();
                    categoryCounts.put(cat.name(), count);
                }
                counts.put("byCategory", categoryCounts);
            }
            
            
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Project counts retrieved successfully",
                counts
            );
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving project counts: {}", e.getMessage(), e);
            ApiResponse<Map<String, Object>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve project counts: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Validate ProjectCreateRequest fields
     * Returns list of validation error messages
     */
    private List<String> validateProjectRequest(ProjectCreateRequest request) {
        List<String> errors = new ArrayList<>();
        
        // Validate title
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            errors.add("Project title is required");
        } else if (request.getTitle().trim().length() < 5) {
            errors.add("Project title must be at least 5 characters long");
        } else if (request.getTitle().trim().length() > 200) {
            errors.add("Project title must not exceed 200 characters");
        }
        
        // Validate partner
        if (request.getPartner() == null || request.getPartner().trim().isEmpty()) {
            errors.add("Partner is required");
        }
        
        // Validate themes
        if (request.getThemes() == null || request.getThemes().isEmpty()) {
            errors.add("At least one project theme is required");
        }
        
        // Validate project category
        if (request.getProjectCategory() == null) {
            errors.add("Project category is required");
        }
        
        // Validate start date
        if (request.getStartDate() == null) {
            errors.add("Start date is required");
        }
        
        // Validate end date is after start date (if provided)
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate()) || 
                request.getEndDate().isEqual(request.getStartDate())) {
                errors.add("End date must be after start date");
            }
        }
        
        // Validate activity type
        if (request.getActivityType() == null || request.getActivityType().trim().isEmpty()) {
            errors.add("Activity type is required");
        } else if (request.getActivityType().trim().length() < 10) {
            errors.add("Activity type must be at least 10 characters long");
        }
        
        // Validate locations
        if (request.getLocations() == null || request.getLocations().isEmpty()) {
            errors.add("At least one location is required");
        } else {
            // Validate each location
            for (int i = 0; i < request.getLocations().size(); i++) {
                ProjectCreateRequest.LocationRequest loc = request.getLocations().get(i);
                if (loc.getCounty() == null || loc.getCounty().trim().isEmpty()) {
                    errors.add("County is required for location " + (i + 1));
                }
                if (loc.getLatitude() == null || loc.getLongitude() == null) {
                    errors.add("Latitude and longitude are required for location " + (i + 1));
                }
            }
        }
        
        // Validate contact person information
        if (request.getContactPersonName() == null || request.getContactPersonName().trim().isEmpty()) {
            errors.add("Contact person name is required");
        }
        
        if (request.getContactPersonRole() == null || request.getContactPersonRole().trim().isEmpty()) {
            errors.add("Contact person role is required");
        }
        
        if (request.getContactPersonEmail() != null && !request.getContactPersonEmail().trim().isEmpty()) {
            // Validate email format if provided
            if (!request.getContactPersonEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                errors.add("Contact person email is not valid");
            }
        }
        
        // Validate objectives
        if (request.getObjectives() == null || request.getObjectives().trim().isEmpty()) {
            errors.add("Project objectives are required");
        } else if (request.getObjectives().trim().length() < 20) {
            errors.add("Project objectives must be at least 20 characters long");
        }
        
        // Validate budget
        if (request.getBudget() == null) {
            errors.add("Budget is required");
        } else if (request.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Budget must be greater than 0");
        }
        
        return errors;
    }
}

