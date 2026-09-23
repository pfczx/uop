create table workspaces (
    id uuid primary key,
    name varchar(100) not null,
    description varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
