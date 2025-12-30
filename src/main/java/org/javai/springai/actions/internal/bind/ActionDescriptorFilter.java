package org.javai.springai.actions.internal.bind;

public interface ActionDescriptorFilter {
	boolean include(ActionDescriptor descriptor);

	ActionDescriptorFilter ALL = descriptor -> true;
}

