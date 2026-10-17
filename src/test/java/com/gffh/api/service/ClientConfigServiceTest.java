package com.gffh.api.service;

import com.gffh.api.config.OAuthProperties;
import com.gffh.api.dto.ClientConfigDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientConfigServiceTest {

    @Test
    void supportEmailIsTheConfiguredAddressFirst() {
        assertEquals("help@example.com",
                ClientConfigService.supportEmail(" help@example.com ", "Kickabout <noreply@example.com>"));
    }

    @Test
    void supportEmailFallsBackToTheBareMailFromAddress() {
        assertEquals("noreply@example.com", ClientConfigService.supportEmail("", "Kickabout <noreply@example.com>"));
        assertEquals("noreply@example.com", ClientConfigService.supportEmail(null, "noreply@example.com"));
    }

    @Test
    void supportEmailIsNullWhenNothingUsableIsConfigured() {
        assertNull(ClientConfigService.supportEmail("", ""));
        assertNull(ClientConfigService.supportEmail(null, null));
        assertNull(ClientConfigService.supportEmail("", "Kickabout"));
    }

    @Test
    void providersAreOffUntilConfigured() {
        ClientConfigDtos.ClientConfigView view = new ClientConfigService(new OAuthProperties(), "", "").current();

        assertNull(view.supportEmail());
        assertFalse(view.google().enabled());
        assertNull(view.google().clientId());
        assertFalse(view.apple().enabled());
        assertNull(view.apple().servicesId());
        assertFalse(view.apple().iosEnabled());
    }

    @Test
    void configuredProvidersArePublishedWithTheirPublicIds() {
        OAuthProperties oauth = new OAuthProperties();
        oauth.getGoogle().setClientId("123-abc.apps.googleusercontent.com");
        oauth.getGoogle().setAdditionalClientIds(List.of("ios-client", " "));
        oauth.getApple().setServicesId("com.kickabout.web");
        oauth.getApple().setBundleId("com.gffh.mobile");

        ClientConfigDtos.ClientConfigView view = new ClientConfigService(oauth, "", "").current();

        assertTrue(view.google().enabled());
        assertEquals("123-abc.apps.googleusercontent.com", view.google().clientId());
        assertTrue(view.apple().enabled());
        assertEquals("com.kickabout.web", view.apple().servicesId());
        assertEquals(List.of("123-abc.apps.googleusercontent.com", "ios-client"), oauth.getGoogle().audiences());
        assertEquals(List.of("com.kickabout.web", "com.gffh.mobile"), oauth.getApple().audiences());
    }

    @Test
    void appleIsEnabledForIosAloneButHasNoWebServicesId() {
        OAuthProperties oauth = new OAuthProperties();
        oauth.getApple().setBundleId("com.gffh.mobile");

        ClientConfigDtos.ClientConfigView view = new ClientConfigService(oauth, "", "").current();

        assertTrue(view.apple().enabled());
        assertNull(view.apple().servicesId());
        assertTrue(view.apple().iosEnabled());
    }

    @Test
    void appleForWebAloneDoesNotTurnOnTheIosButton() {
        OAuthProperties oauth = new OAuthProperties();
        oauth.getApple().setServicesId("com.kickabout.web");

        ClientConfigDtos.ClientConfigView view = new ClientConfigService(oauth, "", "").current();

        assertTrue(view.apple().enabled());
        assertEquals("com.kickabout.web", view.apple().servicesId());
        assertFalse(view.apple().iosEnabled());
    }
}
