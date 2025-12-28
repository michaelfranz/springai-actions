package org.javai.springai.scenarios.stats_app;

import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;

/**
 * Actions for statistical process control and data analysis scenarios.
 */
public class StatsActions {
	private final AtomicBoolean displayControlChartInvoked = new AtomicBoolean(false);
	private final AtomicBoolean exportControlChartToExcelInvoked = new AtomicBoolean(false);
	private final AtomicBoolean evaluateSpcReadinessInvoked = new AtomicBoolean(false);

	@Action(description = """
			Use the user's input to derive the parameters necessary for the application to compute and display a
			control chart. Don't try to create or compute a control chart. Just provide the parameters.
			The measurementConcept MUST be either "force" or "displacement".""")
	public void displayControlChart(
			@ActionParam(description = "The measurement concept to be charted: must be 'force' or 'displacement'") String measurementConcept,
			@ActionParam(description = "Bundle ID like A12345") String bundleId) {
		displayControlChartInvoked.set(true);
	}

	@Action(description = """
			Use the user's input to derive the parameters necessary for the application to compute and export a
			control chart to Excel. Don't try to create or compute a control chart. Just provide the parameters.
			The measurementConcept MUST be either "force" or "displacement".""")
	public void exportControlChartToExcel(
			@ActionParam(description = "The measurement concept to be charted: must be 'force' or 'displacement'", allowedValues = {"force", "displacement"}) String measurementConcept,
			@ActionParam(description = "Bundle ID like A12345", allowedRegex = "[A-Z0-9]+") String bundleId) {
		exportControlChartToExcelInvoked.set(true);
	}

	@Action(description = """
			Use the user's input to derive the parameters necessary for the application to evaluate SPC readiness.
			Don't try to compute SPC readiness. Just provide the parameters.
			The measurementConcept MUST be either "force" or "displacement".""")
	public void evaluateSpcReadiness(
			@ActionParam(description = "The measurement concept to evaluate: must be 'force' or 'displacement'", allowedValues = {"force", "displacement"}) String measurementConcept,
			@ActionParam(description = "Bundle ID like A12345", allowedRegex = "[A-Z0-9]+") String bundleId) {
		evaluateSpcReadinessInvoked.set(true);
	}

	public boolean displayControlChartInvoked() {
		return displayControlChartInvoked.get();
	}

	public boolean exportControlChartToExcelInvoked() {
		return exportControlChartToExcelInvoked.get();
	}

	public boolean evaluateSpcReadinessInvoked() {
		return evaluateSpcReadinessInvoked.get();
	}
}

