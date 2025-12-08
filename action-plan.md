# Universal action language for agentic applications

I have in mind a solution to problem commonly encountered in agentic application development. That is the articulation of a spec when requesting a response from an LLM with a specific structure. To-date, JSON schema has been the go-to format for expressing a desired structure. But this format has several disadvantages. First of all, for non-trivial type structures, the JSON schema can be quite large, even comprising several tens of kilobytes. This must at some point be serialised into tokens, and this costs money. Furthermore, such large and frequently recursive structures are no easily processed by transformer architectures. The result is greater likelihood of incorrectly formed objects and hallucination.

But there are problems beyond token costs, accuracy and latency. The suite of actions, which we may want an LLM to articulate in a plan may be huge. And if an application has any kind of 'plugin' architecture, then we may not even know all the possible actions until runtime. How hard is it, then, to piece together a system prompt, which includes allthe specs from all the different application parts? The answer: unnecessarily hard.   

I will now articulate a highly general architecture, which solves the following problems:
- how to construct a compact spec comprising precisely the specs that are needed at any given LLM invocation
- how to allow different pieces of an application to focus on their own actions, without worrying about how and when the specs of those actions are communicated to the LLM
- how to create, uniformly and efficiently, typed objects; so the plans that LLMs create, when instantiated locally, contain typed objects as opposed to strings.

# Solution Overview

## S-Expressions

Central to the solution is a grammar based on a class of languages called S-Expressions. Note only are these expressions very powerful (you can create whole computer languages using them, Lisp being one of the earliest examples), they can be processed easily using a very simple type of parser known as LL(1). The form is basically always the same:

 (expr 
    (expr ...)
    (expr (expr ...)))

where expr is either a so-called 'atom' or a 'list', each being defined as follows:

expr = atom | list
atom = symbol | number | string
list = "(" expr* ")"
symbol = [A-Za-z_0-9._:/-]+
string = " ... "
number = 123 | 123.4

And that, quite literally, is the entire grammar of S-expressions.

# Domain-specific S-Expressions

Making S-Expressions useful in a particular setting is a question of limiting the range of acceptable symbols to a well-defined set, plus adding additional production rules for the grammar, which enable us to control when certain symbols can be used.

For example, here is an s-expression grammar for articulating a plan of action:

plan = "(" planStep* ")"
planStep = (planStepDescription action)
planStepDescription = string
action = (actionName argument*)
actionName = string
argument = expr

And here is an instance of this grammar:

(
("Fetch database data" ("performQuery" (<query s-expression>)))
("Show query results in table" ("displayLastQueryResult" ()))
)

And here is an s-expression for articulating a query against a database:

query = ...

# Nesting S-Expressions

S-Expressions are inherently recursive and so nesting is simplicity itself. We saw this when we inserted the "query" s-expression into the action plan. And for any given action plan, we could imagine inserting any number of s-expressions. We could even insert an entire plan within a plan step.

# The spec problem

As stated previously, an agentically enabled application may comprise tens or even hundreds of potential operations that an agent can perform. Collectively, these actions represent a kind of vocabulary, which - of an LLM is going to create a plan - somehow has to know about. But here's the problem: Such a specification has the potential to be huge
, and so we need to manage it in a way that minimises the cost of serialisation and processing.

# Piece-meal delivery of specs

The solution is to articulate a spec for each action, and then to deliver them piece-meal to the LLM. To do this, we make use of tools, which are described to the LLM as sources of spec information. When an LLM needs a spec, it asks the source for it. It then uses the spec to create an object, which is subsequently returned to the application. Contrast this with supplying the entire spec at once - at which point we do not even know which actions will be invoked.  










