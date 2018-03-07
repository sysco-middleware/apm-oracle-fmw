package apm.tracing;

import brave.Tracing;
import brave.opentracing.BraveTracer;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.metrics.Metrics;
import io.opentracing.contrib.metrics.prometheus.PrometheusMetricsReporter;
import io.opentracing.noop.NoopTracerFactory;
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
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.out;

/**
 * OpenTracing Helper for Oracle Fusion Middleware.
 * Can be used from Oracle Service Bus as Java Callouts to start and end spans.
 * Potentially could handle different OpenTracing providers.
 */
public class OpenTracingHelper {

  private static final String serverName = System.getenv("SERVER_NAME");
  private static final String tracingProvider = System.getenv("TRACING_PROVIDER");

  //Jaeger config
  private static final String jaegerHostOption = System.getenv("TRACING_JAEGER_HOST");
  private static final String jaegerHost = jaegerHostOption == null ? "localhost" : jaegerHostOption;
  private static final String jaegerPort = System.getenv("TRACING_JAEGER_PORT");
  private static final Integer port = jaegerPort == null ? 6831 : Integer.valueOf(jaegerPort);

  //Zipkin config
  private static final String zipkinUrlBase = System.getenv("TRACING_ZIPKIN_URL");
  private static final String zipkinUrl = zipkinUrlBase == null ? "http://localhost:9411/api/v2/spans" : zipkinUrlBase;

  //Metrics config
  private static final String pushGatewayServerVariable = System.getenv("PUSH_GATEWAY_SERVER");
  private static final String pushGatewayServer =
      pushGatewayServerVariable != null ? pushGatewayServerVariable : "localhost:9091";
  private static final PushGateway pg = new PushGateway(pushGatewayServer);

  //Maps
  private static ConcurrentHashMap<String, String> workflows = new ConcurrentHashMap<>();
  private static ConcurrentHashMap<String, Span> spans = new ConcurrentHashMap<>();
  private static ConcurrentHashMap<String, Tracer> tracers = new ConcurrentHashMap<>();
  private static ConcurrentHashMap<String, CollectorRegistry> registries = new ConcurrentHashMap<>();

  private static boolean metricsEnabled = true;

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

        final Tracer baseTracer;
        out.println("[Tracer] Provider = " + tracingProvider);

        switch (tracingProvider) {
          case "JAEGER":
            out.println("[Tracer] Jaeger Agent = " + jaegerHost + ":" + port);
            baseTracer = new com.uber.jaeger.Configuration(
                serviceName,
                new com.uber.jaeger.Configuration.SamplerConfiguration("const", 1),
                new com.uber.jaeger.Configuration.ReporterConfiguration(
                    true,  // logSpans
                    jaegerHost,
                    port,
                    1000,   // flush interval in milliseconds
                    10000)  /*max buffered Spans*/)
                .getTracer();
            break;
          case "ZIPKIN":
            out.println("[Tracer] Zipkin = " + zipkinUrl);
            final OkHttpSender sender = OkHttpSender.create(zipkinUrl);
            //final KafkaSender sender = KafkaSender.create("docker-vm:9092").toBuilder().autoBuild();
            final AsyncReporter<zipkin2.Span> spanReporter = AsyncReporter.create(sender);

            // Now, create a Brave tracing component with the service name you want to see in Zipkin.
            //   (the dependency is io.zipkin.brave:brave)
            final Tracing braveTracing =
                Tracing.newBuilder()
                    .localServiceName(serviceName)
                    .spanReporter(spanReporter)
                    .build();

            // use this to create an OpenTracing Tracer
            baseTracer = BraveTracer.create(braveTracing);
            break;
          default:
            baseTracer = NoopTracerFactory.create();
        }

        if (metricsEnabled) {
          final Tracer tracer = Metrics.decorate(baseTracer, reporter);

          tracers.put(workflowName, tracer);
          registries.put(workflowName, registry);
          return tracer;
        } else {
          tracers.put(workflowName, baseTracer);
          return baseTracer;
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
      final Tracer tracer = getTracer(workflowName);

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
        final Span span = spanBuilder.start();

        //Return TraceId
        final String traceId = extractTraceId(tracer, span);
        out.println("TraceID=" + traceId);

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
          final Span activeSpan = spanBuilder.start();
          spans.put(spanId, activeSpan);
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

  public static void errorTrace(String traceId) {
    if (traceId != null) {
      final Set<String> keys = new HashSet<>(spans.keySet());

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
    final Map<String, String> contextMap = new HashMap<>();
    switch (tracingProvider) {
      case "JAEGER":
        contextMap.put("uber-trace-id", traceId);
      case "ZIPKIN":
        contextMap.put("X-B3-TraceId", traceId);
    }
    return tracer.extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(contextMap));
  }

  private static String extractTraceId(Tracer tracer, Span span) {
    final Map<String, String> contextMap = new HashMap<>();
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(contextMap));

    for (Map.Entry<String, String> entry : contextMap.entrySet()) {
      out.println("Entry CM: " + entry.getKey() + ":" + entry.getValue());
    }

    switch (tracingProvider) {
      case "JAEGER":
        return contextMap.get("uber-trace-id");
      case "ZIPKIN":
        return contextMap.get("X-B3-TraceId");
      default:
        return "";
    }
  }

  private static void addTags(Tracer.SpanBuilder spanBuilder, Tag[] tags) {
    for (Tag aTag : tags) {
      spanBuilder.withTag(aTag.getKey(), aTag.getValue());
    }
  }
}
