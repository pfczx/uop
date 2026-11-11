package com.platform.uop.observation.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.monitor.repository.MonitorRepository;
import com.platform.uop.observation.entity.ObservationBatch;
import com.platform.uop.observation.entity.ObservationEntity;
import com.platform.uop.observation.repository.ObservationBatchRepository;
import com.platform.uop.observation.repository.ObservationRepository;
import com.platform.uop.proto.v1.Observation;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class ObservationService {

  private static final int MAX_BATCH_SIZE = 1_000;

  private final ObservationRepository observationRepository;
  private final ObservationBatchRepository batchReceiptRepository;
  private final MonitorRepository monitorRepository;

  public BatchSaveResult saveBatch(
      UUID monitorId,
      String batchId,
      List<Observation> observations) {

    validateBatch(batchId, observations);

    Monitor monitor = monitorRepository.findById(monitorId)
        .orElseThrow(() -> new ResponseStatusException(
            NOT_FOUND, "Monitor not found"));

    if (batchReceiptRepository.existsByMonitor_IdAndBatchId(
        monitorId, batchId)) {
      return new BatchSaveResult(true, 0);
    }

    List<ObservationEntity> entities = observations.stream()
        .map(observation -> new ObservationEntity(monitor, observation))
        .toList();

    observationRepository.saveAll(entities);

    batchReceiptRepository.save(
        new ObservationBatch(monitor, batchId));

    return new BatchSaveResult(false, entities.size());
  }

  @Transactional(readOnly = true)
  public Page<ObservationEntity> findByMonitor(
      UUID monitorId,
      Pageable pageable) {

    if (!monitorRepository.existsById(monitorId)) {
      throw new ResponseStatusException(NOT_FOUND, "Monitor not found");
    }

    return observationRepository
        .findAllByMonitor_IdOrderByObservedAtDesc(monitorId, pageable);
  }

  @Transactional(readOnly = true)
  public List<ObservationEntity> findByMonitorAndTimeRange(
      UUID monitorId,
      Instant from,
      Instant to) {

    if (!monitorRepository.existsById(monitorId)) {
      throw new ResponseStatusException(NOT_FOUND, "Monitor not found");
    }

    if (from == null || to == null || from.isAfter(to)) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Invalid observation time range");
    }

    return observationRepository
        .findAllByMonitor_IdAndObservedAtBetweenOrderByObservedAtAsc(
            monitorId, from, to);
  }

  private void validateBatch(
      String batchId,
      List<Observation> observations) {

    if (batchId == null || batchId.isBlank() || batchId.length() > 128) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Invalid observation batch ID");
    }

    if (observations == null || observations.size() > MAX_BATCH_SIZE) {
      throw new ResponseStatusException(
          BAD_REQUEST,
          "Observation batch must contain between 0 and "
              + MAX_BATCH_SIZE + " observations");
    }

    if (observations.stream().anyMatch(Objects::isNull)) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Observation batch contains a null observation");
    }

    for (Observation observation : observations) {
      if (!observation.hasTimestamp()) {
        throw new ResponseStatusException(
            BAD_REQUEST, "Every observation must have a timestamp");
      }
    }
  }

  public record BatchSaveResult(
      boolean alreadyProcessed,
      int savedCount) {
  }
}
