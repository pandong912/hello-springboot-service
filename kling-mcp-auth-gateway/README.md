# kling-mcp-auth-gateway

OAuth compatibility gateway for opening `kling-mcp` to multiple remote MCP clients.

The gateway is a separate Spring Boot module so `kling-mcp` can stay focused on MCP tools and JWT validation. It provides the public OAuth surface that agents need, while Logto can remain the upstream identity provider in production.

## Current Capabilities

- OAuth Authorization Server metadata: `http://localhost:8082/.well-known/oauth-authorization-server`
- Authorization Code + PKCE endpoints:
  - `http://localhost:8082/oauth/authorize`
  - `http://localhost:8082/oauth/token`
- JWKS endpoint: `http://localhost:8082/.well-known/jwks.json`
- Public client registration endpoint: `http://localhost:8082/oauth/register`
- MCP Protected Resource Metadata:
  - `http://localhost:8082/.well-known/oauth-protected-resource`
  - `http://localhost:8082/.well-known/oauth-protected-resource/mcp`
- Access tokens include `aud` and `resource` claims for the configured `kling-mcp` resource.

This module delegates user login to Logto through OIDC, then issues Gateway access tokens for `kling-mcp`. It still uses an in-memory client registry and generated signing key by default. That is correct for local verification, but production must replace them with persistent storage and managed signing keys.

## Local Run

```bash
mvn -pl kling-mcp-auth-gateway spring-boot:run
```

Default local settings:

```bash
export LOGTO_ISSUER_URI="https://107k1z.logto.app/oidc"
export LOGTO_GATEWAY_CLIENT_ID="<Logto Web App ID>"
export LOGTO_GATEWAY_CLIENT_SECRET="<Logto Web App Secret>"
export KLING_MCP_GATEWAY_ISSUER="http://localhost:8082"
export KLING_MCP_RESOURCE="http://localhost:8081/mcp"
export KLING_MCP_ENDPOINT="http://localhost:8081/mcp"
export KLING_MCP_REQUIRED_SCOPE="kling:invoke"
```

For production, use HTTPS:

```bash
export KLING_MCP_GATEWAY_ISSUER="https://auth.example.com"
export KLING_MCP_RESOURCE="https://mcp.example.com/mcp"
export KLING_MCP_ENDPOINT="https://mcp.example.com/mcp"
```

The matching `kling-mcp` resource server should trust this issuer during or after migration:

```yaml
kling:
  mcp:
    public-base-url: https://mcp.example.com
    endpoint: /mcp
    auth:
      audience: https://mcp.example.com/mcp
      required-scope: kling:invoke
      trusted-issuers:
        - issuer-uri: https://107k1z.logto.app/oidc
          jwk-set-uri: https://107k1z.logto.app/oidc/jwks
        - issuer-uri: https://auth.example.com
          jwk-set-uri: https://auth.example.com/.well-known/jwks.json
```

## Dynamic Client Registration

Register a PKCE public client:

```bash
curl -s http://localhost:8082/oauth/register \
  -H "Content-Type: application/json" \
  -d '{
    "client_name": "cursor",
    "redirect_uris": ["cursor://anysphere.cursor-mcp/oauth/callback"],
    "scope": "kling:invoke"
  }'
```

The response contains a `client_id` and no `client_secret`. The client should use Authorization Code + PKCE with `token_endpoint_auth_method=none`.

Cursor may register either of these redirect URIs depending on the client build and auth flow:

- `cursor://anysphere.cursor-mcp/oauth/callback`
- `https://www.cursor.com/agents/mcp/oauth/callback`
- `http://localhost:8787/callback`

For Client ID Metadata Documents, send an HTTPS `client_id` in the registration request. The gateway preserves it as the OAuth client identifier:

```json
{
  "client_id": "https://agent.example.com/.well-known/oauth-client",
  "redirect_uris": ["https://agent.example.com/oauth/callback"],
  "scope": "kling:invoke"
}
```

Production hardening should add metadata-document fetch and validation, SSRF protection, software statement verification if needed, and an approval workflow for unknown public clients.

## Logto Upstream Login

Create a Logto Traditional Web App for the Gateway:

- Application name: `kling-mcp-auth-gateway`
- Redirect URI for local verification: `http://localhost:8082/login/oauth2/code/logto`
- Redirect URI for production: `https://auth.example.com/login/oauth2/code/logto`

The Gateway maps Logto `sub`, `email`, `name`, `roles`, `organizations`, `organization_data`, and `organization_roles` claims into Gateway-issued access tokens when those claims are present in the upstream login result.

Keep `kling-mcp` validating gateway-issued tokens, not Logto tokens, after the migration window.

## Token Contract

Gateway-issued access tokens for `kling-mcp` should contain:

- `iss`: `https://auth.example.com`
- `sub`: Logto user id or service account subject
- `aud`: `https://mcp.example.com/mcp`
- `scope`: space-separated scopes, initially `kling:invoke`
- `client_id`: registered agent client id
- `resource`: `https://mcp.example.com/mcp`
- Optional business claims: `organization_id`, `roles`, `tenant_id`

## Production Checklist

- Replace in-memory clients with a database-backed `RegisteredClientRepository`.
- Replace generated RSA keys with managed signing keys and rotation.
- Add Logto OIDC login and user claim mapping.
- Add client review, rate limiting, quotas, audit logs, and revoke/disable flows.
- Serve only over HTTPS and publish stable metadata URLs.
- Keep reverse proxy headers required by MCP: `Authorization`, `mcp-session-id`, `Accept`, and `Content-Type`.
