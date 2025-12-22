package org.javai.springai.dsl.act;

public interface ActionDescriptorFilter {
	boolean include(ActionDescriptor descriptor);

	ActionDescriptorFilter ALL = descriptor -> true;
}

