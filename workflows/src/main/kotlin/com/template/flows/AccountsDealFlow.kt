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


// Note this assumes that Account UUID is unique at the network level - this is not necessarily valid only host-account UUID combined are guaranteed to be unique

@InitiatingFlow
@StartableByRPC
class AccountsDealFlow(val buyerAccountUUID: UUID, val sellerAccountUUID: UUID, val brokerAccountUUID: UUID, val deal: String): FlowLogic<SignedTransaction>(){

    @Suspendable
    override fun call(): SignedTransaction {

        // Get AccountInfos

        // todo: change to elvis operator

        val buyerAccountStateAndRef = accountService.accountInfo(buyerAccountUUID)
        val sellerAccountStateAndRef = accountService.accountInfo(sellerAccountUUID)
        val brokerAccountStateAndRef = accountService.accountInfo(brokerAccountUUID)

        if(buyerAccountStateAndRef == null) throw FlowException("buyerAccount not found")
        if(sellerAccountStateAndRef == null) throw FlowException("sellerAccount not found")
        if(brokerAccountStateAndRef == null) throw FlowException("brokerAccount not found")

        val buyerAccountInfo = buyerAccountStateAndRef.state.data
        val sellerAccountInfo = sellerAccountStateAndRef.state.data
        val brokerAccountInfo = brokerAccountStateAndRef.state.data

        // Get anonymousParty/keys

        val buyerAnon = subFlow(RequestKeyForAccount(buyerAccountInfo))
        val sellerAnon = subFlow(RequestKeyForAccount(sellerAccountInfo))
        val brokerAnon = subFlow(RequestKeyForAccount(brokerAccountInfo))

        // maps for accounts

        val buyerMap = AccountMapper(buyerAccountInfo, buyerAnon)
        val sellerMap = AccountMapper(sellerAccountInfo, sellerAnon)
        val brokerMap = AccountMapper(brokerAccountInfo, brokerAnon)
        val accountMaps: List<AccountMapper> = listOf(buyerMap, sellerMap, brokerMap)

        val myNode = serviceHub.myInfo.legalIdentities.first()


        // create a list of Keys To Share ie keys created by this node

        val keysToShare = accountMaps.map {InfoToRegisterKey(
                it.anonParty.owningKey,
                it.accountInfo.host,
                it.accountInfo.identifier.id)}

        // establish FlowSessions with account host which are not us and send keysToShare

        accountMaps.filter { it.accountInfo.host != myNode }.map {
            it.sessionToHost = initiateFlow(it.accountInfo.host)
            it.sessionToHost?.send(keysToShare)
        }
        // create and verify Transaction

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tx = TransactionBuilder(notary)
        tx.addCommand(AccountsDealContract.Commands.CreateDeal(), buyerAnon.owningKey, sellerAnon.owningKey, brokerAnon.owningKey)
        val output = AccountDealState(buyerAnon, sellerAnon, brokerAnon, deal)
        tx.addOutputState(output, AccountsDealContract.ID)
        tx.verify(serviceHub)

        // This node sign

        val localKeysToSign = accountMaps.filter{ it.accountInfo.host == myNode}.map { it.anonParty.owningKey }
        val locallySignedTx = serviceHub.signInitialTransaction(tx, localKeysToSign )


        // Other nodes sign

        val otherSignatures = accountMaps.filter{ it.accountInfo.host != myNode}.flatMap{
            subFlow(CollectSignatureFlow(locallySignedTx, it.sessionToHost!!, listOf(it.anonParty.owningKey)))
        }

        val fullySignedTx = locallySignedTx.withAdditionalSignatures(otherSignatures)

        // Finalise

        val sessions = accountMaps.filter { it.accountInfo.host != myNode}.map { it.sessionToHost } as List<FlowSession>
        return subFlow(FinalityFlow(fullySignedTx, sessions))

    }
}


@InitiatedBy(AccountsDealFlow::class)
class AccountsDealFlowResponder(val otherPartySession: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        // Receive info to register keys created on other nodes

        val infoToRegisterKeys = otherPartySession.receive<List<InfoToRegisterKey>>().unwrap {it}
        infoToRegisterKeys.map { serviceHub.identityService.registerKey(it.publicKey, it.party, it.externalId) }

        val transactionSigner = object: SignTransactionFlow(otherPartySession){

            override fun checkTransaction(stx: SignedTransaction) {

                requireThat {

                    val deal = stx.coreTransaction.outputs.single().data as AccountDealState
                    val buyerAccount = serviceHub.accountService.accountInfo(deal.buyer.owningKey)
                    val sellerAccount = serviceHub.accountService.accountInfo(deal.seller.owningKey)
                    val brokerAccount = serviceHub.accountService.accountInfo(deal.broker.owningKey)

                    // check the Responding Node knows about the Accounts used in the transaction
                    "The responder can resolve buyer's Account from the buyer's Pubic Key" using (buyerAccount != null)
                    "The responder can resolve seller's Account from the seller's Pubic Key" using (sellerAccount != null)
                    "The responder can resolve broker's Account from the seller's Pubic Key" using (brokerAccount != null)
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

/**
 * Convenient class to wrap up the data related to one actor: AccountInfo, Specific key being used and the session to talk to the host.
 */

class AccountMapper(val accountInfo: AccountInfo, val anonParty: AnonymousParty){
    var sessionToHost: FlowSession? = null
}