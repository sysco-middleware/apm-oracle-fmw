package apm.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import no.sysco.middleware.apm.schema.logging.Log;
import no.sysco.middleware.apm.schema.logging.LogDocument;
import org.apache.xmlbeans.XmlObject;

import java.net.URL;

/**
 *
 */
public class LoggingHelper {
  private static final String serverName = System.getenv("SERVER_NAME");
  private static LoggerContext loggerContext = new LoggerContext();
  private static Logger logger = null;

  static {
    ContextInitializer contextInitializer = new ContextInitializer(loggerContext);
    try {
      // Get a configuration file from classpath
      URL configurationUrl =
          Thread.currentThread().getContextClassLoader().getResource("logback.xml");
      if (configurationUrl == null) {
        throw new IllegalStateException("Unable to find custom logback configuration file");
      }
      // Ask context initializer to load configuration into context
      contextInitializer.configureByResource(configurationUrl);
      // Here we get logger from context
      loggerContext.putProperty("serverName", serverName);

      logger = loggerContext.getLogger("workflow");
    } catch (JoranException e) {
      throw new RuntimeException("Unable to configure logger", e);
    }
  }

  public static void info(String transactionId,
                          String workflowName,
                          XmlObject payload) {
    final LogDocument logDocument = LogDocument.Factory.newInstance();
    final Log log = logDocument.addNewLog();

    log.setPayload(payload);
    log.setTransactionId(transactionId);
    log.setWorkflowName(workflowName);

    logger.info(log.toString());
  }


  public static void info(String transactionId, String workflowName, String message) {
    final LogDocument logDocument = LogDocument.Factory.newInstance();
    final Log log = logDocument.addNewLog();

    log.setMessage(message);
    log.setTransactionId(transactionId);
    log.setWorkflowName(workflowName);

    logger.info(logDocument.toString());
  }
}
