package apm.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import no.sysco.middleware.apm.schema.common.Tag;
import no.sysco.middleware.apm.schema.common.TagsDocument;
import no.sysco.middleware.apm.schema.metric.Metric;
import no.sysco.middleware.apm.schema.metric.MetricDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 */
public class PrometheusHelper {

  private static final Logger LOGGER = Logger.getLogger(PrometheusHelper.class.getName());

  private static final CollectorRegistry registry = new CollectorRegistry();
  private static final String pushGatewayServerVariable = System.getenv("PUSH_GATEWAY_SERVER");
  private static final String pushGatewayServer =
      pushGatewayServerVariable != null ? pushGatewayServerVariable : "localhost:9091";
  private static final PushGateway pg = new PushGateway(pushGatewayServer);
  private static final String serverName = System.getenv("SERVER_NAME");

  private static Map<String, Counter> counters = new HashMap<String, Counter>();
  private static Map<String, Gauge> gauges = new HashMap<String, Gauge>();

  /**
   * @param workflowName
   * @param metricName
   * @param metricDescription
   * @param metricValue
   * @param tagsXmlObject
   */
  public static void increaseCounter(String workflowName,
                                     String metricName,
                                     String metricDescription,
                                     Double metricValue,
                                     XmlObject tagsXmlObject) {
    try {
      LOGGER.info("Server name: " + serverName);
      LOGGER.info("Push gateway server: " + pushGatewayServer);

      ///final Metric metric = getMetric(xmlObject);

      final String componentName = getComponentName(workflowName);
      final String counterNameSuffix = getMetricName(metricName);

      final TagsDocument.Tags tags = getTags(tagsXmlObject);

      final String[] labelNames = getLabelNames(tags);

      final String[] labels = getLabels(tags);

      final String counterName = componentName + "_" + counterNameSuffix;

      final double value = metricValue;

      if (counters.containsKey(counterName)) {
        final Counter counter = counters.get(counterName);
        counter.labels(labels)
            .inc(value);
        pg.push(registry, componentName);
      } else {
        final Counter counter =
            Counter.build()
                .name(counterName)
                .help(metricDescription)
                .labelNames(labelNames)
                .register(registry);
        counter.labels(labels)
            .inc(value);
        pg.push(registry, componentName);
        counters.put(counterName, counter);
      }
    } catch (XmlException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   *
   * @param workflowName
   * @param metricName
   * @param metricDescription
   * @param metricValue
   * @param tagsXmlObject
   */
  public static void increaseGauge(String workflowName,
                                   String metricName,
                                   String metricDescription,
                                   Double metricValue,
                                   XmlObject tagsXmlObject) {
    try {
      LOGGER.info("Server name: " + serverName);
      LOGGER.info("Push gateway server: " + pushGatewayServer);

      //final Metric metric = getMetric(xmlObject);

      final String componentName = getComponentName(workflowName);
      final String gaugeNameSuffix = getMetricName(metricName);

      final TagsDocument.Tags tags = getTags(tagsXmlObject);

      final String[] labelNames = getLabelNames(tags);

      final String[] labels = getLabels(tags);

      final String gaugeName = componentName + "_" + gaugeNameSuffix;

      final double value = metricValue;

      if (gauges.containsKey(gaugeName)) {
        final Gauge gauge = gauges.get(gaugeName);
        gauge.labels(labels)
            .inc(value);
        pg.pushAdd(registry, componentName);
      } else {
        final Gauge gauge =
            Gauge.build()
                .name(gaugeName)
                .help(metricDescription)
                .labelNames(labelNames)
                .register(registry);
        gauge.labels(labels)
            .inc(value);
        pg.pushAdd(registry, componentName);
        gauges.put(gaugeName, gauge);
      }
    } catch (XmlException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   *
   * @param workflowName
   * @param metricName
   * @param metricDescription
   * @param metricValue
   * @param tagsXmlObject
   */
  public static void decreaseGauge(String workflowName,
                                   String metricName,
                                   String metricDescription,
                                   Double metricValue,
                                   XmlObject tagsXmlObject) {
    try {
      LOGGER.info("Server name: " + serverName);
      LOGGER.info("Push gateway server: " + pushGatewayServer);

      //final Metric metric = getMetric(xmlObject);

      final String componentName = getComponentName(workflowName);
      final String gaugeNameSuffix = getMetricName(metricName);

      TagsDocument.Tags tags = getTags(tagsXmlObject);

      final String[] labelNames = getLabelNames(tags);

      final String[] labels = getLabels(tags);

      final String gaugeName = componentName + "_" + gaugeNameSuffix;

      final double value = metricValue;

      if (gauges.containsKey(gaugeName)) {
        final Gauge gauge = gauges.get(gaugeName);
        gauge.labels(labels)
            .dec(value);
        pg.pushAdd(registry, componentName);
      } else {
        final Gauge gauge =
            Gauge.build()
                .name(gaugeName)
                .help(metricDescription)
                .labelNames(labelNames)
                .register(registry);
        gauge.labels(labels)
            .dec(value);
        pg.pushAdd(registry, componentName);
        gauges.put(gaugeName, gauge);
      }
    } catch (XmlException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String getMetricName(String metricName) {
    return metricName.toLowerCase().replace("\\s+", "_");
  }

  private static String getComponentName(String componentName) {
    return componentName.toLowerCase().replace("\\s+", "_");
  }

  private static String[] getLabelNames(TagsDocument.Tags tags) {
    final Tag[] tagArray = tags.getTagArray();
    final String[] labelNames = new String[tagArray.length + 1];
    for (int i = 0; i < tagArray.length; i++) {
      labelNames[i] = tagArray[i].getKey();
    }
    labelNames[tagArray.length] = "instance";
    return labelNames;
  }

  private static String[] getLabels(TagsDocument.Tags tags) {
    final Tag[] tagArray = tags.getTagArray();
    final String[] labels = new String[tagArray.length + 1];
    for (int i = 0; i < tagArray.length; i++) {
      labels[i] = tagArray[i].getValue();
    }
    labels[tagArray.length] = serverName;
    return labels;
  }

  private static TagsDocument.Tags getTags(XmlObject tagsXmlObject) throws XmlException {
    TagsDocument.Tags tags = TagsDocument.Factory.newInstance().addNewTags();
    if (tagsXmlObject != null) {
      final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
      tags = tagsDocument.getTags();
    }
    return tags;
  }

  private static Metric getMetric(XmlObject xmlObject) throws XmlException {
    final MetricDocument metricDocument =
        MetricDocument.Factory.parse(xmlObject.getDomNode());
    return metricDocument.getMetric();
  }
}
