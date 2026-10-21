package com.platform.uop.monitor.entity;

import com.platform.uop.monitor.converter.MonitorDescriptorConverter;
import com.platform.uop.proto.v1.MonitorDescriptor;
import com.platform.uop.workspace.entity.Workspace;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "monitors", indexes = {
    @Index(name = "idx_monitors_workspace_id", columnList = "workspace_id")
})
@NoArgsConstructor
public class Monitor {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  @Convert(converter = MonitorDescriptorConverter.class)
  @Column(name = "descriptor", nullable = false, columnDefinition = "bytea")
  private MonitorDescriptor descriptor;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Monitor(Workspace workspace, MonitorDescriptor descriptor) {
    this.workspace = workspace;
    this.descriptor = descriptor;
  }

  public void updateDescriptor(MonitorDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  public void setActive() {
    this.descriptor = descriptor.toBuilder()
        .setActive(true)
        .build();
  }

  public void setInActive() {
    this.descriptor = descriptor.toBuilder()
        .setActive(false)
        .build();
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
