package de.swdec.jaccount.keycloak.spi.extensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.protocol.ProtocolMapperUtils;
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
    private static final String CONFIG_PROP_ORGS_AS_GROUPS = "orgs_as_groups";
    private static final String CONFIG_PROP_ORG_ID_PREFIX = "org_id_prefix";
    private static final String CONFIG_PROP_PREPEND_ORG_TO_GROUP_NAME = "prepend_org_to_group_name";
    private static final String CONFIG_PROP_GROUP_ID_PREFIX = "group_id_prefix";
    private static final String CONFIG_PROP_ID_OVERRIDE_ATTR = "id_override_attr";

    static {
        // Add option to configure the claim name
        OIDCAttributeMapperHelper.addTokenClaimNameConfig(configProperties);

        // Add toggle to include organizations themselves as groups
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_ORGS_AS_GROUPS);
        property.setLabel("Include Organizations as groups");
        property.setHelpText(
                "In addition to organization groups, should the token also include organizations the user is a member of? This will look to the application like the user is part of a group called the name of the organization.");
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(property);

        // Add a setting to prefix the ids of groups
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_GROUP_ID_PREFIX);
        property.setLabel("ID-Prefix for Groups");
        property.setHelpText(
                "To avoid collisions among group and org ids, add a prefix that will be added to the group ids.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        // Add toggle to prepend the organization name to the group names
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_PREPEND_ORG_TO_GROUP_NAME);
        property.setLabel("Prepend Organization to Group Names");
        property.setHelpText(
                "If enabled, the name of the organization will be prepended to all group names of that organization. Example: Instead of '/Group-of-OrgA', the name will be 'OrgA/Group-of-OrgA'.");
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(property);

        // Add a setting to prefix the ids of orgs as groups
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_ORG_ID_PREFIX);
        property.setLabel("ID-Prefix for Organizations as groups");
        property.setHelpText(
                "To avoid collisions among group and org ids, add a prefix that will be added to the organization ids.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        // Add a setting to override the ids of orgs or groups if the specified
        // attribute exists
        property = new ProviderConfigProperty();
        property.setName(CONFIG_PROP_ID_OVERRIDE_ATTR);
        property.setLabel("ID-overriding attribute name");
        property.setHelpText(
                "Use the specified attribute name in place of the group/org ID if found. Useful for connecting legacy systems, where you can add an attribute to the orgs/groups in Keycloak, and keep using pre-existing legacy system IDs.");
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
            ClientSessionContext clientSessionCtx) {
        final boolean orgsAsGroups = Boolean.parseBoolean(
                mappingModel
                        .getConfig()
                        .getOrDefault(
                                CONFIG_PROP_ORGS_AS_GROUPS,
                                Boolean.FALSE.toString()));
        final String orgIdPrefix = mappingModel
                .getConfig()
                .getOrDefault(CONFIG_PROP_ORG_ID_PREFIX, "");
        final String groupIdPrefix = mappingModel
                .getConfig()
                .getOrDefault(CONFIG_PROP_GROUP_ID_PREFIX, "");
        final boolean prependOrgToGroupName = Boolean.parseBoolean(
                mappingModel
                        .getConfig()
                        .getOrDefault(
                                CONFIG_PROP_PREPEND_ORG_TO_GROUP_NAME,
                                Boolean.FALSE.toString()));
        final String idOverrideAttr = mappingModel
                .getConfig()
                .getOrDefault(CONFIG_PROP_ID_OVERRIDE_ATTR, null);

        OrganizationProvider orgProvider = session.getProvider(
                OrganizationProvider.class);
        UserModel user = userSession.getUser();
        Stream<OrganizationModel> organizations = orgProvider
                .getByMember(user)
                .filter(OrganizationModel::isEnabled);

        List<Map<String, String>> orgGroups = organizations
                .flatMap(org -> {
                    List<Map<String, String>> groups = new ArrayList<>();

                    // Include this org as a group if configured
                    if (orgsAsGroups) {
                        groups.add(makeGroupEntry(orgIdPrefix, org.getId(), org.getName(), idOverrideAttr, org.getAttributes()));
                    }

                    // Add org groups
                    orgProvider.getOrganizationGroupsByMember(org, user)
                        .forEach((group) -> {
                            String groupPath = "";
                            if (prependOrgToGroupName) {
                                groupPath += org.getName();
                            }
                            groupPath += ModelToRepresentation.buildGroupPath(group);

                            groups.add(makeGroupEntry(groupIdPrefix, group.getId(), groupPath, idOverrideAttr, group.getAttributes()));
                        });

                    return groups.stream();
                })
                .toList();

        // Always tell the OIDCAttributeMapperHelper that this property is multivalued
        // (= an array)
        var config = new HashMap<String, String>(mappingModel.getConfig());
        config.put(ProtocolMapperUtils.MULTIVALUED, Boolean.TRUE.toString());
        mappingModel.setConfig(config);


        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, orgGroups);

        return token;
    }

    private Map<String, String> makeGroupEntry(final String idPrefix, final String id, final String name, final String idOverrideAttr, final Map<String, List<String>> attributes) {
        String gid = idPrefix + id;

        // Allow overriding the ID with the specified attribute
        if (idOverrideAttr != null) {
            var attr = attributes.get(idOverrideAttr);
            if (attr != null && attr.size() > 0) {
                gid = attr.get(0);
            }
        }

        return Map.of("gid", gid, "displayName", name);
    }
}
