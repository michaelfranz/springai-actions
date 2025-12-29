package org.javai.springai.actions.instrument;

@FunctionalInterface
public interface PayloadAugmentor {

	AugmentedPayload augment(String name, AugmentedPayload payload);
}

