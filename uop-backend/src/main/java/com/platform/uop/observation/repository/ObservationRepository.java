package com.platform.uop.observation.repository;

import com.platform.uop.observation.entity.ObservationEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<ObservationEntity, UUID> {

  Page<ObservationEntity> findAllByMonitor_IdOrderByObservedAtDesc(
      UUID monitorId,
      Pageable pageable);

  List<ObservationEntity> findAllByMonitor_IdAndObservedAtBetweenOrderByObservedAtAsc(
      UUID monitorId,
      Instant from,
      Instant to);
}
