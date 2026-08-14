package com.tujulishanehub.backend.controllers;

import com.tujulishanehub.backend.models.ThematicAreaDefinition;
import com.tujulishanehub.backend.payload.ApiResponse;
import com.tujulishanehub.backend.repositories.ThematicAreaDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/thematic-areas")
public class ThematicAreaDefinitionController {

    @Autowired
    private ThematicAreaDefinitionRepository repository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ThematicAreaDefinition>>> getAllThematicAreas() {
        try {
            List<ThematicAreaDefinition> areas = repository.findAll();
            ApiResponse<List<ThematicAreaDefinition>> response = new ApiResponse<>(
                HttpStatus.OK.value(),
                "Thematic areas retrieved successfully",
                areas
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<List<ThematicAreaDefinition>> response = new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to retrieve thematic areas: " + e.getMessage(),
                null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<ThematicAreaDefinition>> createThematicArea(@RequestBody ThematicAreaDefinition area) {
        try {
            if (area.getCode() == null || area.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Thematic area code is required",
                    null
                ));
            }
            if (area.getTitle() == null || area.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Thematic area title is required",
                    null
                ));
            }

            // Convert code to uppercase for consistency
            area.setCode(area.getCode().trim().toUpperCase());

            if (repository.existsByCode(area.getCode())) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                    HttpStatus.BAD_REQUEST.value(),
                    "Thematic area with code " + area.getCode() + " already exists",
                    null
                ));
            }

            ThematicAreaDefinition saved = repository.save(area);
            return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                "Thematic area created successfully",
                saved
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to create thematic area: " + e.getMessage(),
                null
            ));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<ThematicAreaDefinition>> updateThematicArea(
            @PathVariable Long id,
            @RequestBody ThematicAreaDefinition updatedArea
    ) {
        try {
            Optional<ThematicAreaDefinition> existingOpt = repository.findById(id);
            if (existingOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Thematic area not found with ID: " + id,
                    null
                ));
            }

            ThematicAreaDefinition existing = existingOpt.get();
            if (updatedArea.getTitle() != null && !updatedArea.getTitle().trim().isEmpty()) {
                existing.setTitle(updatedArea.getTitle().trim());
            }
            if (updatedArea.getDescription() != null) {
                existing.setDescription(updatedArea.getDescription().trim());
            }
            if (updatedArea.getIcon() != null) {
                existing.setIcon(updatedArea.getIcon().trim());
            }
            if (updatedArea.getColor() != null) {
                existing.setColor(updatedArea.getColor().trim());
            }
            
            // Allow editing the code as long as it doesn't conflict with another definition
            if (updatedArea.getCode() != null && !updatedArea.getCode().trim().isEmpty()) {
                String newCode = updatedArea.getCode().trim().toUpperCase();
                if (!newCode.equals(existing.getCode())) {
                    Optional<ThematicAreaDefinition> conflict = repository.findByCode(newCode);
                    if (conflict.isPresent()) {
                        return ResponseEntity.badRequest().body(new ApiResponse<>(
                            HttpStatus.BAD_REQUEST.value(),
                            "Thematic area with code " + newCode + " already exists",
                            null
                        ));
                    }
                    existing.setCode(newCode);
                }
            }

            ThematicAreaDefinition saved = repository.save(existing);
            return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                "Thematic area updated successfully",
                saved
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to update thematic area: " + e.getMessage(),
                null
            ));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN_APPROVER')")
    public ResponseEntity<ApiResponse<Void>> deleteThematicArea(@PathVariable Long id) {
        try {
            if (!repository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                    HttpStatus.NOT_FOUND.value(),
                    "Thematic area not found with ID: " + id,
                    null
                ));
            }
            repository.deleteById(id);
            return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                "Thematic area deleted successfully",
                null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to delete thematic area: " + e.getMessage(),
                null
            ));
        }
    }
}
