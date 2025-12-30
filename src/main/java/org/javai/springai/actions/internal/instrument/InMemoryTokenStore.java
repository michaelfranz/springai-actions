package org.javai.springai.actions.internal.instrument;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTokenStore implements TokenStore {

	private final Map<String, String> originalToToken = new ConcurrentHashMap<>();
	private final Map<String, String> tokenToOriginal = new ConcurrentHashMap<>();

	@Override
	public Optional<String> findTokenForOriginal(String original) {
		if (original == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(originalToToken.get(original));
	}

	@Override
	public Optional<String> findOriginalForToken(String token) {
		if (token == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(tokenToOriginal.get(token));
	}

	@Override
	public void put(String original, String token) {
		if (original == null || token == null) {
			return;
		}
		originalToToken.putIfAbsent(original, token);
		tokenToOriginal.putIfAbsent(token, original);
	}

	@Override
	public Map<String, String> snapshot() {
		return Map.copyOf(originalToToken);
	}
}

