package de.swdec.jaccount.keycloak.spi.extensions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.representations.AccessToken;

public class OrganizationsGroupsStructuredMapperTest {

    @Test
    void shouldAddOrganizationsAsGroups() {
        OrganizationsGroupsStructuredMapper mapper = new OrganizationsGroupsStructuredMapper();

        KeycloakSession session = mock(KeycloakSession.class);
        OrganizationProvider orgProvider = mock(OrganizationProvider.class);
        UserSessionModel userSession = mock(UserSessionModel.class);
        UserModel user = mock(UserModel.class);
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();
        OrganizationModel org = mock(OrganizationModel.class);

        when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
        when(userSession.getUser()).thenReturn(user);
        when(orgProvider.getByMember(user)).thenReturn(Stream.of(org));
        when(org.isEnabled()).thenReturn(true);
        when(org.getId()).thenReturn("org1");
        when(org.getName()).thenReturn("Acme");
        when(org.getAttributes()).thenReturn(Map.of());
        when(orgProvider.getOrganizationGroupsByMember(org, user)).thenReturn(Stream.empty());

        mappingModel.setConfig(
                Map.of(
                        "claim.name", "groups",
                        "orgs_as_groups", "true",
                        "org_id_prefix", "org:",
                        "group_id_prefix", ""));

        AccessToken token = new AccessToken();

        mapper.transformUserInfoToken(
                token,
                mappingModel,
                session,
                userSession,
                mock(ClientSessionContext.class));

        Object claim = token.getOtherClaims().get("groups");
        assertNotNull(claim);

        List<?> groups = (List<?>) claim;
        assertEquals(1, groups.size());

        Map<?, ?> first = (Map<?, ?>) groups.get(0);
        assertEquals("org:org1", first.get("gid"));
        assertEquals("Acme", first.get("displayName"));
    }

    @Test
    void shouldAddOrganizationGroupsWithPrefixAndPrependOrganizationName() {
        OrganizationsGroupsStructuredMapper mapper = new OrganizationsGroupsStructuredMapper();

        KeycloakSession session = mock(KeycloakSession.class);
        OrganizationProvider orgProvider = mock(OrganizationProvider.class);
        UserSessionModel userSession = mock(UserSessionModel.class);
        UserModel user = mock(UserModel.class);
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();

        OrganizationModel org = mock(OrganizationModel.class);
        GroupModel group = mock(GroupModel.class);

        when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
        when(userSession.getUser()).thenReturn(user);
        when(orgProvider.getByMember(user)).thenReturn(Stream.of(org));
        when(org.isEnabled()).thenReturn(true);
        when(org.getId()).thenReturn("org1");
        when(org.getName()).thenReturn("Acme");
        when(org.getAttributes()).thenReturn(Map.of());

        when(group.getId()).thenReturn("group1");
        when(group.getName()).thenReturn("Group 1");
        when(group.getAttributes()).thenReturn(Map.of());
        when(orgProvider.getOrganizationGroupsByMember(org, user)).thenReturn(Stream.of(group));
        when(orgProvider.getByMember(user)).thenReturn(Stream.of(org));

        mappingModel.setConfig(
                Map.of(
                        "claim.name", "groups",
                        "orgs_as_groups", "false",
                        "group_id_prefix", "grp:",
                        "prepend_org_to_group_name", "true"));

        AccessToken token = new AccessToken();

        mapper.transformUserInfoToken(
                token,
                mappingModel,
                session,
                userSession,
                mock(ClientSessionContext.class));

        Object claim = token.getOtherClaims().get("groups");
        assertNotNull(claim);

        List<?> groups = (List<?>) claim;
        assertEquals(1, groups.size());

        Map<?, ?> first = (Map<?, ?>) groups.get(0);
        assertEquals("grp:group1", first.get("gid"));
        assertEquals("Acme/Group 1", first.get("displayName"));
    }

    @Test
    void shouldOverrideIdsFromAttributesForOrganizationsAndGroups() {
        OrganizationsGroupsStructuredMapper mapper = new OrganizationsGroupsStructuredMapper();

        KeycloakSession session = mock(KeycloakSession.class);
        OrganizationProvider orgProvider = mock(OrganizationProvider.class);
        UserSessionModel userSession = mock(UserSessionModel.class);
        UserModel user = mock(UserModel.class);
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();

        OrganizationModel org = mock(OrganizationModel.class);
        GroupModel group = mock(GroupModel.class);

        when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
        when(userSession.getUser()).thenReturn(user);
        when(orgProvider.getByMember(user)).thenReturn(Stream.of(org));
        when(org.isEnabled()).thenReturn(true);
        when(org.getId()).thenReturn("org1");
        when(org.getName()).thenReturn("Acme");
        when(org.getAttributes()).thenReturn(Map.of("legacyId", List.of("legacy-org-id")));

        when(group.getId()).thenReturn("group1");
        when(group.getName()).thenReturn("Group 1");
        when(group.getAttributes()).thenReturn(Map.of("legacyId", List.of("legacy-group-id")));
        when(orgProvider.getOrganizationGroupsByMember(org, user)).thenReturn(Stream.of(group));

        mappingModel.setConfig(
                Map.of(
                        "claim.name", "groups",
                        "orgs_as_groups", "true",
                        "org_id_prefix", "org:",
                        "group_id_prefix", "grp:",
                        "id_override_attr", "legacyId"));

        AccessToken token = new AccessToken();

        mapper.transformUserInfoToken(
                token,
                mappingModel,
                session,
                userSession,
                mock(ClientSessionContext.class));

        Object claim = token.getOtherClaims().get("groups");
        assertNotNull(claim);

        List<?> groups = (List<?>) claim;
        assertEquals(2, groups.size());

        Map<?, ?> orgEntry = (Map<?, ?>) groups.get(0);
        Map<?, ?> groupEntry = (Map<?, ?>) groups.get(1);

        assertEquals("legacy-org-id", orgEntry.get("gid"));
        assertEquals("Acme", orgEntry.get("displayName"));

        assertEquals("legacy-group-id", groupEntry.get("gid"));
        assertEquals("/Group 1", groupEntry.get("displayName"));
    }

    @Test
    void shouldSkipDisabledOrganizations() {
        OrganizationsGroupsStructuredMapper mapper = new OrganizationsGroupsStructuredMapper();

        KeycloakSession session = mock(KeycloakSession.class);
        OrganizationProvider orgProvider = mock(OrganizationProvider.class);
        UserSessionModel userSession = mock(UserSessionModel.class);
        UserModel user = mock(UserModel.class);
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();

        OrganizationModel org = mock(OrganizationModel.class);

        when(session.getProvider(OrganizationProvider.class)).thenReturn(orgProvider);
        when(userSession.getUser()).thenReturn(user);
        when(orgProvider.getByMember(user)).thenReturn(Stream.of(org));
        when(org.isEnabled()).thenReturn(false);

        mappingModel.setConfig(Map.of("claim.name", "groups"));

        AccessToken token = new AccessToken();

        mapper.transformUserInfoToken(
                token,
                mappingModel,
                session,
                userSession,
                mock(ClientSessionContext.class));

        assertNull(token.getOtherClaims().get("groups"));
    }
}
