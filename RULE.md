Naming Conventions (2025-12-16)
==================

This document defines the naming conventions used in the programs on this site.

These conventions are introduced to improve the readability of example code.
They are **not intended as general recommendations**, but as project‑specific rules
to keep the codebase consistent and easy to read.

# Class Names

- Start with an uppercase letter
- Use CamelCase

### Example

```
case class PurchaseOrder()
```

# Method Names

Naming rules for methods vary depending on their visibility and scope.

## Public Methods

- Start with a lowercase letter
- Use camelCase

### Example

```
def purchaseOrder(cmd: PurchaseOrder): Consequence[PurchaseResult]
```

## Protected Methods

- Start with a lowercase letter
- Use snake_case

### Example

```
protected def purchase_order(cmd: PurchaseOrder): Consequence[PurchaseResult]
```

## Private Methods

- Start with an underscore (`_`)
- Use snake_case

### Example

```
private def _purchase_order(cmd: PurchaseOrder): Consequence[PurchaseResult]
```

## Method‑Local Helper Methods

- Start and end with an underscore (`_`)
- Use snake_case

### Example

```
def _validate_order_(cmd: PurchaseOrder): Boolean
```

# Variable Names

Naming rules for variables also depend on visibility and scope.

## Public Variables

- Start with a lowercase letter
- Use camelCase

### Example

```
val reservationCount: Int
```

## Protected Variables

- Start with a lowercase letter
- Use snake_case

### Example

```
protected val reservation_count: Int
```

## Private Variables

- Start with an underscore (`_`)
- Use snake_case

### Example

```
private val _reservation_count: Int
```

## Method Parameters

- Start with a lowercase letter
- Use flatcase (all lowercase, no separators)

### Example

```
def x(reservationcount: Int)
```

## Method‑Local Variables

- Start with a lowercase letter
- Use flatcase

### Example

```
val rc: Int = ???
```

## Constants

- Use SCREAMING_SNAKE_CASE

### Example

```
val RESERVATION_COUNT: Int = ???
```

# Naming Aids for API Safety and Stability

This section defines auxiliary naming rules that express **API safety, trust level, and stability**
directly in method and class names.

These name properties act as *lightweight contracts* for readers, reviewers, and tools,
and are especially important at component and componentlet boundaries.

## Visibility-Aware Composition Rule

When safety or stability properties are attached to a method name,
they must follow the visibility-based naming rules already defined.

- **Public methods** use camelCase
- **Protected methods** use snake_case
- **Private methods** use snake_case with a leading underscore
- Name properties are composed as semantic modifiers, not as part of the base verb

### Examples

Public:
```
def experimentalParseUnsafe(input: String): Ast
```

Protected:
```
protected def experimental_parse_unsafe(input: String): Ast
```

Private:
```
private def _experimental_parse_unsafe(input: String): Ast
```

---

## Experimental

### Meaning

- The API contract is not yet stable
- Signature, semantics, or existence may change in future releases

### Naming Rule

Prefix:
```
experimental
```

Snake case:
```
experimental_
```

### Example

```
def experimentalStreamApply(cmd: Command): Result
protected def experimental_stream_apply(cmd: Command): Result
```

---

## Unsafe

### Meaning

- Validation, checks, or error handling are intentionally skipped
- May throw exceptions or cause undefined behavior
- The caller bears full responsibility

### Naming Rule

Suffix:
```
Unsafe
```

Snake case:
```
_unsafe
```

### Requirements

- A safe alternative **must exist** when exposed publicly
- Unsafe behavior must never be hidden behind a neutral name

### Example

```
def parse(input: String): Either[ParseError, Ast]
def parseUnsafe(input: String): Ast
```

```
protected def parse_unsafe(input: String): Ast
```

---

## Unchecked

### Meaning

- Preconditions are assumed to be satisfied
- Typically used for internal or framework-level APIs
- Safer than `Unsafe`, but still trust-based

### Naming Rule

Suffix:
```
Unchecked
```

Snake case:
```
_unchecked
```

### Example

```
protected def load_unchecked(id: Id): Entity
private def apply_unchecked(cmd: ValidatedCommand): Result
```

---

## Composition Order

When multiple name properties are combined, they must appear
in the following order (from strongest to weakest):

```
experimental > unsafe > unchecked
```

### Valid Examples

```
experimentalParseUnsafe
experimentalLoadUnchecked
```

Snake case:
```
experimental_parse_unsafe
experimental_load_unchecked
```

### Invalid Examples

```
unsafeExperimentalParse
uncheckedUnsafeApply
```

---

## Additional Recommended Name Properties

The following properties may be attached to method or class names
when they add clear semantic value.

### Internal

Indicates framework-internal usage and non-public contracts.

```
internalResolveDependency
internal_resolve_dependency
```

### Temporary

Indicates short-lived or transitional code that should be removed.

```
temporaryFixRouting
temporary_fix_routing
```

