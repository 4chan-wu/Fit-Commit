package com.fitcommit.app.services.dto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

public class SignUpResponseTest {
    private final Gson gson = new Gson();

    @Test
    public void emptySecurityResponseIsNotTreatedAsNewSignup() {
        SignUpResponse response = gson.fromJson("{}", SignUpResponse.class);

        assertFalse(response.hasUser());
        assertTrue(response.requiresEmailConfirmation());
    }

    @Test
    public void newUnconfirmedUserHasUserAndRequiresConfirmation() {
        SignUpResponse response = gson.fromJson(
                "{\"user\":{\"id\":\"new-user-id\"}}",
                SignUpResponse.class
        );

        assertTrue(response.hasUser());
        assertTrue(response.requiresEmailConfirmation());
    }
}
