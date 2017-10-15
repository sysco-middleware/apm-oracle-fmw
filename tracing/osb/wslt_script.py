connect('weblogic', 'welcome1', 't3://localhost:8001')
edit()
startEdit()

cd('/')
cmo.createJMSServer('TracingJMSServer')

cd('/JMSServers/TracingJMSServer')
cmo.setPersistentStore(getMBean('/FileStores/FileStore'))
set('Targets',jarray.array([ObjectName('com.bea:Name=AdminServer,Type=Server')], ObjectName))


cd('/')
cmo.createJMSSystemResource('TracingSystemModule')

cd('/JMSSystemResources/TracingSystemModule')
set('Targets',jarray.array([ObjectName('com.bea:Name=AdminServer,Type=Server')], ObjectName))

cmo.createSubDeployment('TracingSubDeployment')

cd('/JMSSystemResources/TracingSystemModule/SubDeployments/TracingSubDeployment')
set('Targets',jarray.array([ObjectName('com.bea:Name=TracingJMSServer,Type=JMSServer')], ObjectName))

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule')
cmo.createConnectionFactory('TracingConnectionFactory')

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule/ConnectionFactories/TracingConnectionFactory')
cmo.setJNDIName('jms.tracing.cf')

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule/ConnectionFactories/TracingConnectionFactory/SecurityParams/TracingConnectionFactory')
cmo.setAttachJMSXUserId(false)

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule/ConnectionFactories/TracingConnectionFactory/ClientParams/TracingConnectionFactory')
cmo.setClientIdPolicy('Restricted')
cmo.setSubscriptionSharingPolicy('Exclusive')
cmo.setMessagesMaximum(10)

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule/ConnectionFactories/TracingConnectionFactory/TransactionParams/TracingConnectionFactory')
cmo.setXAConnectionFactoryEnabled(false)

cd('/JMSSystemResources/TracingSystemModule/SubDeployments/TracingSubDeployment')
set('Targets',jarray.array([ObjectName('com.bea:Name=TracingJMSServer,Type=JMSServer')], ObjectName))

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule/ConnectionFactories/TracingConnectionFactory')
cmo.setSubDeploymentName('TracingSubDeployment')

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule')
cmo.createQueue('TraceQueue')

cd('/JMSSystemResources/TracingSystemModule/JMSResource/TracingSystemModule/Queues/TraceQueue')
cmo.setJNDIName('jms.tracing.traceQueue')
cmo.setSubDeploymentName('TracingSubDeployment')

cd('/JMSSystemResources/TracingSystemModule/SubDeployments/TracingSubDeployment')
set('Targets',jarray.array([ObjectName('com.bea:Name=TracingJMSServer,Type=JMSServer')], ObjectName))

save()
activate()