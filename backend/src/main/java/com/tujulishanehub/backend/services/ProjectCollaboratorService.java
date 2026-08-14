package com.tujulishanehub.backend.services;

import com.tujulishanehub.backend.models.*;
import com.tujulishanehub.backend.repositories.ProjectCollaboratorRepository;
import com.tujulishanehub.backend.repositories.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectCollaboratorService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectCollaboratorService.class);
    
    @Autowired
    private ProjectCollaboratorRepository collaboratorRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    @Autowired
    private UserService userService;
    
    /**
     * Add a collaborator to a project
     */
    public ProjectCollaborator addCollaborator(Long projectId, User user, CollaboratorRole role, 
                                               User addedBy, String notes) {
        logger.info("Adding collaborator {} to project {} with role {}", 
                   user.getEmail(), projectId, role);
        
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        // Check if project is approved
        if (project.getApprovalWorkflowStatus() != ApprovalWorkflowStatus.APPROVED) {
            throw new RuntimeException("Collaborators can only be added after the project has been approved.");
        }
        
        // Check if already a collaborator
        if (collaboratorRepository.existsByProjectAndUserAndIsActive(project, user, true)) {
            throw new RuntimeException("User is already a collaborator on this project");
        }
        
        ProjectCollaborator collaborator = new ProjectCollaborator();
        collaborator.setProject(project);
        collaborator.setUser(user);
        collaborator.setOrganization(user.getOrganization());
        collaborator.setRole(role);
        collaborator.setAddedBy(addedBy);
        collaborator.setAddedAt(LocalDateTime.now());
        collaborator.setIsActive(true);
        collaborator.setNotes(notes);
        
        ProjectCollaborator saved = collaboratorRepository.save(collaborator);
        logger.info("Collaborator added successfully with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Get all active collaborators for a project
     */
    public List<ProjectCollaborator> getProjectCollaborators(Long projectId) {
        logger.info("Fetching collaborators for project: {}", projectId);
        return collaboratorRepository.findActiveCollaboratorsByProjectId(projectId);
    }
    
    /**
     * Get all projects where user is a collaborator
     */
    public List<ProjectCollaborator> getUserCollaborations(String userEmail) {
        logger.info("Fetching collaborations for user: {}", userEmail);
        return collaboratorRepository.findActiveCollaborationsByUserEmail(userEmail);
    }
    
    /**
     * Get all collaborators across all projects (for admin)
     */
    public List<ProjectCollaborator> getAllCollaborators() {
        logger.info("Fetching all collaborators");
        return collaboratorRepository.findAll();
    }
    
    /**
     * Check if user is a collaborator on a project
     */
    public boolean isCollaborator(Long projectId, String userEmail) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return false;
            
            User user = userService.getUserByEmail(userEmail);
            return collaboratorRepository.existsByProjectAndUserAndIsActive(project, user, true);
        } catch (Exception e) {
            logger.error("Error checking collaborator status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if user can edit a project (owner or active collaborator with EDITOR or CO_OWNER role)
     */
    public boolean canEditProject(Long projectId, String userEmail) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return false;
            
            // Check if user is the project owner
            if (project.getContactPersonEmail().equals(userEmail)) {
                return true;
            }
            
            // Check if user is an active collaborator with edit permissions
            User user = userService.getUserByEmail(userEmail);
            Optional<ProjectCollaborator> collaborator = collaboratorRepository
                .findByProjectAndUserAndIsActive(project, user, true);
            
            if (collaborator.isPresent()) {
                CollaboratorRole role = collaborator.get().getRole();
                return role == CollaboratorRole.EDITOR || role == CollaboratorRole.CO_OWNER;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error checking edit permission: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove a collaborator from a project
     */
    public boolean removeCollaborator(Long collaboratorId, String requestingUserEmail) {
        logger.info("Removing collaborator ID: {} by user: {}", collaboratorId, requestingUserEmail);
        
        Optional<ProjectCollaborator> collaboratorOpt = collaboratorRepository.findById(collaboratorId);
        if (collaboratorOpt.isPresent()) {
            ProjectCollaborator collaborator = collaboratorOpt.get();
            Project project = collaborator.getProject();
            
            // Only project owner or MOH can remove collaborators
            User requestingUser = userService.getUserByEmail(requestingUserEmail);
            if (!project.getContactPersonEmail().equals(requestingUserEmail) && 
                !requestingUser.isSuperAdmin()) {
                throw new RuntimeException("Only project owner or MOH can remove collaborators");
            }
            
            collaborator.setIsActive(false);
            collaborator.setRemovedAt(LocalDateTime.now());
            collaboratorRepository.save(collaborator);
            
            logger.info("Collaborator {} removed from project {}", 
                       collaborator.getUser().getEmail(), project.getId());
            return true;
        }
        
        return false;
    }
    
    /**
     * Update collaborator role
     */
    public ProjectCollaborator updateCollaboratorRole(Long collaboratorId, CollaboratorRole newRole, String requestingUserEmail) {
        logger.info("Updating collaborator ID: {} to role: {} by user: {}", collaboratorId, newRole, requestingUserEmail);
        
        ProjectCollaborator collaborator = collaboratorRepository.findById(collaboratorId)
            .orElseThrow(() -> new RuntimeException("Collaborator not found"));
        
        Project project = collaborator.getProject();
        
        // Only project owner or MOH can update roles
        User requestingUser = userService.getUserByEmail(requestingUserEmail);
        if (!project.getContactPersonEmail().equals(requestingUserEmail) && 
            !requestingUser.isSuperAdmin()) {
            throw new RuntimeException("Only project owner or MOH can update collaborator roles");
        }
        
        collaborator.setRole(newRole);
        ProjectCollaborator updated = collaboratorRepository.save(collaborator);
        
        logger.info("Collaborator role updated successfully");
        return updated;
    }
    
    /**
     * Simplified method to add collaborator using emails
     */
    public ProjectCollaborator addCollaboratorByEmail(Long projectId, String collaboratorEmail, 
                                                      CollaboratorRole role, String addedByEmail, String notes) {
        User user = userService.getUserByEmail(collaboratorEmail);
        User addedBy = userService.getUserByEmail(addedByEmail);
        return addCollaborator(projectId, user, role, addedBy, notes);
    }
}