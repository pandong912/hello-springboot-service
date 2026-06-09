# Kling MCP Public Agent Integration

This document tracks the public-agent rollout for `kling-mcp`.

## Public URLs

Use stable HTTPS URLs in production:

- MCP resource: `https://mcp.example.com/mcp`
- Protected resource metadata: `https://mcp.example.com/.well-known/oauth-protected-resource/mcp`
- OAuth gateway issuer: `https://auth.example.com`
- OAuth authorization server metadata: `https://auth.example.com/.well-known/oauth-authorization-server`
- JWKS: `https://auth.example.com/.well-known/jwks.json`
- Dynamic client registration: `https://auth.example.com/oauth/register`

All public metadata should use the same MCP resource URI as the token `aud` claim.

## Integration Matrix

Cursor:
Recommended mode is Streamable HTTP + OAuth. Use a public PKCE client through DCR or the existing static Logto `CLIENT_ID`. Verify `initialize`, `tools/list`, and `tools/call current_date`. Static Logto remains the fallback while Gateway DCR matures.

MCP Inspector:
Recommended mode is Streamable HTTP + Bearer for smoke tests or Streamable HTTP + OAuth for end-to-end tests. Use M2M tokens first, then a PKCE client. Verify the proxy connects, the MCP session id is captured, and `current_date` succeeds. Keep `localhost` and proxy token settings consistent.

Claude Desktop:
Recommended mode is stdio through `mcp-remote`. Use a public PKCE client. Verify the bridge obtains a token and forwards MCP requests. Keep this path until the target Claude environment supports remote Streamable HTTP directly.

Windsurf or other IDE agents:
Recommended mode is Streamable HTTP + OAuth. Use a public PKCE client through DCR or CIMD. Verify login completes and tools are listed. Validate redirect URI requirements per client.

Server-side custom agent:
Recommended mode is direct Streamable HTTP + Bearer. Use a confidential client or M2M token. Verify client credentials tokens can call MCP. Use short TTL and client-specific quotas.

Browser-backed website agent:
Recommended mode is a backend proxy to MCP. Map the website session to a server-side MCP token exchange. Verify user sessions map to MCP calls. Do not expose long-lived MCP tokens to the browser.

## Cursor Configuration

During Phase 1 with Logto static OAuth:

```json
{
  "mcpServers": {
    "kling-mcp": {
      "url": "https://mcp.example.com/mcp",
      "auth": {
        "CLIENT_ID": "<Logto Native App ID>",
        "scopes": ["kling:invoke"]
      }
    }
  }
}
```

With Gateway DCR:

```json
{
  "mcpServers": {
    "kling-mcp": {
      "url": "https://mcp.example.com/mcp"
    }
  }
}
```

The MCP server should return `WWW-Authenticate` with `resource_metadata="https://mcp.example.com/.well-known/oauth-protected-resource/mcp"`. The protected resource metadata should point Cursor to `https://auth.example.com`.

## mcp-remote Bridge

For stdio-only agents:

```bash
npx mcp-remote https://mcp.example.com/mcp
```

If the agent cannot perform OAuth itself, pre-register a public PKCE client or use a short-lived bearer token only for local verification.

## Verification Checklist

1. Metadata discovery returns HTTPS URLs only.
2. DCR creates a PKCE client without a secret for public agents.
3. Authorization Code + PKCE returns an access token with `aud=https://mcp.example.com/mcp`.
4. `kling-mcp` accepts Gateway issuer tokens and rejects unknown issuers.
5. `kling-mcp` rejects valid tokens missing `kling:invoke`.
6. `tools/list` returns `current_date`.
7. `tools/call` for `current_date` returns the server date.
8. Logs include client id, user subject, issuer, audience, and denial reason without logging token values.

## Migration Strategy

Use a two-issuer window:

```yaml
kling:
  mcp:
    auth:
      audience: https://mcp.example.com/mcp
      trusted-issuers:
        - issuer-uri: https://107k1z.logto.app/oidc
          jwk-set-uri: https://107k1z.logto.app/oidc/jwks
        - issuer-uri: https://auth.example.com
          jwk-set-uri: https://auth.example.com/.well-known/jwks.json
```

After clients move to Gateway, remove the Logto issuer from `trusted-issuers` so `kling-mcp` only accepts gateway-issued access tokens.
