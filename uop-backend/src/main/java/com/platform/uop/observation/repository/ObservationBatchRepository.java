package com.platform.uop.observation.repository;

import com.platform.uop.observation.entity.ObservationBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationBatchRepository extends JpaRepository<ObservationBatch, UUID> {

  boolean existsByMonitor_IdAndBatchId(UUID monitorId, String batchId);
}
