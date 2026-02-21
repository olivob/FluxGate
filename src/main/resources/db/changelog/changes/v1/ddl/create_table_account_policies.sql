create table if not exists account_policies (
  account_id uuid primary key references accounts(id),
  -- ex: ["gpt-4o-mini","sonnet-3.5"])
  allowed_models jsonb not null default '[]'::jsonb,
  daily_token_limit bigint,
  monthly_token_limit bigint,
  daily_cost_cents_limit bigint,
  monthly_cost_cents_limit bigint,
  hard_fail_on_limit boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);