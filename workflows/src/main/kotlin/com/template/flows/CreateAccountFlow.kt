package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class CreateAccountFlow(val accountName: String): FlowLogic<StateAndRef<AccountInfo>>(){

    @Suspendable
    override fun call(): StateAndRef<AccountInfo> {

        return subFlow(CreateAccount(accountName))

    }
}