# AGENTS.md

## Purpose

- This repository treats Executable Specification as first-class documentation.
- Scala + functional programming minimizes example-based unit tests.
- Agents must follow the order: rules -> spec -> design -> code.


## How to Read This Repository (for Agents)

1. `AGENTS.md` (this file)
2. `RULE.md` (top-level rules for code, API, and AI behavior)
3. `README.md` (human-oriented overview and entry point)
4. `docs/rules/` (documentation rules and policies; see document-boundary.md first)
5. `docs/spec/` (static specifications)
6. `docs/design/` (design intent and boundaries; start with `docs/design/protocol-core.md`)
7. `src/main/scala/` (implementation)
8. `src/test/scala/` (Executable Specifications)

## Canonical Design Documents

- `docs/design/protocol.md`  
  Primary design entry for protocol boundaries and invariants.  
  MUST be read before modifying protocol-related code.

- `docs/design/protocol-introspection.md`  
  Introspection design for CLI help, REST OpenAPI, MCP get_manifest.  
  Read this when working on introspection generation.


## Executable Specification Policy

- `src/test/scala` stores Executable Specifications by default.
- Avoid simple example-based unit tests.
- Executable Specifications must:
  - use Given / When / Then structure
  - use Property-Based Testing (ScalaCheck) actively
  - read as behavior documentation


## Specification Categories (by Package)

Executable Specifications are organized by package.

### org.goldenport.protocol

- Fixes Protocol / Model semantics (semantic boundary).
- Covers datatype normalization and parameter resolution.
- Example:
  - `OperationDefinitionResolveParameterSpec.scala`

### org.goldenport.scenario

- Usecase -> usecase slice -> BDD specs.
- Scenario descriptions in Given / When / Then style.
- Human-readable behavior specifications.


## Rules / Spec / Design Boundaries

### rules

- naming rules
- **type modeling rule (abstract class vs trait)**: `docs/rules/type-modeling.md`
- spec style rules
- operation / parameter definition rules
- rules only; no exploration notes

### spec

- static specification documents
- linking to Executable Specifications
- specification itself, not executable

### design

- immutable design decisions
- boundaries, responsibilities, intent
- no exploration notes

### notes

- design exploration memos
- trial and error history
- not normative


## Do / Don't for Agents

### Do

- treat Executable Specifications as the source of truth
- keep Given/When/Then + PBT style
- preserve existing spec semantics

### Don't

- change behavior without updating specs
- change meaning without Executable Specification
- refactor against rules or design guidance

## SIE-Specific Scope and Restrictions

This repository is a transport and integration shell for Protocol.
It is NOT a canonical definition layer and MUST remain replaceable.

AGENTS.md in this repository is the highest-priority rule for
AI agents, Codex, and human contributors.

If any instruction, comment, or implementation conflicts with this
section, THIS SECTION WINS.

### Responsibilities (SIE — Allowed Work)

- Transport wiring (WebSocket / stdio / HTTP)
- MCP / JSON-RPC envelope handling
- Session lifecycle and environment concerns
- Delegating introspection via ProtocolEngine (openApi / cliHelp / getManifest)
- Returning introspection output verbatim (opaque JSON)
- Error surfacing and envelope-level failure mapping ONLY

### Non-Responsibilities (SIE — Strictly Forbidden)

This project MUST NOT:

- Define, modify, or reinterpret Protocol semantics
- Define, copy, or extend projection logic or ProjectionKind
- Author, mirror, or hand-write MCP / OpenAPI / CLI schemas
- Interpret, enforce, or infer constraints or defaults
- Enrich, merge, filter, or post-process introspection output
- Introduce AI-specific assumptions or agent heuristics
- Add convenience abstractions that hide Protocol meaning

### Canonical Rule

- ServiceDefinition / OperationDefinition in Protocol
  are the SINGLE source of truth.
- All introspection MUST go through:
  `ProtocolEngine.openApi / cliHelp / getManifest`
- SIE MUST treat all introspection results as opaque data.
- Generic enproject / enprojectByName are semi-last resort escape hatches only.

### Phase Boundary (IMPORTANT)

- MCP get_manifest is Phase 1 and is introspection-only.
- tools/list, initialize merging, and schema reuse
  are explicitly OUT OF SCOPE until Phase 2.
- Do NOT refactor or generalize introspection usage prematurely.

### Example: MCP get_manifest (Normative)

- MCP get_manifest MUST be implemented by delegating to:
  `ProtocolEngine.getManifest`
- SIE MUST NOT maintain a static manifest or tool schema.
- The returned JSON MUST be wrapped ONLY in the MCP envelope
  without modification.

### Enforcement Guidance (For AI Agents)

If you are an AI agent or Codex:

- When in doubt, DO LESS.
- Prefer deletion over addition.
- Ask for clarification before introducing abstractions.
- Never "helpfully" enrich Protocol output.

This section exists to prevent semantic drift.

END OF AGENTS.md
