# Contextual Retrieval Architecture Notes

Non-normative exploration. [Delivery plan](../phase/contextual-retrieval-plan.md); [TKL integration](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-3.md).

Pipeline: authorized source -> structure-aware chunks -> chunk-specific contextualization -> contextual embedding and BM25 -> rank fusion with optional RDF retrieval -> reranking -> bounded context assembly. Keep contextualization independent of embedding and reusable. Raw chunk, generated explanation and source references must remain distinguishable. Treat document content as untrusted data, not instructions.

SIE ecosystem owns retrieval behavior; this repository remains a replaceable integration shell under AGENTS.md. Delegate semantic processing to an appropriate backend; do not introduce Protocol semantics or introspection heuristics. Existing Fuseki and vector backend contracts should be inventoried before selecting storage or lexical/reranking providers. TKL owns source authorization and durable source/provenance contracts; avoid duplicate canonical stores. Support standalone sources through equivalent contracts.

Evaluate document context first; later test graph-aware provenance, structural context, Commons terminology and pattern relationships. Bound expansion, token budget and cost. Cache by source/dependency versions, model, prompt and policy scope; invalidate on changes. Measure recall@K, grounding, latency and cost with representative Japanese/English and technical identifier queries. Provider/model choices remain open.
