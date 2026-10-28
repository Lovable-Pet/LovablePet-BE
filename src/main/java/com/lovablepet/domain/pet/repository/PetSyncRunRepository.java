package com.lovablepet.domain.pet.repository;

import com.lovablepet.domain.pet.entity.PetSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PetSyncRunRepository extends JpaRepository<PetSyncRun, Long> {

    Optional<PetSyncRun> findFirstByOrderByStartedAtDesc();
}
