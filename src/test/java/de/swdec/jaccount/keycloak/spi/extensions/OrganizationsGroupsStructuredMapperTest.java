package de.swdec.jaccount.keycloak.spi.extensions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.keycloak.models.*;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.representations.AccessToken;

public class OrganizationsGroupsStructuredMapperTest {

    @Test
    void shouldAddOrganizationsAsGroups() {
        OrganizationsGroupsStructuredMapper mapper =
            new OrganizationsGroupsStructuredMapper();

        // ----- mocks -----

        KeycloakSession session = mock(KeycloakSession.class);
        OrganizationProvider orgProvider = mock(OrganizationProvider.class);
        UserSessionModel userSession = mock(UserSessionModel.class);
        UserModel user = mock(UserModel.class);
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();

        OrganizationModel org = mock(OrganizationModel.class);

        when(session.getProvider(OrganizationProvider.class)).thenReturn(
            orgProvider
        );

        when(userSession.getUser()).thenReturn(user);

        when(orgProvider.getByMember(user)).thenReturn(Stream.of(org));

        when(org.getId()).thenReturn("org1");
        when(org.getName()).thenReturn("Acme");
        when(org.isEnabled()).thenReturn(true);

        // ----- mapper config -----

        mappingModel.setConfig(
            Map.of(
                "claim.name",
                "groups",
                "orgs_as_groups",
                "true",
                "org_id_prefix",
                "org:",
                "group_id_prefix",
                ""
            )
        );

        AccessToken token = new AccessToken();

        // ----- execute -----

        mapper.transformUserInfoToken(
            token,
            mappingModel,
            session,
            userSession,
            mock(ClientSessionContext.class)
        );

        // ----- assertions -----

        Object claim = token.getOtherClaims().get("groups");

        assertNotNull(claim);

        List<?> groups = (List<?>) claim;

        Map<?, ?> first = (Map<?, ?>) groups.get(0);

        assertEquals("org:org1", first.get("id"));
        assertEquals("Acme", first.get("name"));
    }
}
