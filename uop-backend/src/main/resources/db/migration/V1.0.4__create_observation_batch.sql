create table observation_batch (
    id uuid primary key,
    monitor_id uuid not null,
    batch_id varchar(128) not null,
    received_at timestamp with time zone not null,

    constraint fk_observation_batch_monitor
        foreign key (monitor_id)
        references monitors (id)
        on delete cascade,

    constraint uk_observation_batch_monitor_batch
        unique (monitor_id, batch_id)
);

create index idx_observation_batch_monitor_id
    on observation_batch (monitor_id);
