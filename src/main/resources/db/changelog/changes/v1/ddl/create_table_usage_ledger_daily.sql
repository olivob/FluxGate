create table if not exists usage_ledger_daily (
  account_id uuid not null references accounts(id),
  day date not null,
  requests bigint not null default 0,
  input_tokens bigint not null default 0,
  output_tokens bigint not null default 0,
  total_tokens bigint not null default 0,
  cost_cents bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (account_id, day)
);

create index if not exists usage_ledger_daily_day_idx
  on usage_ledger_daily (day);