### Legacy

Indicates deprecated or backward-compatibility behavior.

```
legacyApplyRule
legacy_apply_rule
```

### Derived

Indicates computed or synthesized behavior rather than primary state.

```
derivedStatus
derived_status
```

---

## Design Principles

- Names must clearly communicate **risk, trust, and stability**
- Dangerous behavior must never be implicit
- Naming is part of the API contract, not an implementation detail

# Naming Aids for Concurrency and Execution Model

This section defines naming properties that clarify **how and when a method executes**.
These rules are especially important in concurrent, asynchronous, and cloud-native environments.

## Blocking

### Meaning

- The method may block the calling thread
- Execution time is unbounded or depends on external resources

### Naming Rule

Suffix:
```
Blocking
```

Snake case:
```
_blocking
```

### Example

```
def loadBlocking(id: Id): Entity
protected def load_blocking(id: Id): Entity
```

---

## NonBlocking

### Meaning

- The method does not block the calling thread
- Execution is asynchronous or uses callbacks / futures

### Naming Rule

Suffix:
```
NonBlocking
```

Snake case:
```
_non_blocking
```

### Example

```
def loadNonBlocking(id: Id): Future[Entity]
protected def load_non_blocking(id: Id): Future[Entity]
```

---

## Async / Sync

### Async

#### Meaning

- Execution is asynchronous
- The result is delivered later (Future, IO, Task, etc.)

#### Naming Rule

Suffix:
```
Async
```

Snake case:
```
_async
```

#### Example

```
def fetchAsync(req: Request): Future[Response]
protected def fetch_async(req: Request): Future[Response]
```

### Sync

#### Meaning

- Execution is synchronous
- The result is returned directly

#### Naming Rule

Suffix:
```
Sync
```

Snake case:
```
_sync
```

#### Example

```
def fetchSync(req: Request): Response
protected def fetch_sync(req: Request): Response
```

---

## Idempotent

### Meaning

- Repeated execution produces the same effect
- Safe to retry

### Naming Rule

Suffix:
```
Idempotent
```

Snake case:
```
_idempotent
```

### Example

```
def applyIdempotent(cmd: Command): Result
protected def apply_idempotent(cmd: Command): Result
```

---

# Naming Aids for Semantic and Behavioral Properties

This section defines naming properties that express **semantic or behavioral characteristics**
of methods and values.

## Pure

### Meaning

- No side effects
- Output depends only on input

### Naming Rule

Suffix:
```
Pure
```

Snake case:
```
_pure
```

### Example

```
def normalizePure(input: String): String
protected def normalize_pure(input: String): String
```

---

## Impure

### Meaning

- Has side effects (I/O, mutation, time, randomness)
- Execution may affect external state

### Naming Rule

Suffix:
```
Impure
```

Snake case:
```
_impure
```

### Example

```
def readConfigImpure(): Config
protected def read_config_impure(): Config
```

---

## Cached

### Meaning

- Returns cached data when available
- May not reflect the latest underlying state

### Naming Rule

Suffix:
```
Cached
```

Snake case:
```
_cached
```

### Example

```
def lookupCached(key: Key): Value
protected def lookup_cached(key: Key): Value
```

---

## Memoized

### Meaning

- Results are memoized per input
- Cache lifetime is bound to process or instance

### Naming Rule

Suffix:
```
Memoized
```

Snake case:
```
_memoized
```

### Example

```
def computeMemoized(x: Int): Int
protected def compute_memoized(x: Int): Int
```

---

## Composition Guidance

- Execution-model properties (`Blocking`, `Async`, etc.) should appear **after**
  safety/stability properties
- Semantic properties (`Pure`, `Cached`, etc.) should appear **last**
- Avoid over-composition; names should remain readable

### Example

```
experimentalLoadNonBlockingCached
experimental_load_non_blocking_cached
```

---

## Design Principles (Extended)

- Execution characteristics must be visible at the call site
- Semantic guarantees should not rely on documentation alone
- Naming is an executable form of design intent



# Error Handling and Result Conventions

This section defines conventions for representing errors, failures, and results in the codebase.

## Principle

- **Domain errors** and **expected failures** should be modeled as values, not exceptions.
- Use the `Consequence` type (or similar) to represent recoverable errors and business rule violations.
- Use exceptions only for programming errors, contract violations, or truly exceptional conditions.

## Consequence Type

The `Consequence` type is used to represent the outcome of an operation that may fail.

### Example

```
def reserve(cmd: ReserveCommand): Consequence[Reservation]
```

### Guidelines

- Do not use exceptions for validation failures or business rule violations.
- Use `Consequence` for all domain-level errors.
- Only throw exceptions for contract violations or unrecoverable errors.

---

# Design by Contract (DbC) Conventions

This section defines conventions for applying **Design by Contract (DbC)**
in a pragmatic and lightweight manner.

