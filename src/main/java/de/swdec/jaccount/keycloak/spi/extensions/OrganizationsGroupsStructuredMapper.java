package de.swdec.jaccount.keycloak.spi.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;

public class OrganizationsGroupsStructuredMapper
    extends AbstractOIDCProtocolMapper
    implements UserInfoTokenMapper
{

    public static final String PROVIDER_ID =
        "organization-groups-structured-mapper";

    private static final List<ProviderConfigProperty> configProperties =
        new ArrayList<>();
    private static final String CONFIG_PROP_ORGS_AS_GROUPS = "orgs_as_groups";
    private static final String CONFIG_PROP_ORG_ID_PREFIX = "org_id_prefix";
    private static final String CONFIG_PROP_GROUP_ID_PREFIX = "group_id_prefix";
    private static final String CONFIG_PROP_ID_OVERRIDE_ATTR =
        "id_override_attr";

    static {
        // Add option to configure the claim name
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);

        // Add toggle to include organizations themselves as groups
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_ORGS_AS_GROUPS);
        property.setLabel("Include Organizations as groups");
        property.setHelpText(
            "In addition to organization groups, should the token also include organizations the user is a member of? This will look to the application like the user is part of a group called the name of the organization."
        );
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(property);

        // Add a setting to prefix the ids of groups
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_GROUP_ID_PREFIX);
        property.setLabel("ID-Prefix for Groups");
        property.setHelpText(
            "To avoid collisions among group and org ids, add a prefix that will be added to the group ids."
        );
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        // Add a setting to prefix the ids of orgs as groups
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_ORG_ID_PREFIX);
        property.setLabel("ID-Prefix for Organizations as groups");
        property.setHelpText(
            "To avoid collisions among group and org ids, add a prefix that will be added to the organization ids."
        );
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        // Add a setting to override the ids of orgs or groups if the specified attribute exists
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_ID_OVERRIDE_ATTR);
        property.setLabel("ID-overriding attribute name");
        property.setHelpText(
            "Use the specified attribute name in place of the group/org ID if found. Useful for connecting legacy systems, where you can add an attribute to the orgs/groups in Keycloak, and keep using pre-existing legacy system IDs."
        );
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
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
        ClientSessionContext clientSessionCtx
    ) {
        final boolean orgsAsGroups = Boolean.parseBoolean(
            mappingModel
                .getConfig()
                .getOrDefault(
                    CONFIG_PROP_ORGS_AS_GROUPS,
                    Boolean.FALSE.toString()
                )
        );
        final String orgIdPrefix = mappingModel
            .getConfig()
            .getOrDefault(CONFIG_PROP_ORG_ID_PREFIX, "");
        final String groupIdPrefix = mappingModel
            .getConfig()
            .getOrDefault(CONFIG_PROP_GROUP_ID_PREFIX, "");
        final String idOverrideAttr = mappingModel
            .getConfig()
            .getOrDefault(CONFIG_PROP_ID_OVERRIDE_ATTR, null);

        OrganizationProvider orgProvider = session.getProvider(
            OrganizationProvider.class
        );
        Stream<OrganizationModel> organizations = orgProvider.getByMember(
            userSession.getUser()
        );

        List<Map<String, String>> orgGroups = organizations
            .flatMap(org -> {
                List<Map<String, String>> groups = List.of();

                // Include this org as a group if configured
                if (orgsAsGroups) {
                    String orgId = orgIdPrefix + org.getId();
                    // Allow overriding the ID with the specified attribute
                    if (idOverrideAttr != null) {
                        var attr = org.getAttributes().get(idOverrideAttr);
                        if (attr != null && attr.size() > 0) {
                            orgId = attr.get(0);
                        }
                    }

                    groups.add(Map.of("id", orgId, "name", org.getName()));
                }

                // TODO: Add org groups

                return groups.stream();
            })
            .toList();

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, orgGroups);

        return token;
    }
}
