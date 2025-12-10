package org.javai.springai.sxl.grammar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Generates golden files for each grammar definition in the resources directory.
 * Golden files contain the system prompts that would be generated from each grammar,
 * allowing developers to easily view and verify expected prompt output.
 *
 * Golden files are automatically generated during the build process and are stored in
 * src/test/resources/golden/ for easy access and version control.
 *
 * This approach provides:
 * - Visibility into what system prompts look like
 * - Documentation through example
 * - Easy comparison of prompt changes
 * - Automatic updates as grammars evolve
 */
public class GoldenFileGenerator {

	private static final String GRAMMAR_RESOURCE_DIR = "src/main/resources";
	private static final String GOLDEN_FILE_DIR = "src/test/resources/golden";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final SxlGrammarParser grammarParser;
	private final SxlGrammarPromptGenerator promptGenerator;

	public GoldenFileGenerator() {
		this.grammarParser = new SxlGrammarParser();
		this.promptGenerator = new SxlGrammarPromptGenerator();
	}

	public static void main(String[] args) {
		try {
			GoldenFileGenerator generator = new GoldenFileGenerator();
			generator.generateAllGoldenFiles();
			System.exit(0);
		} catch (Exception e) {
			System.err.println("Failed to generate golden files: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	/**
	 * Discovers all grammar files in resources and generates golden files.
	 */
	public void generateAllGoldenFiles() throws IOException {
		File grammarDir = new File(GRAMMAR_RESOURCE_DIR);
		File goldenDir = new File(GOLDEN_FILE_DIR);

		// Validate grammar directory exists
		if (!grammarDir.exists()) {
			throw new IOException("Grammar resource directory not found: " + grammarDir.getAbsolutePath());
		}

		// Create golden files directory
		if (!goldenDir.exists()) {
			boolean created = goldenDir.mkdirs();
			if (!created) {
				throw new IOException("Could not create golden files directory: " + goldenDir.getAbsolutePath());
			}
			System.out.println("Created golden files directory: " + goldenDir.getAbsolutePath());
		}

		// Process all grammar files
		int count = 0;
		try (Stream<Path> paths = Files.list(grammarDir.toPath())) {
			count = (int) paths.filter(p -> p.getFileName().toString().matches("sxl-meta-grammar-.*\\.yml"))
					.peek(grammarPath -> {
						try {
							generateGoldenFile(grammarPath, goldenDir);
						} catch (IOException e) {
							System.err.println("Error generating golden file for: " + grammarPath);
							e.printStackTrace(System.err);
						}
					})
					.count();
		}

		System.out.println("Successfully generated " + count + " golden files in: " + goldenDir.getAbsolutePath());
	}

	/**
	 * Generates a golden file for a specific grammar.
	 *
	 * @param grammarPath the path to the grammar YAML file
	 * @param goldenDir   the directory to write golden files to
	 */
	private void generateGoldenFile(Path grammarPath, File goldenDir) throws IOException {
		String grammarFileName = grammarPath.getFileName().toString();
		String goldenFileName = grammarFileName
				.replace("sxl-meta-grammar-", "system-prompt-")
				.replace(".yml", ".txt");

		File goldenFile = new File(goldenDir, goldenFileName);

		System.out.println("Generating golden file: " + goldenFileName);

		try (InputStream grammarStream = Files.newInputStream(grammarPath)) {
			// Parse grammar
			SxlGrammar grammar = grammarParser.parse(grammarStream);

			// Generate system prompt
			String promptContent = promptGenerator.generate(grammar);

			// Create golden file content with metadata
			String goldenContent = buildGoldenFileContent(grammarFileName, promptContent);

			// Write golden file
			try (FileWriter writer = new FileWriter(goldenFile, StandardCharsets.UTF_8)) {
				writer.write(goldenContent);
			}

			System.out.println("  ✓ Generated: " + goldenFile.getAbsolutePath());
		}
	}

	/**
	 * Builds the golden file content with metadata and system prompt.
	 */
	private String buildGoldenFileContent(String grammarFileName, String promptContent) {

		return """
				================================================================================
				GOLDEN FILE: %s
				================================================================================
				
				Generated: %s
				Purpose: System prompt generated from DSL grammar for LLM instruction
				Usage: This file documents what the system prompt looks like for this DSL
				       It should be committed to version control and updated when grammar changes.
				
				================================================================================
				
				%s
				
				================================================================================
				END OF GOLDEN FILE
				================================================================================
				""".formatted(grammarFileName, LocalDateTime.now().format(TIMESTAMP_FORMAT), promptContent);
	}
}

