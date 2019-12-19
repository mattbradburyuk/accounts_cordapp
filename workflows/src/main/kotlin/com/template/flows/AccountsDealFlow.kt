//package com.template.flows
//
//import co.paralleluniverse.fibers.Suspendable
//import com.r3.corda.lib.accounts.workflows.accountService
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.InitiatingFlow
//import net.corda.core.flows.StartableByRPC
//import net.corda.core.transactions.SignedTransaction
//
//@InitiatingFlow
//@StartableByRPC
//class AccountsDealFlow(val buyerAccountName: String, val sellerAccountName: String, val deal: String): FlowLogic<SignedTransaction>(){
//
//
//    @Suspendable
//    override fun call(): SignedTransaction {
//
//
//
//
//
//
//        // Get AccountInfos
//        val buyerAccount = accountService.accountInfo(buyerAccountName)
//
//
//
//        // Get Keys
//
//
//
//        // create DealState
//
//
//        // create Transaction
//
//
//    }
//
//
//}