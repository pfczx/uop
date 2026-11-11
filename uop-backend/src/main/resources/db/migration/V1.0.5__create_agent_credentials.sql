create table agent_credentials (
    id uuid primary key,
    monitor_id uuid not null,
    registration_token_hash varchar(64),
    registration_token_expires_at timestamptz,
    credential_hash varchar(64),
    credential_created_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint fk_agent_credentials_monitor
        foreign key (monitor_id)
        references monitors (id)
        on delete cascade,

    constraint uk_agent_credentials_registration_token_hash
        unique (registration_token_hash),

    constraint uk_agent_credentials_credential_hash
        unique (credential_hash)
);

create index idx_agent_credentials_monitor_id
    on agent_credentials (monitor_id);
