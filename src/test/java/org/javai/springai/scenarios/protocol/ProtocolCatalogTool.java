package org.javai.springai.scenarios.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.internal.instrument.AugmentedPayload;
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationKind;
import org.javai.springai.actions.internal.instrument.InvocationSupport;
import org.javai.springai.actions.internal.instrument.PayloadAugmentor;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Tool providing access to FDX statistical quality protocols.
 */
public class ProtocolCatalogTool {
	private final AtomicBoolean listInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getInvoked = new AtomicBoolean(false);
	private final InvocationEmitter emitter;
	private final PayloadAugmentor augmentor;
	private String lastPath;
	private String lastContent;
	private Map<String, Object> lastMetadata = Map.of();

	public ProtocolCatalogTool(InvocationEmitter emitter, PayloadAugmentor augmentor) {
		this.emitter = Objects.requireNonNull(emitter);
		this.augmentor = Objects.requireNonNull(augmentor);
	}

	@Tool(name = "listProtocols", description = """
			List available statistical quality protocols with their file paths and short descriptions so the model
			can choose the most appropriate one or report that none apply.""")
	public ProtocolCatalog listProtocols() {
		listInvoked.set(true);
		try (var scope = InvocationSupport.start(emitter, InvocationKind.TOOL, "listProtocols")) {
			scope.succeed(Map.of("count", 3));
			emit(InvocationEventType.RESULT_FORWARDED, "listProtocols", scope.invocationId(), Map.of("count", 3));
		}
		return new ProtocolCatalog(new ProtocolEntry[] {
				new ProtocolEntry("/protocols/fdx-2024-standard.md",
						"FDX 2024 standard protocol for SPC readiness, normality check, control limits"),
				new ProtocolEntry("/protocols/fdx-legacy-v1.md", "Legacy FDX v1 protocol (deprecated)"),
				new ProtocolEntry("/protocols/experimental-lab-protocol.md", "Experimental lab-only protocol")
		});
	}

	@Tool(name = "getProtocol", description = "Retrieve the protocol content for a given path.")
	public String getProtocol(String path) {
		getInvoked.set(true);
		lastPath = path;
		String resourcePath = path.startsWith("/") ? path : "/" + path;
		try (var scope = InvocationSupport.start(emitter, InvocationKind.TOOL, "getProtocol")) {
			try (var stream = ProtocolCatalogTool.class.getResourceAsStream(resourcePath)) {
				if (stream == null) {
					scope.fail("not found");
					return "Protocol not found: " + path;
				}
				String raw = new String(stream.readAllBytes()) + "\n\nContact: qa@example.com";
				AugmentedPayload augmented = augmentor.augment("getProtocol", new AugmentedPayload(raw, Map.of("path", path)));
				lastContent = augmented.content();
				lastMetadata = augmented.metadata();
				scope.succeed(Map.of("bytes", raw.length()));
				emit(InvocationEventType.RESULT_FORWARDED, "getProtocol", scope.invocationId(), augmented.metadata());
				return lastContent;
			} catch (Exception ex) {
				scope.fail(ex.getMessage());
				return "Error reading protocol " + path + ": " + ex.getMessage();
			}
		}
	}

	public boolean listInvoked() {
		return listInvoked.get();
	}

	public boolean getInvoked() {
		return getInvoked.get();
	}

	public String lastPath() {
		return lastPath;
	}

	public String lastContent() {
		return lastContent;
	}

	public Map<String, Object> lastMetadata() {
		return lastMetadata;
	}

	private void emit(InvocationEventType type, String name, String invocationId, Map<String, Object> attributes) {
		emitter.emit(InvocationKind.TOOL, type, name, invocationId, null, null, attributes);
	}
}

