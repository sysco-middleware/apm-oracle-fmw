package apm.tracing;

import io.opentracing.Span;
import io.opentracing.References;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.metrics.Metrics;
import io.opentracing.contrib.metrics.prometheus.PrometheusMetricsReporter;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import no.sysco.middleware.apm.schema.common.Tag;
import no.sysco.middleware.apm.schema.common.TagsDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.System.out;

/**
 * OpenTracing Helper for Oracle Fusion Middleware.
 * Can be used from Oracle Service Bus as Java Callouts to start and end spans.
 * Potentially could handle different OpenTracing providers.
 */
public class OpenTracingHelper {

  private static final String serverName = System.getenv("SERVER_NAME");
  private static final String jaegerHost = System.getenv("JAEGER_HOST");
  private static final String host = jaegerHost == null ? "localhost" : jaegerHost;
  private static final String jaegerPort = System.getenv("JAEGER_PORT");
  private static final Integer port = jaegerPort == null ? 6831 : Integer.valueOf(jaegerPort);

  private static final CollectorRegistry registry = new CollectorRegistry();
  private static final String pushGatewayServerVariable = System.getenv("PUSH_GATEWAY_SERVER");
  private static Map<String, String> workflows = new HashMap<String, String>();
  private static final String pushGatewayServer =
      pushGatewayServerVariable != null ? pushGatewayServerVariable : "localhost:9091";
  private static final PushGateway pg = new PushGateway(pushGatewayServer);
  private static Map<String, Span> spans = new HashMap<String, Span>();
  private static Map<String, Tracer> tracers = new HashMap<String, Tracer>();
  private static boolean metricsEnabled = true;
  private static PrometheusMetricsReporter reporter;

  static {
    reporter =
        PrometheusMetricsReporter.newMetricsReporter()
            .withCollectorRegistry(registry)
            .withConstLabel("instance", serverName)
            .build();
  }

  private static Tracer getTracer(String workflowName) {
    if (tracers.get(workflowName) == null) {
      out.println("[Tracer] Jaeger Agent = " + host + ":" + port);
      final String serviceName = "service-bus:" + workflowName;

      Tracer jaegerTracer = new com.uber.jaeger.Configuration(
          serviceName,
          new com.uber.jaeger.Configuration.SamplerConfiguration("const", 1),
          new com.uber.jaeger.Configuration.ReporterConfiguration(
              true,  // logSpans
              host,
              port,
              1000,   // flush interval in milliseconds
              10000)  /*max buffered Spans*/)
          .getTracer();

      if (metricsEnabled) {
        Tracer tracer = Metrics.decorate(jaegerTracer, reporter);

        tracers.put(workflowName, tracer);
        return tracer;
      } else {
        tracers.put(workflowName, jaegerTracer);
        return jaegerTracer;
      }
    } else {
      return tracers.get(workflowName);
    }
  }

  /**
   * Start a trace, usually mapped to the beginning and end of each workflow.
   *
   * @return Trace ID
   */
  public static String startTrace(String parentId,
                                  String transactionId,
                                  String workflowName,
                                  XmlObject tagsXmlObject) {
    try {
      Tracer tracer = getTracer(workflowName);

      out.println("[Tracer] Starting trace for " + transactionId);

      //Build Span
      final String operationName = "main";
      final Tracer.SpanBuilder spanBuilder =
          tracer.buildSpan(operationName)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(Tags.COMPONENT.getKey(), workflowName)
              .withTag("instance", serverName);

      if (parentId != null) {
        final SpanContext spanContext = extractSpanContext(tracer, parentId);
        spanBuilder.addReference(References.FOLLOWS_FROM, spanContext);
      }

      //Parse XML
      if (tagsXmlObject != null) {
        final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
        final TagsDocument.Tags tags = tagsDocument.getTags();
        addTags(spanBuilder, tags.getTagArray());
      }

      //Start Span
      final Span span = spanBuilder.startManual();

      //Store references
      spans.put(transactionId, span);
      workflows.put(transactionId, workflowName);

      //Return TraceId
      final String traceId = extractTraceId(tracer, span);
      out.println("[Tracer] Trace started " + traceId);
      return traceId;
    } catch (XmlException e) {
      e.printStackTrace();
      return "";
    }
  }

