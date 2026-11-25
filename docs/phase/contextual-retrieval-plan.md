# Contextual Retrieval — Proposed Delivery Phase

status=planned
planned_at=2026-11-25
owner=semantic-integration-engine

This is a planning proposal, not authorization to expand the repository's AGENTS.md transport/protocol scope. Resolve the retrieval implementation owner (SIE backend or delegated component) before coding. Do not alter Protocol semantics, introspection output or ExecutionContext ownership. Coordinate with [TKL Phase 3](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-3.md).

- [ ] SIE-CR01: Inventory existing hybrid retrieval and backend contracts; agree ownership and authorized source/version handoff; establish baseline evaluation.
- [ ] SIE-CR02: Implement or delegate structure-aware chunking and chunk-specific document contextualization; keep raw text separate and persist versioned derivation references.
- [ ] SIE-CR03: Evaluate contextual embeddings, contextual BM25 and rank fusion against baseline.
- [ ] SIE-CR04: Evaluate reranking and bounded context assembly with source citations and configurable budgets.
- [ ] SIE-CR05: Explore authorized graph-aware context using RDF, TKL relationships and Commons terminology; evaluate incremental benefit.
- [ ] SIE-CR06: Joint acceptance: deterministic contracts, recall@K, answer grounding, latency, indexing/query cost, stale/revoked source handling and tenant isolation.

No implementation is accepted by this plan. Start with document context; graph expansion and pattern context are subsequent PoC candidates. Preserve standalone RAG use and existing REST/MCP protocol boundaries.
