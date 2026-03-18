create table api_request_logs (
    id UUID primary key,

    account_id UUID not null,
    api_key_id UUID not null,

    path text not null,
    method varchar(10) not null,

    status_code integer not null,

    requested_at timestamptz not null,
    completed_at timestamptz,

    latency_ms integer,

    provider varchar(50),
    model varchar(100),

    error_code varchar(100),

    constraint fk_api_request_logs_account
        foreign key (account_id)
        references accounts (id)
        on delete cascade,

    constraint fk_api_request_logs_api_key
        foreign key (api_key_id)
        references api_keys (id)
        on delete cascade
);

create index idx_api_request_logs_account_id
    on api_request_logs (account_id);

create index idx_api_request_logs_api_key_id
    on api_request_logs (api_key_id);

create index idx_api_request_logs_requested_at
    on api_request_logs (requested_at DESC);

create index idx_api_request_logs_account_requested_at
    on api_request_logs (account_id, requested_at DESC);