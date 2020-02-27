package com.template.states;

import com.google.common.collect.ImmutableList;
import com.template.contracts.AccountsDealContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@BelongsToContract(AccountsDealContract.class)
public class AccountsDealState implements ContractState {
    private final AnonymousParty buyer;
    private final AnonymousParty seller;
    private final AnonymousParty broker;
    private final String deal;

    public AccountsDealState(AnonymousParty buyer, AnonymousParty seller, AnonymousParty broker, String deal) {
        this.buyer = buyer;
        this.seller = seller;
        this.broker = broker;
        this.deal = deal;
    }

    public String getDeal() {
        return deal;
    }

    public AnonymousParty getBuyer() {
        return buyer;
    }

    public AnonymousParty getSeller() {
        return seller;
    }

    public AnonymousParty getBroker() {
        return broker;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(buyer, seller, broker);
    }
}
