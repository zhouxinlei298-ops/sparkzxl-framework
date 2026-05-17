package com.github.sparkzxl.log.layout;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import com.github.sparkzxl.core.trace.TraceLayoutFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ServiceLoader;

@Slf4j
@Setter
@Getter
public class ConditionalTraceLayout extends PatternLayout {

    private String traceType = "local";
    private Layout<ILoggingEvent> delegate;

    @Override
    public void start() {
        Layout<ILoggingEvent> resolved = resolveLayout(traceType);
        if (resolved != null) {
            this.delegate = resolved;
            this.delegate.start();
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        if (delegate != null) {
            return delegate.doLayout(event);
        }
        return super.doLayout(event);
    }

    @SuppressWarnings("unchecked")
    private Layout<ILoggingEvent> resolveLayout(String type) {

        for (TraceLayoutFactory factory : ServiceLoader.load(TraceLayoutFactory.class)) {
            if (factory.type().equalsIgnoreCase(type)) {
                try {
                    Class<? extends Layout<?>> layoutClass =
                            (Class<? extends Layout<?>>) Class.forName(factory.layoutClassName());
                    Layout<ILoggingEvent> layout = (Layout<ILoggingEvent>) layoutClass.getConstructor().newInstance();
                    layout.getClass().getMethod("setPattern", String.class).invoke(layout, getPattern());
                    layout.getClass().getMethod("setContext", ch.qos.logback.core.Context.class).invoke(layout, getContext());
                    return layout;
                } catch (Exception e) {
                    log.warn("Failed to create layout for type '{}', falling back to local", type, e);
                    break;
                }
            }
        }

        // fallback: local
        if (!"local".equals(type)) {
            return resolveLayout("local");
        }
        return null;
    }
}
