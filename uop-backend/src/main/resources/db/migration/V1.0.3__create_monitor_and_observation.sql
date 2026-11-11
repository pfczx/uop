create table monitors (
    id uuid primary key,
    workspace_id uuid not null,
    descriptor bytea not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint fk_monitors_workspace
        foreign key (workspace_id)
        references workspaces (id)
);

create index idx_monitors_workspace_id
    on monitors (workspace_id);

create table observations (
    id uuid primary key,
    monitor_id uuid not null,
    payload bytea not null,
    observed_at timestamptz not null,
    received_at timestamptz not null,

    constraint fk_observations_monitor
        foreign key (monitor_id)
        references monitors (id)
);

create index idx_observations_monitor_observed_at
    on observations (monitor_id, observed_at);