The goal is to make **assumptions, guarantees, and responsibilities**
explicit, without overusing exceptions or heavy assertion frameworks.

These are **guiding conventions**, not strict enforcement rules.

## Basic Principles

- Contracts describe **expected usage**, not defensive programming
- Violations of contracts indicate **programming errors**, not domain errors
- Domain errors should be represented using `Consequence`, not DbC failures

---

## DbC and Defect Detection

### Clarified Understanding

Yes — in this codebase, **Design by Contract (DbC) is primarily used for defect detection**.

DbC is *not* a general-purpose error-handling mechanism.
Instead, it is used to detect violations that indicate **bugs, incorrect usage, or broken assumptions**
during development and testing.

### What DbC Is Used For

DbC is used to detect:

- Incorrect usage by the caller (precondition violations)
- Broken invariants in domain objects
- Implementation defects inside a method
- Unexpected states that should be impossible by design

These failures typically indicate **defects that must be fixed**, not situations to be recovered from.

### What DbC Is NOT Used For

DbC must NOT be used for:

- Business rule validation
- Expected validation failures
- User input errors
- I/O or external system failures

These cases must be modeled explicitly using `Consequence`.

### Practical Rule of Thumb

- If a failure should lead to **bug fixing**, use DbC (exceptions are acceptable)
- If a failure should lead to **error handling or branching**, use `Consequence`
- If a failure can occur during correct and expected usage, it is **not** a DbC concern

### Design Intent

By restricting DbC to defect detection:

- Contracts remain simple and meaningful
- Exception usage stays limited and intentional
- Domain error handling remains explicit and composable
- Runtime behavior becomes easier to reason about

## Preconditions

### Meaning

- Conditions that must be satisfied by the caller
- Violations indicate incorrect usage by the caller

### Expression

- Use `require` or equivalent checks
- May throw an exception when violated

### Example

```
def transfer(from: Account, to: Account, amount: Money): Consequence[Result] =
  require(amount.isPositive)
  ...
```

### Guideline

- Preconditions should be **simple and cheap**
- Do not use preconditions for business rule validation
- Prefer `Consequence` for recoverable or expected failures

---

## Postconditions

### Meaning

- Conditions guaranteed by the method upon successful completion
- Violations indicate implementation errors

### Expression

- Use `ensure`-like checks or assertions
- May throw an exception if violated

### Example

```
def normalize(value: Int): Int =
  val result = ...
  assert(result >= 0)
  result
```

### Guideline

- Use postconditions sparingly
- Focus on **invariants**, not incidental properties

---

## Invariants

### Meaning

- Conditions that must always hold for an object
- Checked at construction time or at key mutation points

### Expression

- Validate invariants in constructors or factory methods
- May use assertions or validation helpers

### Example

```
case class Percentage(value: Int):
  require(value >= 0 && value <= 100)
```

---

## DbC vs Error Handling

### DbC (Exceptions Allowed)

- Violated preconditions
- Broken invariants
- Internal logic errors

### Consequence (Preferred)

- Validation failures
- Business rule violations
- I/O and external system errors

---

## Naming Conventions Related to DbC

### requireXXX

Indicates that a precondition is being enforced.

```
requireValidState(state)
```

### ensureXXX

Indicates that a postcondition is being checked.

```
ensureNormalized(result)
```

### assertXXX

Indicates internal consistency checks, typically non-public.

```
assertInvariant()
```

---

## Design Rationale

- DbC clarifies **responsibility boundaries**
- Preconditions protect implementations, not callers
- Consequence represents **recoverable domain errors**
- Contracts complement, but do not replace, type-based design

# Protected Method Role Conventions

This section defines **role-based naming conventions for protected methods**.
These rules clarify how a protected method is intended to be used by subclasses.

The goal is to make subclassing intent explicit **from the method name alone**,
without relying on comments or documentation.

## Role Categories for Protected Methods

Protected methods MUST fall into one of the following categories.

---

## 1. Subroutine (Not Intended for Override)

### Intent

- Used as an internal subroutine
- Called by other methods in the same class
- **Not intended to be overridden**

### Naming Rule

- Use snake_case
- Mark as `final`

### Example

```
protected final def do_test(p: XXX): Result
```

### Design Notes

- Declaring the method as `final` enforces the intent
- Subclasses may call this method, but must not override it

---

## 2. Template Method (Override Rare and Discouraged)

### Intent

- Acts as a template method
- Provides a default implementation
- Overriding is possible but **not expected in normal use**

### Naming Rule

- Use snake_case
- Do NOT mark as `final`

### Example

```
protected def do_test(p: XXX): Result
```

### Design Notes

- This is the default choice for most protected extension points
- Overriding should be exceptional and well-justified

---

## 3. Overridable Hook (Override Expected)

### Intent

- Designed explicitly for subclass override
- Represents a customization or extension point

### Naming Rule

- Capitalize the following word to signal override intent

