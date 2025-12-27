package org.javai.springai.dsl.instrument;

@FunctionalInterface
public interface PayloadAugmentor {

	AugmentedPayload augment(String name, AugmentedPayload payload);
}

