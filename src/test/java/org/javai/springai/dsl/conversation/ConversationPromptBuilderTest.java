package org.javai.springai.dsl.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationPromptBuilderTest {

	@Test
	void buildsRetryAddendumWithAllContext() {
		ConversationState state = new ConversationState(
				"export a control chart to excel for displacement values",
				List.of(new PendingParamSnapshot("exportControlChartToExcel", "bundleId", "Provide bundle id")),
				Map.of("domainEntity", "displacement"),
				"bundle id is A12345"
		);

		String addendum = ConversationPromptBuilder.buildRetryAddendum(state);

		assertThat(addendum).contains("Retrying planning");
		assertThat(addendum).contains("Original instruction: export a control chart to excel for displacement values");
		assertThat(addendum).contains("Already provided: domainEntity=displacement");
		assertThat(addendum).contains("Pending: bundleId");
		assertThat(addendum).contains("Latest user reply: \"bundle id is A12345\"");
		assertThat(addendum).contains("Use the new reply only if it truly satisfies the pending items");
	}
}

