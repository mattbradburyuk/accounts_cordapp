package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.template.contracts.AccountsDealContract
import com.template.states.AccountDealState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.util.*

@InitiatingFlow
@StartableByRPC
class AccountsDealFlow(val buyerAccountUUID: UUID, val sellerAccountUUID: UUID, val deal: String): FlowLogic<SignedTransaction>(){


    //todo() : make this so it works when both accounts are on the same node currently fails with the following error:

//    >>> a.createDealByName("Account A1", "Matt test account", "buy some chips")
//    java.util.concurrent.ExecutionException: net.corda.core.CordaRuntimeException: java.lang.IllegalArgumentException: Do not provide flow sessions for the local node. FinalityFlow will record the notarised transaction locally.
//    at java.util.concurrent.CompletableFuture.reportGet(CompletableFuture.java:357)
//    at java.util.concurrent.CompletableFuture.get(CompletableFuture.java:1895)
//    at net.corda.core.internal.concurrent.CordaFutureImpl.get(CordaFutureImpl.kt)
//    at Line_172.createDealByName(Unknown Source)
//    Caused by: net.corda.core.CordaRuntimeException: java.lang.IllegalArgumentException: Do not provide flow sessions for the local node. FinalityFlow will record the notarised transaction locally.

    companion object {
        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
    }

    fun tracker() = ProgressTracker(FINALISING_TRANSACTION)

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Get AccountInfos

        val buyerAccountStateAndRef = accountService.accountInfo(buyerAccountUUID)
        val sellerAccountStateAndRef = accountService.accountInfo(sellerAccountUUID)

        if(buyerAccountStateAndRef == null) throw FlowException("buyerAccount not found")
        if(sellerAccountStateAndRef == null) throw FlowException("sellerAccount not found")

        val buyerAccountInfo = buyerAccountStateAndRef.state.data
        val sellerAccountInfo = sellerAccountStateAndRef.state.data

        // Get anonymousParty/keys

        val buyerAnon = subFlow(RequestKeyForAccount(buyerAccountInfo))
        val sellerAnon = subFlow(RequestKeyForAccount(sellerAccountInfo))

        // workout who's who

        //todo(): need to update for case where both buyer and seller are on same host

        val me = serviceHub.myInfo.legalIdentities.first()
        lateinit var myAnon: AnonymousParty
        lateinit var myAccount: AccountInfo
        lateinit var otherPartyAnon: AnonymousParty
        lateinit var otherParty: Party
        lateinit var otherAccount: AccountInfo


        if ( buyerAccountInfo.host == me){
            myAnon = buyerAnon
            myAccount = buyerAccountInfo
            otherPartyAnon = sellerAnon
            otherParty = sellerAccountInfo.host
            otherAccount = sellerAccountInfo
        } else {
            myAnon = sellerAnon
            myAccount = sellerAccountInfo
            otherPartyAnon = buyerAnon
            otherParty = buyerAccountInfo.host
            otherAccount = buyerAccountInfo
        }


        // Send the Initiators key and mapping to Account to Responder.
        // The responder will need the Initiators key to be register to the Initiators account to execute checkTransaction().
        // Can't use 'ShareStateAndSyncAccounts' because that shares the Key and mapping after the transactions has been completed,
        // which is no good if the responder flow needs the mapping in checkTransaction().

        val infoToRegisterKey = InfoToRegisterKey(myAnon.owningKey, myAccount.host,myAccount.identifier.id )
        val otherPartySession = initiateFlow(otherParty)
        otherPartySession.send(infoToRegisterKey)

        // create Transaction

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tx = TransactionBuilder(notary)

        tx.addCommand(AccountsDealContract.Commands.CreateDeal(), buyerAnon.owningKey, sellerAnon.owningKey)

        val output = AccountDealState(buyerAnon, sellerAnon, deal)
        tx.addOutputState(output, AccountsDealContract.ID)

        tx.verify(serviceHub)

        // Sign and finalise

        val pst = serviceHub.signInitialTransaction(tx, myAnon.owningKey )

        val otherPartySig = subFlow(CollectSignatureFlow(pst, otherPartySession, listOf(otherPartyAnon.owningKey)))
        val fst = pst.withAdditionalSignatures(otherPartySig)

        return subFlow(FinalityFlow(fst, setOf(otherPartySession)))

    }
}


@InitiatedBy(AccountsDealFlow::class)
class AccountsDealFlowResponder(val otherPartySession: FlowSession): FlowLogic<Unit>() {


    @Suspendable
    override fun call() {


        val infoToRegisterKey = otherPartySession.receive<InfoToRegisterKey>().unwrap {it}
        serviceHub.identityService.registerKey(infoToRegisterKey.publicKey, infoToRegisterKey.party, infoToRegisterKey.externalId)

        val transactionSigner = object: SignTransactionFlow(otherPartySession){

            override fun checkTransaction(stx: SignedTransaction) {


                // todo(): Work out why this is not working, not finding buyerAccount from the owningKey - which makes sense as buyer (initiator) generates a key but doesn't send it over to the seller (responder)
                // So how does the responding node know who the buyer's account is?
                // Investigate 'ShareStateAndSyncAccounts' Flow
                requireThat {

                    val deal = stx.coreTransaction.outputs.single().data as AccountDealState
                    val buyerAccount = serviceHub.accountService.accountInfo(deal.buyer.owningKey)
                    val sellerAccount = serviceHub.accountService.accountInfo(deal.seller.owningKey)

                    // check the Responding Node knows abnout the Accounts used in the transaction
                    "The responder can resolve buyer's Account from the buyer's Pubic Key" using (buyerAccount != null)
                    "The responder can resolve seller's Account from the seller's Pubic Key" using (sellerAccount != null)

                }
            }

        }

        val st = subFlow(transactionSigner)

        subFlow(ReceiveFinalityFlow(otherPartySession, st.id, statesToRecord = StatesToRecord.ALL_VISIBLE))


    }

}

/**
 * Data class to package up the info required for a node to register a key to an account
 */

@CordaSerializable
data class InfoToRegisterKey(val publicKey: PublicKey, val party: Party, val externalId: UUID? = null)