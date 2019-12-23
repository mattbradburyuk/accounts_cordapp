package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.internal.accountService
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


/**
 * Note, this returns all the AccountDealStates for the account which the key belongs to, not just the AccountDealState which the key was used for
 */
@InitiatingFlow
@StartableByRPC
class GetDealsByKeyFlow(val pubKey: PublicKey): FlowLogic<List<StateAndRef<AccountDealState>>>() {


    @Suspendable
    override fun call():List<StateAndRef<AccountDealState>> {

        val accountUUID = serviceHub.accountService.accountIdForKey(pubKey)

        accountUUID ?: throw FlowException("No accounts found for key $pubKey")

        val results = serviceHub.vaultService.queryBy(
                AccountDealState::class.java,
                QueryCriteria.VaultQueryCriteria(externalIds = listOf(accountUUID))

        )
        if (results.totalStatesAvailable > DEFAULT_PAGE_NUM) throw FlowException("More results than max page size")

        return results.states

    }

}