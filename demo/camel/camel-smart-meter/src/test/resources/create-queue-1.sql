create or replace type invoice_type as object(
      invoice_id number,
      invoice_number varchar2(60),
      vendor_id number
);
/

exec DBMS_AQADM.CREATE_QUEUE_TABLE (queue_table => 'EVENT_USER.invoice_queue_table',queue_payload_type => 'EVENT_USER.invoice_type');
/

SELECT queue_table,type,object_type,recipients FROM USER_QUEUE_TABLES;

exec DBMS_AQADM.CREATE_QUEUE (queue_name =>'EVENT_USER.invoice_queue', queue_table=>'EVENT_USER.invoice_queue_table');
/
select name, queue_table, queue_type from user_queues;

exec DBMS_AQADM.START_QUEUE('EVENT_USER.invoice_queue');
/