### Example

```
protected def do_Test(p: XXX): Result
```

### Design Notes

- The camelCase form visually distinguishes hooks from subroutines
- Subclasses are expected to override this method

---

## Abstract vs Default Implementation

- If a reasonable default implementation exists,
  the method **must not** be declared abstract
- Abstract protected methods should be used only when
  no meaningful default behavior can be provided

### Rationale

Providing a default implementation:

- Reduces subclass boilerplate
- Preserves backward compatibility
- Allows incremental extension without forcing overrides

---

## Design Principles

- Protected methods must clearly communicate **override intent**
- `final` is part of the semantic contract, not an implementation detail
- Naming conventions are used to express inheritance design explicitly


# Method Naming Conventions for Creation

This section defines naming conventions for **creation-oriented methods**.
These conventions clarify **assumptions, side effects, and reliability**
of object creation from the method name alone.

These are **conventions**, not strict enforcement rules, and may be extended over time.

## apply(...)

### Intended Use

- Used primarily in constructors or companion objects
- Represents the most basic and predictable form of creation

### Guarantees

- Must **never fail** under normal usage
  - Domain errors must not occur
  - Recoverable failures must not be represented
- DbC violations (defects) are the only acceptable failures
- Must NOT call other modules or external services
- Must NOT perform complex or heavy computation
- Must be deterministic and context-independent

### Typical Characteristics

- Pure or nearly pure
- No I/O
- No environment or runtime dependency

### Example

```
def apply(value: Int): Percentage =
  require(value >= 0 && value <= 100)
  new Percentage(value)
```

---

## create(...)

### Intended Use

- Represents standard creation logic
- Used when creation may reasonably fail or involve external effects

### Characteristics

- May involve I/O (database, file system, network, configuration)
- May return `Consequence[T]`, `Either`, or effect types (`IO`, `Future`, etc.)
- Failure is considered part of normal control flow

### Example

```
def create(cmd: CreateOrder): Consequence[Order]
```

```
def createFromStorage(id: Id): IO[Entity]
```

---

## make(...)

### Intended Use

- Represents **best-effort or heuristic-based creation**
- Creation logic relies on assumptions, inference, or contextual interpretation

### Characteristics

- May depend on runtime context, locale, configuration, or heuristics
- Results may vary depending on input interpretation or environment
- Still expected to succeed in most practical cases

### Typical Use Cases

- Language or format inference
- Context-sensitive object construction
- Convenience factories that trade strictness for usability

### Example

```
def makeName(input: String): Consequence[Name]
```

```
def makeText(input: String, context: Context): Text
```

---

## Comparative Summary

| Method | Failure Model | Side Effects | Context Dependence |
|------|---------------|--------------|--------------------|
| apply | No (DbC only) | None | None |
| create | Yes | Possible | Low |
| make | Yes | Possible | High |

---

## Extension Policy

- New creation-related verbs may be introduced when they convey
  a clearly distinct semantic contract
- Any new verb must be documented in this section before use

# Method Naming Conventions for Parsing and Encoding

This section defines naming conventions for **parsing, decoding, and encoding operations**.
These conventions clarify **input assumptions, failure models, and semantic intent**
from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## parse(...)

### Intended Use

- Converts **unstructured or loosely structured input** into a structured model
- Typical inputs:
  - Free-form text
  - User input
  - Configuration text
  - DSL or source-like strings

### Characteristics

- Input may be malformed or invalid
- Failure is **expected and normal**
- Should not rely on strong external context unless explicitly documented

### Error Model

- Must NOT throw exceptions for input-related errors
- Must return `Consequence[T]`, `Either`, or an effect type

### Example

def parse(input: String): Consequence[Ast]

def parseConfig(text: String): Either[ParseError, Config]

### Design Note

- `parse` is about **syntax and structure**
- Domain validation should happen *after* parsing

---

## decode(...)

### Intended Use

- Converts a **well-defined encoded representation** into a domain object
- Typical inputs:
  - Binary formats
  - JSON / XML
  - Protocol or schema-driven data

### Characteristics

- Input is assumed to follow a known format or protocol
- Failures indicate:
  - Invalid or corrupted data
  - Schema or version mismatch
- Often used at **system boundaries**

### Error Model

- Return `Consequence[T]`, `Either`, or effect types
- Exceptions are allowed **only for DbC violations**

### Example

def decode(bytes: Array[Byte]): Consequence[Message]

def decodeJson(json: String): Either[DecodeError, DomainEvent]

### Design Note

- `decode` is about **schema and protocol correctness**
- Business rules do NOT belong here

---

## encode(...)

### Intended Use

- Converts a domain object into a **serialized or transferable representation**
- Used for:
  - Persistence
  - Messaging
  - External communication

### Characteristics

- Should **not fail under normal usage**
- Must NOT perform I/O
- Failures indicate defects or broken invariants

### Error Model

