package de.swdec.jaccount.keycloak.spi.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;

public class OrganizationsGroupsStructuredMapper
        extends AbstractOIDCProtocolMapper
        implements UserInfoTokenMapper {

    public static final String PROVIDER_ID = "organization-groups-structured-mapper";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Add a claim in the form \"groups\": [ { \"id\": \"group-id\", \"name\": \"Group 1\" }, ...]. Due to its potential size, it is only available at the user info endpoint.";
    }

    @Override
    public String getDisplayType() {
        return "Organization Groups Structured";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    public AccessToken transformUserInfoToken(
            AccessToken token,
            ProtocolMapperModel mappingModel,
            KeycloakSession session,
            UserSessionModel userSession,
            ClientSessionContext clientSessionCtx) {
        List<Map<String, String>> groups = userSession
                .getUser()
                .getGroupsStream()
                .map(g -> Map.of("id", g.getId(), "name", g.getName()))
                .toList();

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, groups);

        return token;
    }
}
