package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.states.AccountsDealState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;

import java.util.List;
import java.util.UUID;

@InitiatingFlow
@StartableByRPC
public class GetDealsByAccountFlow extends FlowLogic<List<StateAndRef<AccountsDealState>>> {
    private final List<UUID> accountUUIDs;

    public GetDealsByAccountFlow(List<UUID> accountUUIDs) {
        this.accountUUIDs = accountUUIDs;
    }

    @Suspendable
    @Override
    public List<StateAndRef<AccountsDealState>> call() throws FlowException {
        int DEFAULT_PAGE_SIZE = QueryCriteriaUtils.DEFAULT_PAGE_SIZE;

        Vault.Page<AccountsDealState> results = getServiceHub().getVaultService().queryBy(
                AccountsDealState.class,
                new QueryCriteria.VaultQueryCriteria().withExternalIds(accountUUIDs)
        );
        if (results.getTotalStatesAvailable() > DEFAULT_PAGE_SIZE)
            throw new FlowException("more results than max page size");
        return results.getStates();
    }
}
