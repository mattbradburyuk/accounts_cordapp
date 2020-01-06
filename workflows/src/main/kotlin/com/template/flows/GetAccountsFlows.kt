package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

@StartableByRPC
@InitiatingFlow
class GetAllAccountsFlow: FlowLogic<List<StateAndRef<AccountInfo>>>(){


    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> {

        return serviceHub.accountService.allAccounts()

    }

}

@StartableByRPC
@InitiatingFlow
class GetOurAccountsFlow: FlowLogic<List<StateAndRef<AccountInfo>>>(){


    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> {

        return serviceHub.accountService.ourAccounts()

    }

}

@StartableByRPC
@InitiatingFlow
class GetAccountsForHostFlow(val host: Party): FlowLogic<List<StateAndRef<AccountInfo>>>(){


    @Suspendable
    override fun call(): List<StateAndRef<AccountInfo>> {

        return serviceHub.accountService.accountsForHost(host)

    }

}