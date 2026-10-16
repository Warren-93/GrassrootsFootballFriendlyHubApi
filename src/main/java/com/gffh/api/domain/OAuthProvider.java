package com.gffh.api.domain;

/** The identity providers people can sign in with instead of a password. */
public enum OAuthProvider {

    GOOGLE("Google"),
    APPLE("Apple");

    private final String label;

    OAuthProvider(String label) {
        this.label = label;
    }

    /** The provider's name as people know it, for messages. */
    public String label() { return label; }
}
