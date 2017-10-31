BEGIN
 DBMS_AQADM.CREATE_QUEUE_TABLE( queue_table => 'queue_message_table', queue_payload_type => 'SYS.AQ$_JMS_TEXT_MESSAGE');
END;        
/
BEGIN
  DBMS_AQADM.CREATE_QUEUE( queue_name => 'ORACLE_QUEUE', queue_table => 'queue_message_table');
END;
/
BEGIN
  DBMS_AQADM.START_QUEUE(queue_name => 'ORACLE_QUEUE');
END;
/
