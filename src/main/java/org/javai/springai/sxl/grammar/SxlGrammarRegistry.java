package org.javai.springai.sxl.grammar;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for SXL grammars.
 *
 * Applications can use this to register built-in and customer grammars once and
 * access them by DSL id without copy/pasting loader code. Registrations are
 * idempotent per DSL id.
 */
public final class SxlGrammarRegistry {

	private static final Logger logger = LoggerFactory.getLogger(SxlGrammarRegistry.class);

	private static final String UNIVERSAL_GRAMMAR_RESOURCE = "META-INF/sxl-meta-grammar-universal.yml";

	private final Map<String, SxlGrammar> grammars = new LinkedHashMap<>();
	private final SxlGrammarParser parser;

	private SxlGrammarRegistry(SxlGrammarParser parser) {
		this.parser = parser;
	}

	/**
	 * Create an empty registry backed by a fresh parser.
	 */
	public static SxlGrammarRegistry create() {
		return new SxlGrammarRegistry(new SxlGrammarParser());
	}

	/**
	 * Register a grammar object directly. If a grammar with the same DSL id is already
	 * present, the existing one is kept and returned.
	 */
	public SxlGrammar register(SxlGrammar grammar) {
		Objects.requireNonNull(grammar, "grammar must not be null");
		String dslId = grammar.dsl() != null ? grammar.dsl().id() : null;
		if (dslId == null || dslId.isBlank()) {
			throw new IllegalArgumentException("Grammar is missing a DSL id");
		}
		SxlGrammar existing = grammars.get(dslId);
		if (existing != null) {
			logger.debug("Grammar with id '{}' already registered; skipping", dslId);
			return existing;
		}
		grammars.put(dslId, grammar);
		return grammar;
	}

	/**
	 * Load and register the universal grammar if available on the given loader.
	 */
	public SxlGrammar registerUniversal(ClassLoader loader) {
		return registerResource(UNIVERSAL_GRAMMAR_RESOURCE, loader);
	}

	/**
	 * Discover and register grammars found under META-INF on the classpath.
	 * <p>
	 * This scans both exploded directories and JARs for files named
	 * {@code sxl-meta-grammar-*.yml}. The first grammar for a given DSL id wins; later
	 * matches with the same id are ignored.
	 */
	public SxlGrammarRegistry registerMetaInfGrammars(ClassLoader loader) {
		Objects.requireNonNull(loader, "loader must not be null");
		try {
			Enumeration<URL> resources = loader.getResources("META-INF/");
			while (resources.hasMoreElements()) {
				URL url = resources.nextElement();
				if ("file".equalsIgnoreCase(url.getProtocol())) {
					loadFromDirectory(url);
				}
				else if ("jar".equalsIgnoreCase(url.getProtocol())) {
					loadFromJar(url);
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to scan META-INF for grammars", e);
		}
		return this;
	}

	/**
	 * Load a grammar from a classpath resource using this class' loader.
	 */
	public SxlGrammar registerResource(String resourcePath) {
		return registerResource(resourcePath, SxlGrammarRegistry.class.getClassLoader());
	}

	/**
	 * Load and register a grammar from a classpath resource.
	 *
	 * @throws IllegalArgumentException if the resource cannot be found
	 * @throws IllegalStateException if parsing fails
	 */
	public SxlGrammar registerResource(String resourcePath, ClassLoader loader) {
		Objects.requireNonNull(resourcePath, "resourcePath must not be null");
		Objects.requireNonNull(loader, "loader must not be null");
		try (InputStream is = loader.getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IllegalArgumentException("Resource not found: " + resourcePath);
			}
			return register(parser.parse(is));
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load grammar from resource: " + resourcePath, e);
		}
	}

	/**
	 * Load and register a grammar from a filesystem path.
	 *
	 * @throws IllegalArgumentException if the path is null
	 * @throws IllegalStateException if parsing fails
	 */
	public SxlGrammar registerPath(Path path) {
		Objects.requireNonNull(path, "path must not be null");
		try {
			return register(parser.parse(path));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load grammar from path: " + path, e);
		}
	}

	/**
	 * Bulk register grammars from resource paths using the provided loader.
	 */
	public SxlGrammarRegistry registerResources(List<String> resourcePaths, ClassLoader loader) {
		if (resourcePaths == null) {
			return this;
		}
		for (String path : resourcePaths) {
			if (path != null) {
				registerResource(path, loader);
			}
		}
		return this;
	}

	/**
	 * Retrieve a grammar by DSL id.
	 */
	public Optional<SxlGrammar> grammarFor(String dslId) {
		return Optional.ofNullable(grammars.get(dslId));
	}

	/**
	 * Retrieve a grammar by DSL id or throw if not present.
	 */
	public SxlGrammar requireGrammar(String dslId) {
		return grammarFor(dslId).orElseThrow(() -> new IllegalStateException("No grammar registered for DSL id: " + dslId));
	}

	/**
	 * All registered grammars in insertion order.
	 */
	public List<SxlGrammar> grammars() {
		return List.copyOf(grammars.values());
	}

	private void loadFromDirectory(URL url) {
		try {
			Path path = Paths.get(url.toURI());
			if (!Files.isDirectory(path)) {
				return;
			}
			Files.list(path)
					.filter(p -> Files.isRegularFile(p))
					.filter(p -> p.getFileName().toString().startsWith("sxl-meta-grammar-"))
					.filter(p -> p.getFileName().toString().endsWith(".yml"))
					.forEach(p -> {
						try (InputStream is = Files.newInputStream(p)) {
							register(parser.parse(is));
						}
						catch (Exception ex) {
							logger.warn("Failed to load grammar from {}", p, ex);
						}
					});
		}
		catch (Exception e) {
			logger.warn("Failed to scan directory {}", url, e);
		}
	}

	private void loadFromJar(URL url) {
		try {
			JarURLConnection conn = (JarURLConnection) url.openConnection();
			try (JarFile jar = conn.getJarFile()) {
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (entry.isDirectory()) {
						continue;
					}
					String name = entry.getName();
					if (!name.startsWith("META-INF/") || !name.endsWith(".yml") || !name.contains("sxl-meta-grammar-")) {
						continue;
					}
					try (InputStream is = jar.getInputStream(entry)) {
						register(parser.parse(is));
					}
					catch (Exception ex) {
						logger.warn("Failed to load grammar from JAR entry {}", name, ex);
					}
				}
			}
		}
		catch (Exception e) {
			logger.warn("Failed to scan JAR {}", url, e);
		}
	}
}

