package org.javai.springai.actions.api;

/**
 * Describes how an action interacts with shared resources so schedulers can
 * decide whether it may run concurrently with other actions on the same
 * affinity.
 */
public enum Mutability {

	/**
	 * Action performs only read operations. These actions can usually execute in
	 * parallel unless an administrator imposes stricter policies.
	 */
	READ_ONLY,

	/**
	 * Action creates new resources or appends data without touching existing
	 * records. Creation can often be parallelised unless creations share a parent
	 * affinity (e.g. creating invoices under the same account).
	 */
	CREATE,

	/**
	 * Action mutates or deletes existing resources and therefore must typically be
	 * serialized per affinity to avoid race conditions.
	 */
	MUTATE
}

