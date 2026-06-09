# kling-mcp

Spring AI MCP Server for wrapping Kling APIs. The current implementation exposes a single `current_date` tool and protects the MCP endpoint with Logto-issued OAuth access tokens.

## MCP Endpoint

- Streamable HTTP endpoint: `http://localhost:8081/mcp`
- Protected resource metadata:
  - `http://localhost:8081/.well-known/oauth-protected-resource`
  - `http://localhost:8081/.well-known/oauth-protected-resource/mcp`
- Health endpoint: `http://localhost:8081/actuator/health`

For public production access, use a stable HTTPS resource URI:

- Streamable HTTP endpoint: `https://mcp.example.com/mcp`
- `LOGTO_AUDIENCE` or Gateway token `aud`: `https://mcp.example.com/mcp`
- `KLING_MCP_PUBLIC_BASE_URL`: `https://mcp.example.com`

The Logto API identifier, MCP protected resource metadata `resource`, and JWT `aud` claim must be exactly the same URI.

## Logto Configuration

Create an API resource in Logto:

- API name: `kling-mcp-server`
- API identifier: `http://localhost:8081/mcp`
- Permission: `kling:invoke`

For Cursor OAuth login, create a Logto Native app:

- Application name: `kling-mcp-cursor-client`
- Redirect URI: `cursor://anysphere.cursor-mcp/oauth/callback`

Assign a user role with the `kling:invoke` API permission to users who should be allowed to invoke MCP tools from Cursor.

For machine-to-machine or curl testing, create a Logto M2M app, create an M2M role with the `kling:invoke` API permission, and assign that role to the M2M app.

## Environment Variables

```bash
export LOGTO_ISSUER_URI="https://<tenant>.logto.app/oidc"
export LOGTO_AUDIENCE="http://localhost:8081/mcp"
export KLING_MCP_REQUIRED_SCOPE="kling:invoke"
export KLING_MCP_PUBLIC_BASE_URL="http://localhost:8081"
```

Optional:

```bash
export LOGTO_JWK_SET_URI="https://<tenant>.logto.app/oidc/jwks"
export KLING_MCP_ENDPOINT="/mcp"
export KLING_MCP_GATEWAY_ISSUER="http://localhost:8082"
export KLING_MCP_GATEWAY_JWK_SET_URI="http://localhost:8082/.well-known/jwks.json"
```

`LOGTO_AUDIENCE` must exactly match the Logto API identifier and the access token `aud` claim.

## Trusted Issuer Migration

`kling-mcp` can trust one issuer or a migration list. Without `trusted-issuers`, it falls back to the legacy `LOGTO_ISSUER_URI` and `LOGTO_JWK_SET_URI` settings.

During migration from Logto direct tokens to `kling-mcp-auth-gateway` tokens:

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

After all agents move to the Gateway, remove the Logto issuer so `kling-mcp` only accepts Gateway-issued JWTs.

The local `application.yml` includes both the Logto issuer and the local Gateway issuer by default:

- `http://localhost:8082`
- `https://107k1z.logto.app/oidc`

The Gateway issuer is listed first so MCP clients that only use the first authorization server will choose the DCR-capable Gateway instead of Logto.

## Run Locally

```bash
mvn -pl kling-mcp spring-boot:run
```

The startup logs print non-sensitive auth configuration: endpoint, resource, issuer, required scope, and metadata URL.

## Cursor Configuration

Use static OAuth client configuration because Logto does not currently expose the Dynamic Client Registration endpoint Cursor expects.

`.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "kling-mcp": {
      "url": "http://localhost:8081/mcp",
      "auth": {
        "CLIENT_ID": "<Logto Native App ID>",
        "scopes": ["kling:invoke"]
      }
    }
  }
}
```

After saving the file, restart Cursor or reconnect the MCP server from Tools & MCP. Cursor should open Logto for user login and then list the `current_date` tool.

With `kling-mcp-auth-gateway` in front of Logto, Cursor can rely on OAuth discovery and DCR:

```json
{
  "mcpServers": {
    "kling-mcp": {
      "url": "https://mcp.example.com/mcp"
    }
  }
}
```

This requires `kling-mcp` protected resource metadata to point to the Gateway issuer through `trusted-issuers`.

## Manual Token Test

Fetch a token with Logto client credentials:

```bash
export LOGTO_ENDPOINT="https://<tenant>.logto.app"
export LOGTO_M2M_APP_ID="<M2M App ID>"
export LOGTO_M2M_APP_SECRET="<M2M App Secret>"
export LOGTO_AUDIENCE="http://localhost:8081/mcp"
export KLING_MCP_REQUIRED_SCOPE="kling:invoke"

curl -X POST "$LOGTO_ENDPOINT/oidc/token" \
  -H "Authorization: Basic $(printf '%s:%s' "$LOGTO_M2M_APP_ID" "$LOGTO_M2M_APP_SECRET" | base64)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "resource=$LOGTO_AUDIENCE" \
  -d "scope=$KLING_MCP_REQUIRED_SCOPE"
```

Store the returned access token:

```bash
export KLING_MCP_ACCESS_TOKEN="<access_token>"
```

Initialize an MCP session:

```bash
curl -i -D /tmp/kling-mcp-headers.txt \
  -X POST http://localhost:8081/mcp \
  -H "Authorization: Bearer $KLING_MCP_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-03-26",
      "capabilities": {},
      "clientInfo": {
        "name": "curl",
        "version": "0.1.0"
      }
    }
  }'
```

Capture the MCP session ID:

```bash
export MCP_SESSION_ID=$(awk 'tolower($1)=="mcp-session-id:" {print $2}' /tmp/kling-mcp-headers.txt | tr -d '\r')
```

List tools:

```bash
curl -i \
  -X POST http://localhost:8081/mcp \
  -H "Authorization: Bearer $KLING_MCP_ACCESS_TOKEN" \
  -H "mcp-session-id: $MCP_SESSION_ID" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

Call `current_date`:

```bash
curl -i \
  -X POST http://localhost:8081/mcp \
  -H "Authorization: Bearer $KLING_MCP_ACCESS_TOKEN" \
  -H "mcp-session-id: $MCP_SESSION_ID" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "current_date",
      "arguments": {}
    }
  }'
```

## Troubleshooting

- `401 Unauthorized`: token is missing, expired, has the wrong issuer, has the wrong audience, uses an unsupported JWT type or signing algorithm, or fails signature validation.
- `403 Forbidden`: token is valid but does not include the configured `kling:invoke` scope.
- `400 Invalid Accept header`: the request reached MCP transport without the required `Accept: application/json, text/event-stream` or `Accept: text/event-stream` header.
- Cursor says `does not support dynamic client registration`: use the static OAuth `auth.CLIENT_ID` configuration shown above.
