package org.javai.springai.actions.tuning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TuningReportGenerator {

	public void generateReport(
			TuningExperimentResult result,
			Path outputDirectory
	) throws IOException {
		// 1. Summary CSV
		generateSummaryCSV(result, outputDirectory.resolve("summary.csv"));

		// 2. Detailed results JSON
		generateDetailedJSON(result, outputDirectory.resolve("detailed_results.json"));

		// 3. HTML visualization
		generateHTMLReport(result, outputDirectory.resolve("report.html"));

		// 4. Recommendation
		generateRecommendation(result, outputDirectory.resolve("recommendation.txt"));
	}

	private void generateHTMLReport(TuningExperimentResult result, Path path) throws IOException {
		Set<String> difficultyKeys = collectDifficultyKeys(result);
		ConfigPerformance best = result.getBestPerformance();

		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\" />\n<title>")
				.append(escapeHtml(result.experimentName()))
				.append("</title>\n<style>")
				.append("body{font-family:system-ui,-apple-system,BlinkMacSystemFont,sans-serif;margin:2rem;}")
				.append("table{border-collapse:collapse;width:100%;margin-top:1rem;}")
				.append("th,td{border:1px solid #dee2e6;padding:0.6rem;text-align:left;font-size:0.95rem;}")
				.append("thead{background:#f5f5f5;}")
				.append("tr.best{background:#f0fff4;}")
				.append(".recommendation{margin-top:2rem;padding:1.25rem;border:1px solid #cbd5e1;border-radius:0.5rem;}")
				.append(".recommendation h2{margin-top:0;}")
				.append("</style>\n</head>\n<body>\n");

		sb.append("<h1>Tuning Experiment: ").append(escapeHtml(result.experimentName())).append("</h1>\n");
		sb.append("<p>Executed at ").append(escapeHtml(result.executedAt().toString())).append("</p>\n");

		sb.append("<table>\n<thead>\n<tr>")
				.append("<th>Config</th><th>Temperature</th><th>Top P</th><th>Average Score</th>");

		for (String key : difficultyKeys) {
			sb.append("<th>").append(escapeHtml(capitalize(key))).append(" Score</th>");
		}
		sb.append("</tr>\n</thead>\n<tbody>\n");

		for (ConfigPerformance performance : result.configPerformances()) {
			Map<String, Double> breakdown = performance.breakdown();
			boolean isBest = performance == best;
			sb.append("<tr").append(isBest ? " class=\"best\"" : "").append(">");
			sb.append("<td>").append(escapeHtml(snippet(performance.config().systemPrompt()))).append("</td>");
			sb.append("<td>").append(renderHtmlNumber(performance.config().temperature())).append("</td>");
			sb.append("<td>").append(renderHtmlNumber(performance.config().topP())).append("</td>");
			sb.append("<td>").append(renderHtmlScore(performance.averageScore())).append("</td>");
			for (String key : difficultyKeys) {
				Double value = breakdown == null ? null : breakdown.get(key);
				sb.append("<td>").append(value == null ? "" : renderHtmlScore(value)).append("</td>");
			}
			sb.append("</tr>\n");
		}
		sb.append("</tbody>\n</table>\n");

		sb.append("<div class=\"recommendation\">\n<h2>Recommended Configuration</h2>\n");
		sb.append("<p><strong>System prompt:</strong> ").append(escapeHtml(snippet(best.config().systemPrompt()))).append("</p>\n");
		sb.append("<p><strong>Temperature:</strong> ").append(renderHtmlNumber(best.config().temperature())).append("<br/>");
		sb.append("<strong>Top P:</strong> ").append(renderHtmlNumber(best.config().topP())).append("<br/>");
		sb.append("<strong>Average score:</strong> ").append(renderHtmlScore(best.averageScore())).append("</p>\n");

		Map<String, Double> bestBreakdown = best.breakdown();
		if (bestBreakdown != null && !bestBreakdown.isEmpty()) {
			sb.append("<ul>\n");
			bestBreakdown.forEach((difficulty, score) -> sb.append("<li>")
					.append(escapeHtml(capitalize(difficulty)))
					.append(": ")
					.append(renderHtmlScore(score))
					.append("</li>\n"));
			sb.append("</ul>\n");
		}
		sb.append("</div>\n</body>\n</html>");

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private void generateDetailedJSON(TuningExperimentResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"experimentName\": \"").append(escapeJson(result.experimentName())).append("\",\n");
		sb.append("  \"executedAt\": \"").append(escapeJson(result.executedAt().toString())).append("\",\n");
		sb.append("  \"configPerformances\": [\n");

		List<ConfigPerformance> performances = result.configPerformances();
		for (int i = 0; i < performances.size(); i++) {
			ConfigPerformance performance = performances.get(i);
			sb.append("    {\n");
			sb.append("      \"config\": {\n");
			sb.append("        \"systemPrompt\": \"").append(escapeJson(nullToEmpty(performance.config().systemPrompt()))).append("\",\n");
			sb.append("        \"temperature\": ").append(formatJsonNumber(performance.config().temperature())).append(",\n");
			sb.append("        \"topP\": ").append(formatJsonNumber(performance.config().topP())).append("\n");
			sb.append("      },\n");
			sb.append("      \"averageScore\": ").append(formatJsonNumber(performance.averageScore())).append(",\n");
			sb.append("      \"breakdown\": ").append(formatJsonMap(performance.breakdown())).append(",\n");
			sb.append("      \"testResults\": [\n");

			List<PlanTestResult> testResults = performance.queryResults();
			for (int j = 0; j < testResults.size(); j++) {
				PlanTestResult testResult = testResults.get(j);
				sb.append("        {\n");
				sb.append("          \"testCase\": {\n");
				sb.append("            \"id\": \"").append(escapeJson(nullToEmpty(testResult.testCase().id()))).append("\",\n");
				sb.append("            \"description\": \"").append(escapeJson(nullToEmpty(testResult.testCase().description()))).append("\",\n");
				sb.append("            \"difficulty\": \"").append(testResult.testCase().difficulty()).append("\",\n");
				sb.append("            \"userInput\": \"").append(escapeJson(nullToEmpty(testResult.testCase().userInput()))).append("\"\n");
				sb.append("          },\n");
				sb.append("          \"qualityScore\": ");
				appendQualityScore(sb, testResult.qualityScore(), 10);
				sb.append(",\n");
				sb.append("          \"executionTimeMillis\": ").append(formatDuration(testResult.executionTime())).append(",\n");
				appendError(sb, testResult.error());
				sb.append("\n        }");
				if (j < testResults.size() - 1) {
					sb.append(",");
				}
				sb.append("\n");
			}

			sb.append("      ]\n");
			sb.append("    }");
			if (i < performances.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
		}
		sb.append("  ]\n");
		sb.append("}\n");

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private void generateSummaryCSV(TuningExperimentResult result, Path path) throws IOException {
		Set<String> difficultyKeys = collectDifficultyKeys(result);

		StringBuilder sb = new StringBuilder();
		sb.append("system_prompt_snippet,temperature,top_p,average_score");
		for (String key : difficultyKeys) {
			sb.append(",").append(key).append("_score");
		}
		sb.append(System.lineSeparator());

		for (ConfigPerformance performance : result.configPerformances()) {
			Map<String, Double> breakdown = performance.breakdown();
			sb.append(csvQuote(snippet(performance.config().systemPrompt()))).append(",");
			sb.append(formatCsvDecimal(performance.config().temperature())).append(",");
			sb.append(formatCsvDecimal(performance.config().topP())).append(",");
			sb.append(formatScoreValue(performance.averageScore()));

			for (String key : difficultyKeys) {
				sb.append(",");
				Double bucketScore = breakdown == null ? null : breakdown.get(key);
				if (bucketScore != null) {
					sb.append(formatScoreValue(bucketScore));
				}
			}
			sb.append(System.lineSeparator());
		}

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private void generateRecommendation(TuningExperimentResult result, Path path) throws IOException {
		ConfigPerformance best = result.getBestPerformance();
		StringBuilder sb = new StringBuilder();
		sb.append("RECOMMENDED CONFIGURATION\n");
		sb.append("========================\n\n");
		sb.append("System Prompt:\n").append(best.config().systemPrompt()).append("\n\n");
		sb.append("Temperature: ").append(best.config().temperature()).append("\n");
		sb.append("TopP: ").append(best.config().topP()).append("\n\n");
		sb.append("Average Quality Score: ").append(String.format("%.2f%%", best.averageScore() * 100)).append("\n");
		sb.append("Performance by Difficulty:\n");
		best.breakdown().forEach((k, v) ->
				sb.append("  ").append(k).append(": ").append(String.format("%.2f%%", v * 100)).append("\n")
		);

		Files.write(path, sb.toString().getBytes());
	}

	private void appendError(StringBuilder sb, Throwable throwable) {
		sb.append("          \"error\": ");
		if (throwable == null) {
			sb.append("null");
			return;
		}

		sb.append("{\n");
		sb.append("            \"type\": \"").append(escapeJson(throwable.getClass().getName())).append("\",\n");
		sb.append("            \"message\": \"").append(escapeJson(nullToEmpty(throwable.getMessage()))).append("\"\n");
		sb.append("          }");
	}

	private void appendQualityScore(StringBuilder sb, PlanQualityScore score, int indent) {
		if (score == null) {
			sb.append("null");
			return;
		}

		String indentStr = " ".repeat(indent);
		String inner = " ".repeat(indent + 2);
		sb.append("{\n");
		sb.append(inner).append("\"syntacticCorrectness\": ").append(formatJsonNumber(score.syntacticCorrectness())).append(",\n");
		sb.append(inner).append("\"semanticRelevance\": ").append(formatJsonNumber(score.semanticRelevance())).append(",\n");
		sb.append(inner).append("\"efficiency\": ").append(formatJsonNumber(score.efficiency())).append(",\n");
		sb.append(inner).append("\"safety\": ").append(formatJsonNumber(score.safety())).append(",\n");
		sb.append(inner).append("\"metadata\": ").append(formatJsonMap(score.metadata())).append("\n");
		sb.append(indentStr).append("}");
	}

	private String formatDuration(Duration duration) {
		return duration == null ? "null" : Long.toString(duration.toMillis());
	}

	private Set<String> collectDifficultyKeys(TuningExperimentResult result) {
		Set<String> keys = new LinkedHashSet<>();
		if (result == null || result.configPerformances() == null) {
			return keys;
		}
		for (ConfigPerformance performance : result.configPerformances()) {
			if (performance.breakdown() != null) {
				keys.addAll(performance.breakdown().keySet());
			}
		}
		return keys;
	}

	private String snippet(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String normalized = text.replaceAll("\\s+", " ").trim();
		return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
	}

	private String escapeHtml(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					}
					else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}

	private String csvQuote(String value) {
		if (value == null) {
			return "\"\"";
		}
		String escaped = value.replace("\"", "\"\"");
		return "\"" + escaped + "\"";
	}

	private String renderHtmlNumber(Double value) {
		String formatted = formatDecimal(value);
		return formatted.isEmpty() ? "n/a" : formatted;
	}

	private String renderHtmlScore(Double value) {
		String formatted = formatScoreValue(value);
		return formatted.isEmpty() ? "n/a" : formatted;
	}

	private String formatDecimal(Double value) {
		if (value == null) {
			return "";
		}
		return String.format("%.3f", value);
	}

	private String formatCsvDecimal(Double value) {
		return value == null ? "" : String.format("%.3f", value);
	}

	private String formatScoreValue(Double value) {
		if (value == null) {
			return "";
		}
		return String.format("%.4f", value);
	}

	private String formatJsonNumber(Number number) {
		if (number == null) {
			return "null";
		}
		double value = number.doubleValue();
		if (Double.isFinite(value)) {
			return String.format("%.6f", value);
		}
		return "null";
	}

	private String formatJsonMap(Map<String, Double> map) {
		if (map == null || map.isEmpty()) {
			return "{}";
		}
		StringBuilder sb = new StringBuilder("{");
		int index = 0;
		for (Map.Entry<String, Double> entry : map.entrySet()) {
			if (index++ > 0) {
				sb.append(", ");
			}
			sb.append("\"").append(escapeJson(entry.getKey())).append("\": ")
					.append(formatJsonNumber(entry.getValue()));
		}
		sb.append("}");
		return sb.toString();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String capitalize(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}
}