- Returns a plain value
- DbC violations (exceptions) are acceptable
- Must NOT return `Consequence`

### Example

def encode(event: DomainEvent): Array[Byte]

def encodeJson(event: DomainEvent): String

### Design Note

- `encode` assumes the domain object is already valid
- Validation belongs before encoding

---

## Comparative Summary

| Method | Direction | Failure Expected | Error Model |
|------|-----------|------------------|-------------|
| parse | Text → Model | Yes | Consequence / Either |
| decode | Encoded → Model | Yes | Consequence / Either |
| encode | Model → Encoded | No (DbC only) | Exception (Defect) |

---

## Relationship to Other Conventions

- `parse / decode`
  → Similar to `create / make` in that failure is expected
- `encode`
  → Similar to `apply` in that failure indicates a defect
- DbC is used only for **defect detection**
- Domain errors must use `Consequence`

---

## Design Intent

- Method names communicate **trust level and responsibility**
- Boundary-related failures are explicit
- Encoding assumes correctness; decoding assumes uncertainty

# Method Naming Conventions for Loading and Fetching

This section defines naming conventions for **loading, fetching, and reading data**.
These conventions clarify **data source, latency expectations, side effects,
and failure models** from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## load(...)

### Intended Use

- Loads data from a **local or relatively stable source**
- Typical sources:
  - Database
  - File system
  - In-memory cache
  - Local persistent storage

### Characteristics

- May involve I/O
- Latency is expected but generally bounded
- Often used inside application or infrastructure layers

### Error Model

- Failures are expected and must be represented explicitly
- Return `Consequence[T]`, `Either`, or an effect type (`IO`, `Future`, etc.)
- Must NOT throw exceptions for expected failures

### Example

def load(id: Id): Consequence[Entity]

def loadFromFile(path: Path): IO[Config]

### Design Note

- `load` implies **data already exists**
- Absence or failure is part of normal control flow

---

## fetch(...)

### Intended Use

- Retrieves data from a **remote or volatile source**
- Typical sources:
  - Remote services
  - External APIs
  - Network resources
  - Distributed systems

### Characteristics

- Involves network I/O
- Latency is variable and potentially high
- Failures are common and expected

### Error Model

- Must return `Consequence[T]`, `Either`, or an effect type
- Timeouts, connectivity issues, and partial failures are normal outcomes

### Example

def fetchUser(id: UserId): Consequence[User]

def fetchFromApi(req: Request): IO[Response]

### Design Note

- `fetch` implies **unreliable or remote access**
- Callers should assume retries or fallback may be required

---

## read(...)

### Intended Use

- Reads data from an **already available or provided source**
- Typical sources:
  - Input streams
  - Buffers
  - In-memory representations
  - Already-opened resources

### Characteristics

- Minimal side effects
- No resource acquisition
- Usually cheap compared to `load` or `fetch`

### Error Model

- Failures are possible but usually local (format, state)
- Return `Consequence[T]`, `Either`, or a plain value when safe

### Example

def read(buffer: ByteBuffer): Consequence[Message]

def readLine(reader: Reader): String

### Design Note

- `read` does NOT imply persistence or remote access
- It assumes the source is already under the caller’s control

---

## Comparative Summary

| Method | Source Type | Latency | Failure Expected |
|------|-------------|---------|------------------|
| load | Local / Stable | Medium | Yes |
| fetch | Remote / Volatile | High | Yes |
| read | In-Memory / Open | Low | Sometimes |

---

## Relationship to Other Conventions

- `load / fetch / read`
  → Concern **data access**, not creation
- Combine with `getXXX / takeXXX` to express safety expectations
- Combine with `blocking / async` to express execution model
- Domain-level failures must use `Consequence`

---

## Design Intent

- Method names communicate **where data comes from**
- Latency and reliability expectations are visible at call sites
- I/O boundaries are explicit and reviewable

# Method Naming Conventions for Building and Assembling

This section defines naming conventions for **building and assembling composite objects**.
These conventions clarify **construction strategy, dependency handling,
and failure expectations** from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## build(...)

### Intended Use

- Constructs an object by **explicitly following a defined plan or specification**
- Often used when:
  - Multiple parts must be combined
  - Construction follows a known sequence or recipe
  - Dependencies are already resolved or provided

### Characteristics

- Deterministic given the same inputs
- May involve moderate computation
- May fail if required components are missing or inconsistent

### Error Model

- Failures are expected and must be represented explicitly
- Return `Consequence[T]`, `Either`, or an effect type
- Must NOT hide failures behind partial results

### Example

def buildPlan(parts: Parts): Consequence[Plan]

def buildComponent(config: Config, deps: Dependencies): Component

### Design Note

- `build` emphasizes **process and structure**
- Inputs are assumed to be intentional and prepared by the caller

---

## assemble(...)

### Intended Use

