package com.platform.uop.workspace.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor
public class Workspace {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 1000)
  private String description;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @OneToMany
  (mappedBy = "workspace", 
  cascade = CascadeType.ALL, 
  orphanRemoval = true)
  private Set<WorkspaceMember> members = new HashSet<>();

  public Workspace(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @PrePersist
  private void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void onUpdate() {
    updatedAt = Instant.now();
  }

  public void update(String name, String description) {
    this.name = name;
    this.description = description;
  }
}
