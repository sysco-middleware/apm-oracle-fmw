package apm.tracing;

import com.instana.opentracing.InstanaTracer;
import io.opentracing.NoopTracerFactory;
import io.opentracing.References;
import io.opentracing.Span;
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
import java.util.HashSet;
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
  private static final String tracingProvider = System.getenv("TRACING_PROVIDER");

  private static final String pushGatewayServerVariable = System.getenv("PUSH_GATEWAY_SERVER");
  private static final String pushGatewayServer =
      pushGatewayServerVariable != null ? pushGatewayServerVariable : "localhost:9091";
  private static final PushGateway pg = new PushGateway(pushGatewayServer);
  private static Map<String, String> workflows = new HashMap<String, String>();
  private static Map<String, Span> spans = new HashMap<String, Span>();
  private static Map<String, Tracer> tracers = new HashMap<String, Tracer>();
  private static Map<String, CollectorRegistry> registries = new HashMap<String, CollectorRegistry>();
  private static boolean metricsEnabled;
  static {
    final String metricsEnabledOption = System.getenv("METRICS_ENABLED");
    metricsEnabled = Boolean.parseBoolean(metricsEnabledOption);
  }

  private static Tracer getTracer(String workflowName) {
    if (tracers.get(workflowName) == null) {
      try {
        final String serviceName = "service-bus:" + workflowName;

        final CollectorRegistry registry = new CollectorRegistry();

        final PrometheusMetricsReporter reporter;

        reporter =
            PrometheusMetricsReporter.newMetricsReporter()
                .withCollectorRegistry(registry)
                .withConstLabel("instance", serverName)
                .build();

        final Tracer tracer;

        if (tracingProvider.equals("INSTANA")) {
          out.println("Instantiating Instana Tracer");
          tracer = new InstanaTracer();
          out.println(tracer.toString());
        } else {
          out.println("Instantiating Jaeger Tracer");
          final String jaegerHost = System.getenv("JAEGER_HOST");
          final String host = jaegerHost == null ? "localhost" : jaegerHost;
          final String jaegerPort = System.getenv("JAEGER_PORT");
          final Integer port = jaegerPort == null ? 6831 : Integer.valueOf(jaegerPort);
          out.println("[Tracer] Jaeger Agent = " + host + ":" + port);
          tracer = new com.uber.jaeger.Configuration(
              serviceName,
              new com.uber.jaeger.Configuration.SamplerConfiguration("const", 1),
              new com.uber.jaeger.Configuration.ReporterConfiguration(
                  true,  // logSpans
                  host,
                  port,
                  1000,   // flush interval in milliseconds
                  10000)  /*max buffered Spans*/)
              .getTracer();
        }


        if (metricsEnabled) {
          final Tracer metricsTracer = Metrics.decorate(tracer, reporter);
          tracers.put(workflowName, metricsTracer);
          registries.put(workflowName, registry);
          return metricsTracer;
        } else {
          tracers.put(workflowName, tracer);
          return tracer;
        }
      } catch (Exception e) {
        out.println("Error preparing tracer: " + e.getMessage());
        return null;
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
                                  String workflowName,
                                  XmlObject tagsXmlObject) {
    try {
      Tracer tracer = getTracer(workflowName);

      if (tracer != null) {

        out.println("[Tracer] Starting trace for " + workflowName);

        //Build Span
        final String operationName = "main";
        final Tracer.SpanBuilder spanBuilder =
            tracer.buildSpan(operationName)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), workflowName)
                .withTag("instance", serverName);

        if (parentId != null) {
          try {
            final SpanContext spanContext = extractSpanContext(tracer, parentId);
            spanBuilder.addReference(References.FOLLOWS_FROM, spanContext);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

        //Parse XML
        if (tagsXmlObject != null) {
          final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
          final TagsDocument.Tags tags = tagsDocument.getTags();
          addTags(spanBuilder, tags.getTagArray());
        }

        //Start Span
        final Span span = spanBuilder.startManual();

        //Return TraceId
        final String traceId = extractTraceId(tracer, span);

        //Store references
        spans.put(traceId, span);
        workflows.put(traceId, workflowName);

        out.println("[Tracer] Trace started " + traceId);
        return traceId;
      } else {
        out.println("Error getting tracer: Tracer not instantiated.");
        return null;
      }
    } catch (XmlException e) {
      e.printStackTrace();
      return "";
    }
  }

  /**
   * End an in progress Trace. Execute it at the end of a workflow.
   */
  public static void endTrace(String traceId) {
    try {
      if (traceId != null) {
        out.println("[Tracer] ending trace for " + traceId);

        //Get Span from memory
        final Span span = spans.get(traceId);

        if (span != null) {
          //End span
          span.finish();

          if (metricsEnabled) {
            pushMetrics(traceId);
          }

          //Clean memory collections
          spans.remove(traceId);
          workflows.remove(traceId);

          out.println("[Tracer] Trace ended for " + traceId);
        } else {
          out.println("[Tracer] Trace not found for " + traceId);
        }
      } else {
        out.println("Error getting tracer: Tracer not instantiated.");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * A Span is mapped to a significant operation inside your workflow.
   */
  public static void startSpan(String traceId, String operationName, XmlObject tagsXmlObject) {
    try {
      if (traceId != null) {
        final Span span = spans.get(traceId);

        if (span != null) {
          final String spanId = traceId + "-" + operationName;
          out.println("[Tracer] Starting span for " + spanId);

          //Get parent pipeline name
          final String workflowName = workflows.get(traceId);
          //Build Span as child of parent
          final Tracer tracer = getTracer(workflowName);


          if (tracer != null) {
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
            out.println("Error getting tracer: Tracer not instantiated.");
          }
        } else {
          out.println("[Tracer] Parent trace not found for " + traceId);
        }
      } else {
        out.println("Error getting tracer: Tracer not instantiated.");
      }
    } catch (XmlException e) {
      e.printStackTrace();
    }
  }

  /**
   * End a significant operation inside a workflow.
   */
  public static void endSpan(String traceId, String operationName) {
    final String spanId = traceId + "-" + operationName;

    try {
      if (traceId != null) {
        out.println("[Tracer] ending span for " + spanId);

        //Get Span from memory
        final Span activeSpan = spans.get(spanId);

        if (activeSpan != null) {
          activeSpan.finish();
          spans.remove(spanId);

          if (metricsEnabled) {
            pushMetrics(traceId);
          }

          out.println("[Tracer] Span ended for " + spanId);
        } else {
          out.println("[Tracer] Span not found for " + spanId);
        }
      } else {
        out.println("Error getting tracer: Tracer not instantiated.");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Close a Span with an Error
   * @param traceId Parent trace Id
   */
  public static void errorTrace(String traceId) {
    if (traceId != null) {
      final Set<String> keys = new HashSet<String>(spans.keySet());

      for (String key : keys) {
        if (key.startsWith(traceId)) {
          out.println("[Tracer] Span with error " + traceId);
          final Span span = spans.get(key);
          span.setTag(Tags.ERROR.getKey(), true);
          span.finish();

          spans.remove(key);

          if (metricsEnabled) {
            pushMetrics(traceId);
          }

          if (key.equals(traceId)) {
            workflows.remove(traceId);
          }
        }
      }
    } else {
      out.println("Error getting tracer: Tracer not instantiated.");
    }
  }

  private static void pushMetrics(String transactionId) {
    try {
      final String workflowName = workflows.get(transactionId);

      final CollectorRegistry registry = registries.get(workflowName);

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
