-- Preserve existing records while matching Java's EnumType.STRING representation.
update accounts set status = upper(status) where status in ('active', 'revoked');
update api_keys set status = upper(status) where status in ('active', 'revoked');

alter table accounts alter column status set default 'ACTIVE';
alter table api_keys alter column status set default 'ACTIVE';

alter table accounts add constraint accounts_status_check
    check (status in ('ACTIVE', 'REVOKED'));
alter table api_keys add constraint api_keys_status_check
    check (status in ('ACTIVE', 'REVOKED'));
