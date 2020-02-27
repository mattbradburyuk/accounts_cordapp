package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.template.states.AccountsDealState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;

import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

/**
 * Note, this returns all the AccountDealStates for the account which the key belongs to, not just the AccountDealState which the key was used for
 */
@InitiatingFlow
@StartableByRPC
public class GetDealsByKeyFlow extends FlowLogic<List<StateAndRef<AccountsDealState>>> {
    private final PublicKey pubKey;

    public GetDealsByKeyFlow(PublicKey pubKey) {
        this.pubKey = pubKey;
    }

    @Suspendable
    @Override
    public List<StateAndRef<AccountsDealState>> call() throws FlowException {
        int DEFAULT_PAGE_SIZE = QueryCriteriaUtils.DEFAULT_PAGE_SIZE;

        AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);

        UUID accountUUID = accountService.accountIdForKey(pubKey);
        if (accountUUID == null) throw new FlowException("No accounts found for key " + pubKey);

        Vault.Page<AccountsDealState> results = getServiceHub().getVaultService().queryBy(
                AccountsDealState.class,
                new QueryCriteria.VaultQueryCriteria().withExternalIds(ImmutableList.of(accountUUID))
        );

        if (results.getTotalStatesAvailable() > DEFAULT_PAGE_SIZE) throw new FlowException("More results than max page size");

        return results.getStates();
    }
}
