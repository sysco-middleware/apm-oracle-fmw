package apm.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import no.sysco.middleware.apm.schema.common.Tag;
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

  public static void increaseCounter(XmlObject xmlObject) {
    try {
      LOGGER.info("Server name: " + serverName);
      LOGGER.info("Push gateway server: " + pushGatewayServer);

      final Metric metric = getMetric(xmlObject);

      final String componentName = getComponentName(metric);
      final String counterNameSuffix = getMetricName(metric);
      final String counterDescription = metric.getMetricDescription();

      final String[] labelNames = getLabelNames(metric);

      final String[] labels = getLabels(metric);

      final String counterName = componentName + "_" + counterNameSuffix;

      final double value = metric.getMetricValue();

      if (counters.containsKey(counterName)) {
        final Counter counter = counters.get(counterName);
        counter.labels(labels)
            .inc(value);
        pg.push(registry, componentName);
      } else {
        final Counter counter =
            Counter.build()
                .name(counterName)
                .help(counterDescription)
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

  private static String[] getLabelNames(Metric metric) {
    final Tag[] tags = metric.getTags().getTagArray();
    final String[] labelNames = new String[tags.length + 1];
    for (int i = 0; i < tags.length; i++) {
      labelNames[i] = tags[i].getKey();
    }
    labelNames[tags.length] = "instance";
    return labelNames;
  }

  private static String[] getLabels(Metric metric) {
    final Tag[] tags = metric.getTags().getTagArray();
    final String[] labels = new String[tags.length + 1];
    for (int i = 0; i < tags.length; i++) {
      labels[i] = tags[i].getValue();
    }
    labels[tags.length] = serverName;
    return labels;
  }

  public static void increaseGauge(XmlObject xmlObject) {
    try {
      LOGGER.info("Server name: " + serverName);
      LOGGER.info("Push gateway server: " + pushGatewayServer);

      final Metric metric = getMetric(xmlObject);

      final String componentName = getComponentName(metric);
      final String gaugeNameSufix = getMetricName(metric);
      final String gaugeDescription = metric.getMetricDescription();

      final String[] labelNames = getLabelNames(metric);

      final String[] labels = getLabels(metric);

      final String gaugeName = componentName + "_" + gaugeNameSufix;

      final double value = metric.getMetricValue();

      if (gauges.containsKey(gaugeName)) {
        final Gauge gauge = gauges.get(gaugeName);
        gauge.labels(labels)
            .inc(value);
        pg.pushAdd(registry, componentName);
      } else {
        final Gauge gauge =
            Gauge.build()
                .name(gaugeName)
                .help(gaugeDescription)
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

  private static String getMetricName(Metric increaseCounter) {
    return increaseCounter.getMetricName()
        .toLowerCase()
        .replace("\\s+", "_");
  }

  private static String getComponentName(Metric increaseCounter) {
    return increaseCounter.getComponentName()
        .toLowerCase()
        .replace("\\s+", "_");
  }

  public static void decreaseGauge(XmlObject xmlObject) {
    try {
      LOGGER.info("Server name: " + serverName);
      LOGGER.info("Push gateway server: " + pushGatewayServer);

      final Metric metric = getMetric(xmlObject);

      final String componentName = getComponentName(metric);
      final String gaugeNameSuffix = getMetricName(metric);
      final String gaugeDescription = metric.getMetricDescription();

      final String[] labelNames = getLabelNames(metric);

      final String[] labels = getLabels(metric);

      final String gaugeName = componentName + "_" + gaugeNameSuffix;

      final double value = metric.getMetricValue();

      if (gauges.containsKey(gaugeName)) {
        final Gauge gauge = gauges.get(gaugeName);
        gauge.labels(labels)
            .dec(value);
        pg.pushAdd(registry, componentName);
      } else {
        final Gauge gauge =
            Gauge.build()
                .name(gaugeName)
                .help(gaugeDescription)
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

  private static Metric getMetric(XmlObject xmlObject) throws XmlException {
    final MetricDocument metricDocument =
        MetricDocument.Factory.parse(xmlObject.getDomNode());
    return metricDocument.getMetric();
  }
}
