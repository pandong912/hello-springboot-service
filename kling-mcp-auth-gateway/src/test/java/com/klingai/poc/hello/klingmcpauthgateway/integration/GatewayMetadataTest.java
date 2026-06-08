package com.klingai.poc.hello.klingmcpauthgateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "kling.mcp.auth-gateway.issuer=http://localhost:8082",
        "kling.mcp.auth-gateway.mcp.resource=https://mcp.example.com/mcp",
        "kling.mcp.auth-gateway.mcp.endpoint=https://mcp.example.com/mcp",
        "kling.mcp.auth-gateway.mcp.scopes[0]=kling:invoke",
        "kling.mcp.auth-gateway.registration.redirect-uri-allow-list[0]=cursor://anysphere.cursor-mcp/oauth/callback",
        "kling.mcp.auth-gateway.registration.redirect-uri-allow-list[1]=https://www.cursor.com/agents/mcp/oauth/callback",
        "kling.mcp.auth-gateway.registration.redirect-uri-allow-list[2]=http://localhost:8787/callback",
        "kling.mcp.auth-gateway.logto.issuer-uri=https://example.logto.app/oidc",
        "kling.mcp.auth-gateway.logto.client-id=test-client",
        "kling.mcp.auth-gateway.logto.client-secret=test-secret",
        "spring.security.oauth2.client.registration.logto.client-id=test-client",
        "spring.security.oauth2.client.registration.logto.client-secret=test-secret",
        "spring.security.oauth2.client.provider.logto.issuer-uri=https://example.logto.app/oidc"
})
@AutoConfigureMockMvc
class GatewayMetadataTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authorizationServerMetadataUsesGatewayIssuerAndEndpoints() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8082"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8082/oauth/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8082/oauth/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8082/.well-known/jwks.json"))
                .andExpect(jsonPath("$.registration_endpoint").value("http://localhost:8082/oauth/register"))
                .andExpect(jsonPath("$.token_endpoint_auth_methods_supported[0]").value("none"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("kling:invoke"));
    }

    @Test
    void tokenEndpointAllowsCorsPreflightFromCursorCallback() throws Exception {
        mockMvc.perform(options("/oauth/token")
                        .header("Origin", "https://www.cursor.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.cursor.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,OPTIONS"));
    }

    @Test
    void openidConfigurationExposesGatewayRegistrationEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8082"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8082/oauth/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8082/oauth/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8082/.well-known/jwks.json"))
                .andExpect(jsonPath("$.registration_endpoint").value("http://localhost:8082/oauth/register"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
    }

    @Test
    void protectedResourceMetadataPointsAgentsToGatewayIssuer() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://mcp.example.com/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost:8082"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("kling:invoke"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"));
    }

    @Test
    void publicClientRegistrationCreatesPkceClientWithoutSecret() throws Exception {
        mockMvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["cursor://anysphere.cursor-mcp/oauth/callback"],
                                  "scope": "kling:invoke",
                                  "client_name": "cursor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id", startsWith("kling-mcp-client-")))
                .andExpect(jsonPath("$.client_secret").doesNotExist())
                .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
                .andExpect(jsonPath("$.redirect_uris[0]").value("cursor://anysphere.cursor-mcp/oauth/callback"))
                .andExpect(jsonPath("$.grant_types[0]").value("authorization_code"))
                .andExpect(jsonPath("$.scope").value("kling:invoke"));
    }

    @Test
    void connectRegisterAliasCreatesPublicClient() throws Exception {
        mockMvc.perform(post("/connect/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["cursor://anysphere.cursor-mcp/oauth/callback"],
                                  "scope": "kling:invoke",
                                  "client_name": "cursor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client_id", startsWith("kling-mcp-client-")))
                .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"));
    }

    @Test
    void publicClientRegistrationAcceptsCursorWebCallback() throws Exception {
        mockMvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["https://www.cursor.com/agents/mcp/oauth/callback"],
                                  "scope": "kling:invoke",
                                  "client_name": "cursor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.redirect_uris[0]").value("https://www.cursor.com/agents/mcp/oauth/callback"));
    }

    @Test
    void publicClientRegistrationAcceptsCursorLocalCallback() throws Exception {
        mockMvc.perform(post("/oauth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "redirect_uris": ["http://localhost:8787/callback"],
                                  "scope": "kling:invoke",
                                  "client_name": "cursor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.redirect_uris[0]").value("http://localhost:8787/callback"));
    }
}