- Assembles an object by **wiring together pre-existing components**
- Focuses on composition rather than creation logic
- Typical use cases:
  - Component wiring
  - Dependency injection
  - Runtime configuration binding

### Characteristics

- Little to no complex computation
- Mostly structural composition
- Often used near application or infrastructure boundaries

### Error Model

- Failures may occur due to incompatible components or missing bindings
- Return `Consequence[T]` or an effect type when failure is expected
- DbC may be used when incompatibility indicates a defect

### Example

def assembleService(repo: Repository, cache: Cache): Service

def assembleApplication(modules: Modules): Consequence[Application]

### Design Note

- `assemble` emphasizes **wiring and configuration**
- Logic should be minimal; behavior belongs elsewhere

---

## Comparative Summary

| Method | Focus | Computation | Failure Expected |
|------|-------|-------------|------------------|
| build | Process / Recipe | Medium | Yes |
| assemble | Wiring / Composition | Low | Sometimes |

---

## Relationship to Other Conventions

- `build / assemble`
  → Concern **composition**, not raw creation
- Often used after `create / make` or `load / fetch`
- May be combined with `experimental`, `unsafe`, or execution-model modifiers
- Domain-level failures must use `Consequence`

---

## Design Intent

- Method names communicate **how an object comes together**
- Construction logic and wiring logic are clearly separated
- Large object graphs remain understandable and reviewable

# Method Naming Conventions for Resolving and Querying

This section defines naming conventions for **resolving, looking up, finding,
and searching values or objects**.
These conventions clarify **assumptions, completeness, cost, and failure models**
from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## resolve(...)

### Intended Use

- Resolves a reference, identifier, or dependency into a concrete object
- Often used when:
  - A reference must be mapped to an actual instance
  - Dependencies are wired or selected based on rules
  - Resolution is expected to succeed under correct configuration

### Characteristics

- Assumes the target *should* exist or be resolvable
- Failure usually indicates misconfiguration or defect
- Often used in framework or infrastructure code

### Error Model

- May throw exceptions for resolution failure (DbC / defect)
- May return `Consequence[T]` when failure is an expected domain outcome
- Must NOT silently return `null`

### Example

def resolveService(name: ServiceName): Service

def resolveHandler(key: Key): Consequence[Handler]

### Design Note

- `resolve` implies **logical necessity**
- Failure is exceptional unless explicitly documented otherwise

---

## lookup(...)

### Intended Use

- Looks up a value by key in a **known collection or registry**
- Absence is a normal and expected outcome

### Characteristics

- Usually cheap (map, cache, index)
- No inference or heuristics
- No guarantee that a value exists

### Error Model

- Return `Option[T]` or `Consequence[T]`
- Must NOT throw exceptions for missing values

### Example

def lookupUser(id: UserId): Option[User]

def lookupConfig(key: ConfigKey): Consequence[Config]

### Design Note

- `lookup` emphasizes **explicit absence**
- Prefer over `resolve` when non-existence is normal

---

## find(...)

### Intended Use

- Retrieves a value that is **expected to exist in most cases**
- Absence is possible but relatively rare

### Characteristics

- Often used for repository or collection access
- Semantically stronger than `lookup`, weaker than `resolve`

### Error Model

- Return `Option[T]` or `Consequence[T]`
- May throw exceptions only when absence indicates a defect

### Example

def findOrder(id: OrderId): Option[Order]

def findActiveSession(user: User): Consequence[Session]

### Design Note

- `find` suggests **reasonable expectation**, not certainty
- Use `lookup` when absence is common

---

## search(...)

### Intended Use

- Performs a **query over a potentially large or open-ended space**
- Returns zero or more results

### Characteristics

- Potentially expensive
- Often involves filtering, ranking, or pattern matching
- May involve I/O or external systems

### Error Model

- Return a collection (`List`, `Seq`, etc.) or `Consequence[Seq[T]]`
- Empty result is normal
- Must NOT throw exceptions for “not found”

### Example

def searchUsers(query: Query): Consequence[List[User]]

def searchLogs(criteria: Criteria): IO[Seq[LogEntry]]

### Design Note

- `search` emphasizes **exploration**, not direct access
- Callers should assume cost and latency

---

## Comparative Summary

| Method  | Expectation | Absence | Typical Cost |
|--------|-------------|---------|--------------|
| resolve | Must exist | Exceptional | Low–Medium |
| lookup  | May exist  | Normal | Low |
| find    | Usually exists | Possible | Low–Medium |
| search  | Unknown set | Normal | Medium–High |

---

## Relationship to Other Conventions

- `resolve / lookup / find / search`
  → Concern **reference and query semantics**, not creation
- Combine with `load / fetch` to express data source
- Combine with `getXXX / takeXXX` to express safety expectations
- Domain-level failures must use `Consequence`
- Defect-level failures may use DbC

---

## Design Intent

- Method names communicate **certainty and cost**
- Absence semantics are explicit
- Query intent is visible at the call site

