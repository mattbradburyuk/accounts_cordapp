package com.template.flows;

import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;

import java.security.PublicKey;
import java.util.UUID;

/**
 * Data class to package up the info required for a node to register a key to an account
 */
@CordaSerializable
public class InfoToRegisterKey {
    private final PublicKey publicKey;
    private final Party party;
    private final UUID externalId;

    @ConstructorForDeserialization
    public InfoToRegisterKey(PublicKey publicKey, Party party, UUID externalId) {
        this.publicKey = publicKey;
        this.party = party;
        this.externalId = externalId;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public Party getParty() {
        return party;
    }

    public UUID getExternalId() {
        return externalId;
    }
}
