package org.javai.springai.actions.internal.instrument;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PayloadAugmentor that replaces email addresses with synthetic tokens and records
 * reversible mappings in a {@link TokenStore}.
 */
public class PiiTokenizingAugmentor implements PayloadAugmentor {

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

	private final TokenStore tokenStore;
	private final Function<String, String> tokenGenerator;

	public PiiTokenizingAugmentor(TokenStore tokenStore) {
		this(tokenStore, counterTokenGenerator("user", "example.test"));
	}

	public PiiTokenizingAugmentor(TokenStore tokenStore, String tokenPrefix, String tokenDomain) {
		this(tokenStore, counterTokenGenerator(tokenPrefix, tokenDomain));
	}

	public PiiTokenizingAugmentor(TokenStore tokenStore, Function<String, String> tokenGenerator) {
		this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore must not be null");
		this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
	}

	@Override
	public AugmentedPayload augment(String name, AugmentedPayload payload) {
		Objects.requireNonNull(payload, "payload must not be null");
		Matcher matcher = EMAIL_PATTERN.matcher(payload.content());
		if (!matcher.find()) {
			return payload;
		}

		StringBuffer buffer = new StringBuffer();
		Map<String, String> tokenMap = new LinkedHashMap<>();
		matcher.reset();
		while (matcher.find()) {
			String original = matcher.group();
			String token = tokenStore.findTokenForOriginal(original)
					.or(() -> Optional.ofNullable(tokenGenerator.apply(original)))
					.orElse(original);

			tokenStore.put(original, token);
			tokenMap.put(original, token);
			matcher.appendReplacement(buffer, Matcher.quoteReplacement(token));
		}
		matcher.appendTail(buffer);

		Map<String, Object> meta = new LinkedHashMap<>(payload.metadata());
		meta.put("piiTokens", Map.copyOf(tokenMap));
		return new AugmentedPayload(buffer.toString(), Map.copyOf(meta));
	}

	private static Function<String, String> counterTokenGenerator(String prefix, String domain) {
		AtomicInteger counter = new AtomicInteger();
		return original -> prefix + "-" + counter.getAndIncrement() + "@" + domain;
	}
}

