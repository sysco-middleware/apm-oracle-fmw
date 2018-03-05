package apm.tracing;

import io.opencensus.exporter.trace.logging.LoggingTraceExporter;
import io.opencensus.exporter.trace.zipkin.ZipkinTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Link;
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
 * Opencensus Helper to manage Spans.
 */
public class OpencensusTracingHelper {

  static final String X_B3_TRACE_ID = "X─B3─TraceId";
  static final String X_B3_SPAN_ID = "X─B3─SpanId";
  static final String X_B3_PARENT_SPAN_ID = "X─B3─ParentSpanId";
  static final String X_B3_SAMPLED = "X─B3─Sampled";
  static final String X_B3_FLAGS = "X─B3─Flags";
  private static final TextFormat.Getter<SpanContextDocument> spanContextDocumentGetter = new SpanContextDocumentGetter();
  private static final Map<SpanContext, Span> spanContextAndSpans = new ConcurrentHashMap<>();

  static {
    out.println("Registering Tracing Exporter");
    final String tracingProvider = System.getenv("TRACING_PROVIDER");

    if (tracingProvider == null) {
      LoggingTraceExporter.register();
    } else {
      switch (tracingProvider) {
        case "ZIPKIN":
          final String zipkinUrl = System.getenv("TRACING_ZIPKIN_URL");
          ZipkinTraceExporter.createAndRegister(zipkinUrl, "oracle-service-bus");
        case "LOGGING":
          LoggingTraceExporter.register();
      }
    }
  }

  public static XmlObject startSpan(String spanName) throws XmlException, SpanContextParseException {
    return startSpan(null, spanName, null);
  }

  public static XmlObject startSpan(XmlObject parentSpanContextXmlObject, String spanName) throws XmlException, SpanContextParseException {
    return startSpan(parentSpanContextXmlObject, spanName, null);
  }

  public static XmlObject startSpan(XmlObject parentSpanContextXmlObject,
                                    String spanName,
                                    XmlObject tagsXmlObject)
      throws SpanContextParseException, XmlException {
    final Tracer tracer = Tracing.getTracer();

    out.println("[Tracer] Starting span: " + spanName);

    final SpanContext parentSpanContext = getSpanContextFromXml(parentSpanContextXmlObject);

    final SpanBuilder spanBuilder =
        tracer
            .spanBuilderWithRemoteParent(spanName, parentSpanContext)
            .setSampler(Samplers.alwaysSample())
            .setRecordEvents(true);


    final Span span = spanBuilder.startSpan();

    if (parentSpanContext != null) {
      span.addLink(Link.fromSpanContext(parentSpanContext, Link.Type.CHILD_LINKED_SPAN));
    }

    addAnnotationsToSpan(span, tagsXmlObject);

    final SpanContextDocument spanContextDocument = SpanContextDocument.Factory.newInstance();
    final TextFormat.Setter<SpanContextDocument> spanContextDocumentSetter =
        new SpanContextDocumentSetter(spanContextDocument);
    final SpanContext spanContext = span.getContext();
    Tracing.getPropagationComponent()
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
    final SpanContext spanContext;

    if (spanContextXmlObject == null) {
      spanContext = null;
    } else {
      final SpanContextDocument spanContextDocument = parseSpanContextXml(spanContextXmlObject);
      spanContext =
          Tracing.getPropagationComponent()
              .getB3Format()
              .extract(spanContextDocument, spanContextDocumentGetter);
    }
    return spanContext;
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

  public static void main(String[] args) throws SpanContextParseException, XmlException {
    XmlObject spanContextXml = OpencensusTracingHelper.startSpan("main");
    System.out.println(spanContextXml);
    OpencensusTracingHelper.endSpan(spanContextXml);
  }

  static class SpanContextDocumentGetter extends TextFormat.Getter<SpanContextDocument> {

    @Nullable
    public String get(SpanContextDocument spanContextDocument, String key) {
      switch (key) {
        case X_B3_TRACE_ID:
          return spanContextDocument.getSpanContext().getTraceId();
        case X_B3_SPAN_ID:
          return spanContextDocument.getSpanContext().getSpanId();
        case X_B3_PARENT_SPAN_ID:
          return spanContextDocument.getSpanContext().getParentSpanId();
        case X_B3_SAMPLED:
          return spanContextDocument.getSpanContext().getSampled();
        case X_B3_FLAGS:
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
      this.spanContextDocument.setSpanContext(this.spanContextDocument.addNewSpanContext());
    }

    @Override
    public void put(SpanContextDocument carrier, String key, String value) {
      switch (key) {
        case X_B3_TRACE_ID:
          spanContextDocument.getSpanContext().setTraceId(value);
        case X_B3_SPAN_ID:
          spanContextDocument.getSpanContext().setSpanId(value);
        case X_B3_PARENT_SPAN_ID:
          spanContextDocument.getSpanContext().setParentSpanId(value);
        case X_B3_SAMPLED:
          spanContextDocument.getSpanContext().setSampled(value);
        case X_B3_FLAGS:
          spanContextDocument.getSpanContext().setFlags(value);
      }
    }
  }
}
