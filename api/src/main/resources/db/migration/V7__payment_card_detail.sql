-- 결제 거래 카드 상세 (승인번호·카드사·마스킹·할부·매입상태·정산예정일).
alter table payment_transaction add column approval_no varchar(20);
alter table payment_transaction add column issuer_name varchar(20);
alter table payment_transaction add column card_last4 varchar(4);
alter table payment_transaction add column installment_months integer default 0;
alter table payment_transaction add column acquiring_status varchar(20);
alter table payment_transaction add column settlement_due_date date;

-- 기존 행: 정산 예정일과 매입 상태만 대략 채운다(승인번호·카드사·마스킹은 백필 이니셜라이저가 결정적으로 채움).
update payment_transaction
   set settlement_due_date = cast(approved_at as date) + 2,
       acquiring_status = case
           when payment_status = 'APPROVE_UNKNOWN' then 'UNSETTLED'
           when payment_method <> 'CARD' then 'N/A'
           when approved_at < now() - interval '1 day' then 'ACQUIRED'
           else 'APPROVED' end,
       installment_months = coalesce(installment_months, 0)
 where acquiring_status is null;
