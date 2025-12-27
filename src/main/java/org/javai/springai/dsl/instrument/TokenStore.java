package org.javai.springai.dsl.instrument;

import java.util.Map;
import java.util.Optional;

public interface TokenStore {

	Optional<String> findTokenForOriginal(String original);

	Optional<String> findOriginalForToken(String token);

	void put(String original, String token);

	Map<String, String> snapshot();
}

