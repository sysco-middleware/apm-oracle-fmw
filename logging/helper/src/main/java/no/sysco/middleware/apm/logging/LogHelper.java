package no.sysco.middleware.apm.logging;

import no.sysco.middleware.apm.schema.common.TagsDocument;
import no.sysco.middleware.apm.schema.logging.Log;
import no.sysco.middleware.apm.schema.logging.LogDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class LogHelper {

  private static Logger logger = LoggerFactory.getLogger("workflows");

  public static void info(String transactionId, String workflowName, XmlObject payload, XmlObject tagsXmlObject) {
    try {
      final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
      final TagsDocument.Tags tags = tagsDocument.getTags();

      final LogDocument logDocument = LogDocument.Factory.newInstance();
      final Log log = logDocument.addNewLog();
      log.setPayload(payload);
      log.setTags((Log.Tags) tags);
      log.setTransactionId(transactionId);
      log.setWorkflowName(workflowName);

      logger.info(log.xmlText());
    } catch (XmlException e) {
      e.printStackTrace();
    }
  }


  public static void info(String transactionId, String workflowName, String message, XmlObject tagsXmlObject) {
    try {
      final LogDocument logDocument = LogDocument.Factory.newInstance();
      final Log log = logDocument.addNewLog();

      if (tagsXmlObject != null) {
        final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
        final TagsDocument.Tags tags = tagsDocument.getTags();
        log.setTags((Log.Tags) tags);
      }
      log.setMessage(message);
      log.setTransactionId(transactionId);
      log.setWorkflowName(workflowName);

      logger.info(logDocument.xmlText());
    } catch (XmlException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    LogHelper.info("1", "test", "test", null);
  }
}
