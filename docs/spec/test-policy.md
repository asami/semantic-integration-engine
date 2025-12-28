======================================================================
Test Specification — Semantic Integration Engine (SIE)
======================================================================

Status: Normative
Scope: semantic-integration-engine

----------------------------------------------------------------------
1. Purpose
----------------------------------------------------------------------

This document defines the test policy for the
Semantic Integration Engine (SIE).

SIE is an application built on top of CNCF and therefore
combines semantic correctness with execution-level validation.

----------------------------------------------------------------------
2. Test Layer Overview
----------------------------------------------------------------------

SIE adopts a layered testing strategy.

  src/test
    - unit tests
    - working specifications

  src/it/integration
    - execution-level integration tests

  src/it/scenario
    - use case scenarios
    - end-to-end flows

----------------------------------------------------------------------
3. Unit Tests (src/test)
----------------------------------------------------------------------

Unit tests serve as executable working specifications.

Characteristics:
  - ScalaTest AnyWordSpec
  - deterministic where possible
  - Property-Based Testing may be used
  - focus on semantic correctness and data stability

Unit tests run on every CI cycle.

----------------------------------------------------------------------
4. Integration Tests (src/it/integration)
----------------------------------------------------------------------

Integration tests validate:

  - protocol handling (REST, MCP, CLI)
  - component wiring
  - execution context boundaries

These tests may involve external resources
and are executed selectively.

----------------------------------------------------------------------
5. Scenario Tests (src/it/scenario)
----------------------------------------------------------------------

Scenario tests represent use case scenarios.

They follow the conceptual flow:

  use case -> use case scenario -> (optional) BDD style

BDD (Given / When / Then) may be used as a description technique,
but directory structure reflects semantic intent,
not description syntax.

Scenario tests are intended to be readable and explainable.

----------------------------------------------------------------------
6. Use of TDD
----------------------------------------------------------------------

TDD may be applied in refinement phases to:

  - clarify edge cases
  - stabilize error handling
  - prevent regressions

TDD is used as a refinement tool,
not as a primary design driver.

----------------------------------------------------------------------
7. Summary
----------------------------------------------------------------------

- Unit tests define semantic intent
- Integration tests validate execution
- Scenario tests describe use cases
- Test layers are strictly separated

======================================================================
