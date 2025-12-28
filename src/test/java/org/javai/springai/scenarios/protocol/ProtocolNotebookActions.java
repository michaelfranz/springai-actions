package org.javai.springai.scenarios.protocol;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;

/**
 * Actions for creating protocol-based quality assurance notebooks.
 */
public class ProtocolNotebookActions {
	private final AtomicBoolean invoked = new AtomicBoolean(false);

	public ProtocolNotebookActions() {
	}

	@Action(description = "Add normality test to notebook.")
	public void addNormalityTest(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Normality test");
	}

	@Action(description = "Add statistical readiness to notebook.")
	public void addSpcReadinessTest(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## SPC readiness");
	}

	@Action(description = "Add control chart with control limits to notebook. Use for FDX 2024 standard protocol.")
	public void addSpcControlChart(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Control chart");
	}

	@Action(description = "Add normality spot-check from legacy protocol to notebook.")
	public void addLegacyNormalitySpotCheck(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Normality spot-check");
	}

	@Action(description = "Add minimal SPC readiness checklist from legacy protocol to notebook.")
	public void addLegacySpcReadinessChecklist(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Minimal SPC readiness checklist");
	}

	@Action(description = "Add legacy provisional thresholds to notebook. For Legacy FDX v1 protocol only.")
	public void addLegacyProvisionalThresholds(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Provisional thresholds");
	}

	@Action(description = "Add lab-only data filter from experimental protocol to notebook.")
	public void addExperimentalLabOnlyFilter(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Lab-only data filter");
	}

	@Action(description = "Add experimental distribution fit and residual analysis to notebook.")
	public void addExperimentalDistributionFit(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Experimental distribution fit and residual analysis");
	}

	@Action(description = "Add exploratory lab-only variance chart to notebook. For Experimental protocol only.")
	public void addExperimentalVarianceChart(
			@ActionParam(description = "The type of component", examples = { "bushing", "piston" }) String component,
			@ActionParam(description = "The type of measurement", examples = { "displacement", "force" }) String measurement,
			@ActionParam(description = "The ID of the data bundle containing the measurements", examples = { "A12345", "B6789" }) String bundleId,
			ActionContext context) {
		getOrCreateBuilder(context).addMarkdown("## Exploratory variance chart");
	}

	@Action(description = "Finalize and write the notebook to a file. Call this as the last step after all tests are added.")
	public void writeNotebook(ActionContext context) {
		NotebookBuilder builder =
				Optional.ofNullable(context.get("notebookBuilder", NotebookBuilder.class))
						.orElseThrow(() -> new IllegalStateException("No notebookBuilder in context"));
		Notebook notebook = builder.build();
		context.put("notebook", notebook);
	}

	public boolean invoked() {
		return invoked.get();
	}

	private NotebookBuilder getOrCreateBuilder(ActionContext context) {
		NotebookBuilder builder;
		if (context.contains("notebookBuilder")) {
			builder = context.get("notebookBuilder", NotebookBuilder.class);
		} else {
			builder = new NotebookBuilder();
			context.put("notebookBuilder", builder);
		}
		invoked.set(true);
		return builder;
	}
}

