package org.simplemodeling.sie.vectordb

import org.simplemodeling.sie.embedding.EmbeddingEngine

/**
 * VectorDbClientFactory
 *
 * Central assembly point for VectorDB access in SIE.
 *
 * This factory is the ONLY place where:
 *   - environment variables are interpreted
 *   - concrete VectorDB implementations are instantiated
 *   - read/write routing policies are assembled
 *
 * Design intent:
 *   - Isolate VectorDB wiring from application logic
 *   - Prevent backend-specific branching outside this module
 *   - Enable runtime reconfiguration without code changes
 *
 * Architectural role:
 *   RagServiceMain
 *        |
 *        v
 *   VectorDbClientFactory   ←  configuration & wiring
 *        |
 *        v
 *   VectorDbClient / Router ←  logical VectorDB frontend
 *
 * IMPORTANT RULE:
 *   No other component is allowed to:
 *     - read VectorDB-related environment variables
 *     - construct VectorDbClient implementations directly
 *
 * This guarantees that:
 *   - VectorDB abstraction boundaries remain stable
 *   - Backend replacement does not affect core logic
 *
 * @since   Dec. 12, 2025
 * @version Dec. 12, 2025
 * @author  ASAMI, Tomoharu
 */
object VectorDbClientFactory {

    /**
     * Validate read/write capabilities of a VectorDbClient.
     *
     * This method ensures that the assembled backend configuration
     * is compatible with the requested routing mode.
     *
     * Validation rules:
     *   - Read backend MUST implement VectorDbReadClient
     *   - Write backend MUST implement VectorDbWriteClient
     *
     * Capability violations are considered configuration errors
     * and MUST fail fast during startup.
     *
     * Runtime validation rationale:
     *   Although read/write compatibility is primarily enforced by types,
     *   this method performs an explicit runtime check to:
     *     - fail fast on misconfiguration
     *     - protect against future refactoring errors
     *     - guard dynamic wiring based on environment variables
     */
    private def validateCapabilities(
        read: VectorDbReadClient,
        write: VectorDbWriteClient,
        mode: String
    ): Unit = {

        // Validate read capability
        if !read.isInstanceOf[VectorDbReadClient] then
            throw new IllegalStateException(
                s"Invalid VectorDB configuration for mode '$mode': " +
                s"read backend does not support read capability: ${read.getClass.getName}"
            )

        // Validate write capability
        if !write.isInstanceOf[VectorDbWriteClient] then
            throw new IllegalStateException(
                s"Invalid VectorDB configuration for mode '$mode': " +
                s"write backend does not support write capability: ${write.getClass.getName}"
            )
    }

    /**
     * Create a VectorDbClient using environment-based configuration.
     *
     * This method interprets runtime configuration and assembles
     * the appropriate VectorDbClient or VectorDbClientRouter.
     *
     * Supported environment variables:
     *
     *   SIE_VECTORDB_BACKEND
     *     - chroma
     *         Force direct use of ChromaDbClient
     *
     *     - sie-embedding
     *         Force direct use of SieEmbeddingVectorDbClient
     *
     *     - auto (default)
     *         Delegate backend selection to router configuration
     *
     *   SIE_VECTORDB_ROUTER_MODE
     *     - chroma
     *         read  = chroma
     *         write = chroma
     *
     *     - sie-embedding
     *         read  = sie-embedding
     *         write = sie-embedding
     *         (only valid if write operations are supported)
     *
     *     - mixed
     *         read  = sie-embedding
     *         write = chroma
     *
     * Design rules:
     *   - This method centralizes all VectorDB wiring decisions
     *   - Callers must not branch on backend or routing mode
     *   - Capability validation (read/write) belongs here
     *
     * @param embedding
     *   Embedding engine used by VectorDB implementations
     *
     * @return
     *   Fully assembled VectorDbClient instance
     */
    def fromEnv(
        embedding: EmbeddingEngine
    ): VectorDbClient = {

        // ------------------------------------------------------------
        // Resolve VectorDB endpoint (system properties > env > default)
        // ------------------------------------------------------------
        val resolvedEndpoint: String =
            sys.props.get("SIE_VECTORDB_ENDPOINT")
                .orElse(sys.env.get("SIE_VECTORDB_ENDPOINT"))
                .getOrElse("http://sie-embedding:8081")

        val chromaDbClient: VectorDbClient =
            ChromaDbClient.fromEndpoint(resolvedEndpoint, embedding)

        val sieEmbeddingDbClient: VectorDbClient =
            SieEmbeddingVectorDbClient.fromEndpoint(resolvedEndpoint)

        val backend =
            sys.env
                .get("SIE_VECTORDB_BACKEND")
                .map(_.toLowerCase)
                .getOrElse("auto")

        backend match
            case "chroma" =>
                chromaDbClient

            case "sie-embedding" =>
                sieEmbeddingDbClient

            case "auto" | _ =>
                val routerMode =
                    sys.env
                        .get("SIE_VECTORDB_ROUTER_MODE")
                        .map(_.toLowerCase)
                        .getOrElse("chroma")

                val router =
                    VectorDbClientRouter.fromEnv(
                        chroma = chromaDbClient,
                        sieEmbedding = sieEmbeddingDbClient
                    )

                // Explicit capability validation at assembly time
                routerMode match
                    case "chroma" =>
                        validateCapabilities(
                            read  = chromaDbClient,
                            write = chromaDbClient,
                            mode  = routerMode
                        )

                    case "sie-embedding" =>
                        validateCapabilities(
                            read  = sieEmbeddingDbClient,
                            write = sieEmbeddingDbClient,
                            mode  = routerMode
                        )

                    case "mixed" =>
                        validateCapabilities(
                            read  = sieEmbeddingDbClient,
                            write = chromaDbClient,
                            mode  = routerMode
                        )

                    case other =>
                        throw new IllegalArgumentException(
                            s"Unknown SIE_VECTORDB_ROUTER_MODE: $other"
                        )

                router
    }
}
