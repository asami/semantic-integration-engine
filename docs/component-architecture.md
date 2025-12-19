# Component Interaction Contract (Conceptual)

This section records the conceptual interaction model adopted by the
Semantic Integration Engine (SIE) as a preparation layer for future
migration to the Cloud Native Component Framework (CNCF).

The intent is to preserve design decisions discovered through SIE
development, especially around the separation of protocol,
interaction vocabulary, and execution logic.

---------------------------------------------------------------------

## Component Interaction Contract (tentative)

The Component Interaction Contract defines the complete set of
interactions that a component accepts from its external environment.

It specifies:

- Which kinds of interactions are permitted
- The structural and semantic constraints of those interactions
- The boundary at which quality attributes (logging, tracing, retries,
  policies, etc.) are applied before component execution

The contract is intentionally decomposed into two symmetric sub-languages:

    Component Interaction Contract
      ├─ Component Operation Language
      └─ Component Reception Language

---------------------------------------------------------------------

## Component Operation Language (Operation Language)

The Component Operation Language defines the synchronous interaction
surface of a component.

It is a constrained domain-specific language (DSL) used to express:

- Commands
- Queries
- Other request–response style operations

Operation Language expressions are transformed into executable
OperationCall instances. Before invoking the component implementation,
quality attributes are applied (e.g. logging, metrics, tracing, retries,
authorization, policy checks).

This interaction path is used by:

- MCP (ChatGPT / VS Code)
- REST APIs
- CLI-based clients

---------------------------------------------------------------------

## Component Reception Language (Reception Language)

The Component Reception Language defines the asynchronous interaction
surface of a component.

It specifies:

- Which events a component can receive
- The structure and semantics of those events
- How external signals are admitted into the component

Reception Language expressions are mapped to event-driven execution
paths (e.g. message consumers, event handlers), and form the
asynchronous counterpart of OperationCall in the synchronous model.

---------------------------------------------------------------------

## Design Positioning

This interaction model ensures that:

- Synchronous calls and asynchronous events are treated uniformly
- Protocol tiers (MCP, REST, CLI, messaging) remain decoupled from
  component logic
- The execution boundary for quality attributes is explicit and
  inspectable

SIE maintains this model as an internal shadow abstraction.
Once CNCF APIs are stabilized, the following mappings are expected:

- Component Interaction Contract → CNCF component contract model
- Component Operation Language   → CNCF Operation / OperationCall
- Component Reception Language   → CNCF event reception abstractions

This document intentionally focuses on conceptual structure rather than
concrete implementation details, so that it can remain stable as SIE
and CNCF evolve.
