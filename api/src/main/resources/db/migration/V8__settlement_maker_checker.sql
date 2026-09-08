-- 정산 확정·지급 maker-checker (요청 → 승인 2단계).
alter table settlement_statement add column approval_stage varchar(20) default 'NONE';
alter table settlement_statement add column requested_by varchar(40);
alter table settlement_statement add column requested_at timestamp;

update settlement_statement set approval_stage = 'NONE' where approval_stage is null;
