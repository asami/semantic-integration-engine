# Phase 1: Contextual Retrieval

status=planned
planned_at=2026-11-25
owner=semantic-integration-engine
checklist=inline SIE-CR01..06 below

This is a planning proposal, not authorization to expand the repository's AGENTS.md transport/protocol scope. Resolve the retrieval implementation owner (SIE backend or delegated component) before coding. Do not alter Protocol semantics, introspection output or ExecutionContext ownership. Coordinate with [TKL Phase 3](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-3.md).


No implementation is accepted by this plan. Start with document context; graph expansion and pattern context are subsequent PoC candidates. Preserve standalone RAG use and existing REST/MCP protocol boundaries.

## Stage: Contextual retrieval delivery

Stage Status:
- Current status: OPEN
- Owner: semantic-integration-engine
- Update rule: Close only from checklist and executable acceptance evidence; this plan itself accepts no implementation.

- [ ] SIE-CR01: Inventory existing hybrid retrieval and backend contracts; agree ownership and authorized source/version handoff; establish baseline evaluation.
- [ ] SIE-CR02: Implement or delegate structure-aware chunking and chunk-specific document contextualization; keep raw text separate and persist versioned derivation references.
- [ ] SIE-CR03: Evaluate contextual embeddings, contextual BM25 and rank fusion against baseline.
- [ ] SIE-CR04: Evaluate reranking and bounded context assembly with source citations and configurable budgets.
- [ ] SIE-CR05: Explore authorized graph-aware context using RDF, TKL relationships and Commons terminology; evaluate incremental benefit.
- [ ] SIE-CR06: Joint acceptance: deterministic contracts, recall@K, answer grounding, latency, indexing/query cost, stale/revoked source handling and tenant isolation.

## Executable acceptance

| ID | Given / When / Then acceptance |
| --- | --- |
| CR-01 | Given a fixed corpus, baseline and contextual retrieval use identical evaluation queries and source versions. |
| CR-02 | Given a chunk, generated context is distinct from raw evidence and references exact source spans. |
| CR-03 | Given identifier and semantic queries, lexical/vector fusion and reranking return bounded ranked results with evidence. |
| CR-04 | Given changed or revoked dependencies, stale context cannot bypass policy; cross-tenant queries disclose nothing. |
| CR-05 | Given REST/MCP requests, existing protocol semantics and introspection remain unchanged. |
| CR-06 | Given graph expansion, authorization and budget bounds hold; report incremental quality and cost. |

## Completion criteria

All checklist obligations and applicable executable specifications pass. Record actual producer/consumer versions, deterministic fixture results, representative evaluation, limitations and integrated review. Promote agreed contracts to spec/design before implementation. No priority change or implementation acceptance is implied.
