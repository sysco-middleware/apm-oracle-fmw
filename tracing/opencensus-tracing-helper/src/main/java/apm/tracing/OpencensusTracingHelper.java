package apm.tracing;

import io.opencensus.exporter.trace.logging.LoggingTraceExporter;
import io.opencensus.exporter.trace.zipkin.ZipkinTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.PropagationComponent;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.propagation.TextFormat;
import io.opencensus.trace.samplers.Samplers;
import no.sysco.middleware.apm.schema.common.Tag;
import no.sysco.middleware.apm.schema.common.TagsDocument;
import no.sysco.middleware.apm.schema.tracing.SpanContextDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.out;

/**
 *
 */
public class OpencensusTracingHelper {

  private static final PropagationComponent propagationComponent = PropagationComponent.getNoopPropagationComponent();
  private static final TextFormat.Getter<SpanContextDocument> spanContextDocumentGetter = new SpanContextDocumentGetter();
  private static final Map<SpanContext, Span> spanContextAndSpans = new ConcurrentHashMap<>();

  private static void registerExporter(String workflowName) {
    final String tracingProvider = System.getenv("TRACING_PROVIDER");

    switch (tracingProvider) {
      case "ZIPKIN":
        final String zipkinUrl = System.getenv("TRACING_ZIPKIN_URL");
        ZipkinTraceExporter.createAndRegister(zipkinUrl, workflowName);
      default:
        LoggingTraceExporter.register();
    }
  }

  public static XmlObject startSpan(String workflowName) throws XmlException, SpanContextParseException {
    return startSpan(workflowName, null, null);
  }

  public static XmlObject startSpan(String workflowName, XmlObject spanContextXmlObject) throws XmlException, SpanContextParseException {
    return startSpan(workflowName, spanContextXmlObject, null);
  }

  public static XmlObject startSpan(String workflowName,
                                    XmlObject spanContextXmlObject,
                                    XmlObject tagsXmlObject)
      throws SpanContextParseException, XmlException {
    registerExporter(workflowName);
    final Tracer tracer = Tracing.getTracer();

    out.println("[Tracer] Starting trace for " + workflowName);

    final SpanContext parentSpanContext = getSpanContextFromXml(spanContextXmlObject);

    final String spanName = "main";
    final SpanBuilder spanBuilder =
        tracer
            .spanBuilderWithRemoteParent(spanName, parentSpanContext)
            .setSampler(Samplers.alwaysSample())
            .setRecordEvents(true);

    final Span span = spanBuilder.startSpan();
    addAnnotationsToSpan(span, tagsXmlObject);


    final SpanContextDocument spanContextDocument = SpanContextDocument.Factory.newInstance();
    final TextFormat.Setter<SpanContextDocument> spanContextDocumentSetter =
        new SpanContextDocumentSetter(spanContextDocument);
    final SpanContext spanContext = span.getContext();
    propagationComponent
        .getB3Format()
        .inject(spanContext, spanContextDocument, spanContextDocumentSetter);

    spanContextAndSpans.put(spanContext, span);

    return spanContextDocument;
  }

  private static void addAnnotationsToSpan(Span span, XmlObject tagsXmlObject) throws XmlException {
    //Parse XML
    if (tagsXmlObject != null) {
      final TagsDocument tagsDocument = TagsDocument.Factory.parse(tagsXmlObject.getDomNode());
      addTags(span, tagsDocument.getTags().getTagArray());
    }
  }

  public static void addAttributes(XmlObject spanContextXmlObject, XmlObject tagsXmlObject)
      throws SpanContextParseException, XmlException {
    final SpanContext spanContext = getSpanContextFromXml(spanContextXmlObject);
    final Span span = spanContextAndSpans.get(spanContext);
    addAnnotationsToSpan(span, tagsXmlObject);
  }

  public static void addAnnotations(XmlObject spanContextXmlObject, String annotation)
      throws SpanContextParseException, XmlException {
    final SpanContext spanContext = getSpanContextFromXml(spanContextXmlObject);
    final Span span = spanContextAndSpans.get(spanContext);
    span.addAnnotation(annotation);
  }

  private static void addTags(Span span, Tag[] tagArray) {
    for (Tag tag : tagArray) {
      span.putAttribute(tag.getKey(), AttributeValue.stringAttributeValue(tag.getValue()));
    }
  }

  private static SpanContext getSpanContextFromXml(XmlObject spanContextXmlObject)
      throws XmlException, SpanContextParseException {
    final SpanContext parentSpanContext;

    if (spanContextXmlObject == null) {
      parentSpanContext = null;
    } else {
      final SpanContextDocument spanContextDocument = parseSpanContextXml(spanContextXmlObject);
      parentSpanContext =
          propagationComponent
              .getB3Format()
              .extract(spanContextDocument, spanContextDocumentGetter);
    }
    return parentSpanContext;
  }

  public static void endSpan(XmlObject spanContextXmlObject) throws XmlException, SpanContextParseException {
    endSpan(spanContextXmlObject, null);
  }

  public static void endSpan(XmlObject spanContextXmlObject,
                             XmlObject tagsXmlObject)
      throws SpanContextParseException, XmlException {
    final SpanContext spanContext = getSpanContextFromXml(spanContextXmlObject);

    if (spanContext != null) {
      out.println("[Tracer] ending trace for " + spanContext.toString());

      final Span span = spanContextAndSpans.get(spanContext);

      //Parse XML
      addAnnotationsToSpan(span, tagsXmlObject);

      span.end();

      spanContextAndSpans.remove(spanContext);
    }
  }

  private static SpanContextDocument parseSpanContextXml(XmlObject spanContextXmlObject) throws XmlException {
    return SpanContextDocument.Factory.parse(spanContextXmlObject.getDomNode());
  }

  static class SpanContextDocumentGetter extends TextFormat.Getter<SpanContextDocument> {

    @Nullable
    public String get(SpanContextDocument spanContextDocument, String key) {
      switch (key) {
        case "X-B3-TraceId":
          return spanContextDocument.getSpanContext().getTraceId();
        case "X-B3-SpanId":
          return spanContextDocument.getSpanContext().getSpanId();
        case "X-B3-ParentSpanId":
          return spanContextDocument.getSpanContext().getParentSpanId();
        case "X-B3-Sampled":
          return spanContextDocument.getSpanContext().getSampled();
        case "X-B3-Flags":
          return spanContextDocument.getSpanContext().getFlags();
        default:
          return "";
      }
    }
  }

  static class SpanContextDocumentSetter extends TextFormat.Setter<SpanContextDocument> {

    private final SpanContextDocument spanContextDocument;

    SpanContextDocumentSetter(SpanContextDocument spanContextDocument) {
      this.spanContextDocument = spanContextDocument;
    }

    @Override
    public void put(SpanContextDocument carrier, String key, String value) {
      switch (key) {
        case "X-B3-TraceId":
          spanContextDocument.getSpanContext().setTraceId(value);
        case "X-B3-SpanId":
          spanContextDocument.getSpanContext().setSpanId(value);
        case "X-B3-ParentSpanId":
          spanContextDocument.getSpanContext().setParentSpanId(value);
        case "X-B3-Sampled":
          spanContextDocument.getSpanContext().setSampled(value);
        case "X-B3-Flags":
          spanContextDocument.getSpanContext().setFlags(value);
      }
    }
  }
}