# Method Naming Conventions for Resource Management

This section defines naming conventions for **opening, closing, acquiring,
and releasing resources**.
These conventions clarify **resource ownership, lifetime,
and failure responsibility** from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## open(...)

### Intended Use

- Opens a resource and prepares it for use
- Typical resources:
  - Files
  - Network connections
  - Streams
  - Sessions

### Characteristics

- Acquires an external or system resource
- May involve I/O
- Establishes a lifecycle that must be closed explicitly

### Error Model

- Failures are expected and must be represented explicitly
- Return `Consequence[T]`, `Either`, or an effect type
- Must NOT hide failures using partial or dummy resources

### Example

def open(path: Path): Consequence[FileHandle]

def openConnection(cfg: Config): IO[Connection]

### Design Note

- `open` implies **exclusive or managed ownership**
- The caller becomes responsible for closing the resource

---

## close(...)

### Intended Use

- Closes or finalizes a previously opened resource
- Releases underlying system or external resources

### Characteristics

- Usually idempotent
- Should be safe to call multiple times
- Often used in `finally` blocks or resource scopes

### Error Model

- Failures may be logged but should rarely be propagated
- Return `Unit`, `Consequence[Unit]`, or an effect type when needed
- DbC may be used when closing an invalid resource indicates a defect

### Example

def close(handle: FileHandle): Unit

def closeConnection(conn: Connection): Consequence[Unit]

### Design Note

- `close` signals **end of ownership**
- Business logic must not depend on close-time failures

---

## acquire(...)

### Intended Use

- Acquires a resource from a **pool, registry, or shared manager**
- Typical resources:
  - Thread pools
  - Connection pools
  - Locks
  - Leases

### Characteristics

- Resource ownership is temporary
- May block or wait depending on availability
- Often paired with `release`

### Error Model

- Failures are expected (exhaustion, timeout)
- Must return `Consequence[T]` or an effect type
- Must NOT throw exceptions for normal contention

### Example

def acquire(): Consequence[Connection]

def acquireLock(key: Key): IO[Lock]

### Design Note

- `acquire` emphasizes **controlled access**, not creation
- Ownership is conditional and time-bound

---

## release(...)

### Intended Use

- Releases a previously acquired resource back to its manager
- Complements `acquire`

### Characteristics

- Must be safe to call even if acquisition was partial
- Should be idempotent where possible
- No heavy computation or blocking

### Error Model

- Failures usually indicate defects
- DbC may be used to detect double-release or invalid release
- Return `Unit` or `Consequence[Unit]` when failure handling is required

### Example

def release(conn: Connection): Unit

def releaseLock(lock: Lock): Consequence[Unit]

### Design Note

- `release` indicates **end of temporary ownership**
- Must not perform implicit acquisition or reopening

---

## Comparative Summary

| Method   | Ownership Model | Lifetime Control | Failure Expected |
|---------|-----------------|------------------|------------------|
| open    | Exclusive       | Caller-managed   | Yes |
| close   | Ends exclusive  | Explicit         | Rare |
| acquire | Shared / Pooled | Temporary        | Yes |
| release | Ends temporary  | Explicit         | Rare |

---

## Relationship to Other Conventions

- `open / close / acquire / release`
  → Concern **resource lifecycle management**
- Combine with `blocking / async` to express execution model
- Combine with `unsafe / unchecked` for performance-sensitive paths
- Resource-related failures must use `Consequence`
- Defect-level misuse may be detected using DbC

---

## Design Intent

- Resource ownership is explicit at the call site
- Lifecycle responsibilities are visible and reviewable
- Resource leaks and misuse are easier to detect and prevent

# Method Naming Conventions for Validation and Verification

This section defines naming conventions for **validation, verification,
and checking operations**.
These conventions clarify **purpose, strictness, and failure handling**
from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## validate(...)

### Intended Use

- Validates input, state, or data against **domain or business rules**
- Validation failures are **expected and recoverable**

### Characteristics

- Often used for user input, commands, DTOs, or external data
- Multiple validation errors may be collected
- Validation does not imply correctness of execution context

### Error Model

- Must return `Consequence[T]`, `Either`, or a validation result type
- Must NOT throw exceptions for validation failures

### Example

def validate(cmd: CreateOrderCommand): Consequence[ValidatedOrder]

def validateEmail(input: String): Either[ValidationError, Email]

### Design Note

- `validate` is about **business and domain correctness**
- Validation failures are not defects

---

## verify(...)

### Intended Use

- Verifies that a condition, assumption, or state **actually holds**
- Often used when correctness depends on external or runtime state

### Characteristics

- Stronger than `validate`
- Often used for:
  - Authorization
  - Permissions
  - Consistency with external systems
  - Security or integrity checks

### Error Model

- Return `Consequence[T]` or a boolean-like result
- May throw exceptions only when failure indicates a defect or breach

### Example

