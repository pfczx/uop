package com.platform.uop.workspace.entity;

import java.time.Instant;
import java.util.UUID;

import com.platform.uop.users.entity.User;
import com.platform.uop.workspace.enums.WorkspaceRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "workspace_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_workspace_member",
            columnNames = {"workspace_id", "user_id"}
        )
    }
)
@Getter
@NoArgsConstructor
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkspaceRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public WorkspaceMember(
            Workspace workspace,
            User user,
            WorkspaceRole role
    ) {
        this.workspace = workspace;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }
}
