# LLM Tuning Examples

This package contains working examples demonstrating the LLM tuning framework in action.

## Overview

The tuning framework allows you to systematically evaluate and optimize LLM configurations across different scenarios. Each example is implemented as a Spring `CommandLineRunner` bean that:

1. Defines test cases and configurations to evaluate
2. Runs a tuning experiment
3. Generates comprehensive reports (HTML, JSON, CSV, recommendations)
4. Exits cleanly

## Running the Examples

### Prerequisites

The tuning examples require:
1. **OpenAI API Key** – Set the environment variable: `export OPENAI_API_KEY=sk-...`
2. **Project built** – Run: `./gradlew build`

⚠️ **Important:** When tuning examples are enabled, they will execute immediately on application startup. This requires network access to OpenAI's API and will consume API credits for each query and experiment run.

### Run a Tuning Example

```bash
# Ensure the API key is set
export OPENAI_API_KEY=sk-your-key-here

# Run the tuning example
./gradlew bootRun --args='--app.tuning-example.enabled=true'
```

The application will:
1. Start the Spring Boot context
2. Load the tuning example (StarSchemaQueryScenarioTuningExample)
3. Execute the tuning experiment
4. Generate reports to `build/tuning/<scenario-name>/`
5. Exit cleanly

### Output

When running with `--app.tuning-example.enabled=true`, you'll see console output like:

```
=======================================================================
TUNING EXPERIMENT RESULTS: star-schema-query-tuning
=======================================================================

📈 Best Configuration:
   Average Score: 0.785
   Temperature: 0.2
   Top P: 0.95
   System Prompt (snippet): You are an expert data engineer...

📋 All Configurations:
   [✓] Avg Score: 0.785 | Temp: 0.20 | TopP: 0.95
   [ ] Avg Score: 0.765 | Temp: 0.10 | TopP: 0.95
   [ ] Avg Score: 0.712 | Temp: 0.50 | TopP: 0.95
   [ ] Avg Score: 0.758 | Temp: 0.20 | TopP: 0.70

✅ Tuning experiment completed!
📊 Reports generated to: /path/to/build/tuning/star-schema-query-tuning
```

Reports are written to: `build/tuning/star-schema-query-tuning/`
- `summary.csv` – Quick reference table
- `detailed_results.json` – Full experiment data
- `report.html` – Interactive dashboard
- `recommendation.txt` – Best config + rationale

### Available Examples

#### StarSchemaQueryScenarioTuningExample

Demonstrates the tuning framework applied to the Star Schema Query scenario, which translates natural-language questions into SQL queries for a data warehouse.

**What it does:**
- Evaluates 4 different LLM configurations (baseline + 3 parameter variants)
- Tests against 3 representative queries (easy, medium, hard)
- Runs 2 iterations per config for averaging (accounts for LLM stochasticity)
- Generates quality scores across syntactic correctness, semantic relevance, efficiency, and safety

**Output artifacts:**
- `build/tuning/star-schema-query-tuning/summary.csv` – Summary table of all configs and scores
- `build/tuning/star-schema-query-tuning/detailed_results.json` – Full experiment data in JSON
- `build/tuning/star-schema-query-tuning/report.html` – Interactive HTML dashboard
- `build/tuning/star-schema-query-tuning/recommendation.txt` – Best configuration + rationale

**To run:**
```bash
./gradlew bootRun --args='--spring.profiles.active=tuning-example'
```

## Creating Your Own Tuning Example

To create a new tuning example:

1. **Implement `ScenarioPlanSupplier`** in your scenario test class:
   ```java
   public class MyScenarioTest implements ScenarioPlanSupplier {
       @Override
       public String scenarioId() { return "my-scenario"; }
       
       @Override
       public String description() { 
           return "My scenario description for prompt tuners..."; 
       }
       
       @Override
       public LlmTuningConfig defaultConfig() { /* ... */ }
       
       @Override
       public PlanSupplier planSupplier(LlmTuningConfig config) { /* ... */ }
   }
   ```

2. **Create a `CommandLineRunner` component** in this package:
   ```java
   @Component
   @ConditionalOnProperty(name = "app.tuning-example.enabled", havingValue = "true")
   public class MyScenarioTuningExample implements CommandLineRunner {
       private final ScenarioPlanSupplier scenario;
       
       public MyScenarioTuningExample(ScenarioPlanSupplier scenario) {
           this.scenario = scenario;
       }
       
       @Override
       public void run(String... args) throws Exception {
           // Define test cases
           List<PlanTestCase> testCases = List.of(/* ... */);
           
           // Define configs to evaluate
           List<LlmTuningConfig> configs = List.of(/* ... */);
           
           // Create and execute experiment
           TuningExperiment experiment = new TuningExperiment(
               "my-scenario-tuning",
               configs,
               testCases,
               2  // runs per config
           );
           
           TuningExecutor executor = new DefaultTuningExecutor(evaluator, factory);
           var result = executor.execute(experiment);
           
           // Generate reports
           TuningReportGenerator generator = new TuningReportGenerator();
           generator.generateReport(result, Paths.get("build/tuning/my-scenario-tuning"));
       }
   }
   ```

3. **Register the scenario as a bean** in `TuningExampleConfiguration`:
   ```java
   @Bean
   @ConditionalOnProperty(name = "app.tuning-example.enabled", havingValue = "true")
   public ScenarioPlanSupplier myScenario() {
       try {
           Class<?> testClass = Class.forName("org.javai.springai.scenarios.MyScenarioTest");
           return (ScenarioPlanSupplier) testClass.getConstructor().newInstance();
       } catch (Exception e) {
           throw new IllegalStateException("Failed to load MyScenarioTest", e);
       }
   }
   ```

4. **Run it:**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=tuning-example'
   ```

## Configuration

Tuning examples are disabled by default. To enable them, either:

### Option 1: Command-line argument
```bash
./gradlew bootRun --args='--app.tuning-example.enabled=true'
```

### Option 2: application.yml
```yaml
app:
  tuning-example:
    enabled: true
```

### Option 3: Environment variable
```bash
export APP_TUNING_EXAMPLE_ENABLED=true
./gradlew bootRun
```

## Quality Evaluators

Each tuning example includes a `PlanQualityEvaluator` implementation that scores generated plans across multiple dimensions:

- **Syntactic Correctness**: Does the plan parse without errors?
- **Semantic Relevance**: Does the plan address the user's intent?
- **Efficiency**: Is the plan well-optimized?
- **Safety**: Are there any obvious security issues?

The overall score is a weighted average of these metrics. Customize the evaluator to match your scenario's needs.

## Output Reports

After each experiment, four artifact files are generated:

### 1. summary.csv
Quick reference table with one row per configuration.

### 2. detailed_results.json
Full experiment data including individual test run results, scores, and metadata.

### 3. report.html
Visual dashboard highlighting the best configuration and comparative performance.

### 4. recommendation.txt
Human-readable recommendation for which configuration to adopt, with rationale.

---

For more details on the tuning framework, see `src/main/java/org/javai/springai/actions/tuning/README.md`.

