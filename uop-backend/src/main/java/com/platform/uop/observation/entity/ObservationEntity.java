package com.platform.uop.observation.entity;

import com.platform.uop.monitor.entity.Monitor;
import com.platform.uop.observation.converter.ObservationConverter;
import com.platform.uop.proto.v1.Observation;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "observations", indexes = {
    @Index(name = "idx_observations_monitor_observed_at", columnList = "monitor_id, observed_at")
})
@NoArgsConstructor
public class ObservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "monitor_id", nullable = false)
  private Monitor monitor;

  @Convert(converter = ObservationConverter.class)
  @Column(name = "payload", nullable = false, columnDefinition = "bytea")
  private Observation observation;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  public ObservationEntity(Monitor monitor, Observation observation) {
    this.monitor = monitor;
    this.observation = observation;
    this.observedAt = extractTimestamp(observation);
  }

  @PrePersist
  protected void onCreate() {
    if (receivedAt == null) {
      receivedAt = Instant.now();
    }
  }

  private static Instant extractTimestamp(Observation observation) {
    if (!observation.hasTimestamp()) {
      throw new IllegalArgumentException(
          "Observation timestamp is required");
    }

    return Instant.ofEpochSecond(
        observation.getTimestamp().getSeconds(),
        observation.getTimestamp().getNanos());
  }
}
