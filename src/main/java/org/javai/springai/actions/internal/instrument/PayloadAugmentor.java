package org.javai.springai.actions.internal.instrument;

@FunctionalInterface
public interface PayloadAugmentor {

	AugmentedPayload augment(String name, AugmentedPayload payload);
}

