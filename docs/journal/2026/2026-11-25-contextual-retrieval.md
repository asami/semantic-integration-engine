# 2026-11-25 — Contextual Retrieval

Reviewed [Anthropic Contextual Retrieval](https://www.anthropic.com/research/contextual-retrieval), published 2024-09-19. Its chunk-specific contextualization improves retrieval before query time. Reported top-20 failure rates: 5.7% baseline, 3.7% contextual embedding, 2.9% with contextual BM25, 1.9% with reranking. Results are external, not SIE benchmarks.

Direction: adopt the technique in the SIE ecosystem, with document context first and graph-aware context as a later PoC. TKL supplies authorized versioned source and provenance; generated context remains derived and must not be confused with approved knowledge. Preserve standalone RAG and existing Protocol boundaries. The current AGENTS.md limits this repository to integration/transport responsibilities; resolve delegated implementation ownership before development.

See [notes](../../notes/contextual-retrieval.md) and [delivery plan](../../phase/phase-1.md). No phase priority or implementation status is changed.
