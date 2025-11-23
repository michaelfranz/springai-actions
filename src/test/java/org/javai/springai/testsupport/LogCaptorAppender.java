package org.javai.springai.testsupport;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Simple Log4j2 appender that captures log events for assertions in tests.
 * <p>
 * Usage:
 * <pre>
 * try (LogCaptorAppender appender = LogCaptorAppender.create(DefaultTuningExecutor.class, Level.DEBUG)) {
 *     // exercise code that logs
 *     assertThat(appender.messages()).anyMatch(msg -> msg.contains("expected"));
 * }
 * </pre>
 */
public final class LogCaptorAppender extends AbstractAppender implements AutoCloseable {

	private final LoggerContext context;
	private final LoggerConfig loggerConfig;
	private final Level previousLevel;
	private final List<LogEvent> events = new CopyOnWriteArrayList<>();

	private LogCaptorAppender(String name, LoggerContext context, LoggerConfig loggerConfig, Level previousLevel,
			Filter filter, Layout<? extends Serializable> layout) {
		super(name, filter, layout, false, Property.EMPTY_ARRAY);
		this.context = context;
		this.loggerConfig = loggerConfig;
		this.previousLevel = previousLevel;
	}

	public static LogCaptorAppender create(Class<?> loggerClass, Level level) {
		String loggerName = loggerClass.getName();
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);

		if (!loggerConfig.getName().equals(loggerName)) {
			LoggerConfig childConfig = new LoggerConfig(loggerName, level, true);
			configuration.addLogger(loggerName, childConfig);
			loggerConfig = childConfig;
		}

		Level previousLevel = loggerConfig.getLevel();
		loggerConfig.setLevel(level);

		Layout<? extends Serializable> layout = PatternLayout.newBuilder()
				.withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN)
				.build();

		LogCaptorAppender appender = new LogCaptorAppender(
				"LogCaptor-" + System.nanoTime(),
				context,
				loggerConfig,
				previousLevel,
				null,
				layout
		);
		appender.start();
		loggerConfig.addAppender(appender, level, null);
		context.updateLoggers();
		return appender;
	}

	@Override
	public void append(LogEvent event) {
		events.add(event.toImmutable());
	}

	public List<LogEvent> events() {
		return Collections.unmodifiableList(events);
	}

	public List<String> messages() {
		return events.stream()
				.map(e -> e.getMessage().getFormattedMessage())
				.toList();
	}

	@Override
	public void close() {
		stop();
		loggerConfig.removeAppender(getName());
		loggerConfig.setLevel(previousLevel);
		context.updateLoggers();
		events.clear();
	}
}

