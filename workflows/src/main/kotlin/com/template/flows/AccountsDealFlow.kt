package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccountFlow
import com.r3.corda.lib.accounts.workflows.flows.SendKeyForAccountFlow
import com.template.contracts.AccountsDealContract
import com.template.states.AccountDealState
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class AccountsDealFlow(val buyerAccountUUID: UUID, val sellerAccountUUID: UUID, val deal: String): FlowLogic<SignedTransaction>(){


    companion object {
        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
    }

    fun tracker() = ProgressTracker(FINALISING_TRANSACTION)

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val me = serviceHub.myInfo.legalIdentities.first()

        // Get AccountInfos
        val buyerAccount = accountService.accountInfo(buyerAccountUUID)
        val sellerAccount = accountService.accountInfo(sellerAccountUUID)

        if(buyerAccount != null) FlowException("buyerAccount is null")
        if(sellerAccount != null) FlowException("sellerAccount is null")

        val buyerAccountInfo = buyerAccount!!.state.data
        val sellerAccountInfo = sellerAccount!!.state.data

        // Get anonmousParty/keys

        val buyerAnon = subFlow(RequestKeyForAccount(buyerAccountInfo))
        val sellerAnon = subFlow(RequestKeyForAccount(sellerAccountInfo))

        // workout who's who

        lateinit var myAnon: AnonymousParty
        lateinit var otherPartyAnon: AnonymousParty
        lateinit var otherParty: Party

        if ( buyerAccountInfo.host == me){
            myAnon = buyerAnon
            otherPartyAnon = sellerAnon
            otherParty = sellerAccountInfo.host
        } else {
            myAnon = sellerAnon
            otherPartyAnon = buyerAnon
            otherParty = buyerAccountInfo.host
        }


        // create Transaction

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tx = TransactionBuilder(notary)

        tx.addCommand(AccountsDealContract.Commands.CreateDeal(), buyerAnon.owningKey, sellerAnon.owningKey)

        val output = AccountDealState(buyerAnon, sellerAnon, deal)
        tx.addOutputState(output, AccountsDealContract.ID)

        tx.verify(serviceHub)


        // Sign and finalise

        val pst = serviceHub.signInitialTransaction(tx, myAnon.owningKey )

        val otherPartySession = initiateFlow(otherParty)
        val otherPartySig = subFlow(CollectSignatureFlow(pst, otherPartySession, listOf(otherPartyAnon.owningKey)))
        val fst = pst.withAdditionalSignatures(otherPartySig)

        return subFlow(FinalityFlow(fst, setOf(otherPartySession)))

    }
}


@InitiatedBy(AccountsDealFlow::class)
class AccountsDealFlowResponder(val otherPartySession: FlowSession): FlowLogic<Unit>() {


    @Suspendable
    override fun call() {


        val transactionSigner = object: SignTransactionFlow(otherPartySession){

            override fun checkTransaction(stx: SignedTransaction) {}

        }

        val st = subFlow(transactionSigner)

        subFlow(ReceiveFinalityFlow(otherPartySession, st.id, statesToRecord = StatesToRecord.ALL_VISIBLE))


    }

}