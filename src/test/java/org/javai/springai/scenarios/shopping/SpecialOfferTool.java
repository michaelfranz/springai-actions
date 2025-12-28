package org.javai.springai.scenarios.shopping;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Tool providing access to special offers and discounts for the shopping scenario.
 */
public class SpecialOfferTool {
	private final AtomicBoolean listInvoked = new AtomicBoolean(false);

	@Tool(name = "listSpecialOffers", description = "List current special offers and discounts.")
	public String listSpecialOffers() {
		listInvoked.set(true);
		return """
				Today's offers:
				- 10% off Coca Cola (regular)
				- 10% off Coke Zero
				- 5% off mixed nuts (party size)
				""";
	}

	public boolean listInvoked() {
		return listInvoked.get();
	}
}