def verifyPermission(user: User, action: Action): Consequence[Unit]

def verifySignature(data: Data, sig: Signature): Consequence[Verified]

### Design Note

- `verify` is about **truth and trust**
- Failure often has security or consistency implications

---

## check(...)

### Intended Use

- Performs a **lightweight or internal consistency check**
- Often used for defensive or diagnostic purposes

### Characteristics

- Usually cheap and local
- No domain semantics implied
- Often returns a boolean or simple result

### Error Model

- Return `Boolean`, `Option[T]`, or a simple result
- May throw exceptions when used as a DbC-style assertion

### Example

def checkState(state: State): Boolean

def checkInvariant(obj: Entity): Unit

### Design Note

- `check` is intentionally weak in semantics
- Prefer `validate` or `verify` when meaning matters

---

## Comparative Summary

| Method  | Purpose | Failure Meaning | Typical Use |
|--------|---------|-----------------|-------------|
| validate | Domain correctness | Expected | Input / business rules |
| verify   | Truth / trust | Serious | Security / consistency |
| check    | Sanity / consistency | Diagnostic | Internal logic |

---

## Relationship to Other Conventions

- `validate`
  → Produces domain-level failures using `Consequence`
- `verify`
  → May produce domain failures or detect serious violations
- `check`
  → Often internal; may overlap with DbC usage
- DbC (`require`, `assert`) remains the tool for **defect detection**

---

## Design Intent

- Method names communicate **strictness and intent**
- Domain failures and defects are clearly separated
- Validation logic remains explicit and composable

# Method Naming Conventions for Lifecycle Control

This section defines naming conventions for **starting, stopping,
pausing, and resuming lifecycle-managed components or processes**.
These conventions clarify **lifecycle state transitions,
side effects, and failure expectations** from the method name alone.

These are **conventional rules** and may be extended as needed.

---

## start(...)

### Intended Use

- Transitions a component or process from an **inactive** state to an **active** state
- Typical targets:
  - Services
  - Engines
  - Schedulers
  - Background processes

### Characteristics

- May allocate resources or spawn execution contexts
- May involve I/O or asynchronous processing
- Usually paired with `stop`

### Error Model

- Failures are expected and must be represented explicitly
- Return `Consequence[Unit]` or an effect type
- Must NOT silently ignore startup failures

### Example

def start(): Consequence[Unit]

def startAsync(): IO[Unit]

### Design Note

- `start` establishes **operational responsibility**
- After successful start, the component is considered active

---

## stop(...)

### Intended Use

- Transitions a component or process from **active** to **inactive**
- Releases resources acquired during `start`

### Characteristics

- Should be safe to call multiple times
- Often performs graceful shutdown
- May wait for in-flight operations to complete

### Error Model

- Failures should be rare
- Return `Unit`, `Consequence[Unit]`, or an effect type
- DbC may be used if stopping an inactive component indicates a defect

### Example

def stop(): Unit

def stopGracefully(): Consequence[Unit]

### Design Note

- `stop` signals **end of lifecycle responsibility**
- Business logic must not depend on stop-time failures

---

## pause(...)

### Intended Use

- Temporarily suspends processing while preserving internal state
- Does NOT release core resources

### Characteristics

- Reversible operation
- Component remains initialized but inactive
- Often paired with `resume`

### Error Model

- Failures are possible (invalid state transitions)
- Return `Consequence[Unit]` or an effect type

### Example

def pause(): Consequence[Unit]

def pauseProcessing(): IO[Unit]

### Design Note

- `pause` implies **temporary inactivity**
- The component is expected to resume later

---

## resume(...)

### Intended Use

- Resumes a previously paused component or process
- Restores active processing without full reinitialization

### Characteristics

- Requires prior successful `pause`
- Must preserve consistency and internal state

### Error Model

- Failures indicate invalid lifecycle sequencing
- Return `Consequence[Unit]` or an effect type
- DbC may be used if resume is called in an invalid state

### Example

def resume(): Consequence[Unit]

def resumeProcessing(): IO[Unit]

### Design Note

- `resume` continues an existing lifecycle
- Must not implicitly call `start`

---

## Comparative Summary

| Method | State Transition | Resource Handling | Failure Expected |
|------|------------------|-------------------|------------------|
| start | Inactive → Active | Acquire | Yes |
| stop | Active → Inactive | Release | Rare |
| pause | Active → Paused | Preserve | Yes |
| resume | Paused → Active | Preserve | Yes |

---

## Relationship to Other Conventions

- `start / stop / pause / resume`
  → Concern **lifecycle state management**
- Combine with `async / blocking` to express execution behavior
- Combine with `idempotent` where repeated calls are safe
- Lifecycle failures must use `Consequence`
- Invalid state transitions may be detected using DbC

---

## Design Intent

- Lifecycle transitions are explicit and reviewable
- Resource ownership and responsibility are clear
- Components behave predictably across state changes
