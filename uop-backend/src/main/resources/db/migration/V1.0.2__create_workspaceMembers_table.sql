create table workspace_members (
    id uuid primary key,
    workspace_id uuid not null,
    user_id uuid not null,
    role varchar(20) not null,
    created_at timestamp with time zone not null,

    constraint fk_workspace_members_workspace
        foreign key (workspace_id)
        references workspaces (id)
        on delete cascade,

    constraint fk_workspace_members_user
        foreign key (user_id)
        references users (id)
        on delete cascade,

    constraint uk_workspace_member
        unique (workspace_id, user_id)
);
