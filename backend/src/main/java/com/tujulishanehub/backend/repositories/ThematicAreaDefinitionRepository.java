package com.tujulishanehub.backend.repositories;

import com.tujulishanehub.backend.models.ThematicAreaDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThematicAreaDefinitionRepository extends JpaRepository<ThematicAreaDefinition, Long> {
    Optional<ThematicAreaDefinition> findByCode(String code);
    boolean existsByCode(String code);
}
