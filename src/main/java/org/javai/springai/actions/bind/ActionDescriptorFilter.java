package org.javai.springai.actions.bind;

public interface ActionDescriptorFilter {
	boolean include(ActionDescriptor descriptor);

	ActionDescriptorFilter ALL = descriptor -> true;
}

