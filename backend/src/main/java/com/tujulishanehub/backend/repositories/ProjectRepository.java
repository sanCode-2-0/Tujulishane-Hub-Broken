package com.tujulishanehub.backend.repositories;

import com.tujulishanehub.backend.models.ApprovalStatus;
import com.tujulishanehub.backend.models.ApprovalWorkflowStatus;
import com.tujulishanehub.backend.models.Project;
import com.tujulishanehub.backend.models.ProjectCategory;
import com.tujulishanehub.backend.models.ProjectTheme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    
    // Optimized findAll with themes and locations eagerly fetched to avoid N+1 queries
    @EntityGraph(attributePaths = {"themes", "locations", "themes.projectTheme"})
    Page<Project> findAll(Pageable pageable);
    
    // Optimized findById with themes and locations
    @EntityGraph(attributePaths = {"themes", "locations", "themes.projectTheme"})
    Optional<Project> findById(Long id);
    
    // Find projects by partner
    List<Project> findByPartnerContainingIgnoreCase(String partner);
    
    // Find projects by partner email (exact match)
    List<Project> findByPartner(String partnerEmail);
    
    // Find projects by project number
    Optional<Project> findByProjectNo(String projectNo);
    
    // Find projects by title
    List<Project> findByTitleContainingIgnoreCase(String title);
    
    // Find projects by status
    List<Project> findByStatus(String status);
    Page<Project> findByStatus(String status, Pageable pageable);
    Page<Project> findByStatusIn(List<String> statuses, Pageable pageable);
    
    // Find projects by county (searches in locations)
    @Query("SELECT DISTINCT p FROM Project p JOIN p.locations loc WHERE LOWER(loc.county) LIKE LOWER(CONCAT('%', :county, '%'))")
    List<Project> findByCountyContainingIgnoreCase(@Param("county") String county);
    
    // Find projects by sub-county (searches in locations)
    @Query("SELECT DISTINCT p FROM Project p JOIN p.locations loc WHERE LOWER(loc.subCounty) LIKE LOWER(CONCAT('%', :subCounty, '%'))")
    List<Project> findBySubCountyContainingIgnoreCase(@Param("subCounty") String subCounty);
    
    // Find projects by activity type
    List<Project> findByActivityTypeContainingIgnoreCase(String activityType);
    
    // Find projects by project theme (searches in themes)
    @Query("SELECT DISTINCT p FROM Project p JOIN p.themes t WHERE t.projectTheme = :projectTheme")
    List<Project> findByProjectTheme(@Param("projectTheme") ProjectTheme projectTheme);
    
    // Find projects by project theme with case-insensitive string search
    @Query("SELECT DISTINCT p FROM Project p JOIN p.themes t WHERE LOWER(CAST(t.projectTheme AS string)) LIKE LOWER(CONCAT('%', :theme, '%'))")
    List<Project> findByProjectThemeContaining(@Param("theme") String theme);
    
    // Find projects by date range
    List<Project> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find projects that end before a date and have a specific status
    List<Project> findByEndDateBeforeAndStatus(LocalDate endDate, String status);
    
    // Find projects that are currently active (started but not ended)
    @Query("SELECT p FROM Project p WHERE p.startDate <= :currentDate AND (p.endDate IS NULL OR p.endDate >= :currentDate)")
    List<Project> findActiveProjects(@Param("currentDate") LocalDate currentDate);
    
    // Find projects with coordinates (for map display)
    @Query("SELECT DISTINCT p FROM Project p JOIN p.locations loc WHERE loc.latitude IS NOT NULL AND loc.longitude IS NOT NULL AND p.approvalWorkflowStatus = com.tujulishanehub.backend.models.ApprovalWorkflowStatus.APPROVED")
    List<Project> findProjectsWithCoordinates();
    
    // Find projects without coordinates (need geocoding)
    @Query("SELECT DISTINCT p FROM Project p JOIN p.locations loc WHERE (loc.latitude IS NULL OR loc.longitude IS NULL) AND loc.mapsAddress IS NOT NULL")
    List<Project> findProjectsNeedingGeocoding();
    
    // Find projects by contact person email
    List<Project> findByContactPersonEmail(String email);
    
    // Find all projects with locations eagerly fetched (for statistics)
    @EntityGraph(attributePaths = {"locations"})
    @Query("SELECT p FROM Project p")
    List<Project> findAllWithLocations();

    // Find projects by approval status
    List<Project> findByApprovalStatus(ApprovalStatus approvalStatus);
    
    // Find projects by approval workflow status with pagination
    @EntityGraph(attributePaths = {"themes", "locations", "themes.projectTheme"})
    Page<Project> findByApprovalWorkflowStatus(ApprovalWorkflowStatus approvalWorkflowStatus, Pageable pageable);
    
    // Count projects by approval status
    long countByApprovalStatus(ApprovalStatus approvalStatus);
    
    // Count projects by category
    long countByProjectCategory(ProjectCategory projectCategory);
    
    // Find recent projects (top 10)
    List<Project> findTop10ByOrderByCreatedAtDesc();
    
    // Find projects within a geographic bounding box
    @Query("SELECT DISTINCT p FROM Project p JOIN p.locations loc WHERE loc.latitude BETWEEN :minLat AND :maxLat AND loc.longitude BETWEEN :minLng AND :maxLng")
    List<Project> findProjectsInBoundingBox(
        @Param("minLat") Double minLatitude,
        @Param("maxLat") Double maxLatitude,
        @Param("minLng") Double minLongitude,
        @Param("maxLng") Double maxLongitude
    );
    
    // Search projects by multiple criteria
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.locations loc WHERE " +
           "(:partner IS NULL OR :partner = '' OR LOWER(CAST(p.partner AS string)) LIKE LOWER(CONCAT('%', :partner, '%'))) AND " +
           "(:title IS NULL OR :title = '' OR LOWER(CAST(p.title AS string)) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:projectNo IS NULL OR :projectNo = '' OR p.projectNo = :projectNo) AND " +
           "(:status IS NULL OR :status = '' OR p.status = :status) AND " +
           "(:county IS NULL OR :county = '' OR LOWER(CAST(loc.county AS string)) LIKE LOWER(CONCAT('%', :county, '%'))) AND " +
           "(:activityType IS NULL OR :activityType = '' OR LOWER(CAST(p.activityType AS string)) LIKE LOWER(CONCAT('%', :activityType, '%')))")
    List<Project> searchProjects(
        @Param("partner") String partner,
        @Param("title") String title,
        @Param("projectNo") String projectNo,
        @Param("status") String status,
        @Param("county") String county,
        @Param("activityType") String activityType
    );
    
    // Count projects by status
    @Query("SELECT p.status, COUNT(p) FROM Project p GROUP BY p.status")
    List<Object[]> countProjectsByStatus();
    
    // Count projects by county (from locations)
    @Query("SELECT loc.county, COUNT(DISTINCT p) FROM Project p JOIN p.locations loc WHERE loc.county IS NOT NULL GROUP BY loc.county")
    List<Object[]> countProjectsByCounty();
}