package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.DealContract
import com.template.states.DealState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class DealInitiatorFlow(val buyer: Party, val seller: Party, val deal: String) : FlowLogic<SignedTransaction>() {


    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
// Initiator flow logic goes here.


        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val me = serviceHub.myInfo.legalIdentities.first()

        val deal = DealState(buyer, seller, deal)


        val tx = TransactionBuilder(notary)
        tx.addCommand(DealContract.Commands.Transfer(), me.owningKey)
        tx.addOutputState(deal, DealContract.ID)

        tx.verify(serviceHub)

        val pst = serviceHub.signInitialTransaction(tx)

        val counterparties = listOf(seller, buyer) - me

        val sessions = counterparties.map{initiateFlow( it )}

        return subFlow(FinalityFlow(pst, sessions))
    }
}

@InitiatedBy(com.template.flows.DealInitiatorFlow::class)
class DealResponderFlow(val otherPartySession: FlowSession): FlowLogic<SignedTransaction>(){

    @Suspendable
    override fun call(): SignedTransaction {

        return subFlow(ReceiveFinalityFlow(otherPartySession))



    }



}