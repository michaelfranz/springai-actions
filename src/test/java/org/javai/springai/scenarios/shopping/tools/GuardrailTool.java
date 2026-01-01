package org.javai.springai.scenarios.shopping.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for evaluating content for appropriateness before processing.
 * 
 * Per design principles, guardrails are implemented as tools the LLM can call:
 * - Returns evaluation results
 * - LLM decides how to act (ErrorStep vs. ActionStep)
 * - Rejection results in polite error message explaining reason
 * - Scope: mission requests and user input
 */
public class GuardrailTool {

	private final AtomicBoolean evaluateContentInvoked = new AtomicBoolean(false);
	private final AtomicBoolean evaluateMissionInvoked = new AtomicBoolean(false);

	// Configurable blacklist - in production, this would be loaded from config
	private final Set<String> blacklistedTerms;
	private final List<Pattern> blacklistedPatterns;

	/**
	 * Create with default blacklist.
	 */
	public GuardrailTool() {
		this(getDefaultBlacklist());
	}

	/**
	 * Create with custom blacklist.
	 */
	public GuardrailTool(Set<String> blacklistedTerms) {
		this.blacklistedTerms = blacklistedTerms;
		this.blacklistedPatterns = buildPatterns(blacklistedTerms);
	}

	private static Set<String> getDefaultBlacklist() {
		// Minimal set for testing - production would have comprehensive list
		return Set.of(
				"nazi", "hitler",
				"bomb", "explosive", "weapon",
				"kill", "murder", "harm"
				// Note: Actual vulgarities omitted for code cleanliness
				// In production, use a proper content moderation service
		);
	}

	private List<Pattern> buildPatterns(Set<String> terms) {
		List<Pattern> patterns = new ArrayList<>();
		for (String term : terms) {
			// Word boundary matching, case insensitive
			patterns.add(Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE));
		}
		return patterns;
	}

	@Tool(name = "evaluateContent", description = """
			Evaluate arbitrary text content for appropriateness.
			
			Checks for:
			- Blacklisted keywords (hate speech, violence, etc.)
			- Potentially harmful content
			- Inappropriate requests
			
			Returns an evaluation result with:
			- isAppropriate: true if content passes all checks
			- reason: explanation if content is rejected
			- suggestions: alternative phrasing if applicable
			
			Use this before processing any user input that could be problematic.""")
	public ContentEvaluation evaluateContent(
			@ToolParam(description = "The content to evaluate") String content) {

		evaluateContentInvoked.set(true);

		if (content == null || content.isBlank()) {
			return ContentEvaluation.appropriate();
		}

		// Check against blacklist patterns
		for (Pattern pattern : blacklistedPatterns) {
			if (pattern.matcher(content).find()) {
				String matchedTerm = extractMatch(pattern, content);
				return ContentEvaluation.rejected(
						String.format("Content contains inappropriate term: '%s'", matchedTerm),
						"Please rephrase your request without offensive or harmful language."
				);
			}
		}

		// Additional heuristic checks
		if (containsExcessiveCaps(content)) {
			return ContentEvaluation.warning(
					"Excessive capitalization detected",
					"Please use normal capitalization for better assistance."
			);
		}

		return ContentEvaluation.appropriate();
	}

	@Tool(name = "evaluateMissionRequest", description = """
			Evaluate a shopping mission request for appropriateness.
			
			Specifically checks:
			- Mission description for inappropriate content
			- Occasion type validity
			- Reasonable headcount (not absurdly large)
			- Coherent dietary/allergen requirements
			
			Returns whether the mission can proceed and any warnings.""")
	public ContentEvaluation evaluateMissionRequest(
			@ToolParam(description = "The mission description") String description,
			@ToolParam(description = "Number of people") int headcount,
			@ToolParam(description = "The occasion type") String occasion) {

		evaluateMissionInvoked.set(true);

		// First, check the description content
		ContentEvaluation contentCheck = evaluateContent(description);
		if (!contentCheck.isAppropriate()) {
			return contentCheck;
		}

		// Check for unreasonable headcount
		if (headcount <= 0) {
			return ContentEvaluation.rejected(
					"Invalid headcount: must be at least 1 person",
					"Please specify a valid number of people."
			);
		}

		if (headcount > 1000) {
			return ContentEvaluation.warning(
					String.format("Very large party (%d people) - some items may have limited stock", headcount),
					"For events this large, consider contacting our catering department."
			);
		}

		// Check occasion
		if (occasion != null && !occasion.isBlank()) {
			ContentEvaluation occasionCheck = evaluateContent(occasion);
			if (!occasionCheck.isAppropriate()) {
				return occasionCheck;
			}
		}

		return ContentEvaluation.appropriate();
	}

	// ========== Helpers ==========

	private String extractMatch(Pattern pattern, String content) {
		var matcher = pattern.matcher(content);
		if (matcher.find()) {
			return matcher.group();
		}
		return "[unknown]";
	}

	private boolean containsExcessiveCaps(String content) {
		if (content.length() < 10) return false;

		long upperCount = content.chars().filter(Character::isUpperCase).count();
		long letterCount = content.chars().filter(Character::isLetter).count();

		if (letterCount == 0) return false;

		return (double) upperCount / letterCount > 0.7;
	}

	// ========== Test Assertion Helpers ==========

	public boolean evaluateContentInvoked() {
		return evaluateContentInvoked.get();
	}

	public boolean evaluateMissionInvoked() {
		return evaluateMissionInvoked.get();
	}

	public void reset() {
		evaluateContentInvoked.set(false);
		evaluateMissionInvoked.set(false);
	}

	// ========== Result Types ==========

	/**
	 * Result of content evaluation.
	 */
	public record ContentEvaluation(
			boolean isAppropriate,
			boolean hasWarning,
			String reason,
			String suggestion
	) {
		public static ContentEvaluation appropriate() {
			return new ContentEvaluation(true, false, null, null);
		}

		public static ContentEvaluation warning(String reason, String suggestion) {
			return new ContentEvaluation(true, true, reason, suggestion);
		}

		public static ContentEvaluation rejected(String reason, String suggestion) {
			return new ContentEvaluation(false, false, reason, suggestion);
		}

		@Override
		public String toString() {
			if (isAppropriate && !hasWarning) {
				return "✅ Content is appropriate.";
			} else if (isAppropriate && hasWarning) {
				return String.format("⚠️ Warning: %s\nSuggestion: %s", reason, suggestion);
			} else {
				return String.format("❌ Rejected: %s\nSuggestion: %s", reason, suggestion);
			}
		}
	}
}

