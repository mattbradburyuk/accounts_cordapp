package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.states.AccountDealState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.QueryCriteria
import java.security.PublicKey
import java.util.*

@InitiatingFlow
@StartableByRPC
class GetDealsByAccountFlow(val accountUUIDs: List<UUID>): FlowLogic<List<StateAndRef<AccountDealState>>>() {

    @Suspendable
    override fun call():List<StateAndRef<AccountDealState>> {


        val results = serviceHub.vaultService.queryBy(
                AccountDealState::class.java,
                QueryCriteria.VaultQueryCriteria(externalIds = accountUUIDs)

        )
        if (results.totalStatesAvailable > DEFAULT_PAGE_NUM) throw FlowException("More results than max page size")

    return results.states

    }

}