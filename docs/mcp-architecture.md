# MCP Architecture (Semantic Integration Engine)

This document defines the MCP (Model Context Protocol) design in the
Semantic Integration Engine (SIE).

It specifies MCP as a strict JSON-RPC 2.0 protocol exposed via a
WebSocket endpoint, and clarifies its relationship to the Operation
Language and Interaction Contract.

----------------------------------------------------------------------

1. Goals and Non-Goals

Goals:

- Provide a strict JSON-RPC 2.0 MCP endpoint over WebSocket
- Expose stable, semantic tool APIs derived from the Operation Language
- Isolate protocol encoding/decoding at protocol boundaries
- Keep SieService as the only execution path for operations

Non-Goals:

- Supporting ChatGPT-specific or shorthand protocols on /mcp
- Merging REST and MCP endpoints
- Exposing internal data stores as MCP tools

----------------------------------------------------------------------

2. MCP Endpoint Definition

- Endpoint: WebSocket `/mcp`
- Protocol: JSON-RPC 2.0 (strict)
- Audience: MCP client developers and protocol integrators

The WebSocket is a transport only. All messages are JSON-RPC 2.0
requests and responses.

----------------------------------------------------------------------

3. MCP Request / Response Flow

The execution flow is fixed and protocol boundaries are explicit:

    JSON-RPC request
      → McpJsonRpcIngress (ProtocolIngress)
      → SieService (Interaction Contract)
      → McpJsonRpcAdapter (ProtocolAdapter)
      → JSON-RPC response

The MCP endpoint never bypasses the Interaction Contract.

----------------------------------------------------------------------

4. Tool Semantics

The MCP tools are projections of the Operation Language:

- tools/list: lists available operations as stable tool definitions
- tools/call: executes a specific operation by tool name

Tool definitions are derived from operation metadata and are treated
as stable semantic APIs.

----------------------------------------------------------------------

5. Separation from REST and ChatGPT

- REST APIs are exposed separately under `/api`
- MCP is strict JSON-RPC 2.0 on `/mcp`
- ChatGPT integration is a separate route and not supported on `/mcp`

----------------------------------------------------------------------

6. Error Model

Protocol errors are mapped to JSON-RPC error codes:

- invalid_request  → -32600
- method_not_found → -32601
- invalid_params   → -32602
- internal_error   → -32603

Protocol-specific formatting is handled only by McpJsonRpcAdapter.

----------------------------------------------------------------------

7. Stability Notes

Tool names are treated as stable semantic APIs.
Changes to tool names or required parameters must be treated as
breaking changes for MCP clients.