  /**
   * End an in progress Trace. Execute it at the end of a workflow.
   */
  public static void endTrace(String transactionId) {
    try {
      out.println("[Tracer] ending trace for " + transactionId);

      //Get Span from memory
      final Span span = spans.get(transactionId);

      if (span != null) {
        //End span
        span.finish();

        if (metricsEnabled) {
          pushMetrics(transactionId);
        }

        //Clean memory collections
        spans.remove(transactionId);
        workflows.remove(transactionId);

        out.println("[Tracer] Trace ended for " + transactionId);
      } else {
        out.println("[Tracer] Trace not found for " + transactionId);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * A Span is mapped to a significant operation inside your workflow.
   */
  public static void startSpan(String transactionId, String operationName, XmlObject tagsXmlObject) {
    try {
      final Span span = spans.get(transactionId);

      if (span != null) {
        final String spanId = transactionId + "-" + operationName;
        out.println("[Tracer] Starting span for " + spanId);

        //Get parent pipeline name
        final String workflowName = workflows.get(transactionId);
        //Build Span as child of parent
        final Tracer tracer = getTracer(workflowName);
        final Tracer.SpanBuilder spanBuilder =
            tracer.buildSpan(operationName)
                //.ignoreActiveSpan()
                .asChildOf(span)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), workflowName)
                .withTag("instance", serverName);

        //Parse XML
        if (tagsXmlObject != null) {
          final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
          final TagsDocument.Tags tags = tagsDocument.getTags();
          addTags(spanBuilder, tags.getTagArray());
        }

        //Start span
        final Span activeSpan = spanBuilder.startManual();
        spans.put(spanId, activeSpan);
      } else {
        out.println("[Tracer] Parent trace not found for " + transactionId);
      }
    } catch (XmlException e) {
      e.printStackTrace();
    }
  }

  /**
   * End a significant operation inside a workflow.
   */
  public static void endSpan(String transactionId, String operationName) {
    final String spanId = transactionId + "-" + operationName;

    try {
      out.println("[Tracer] ending span for " + spanId);

      //Get Span from memory
      final Span activeSpan = spans.get(spanId);

      if (activeSpan != null) {
        activeSpan.finish();
        spans.remove(spanId);

        if (metricsEnabled) {
          pushMetrics(transactionId);
        }

        out.println("[Tracer] Span ended for " + spanId);
      } else {
        out.println("[Tracer] Span not found for " + spanId);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void errorTrace(String transactionId) {
    final Set<String> keys = spans.keySet();

    for (String key : keys) {
      if (key.startsWith(transactionId)) {
        out.println("[Tracer] Span with error " + transactionId);
        final Span span = spans.get(key);
        span.setTag(Tags.ERROR.getKey(), true);
        span.finish();

        spans.remove(key);

        if (metricsEnabled) {
          pushMetrics(transactionId);
        }

        if (key.equals(transactionId)) {
          workflows.remove(transactionId);
        }
      }
    }
  }

  private static void pushMetrics(String transactionId) {
    try {
      final String workflowName = workflows.get(transactionId);

      final String jobName =
          workflowName.toLowerCase().replace("\\s+", "_");
      pg.push(registry, jobName);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private static SpanContext extractSpanContext(Tracer tracer, String traceId) {
    final Map<String, String> contextMap = new HashMap<String, String>();
    contextMap.put("uber-trace-id", traceId);
    return tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(contextMap));
  }

  private static String extractTraceId(Tracer tracer, Span span) {
    final Map<String, String> contextMap = new HashMap<String, String>();
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(contextMap));
    return contextMap.get("uber-trace-id");
  }

  private static void addTags(Tracer.SpanBuilder spanBuilder, Tag[] tags) {
    for (Tag aTag : tags) {
      spanBuilder.withTag(aTag.getKey(), aTag.getValue());
    }
  }
}
