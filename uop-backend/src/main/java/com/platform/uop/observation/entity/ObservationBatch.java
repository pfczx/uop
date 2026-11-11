package com.platform.uop.observation.entity;

import com.platform.uop.monitor.entity.Monitor;
import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "observation_batch", uniqueConstraints = {
    @UniqueConstraint(name = "uk_observation_batch_monitor_batch", columnNames = { "monitor_id", "batch_id" })
}, indexes = {
    @Index(name = "idx_observation_batch_monitor_id", columnList = "monitor_id")
})
@NoArgsConstructor
public class ObservationBatch {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "monitor_id", nullable = false)
  private Monitor monitor;

  @Column(name = "batch_id", nullable = false, length = 128)
  private String batchId;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  public ObservationBatch(Monitor monitor, String batchId) {
    this.monitor = monitor;
    this.batchId = batchId;
  }

  @PrePersist
  protected void onCreate() {
    if (receivedAt == null) {
      receivedAt = Instant.now();
    }
  }
}
