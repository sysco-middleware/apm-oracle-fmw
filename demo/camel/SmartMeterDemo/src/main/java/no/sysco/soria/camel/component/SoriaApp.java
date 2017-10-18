package no.sysco.soria.camel.component;

import com.uber.jaeger.Configuration;
import io.opentracing.Tracer;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.main.MainSupport;
import org.apache.camel.opentracing.OpenTracingTracer;
import org.apache.camel.spring.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author SYSCO Middleware <sysco.middleware at sysco.no>
 */
public class SoriaApp {

    static Logger LOG = LoggerFactory.getLogger("file");

    public static void main(String... args) throws Exception {
        Main main = new Main();
        main.setApplicationContextUri("META-INF/spring/camel-context.xml");
        main.addMainListener(new Events());
        LOG.info("Starting Camel...\n");
        main.run();
    }

    public static class Events extends MainListenerSupport {

        @Override
        public void afterStart(MainSupport main) {
            LOG.info("RedBox with Camel is now started!");
            Tracer tracer = new Configuration(
                    "camel-smart-meter",
                    new Configuration.SamplerConfiguration("const", 1),
                    new Configuration.ReporterConfiguration(
                            true,  // logSpans
                            "docker-vm",
                            6831,
                            1000,   // flush interval in milliseconds
                            10000)  /*max buffered Spans*/)
                    .getTracer();
            OpenTracingTracer ottracer = new OpenTracingTracer();
            ottracer.setTracer(tracer);
            main.getCamelContexts().forEach(ottracer::init);
        }

        @Override
        public void beforeStop(MainSupport main) {
            LOG.info("RedBox with Camel is now being stopped!");
        }
    }
}
