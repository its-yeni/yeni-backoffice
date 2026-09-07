-- 결제 채널(WEB 온라인 PG / POS 매장 단말·VAN) 축.
alter table payment_transaction add column channel_type varchar(10) default 'WEB';
alter table sales_transaction  add column channel_type varchar(10) default 'WEB';
alter table commerce_order      add column channel_type varchar(10) default 'WEB';

update payment_transaction set channel_type = 'WEB' where channel_type is null;
update sales_transaction  set channel_type = 'WEB' where channel_type is null;
update commerce_order      set channel_type = 'WEB' where channel_type is null;
