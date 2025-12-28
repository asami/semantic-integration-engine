# SIE Protocol Design
Semantic Integration Engine – Protocol Architecture

---

## 1. Purpose

The SIE Protocol defines the **semantic contract** of the Semantic Integration Engine (SIE).

Its purpose is to describe *what SIE can do* in a single, authoritative place, so that multiple external interfaces—such as **CLI, REST, MCP (JSON-RPC), and ChatGPT (WebSocket)**—can operate consistently by referencing the same semantic definitions.

This protocol **does not define transport or communication mechanics**.  
It defines **meaning**, not wiring.

---

## 2. Core Idea

### 2.1 Semantic-First Protocol

SIE Protocol is based on the following principles:

- CLI / REST / JSON-RPC / WebSocket are *surface protocols*
- SIE defines **semantic operations**
- Adapters must not interpret semantics

Protocols depend on semantics.  
Semantics never depend on protocols.

---

### 2.2 Single Source of Truth

The following elements are defined **only once** in the SIE Protocol:

- `ServiceDefinition`
- `OperationDefinition`
- `ParameterDefinition`
- `OperationRequest`

They are centralized in the protocol layer (e.g. `Protocol.scala`) and are *referenced*, never duplicated, by adapters.

---

## 3. Architectural Position

SIE Protocol sits at the core of the application architecture.

```
[ CLI Adapter ]      [ REST Adapter ]      [ MCP Adapter ]
       |                    |                    |
       +----------> OperationRequest <-----------+
                          |
                  [ SIE Execution Layer ]
```

All adapters converge on `OperationRequest`.

---

## 4. Main Concepts

### 4.1 ServiceDefinition

- Represents a semantic service provided by SIE
- Groups related operations under a single name

Example:

```scala
ServiceDefinition(
  name = "sie",
  operations = SieOperations.definitions
)
```

---

### 4.2 OperationDefinition

- Represents a semantic operation
- Independent of CLI, REST, or MCP
- Defines input, output, and semantic interpretation

An `OperationDefinition` describes *what the operation means*,  
not *how it is invoked*.

---

### 4.3 ParameterDefinition

- Defines semantic parameters of an operation
- Expresses intent (argument, property, etc.)
- Retrieval mechanism is adapter responsibility

---

### 4.4 OperationRequest

- A fully resolved, executable semantic input
- Common across all protocols

`OperationRequest` is the **only input** to the SIE execution layer.

---

## 5. createOperationRequest  
### The Single Entry Point for Semantic Interpretation

`createOperationRequest` is the **only semantic interpretation hook** provided by `OperationDefinition`.

Responsibilities:

- Resolve parameters from `Request`
- Validate multiplicity and presence
- Construct an `OperationRequest`

Non-responsibilities:

- Execution
- Side effects
- Protocol-specific logic

---

## 6. Protocol Adapters

Each protocol adapter has a strictly limited role.

### CLI Adapter
- Convert `argv` / flags into `Request`

### REST Adapter
- Convert HTTP requests into `Request`

### MCP Adapter
- Convert JSON-RPC parameters into `Request`

### ChatGPT Adapter
- Convert tool/function calls into `Request`

All adapters:
- Reference `OperationDefinition`
- Do not interpret semantics

---

## 7. Position of CliEngine

`CliEngine` is one concrete protocol adapter.

Responsibilities:

- Application-facing entry point
- Accept `ServiceDefinitionGroup`
- Build `OperationRequest` via `makeRequest`

Explicitly out of scope:

- Execution
- Business logic
- Semantic interpretation

---

## 8. Design Principles

1. Semantic First  
2. Protocol Independence  
3. Single Source of Truth  
4. Executable Specification  
5. Thin Adapters  

---

## 9. Future Extensions

All future capabilities are derived from `OperationDefinition`:

- OpenAPI / JSON Schema generation
- MCP schema generation
- ChatGPT tool schema generation
- Protocol capability discovery

---

## 10. Summary

SIE Protocol is:

- Not a transport protocol
- Not an API definition
- A **semantic protocol**

CLI, REST, MCP, and ChatGPT are merely **projections** of SIE Protocol.

The protocol defines *meaning once*,  
and every interface follows.
