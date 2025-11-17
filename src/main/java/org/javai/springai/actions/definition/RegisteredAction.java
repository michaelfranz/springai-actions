package org.javai.springai.actions.definition;


import java.lang.reflect.Method;

public record RegisteredAction(
		Object bean,
		Method method
) {}
