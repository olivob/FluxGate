create table if not exists api_keys (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null references accounts(id),
  key_hash text not null,
  key_prefix text not null,
  name text,
  status text not null default 'active', 
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  last_used_at timestamptz,
  expires_at timestamptz,
  constraint api_keys_key_hash_uk unique (key_hash)
);

create index if not exists api_keys_account_status_idx
  on api_keys (account_id, status);

create index if not exists api_keys_account_last_used_idx
  on api_keys (account_id, last_used_at desc);