package com.arijit.authservice.entity;

// How this user proved who they are. LOCAL = email+password stored by us.
// GOOGLE/GITHUB = they logged in via that provider's OAuth2 flow.
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB
}
