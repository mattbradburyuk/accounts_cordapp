package com.template

import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.template.flows.AccountsDealFlow
import com.template.flows.AccountsDealFlowResponder
import com.template.states.AccountDealState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class AccountDealFlowTests {
    private val network = MockNetwork(MockNetworkParameters(
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
            cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.template.contracts"),
                    TestCordapp.findCordapp("com.template.states"),
                    TestCordapp.findCordapp("com.template.flows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
            )))
    private val a = network.createNode()
    private val b = network.createNode()
    private val c = network.createNode()

    init {
        listOf(a, b, c).forEach {
            it.registerInitiatedFlow(AccountsDealFlowResponder::class.java)

        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()


    @Test
    fun `deal with accounts on different nodes test`() {

        // set up accounts to use
        val flowA1 = CreateAccount("Node A Account 1")
        val futureA1 = a.startFlow(flowA1)
        network.runNetwork()
        val accountStateAndRefA1 = futureA1.getOrThrow()
        val accountInfoA = accountStateAndRefA1.state.data
        assert(accountInfoA.name == "Node A Account 1")

        val flowB1 = CreateAccount("Node B Account 1")
        val futureB1 = b.startFlow(flowB1)
        network.runNetwork()
        val accountStateAndRefB1 = futureB1.getOrThrow()
        val accountInfoB = accountStateAndRefB1.state.data
        assert(accountInfoB.name == "Node B Account 1")

        val flowC1 = CreateAccount("Node C Account 1")
        val futureC1 = c.startFlow(flowC1)
        network.runNetwork()
        val accountStateAndRefC1 = futureC1.getOrThrow()
        val accountInfoC = accountStateAndRefC1.state.data
        assert(accountInfoC.name == "Node C Account 1")


        // Setup additional accounts on each node

        for (i in 2..5) {

            val flow1 = CreateAccount("Node A Account $i")
            val future1 = a.startFlow(flow1)
            network.runNetwork()
            val result1 = future1.getOrThrow()
            val accountInfo1 = result1.state.data
            assert(accountInfo1.name == "Node A Account $i")

            val flow2 = CreateAccount("Node B Account $i")
            val future2 = b.startFlow(flow2)
            network.runNetwork()
            val result2 = future2.getOrThrow()
            val accountInfo2 = result2.state.data
            assert(accountInfo2.name == "Node B Account $i")

            val flow3 = CreateAccount("Node C Account $i")
            val future3 = c.startFlow(flow3)
            network.runNetwork()
            val result3 = future3.getOrThrow()
            val accountInfo3 = result3.state.data
            assert(accountInfo3.name == "Node C Account $i")

        }

        // Share AccountInfos

        val aParty = a.services.myInfo.legalIdentities.first()
        val bParty = b.services.myInfo.legalIdentities.first()
        val cParty = c.services.myInfo.legalIdentities.first()

        val flowA2 = ShareAccountInfo(accountStateAndRefA1, listOf(bParty,cParty))
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        futureA2.getOrThrow()


        val flowB2 = ShareAccountInfo(accountStateAndRefB1, listOf(aParty,cParty))
        val futureB2 = b.startFlow(flowB2)
        network.runNetwork()
        futureB2.getOrThrow()

        val flowC2 = ShareAccountInfo(accountStateAndRefC1, listOf(aParty,bParty))
        val futureC2 = c.startFlow(flowC2)
        network.runNetwork()
        futureC2.getOrThrow()


        // for debugging
        val atemp = a.services.accountService.allAccounts()
        val btemp = b.services.accountService.allAccounts()
        val ctemp = c.services.accountService.allAccounts()



        // Do the deal: buyer A (Initiator), seller B, broker C

        val flowA3 = AccountsDealFlow(accountInfoA.identifier.id, accountInfoB.identifier.id, accountInfoC.identifier.id,"Buy some Sausages - A Initiates")
        val futureA3 = a.startFlow(flowA3)
        network.runNetwork()
        val resultA3 = futureA3.getOrThrow()
        val deal1 = resultA3.coreTransaction.outputStates.single() as AccountDealState
        assert(deal1.deal == "Buy some Sausages - A Initiates")

        val buyerUUID1 = a.services.accountService.accountIdForKey(deal1.buyer.owningKey)
        val sellerUUID1 = a.services.accountService.accountIdForKey(deal1.seller.owningKey)
        val brokerUUID1 = a.services.accountService.accountIdForKey(deal1.broker.owningKey)
        assert(accountInfoA.identifier.id == buyerUUID1)
        assert(accountInfoB.identifier.id == sellerUUID1)
        assert(accountInfoC.identifier.id == brokerUUID1)


        // Do the deal: buyer A , seller B (Initiator), broker C

        val flowB3 = AccountsDealFlow(accountInfoA.identifier.id, accountInfoB.identifier.id, accountInfoC.identifier.id, "Buy some Sausages - B Initiates")
        val futureB3 = b.startFlow(flowB3)
        network.runNetwork()
        val resultB3 = futureB3.getOrThrow()
        val deal2 = resultB3.coreTransaction.outputStates.single() as AccountDealState
        assert(deal2.deal == "Buy some Sausages - B Initiates")

        val buyerUUID2 = b.services.accountService.accountIdForKey(deal2.buyer.owningKey)
        val sellerUUID2 = b.services.accountService.accountIdForKey(deal2.seller.owningKey)
        val brokerUUID2 = b.services.accountService.accountIdForKey(deal2.broker.owningKey)
        assert(accountInfoA.identifier.id == buyerUUID2)
        assert(accountInfoB.identifier.id == sellerUUID2)
        assert(accountInfoC.identifier.id == brokerUUID2)


        // Do the deal: buyer A , seller B , broker C (Initiator)

        val flowC3 = AccountsDealFlow(accountInfoA.identifier.id, accountInfoB.identifier.id, accountInfoC.identifier.id, "Buy some Sausages - C Initiates")
        val futureC3 = c.startFlow(flowC3)
        network.runNetwork()
        val resultC3 = futureC3.getOrThrow()
        val deal3 = resultC3.coreTransaction.outputStates.single() as AccountDealState
        assert(deal3.deal == "Buy some Sausages - C Initiates")

        val buyerUUID3 = b.services.accountService.accountIdForKey(deal2.buyer.owningKey)
        val sellerUUID3 = b.services.accountService.accountIdForKey(deal2.seller.owningKey)
        val brokerUUID3 = b.services.accountService.accountIdForKey(deal2.broker.owningKey)
        assert(accountInfoA.identifier.id == buyerUUID3)
        assert(accountInfoB.identifier.id == sellerUUID3)
        assert(accountInfoC.identifier.id == brokerUUID3)

    }


    @Test
    fun `deal with accounts on same node test`() {

        // set up accounts to use

        val flowA1 = CreateAccount("Node A Account 1")
        val futureA1 = a.startFlow(flowA1)
        network.runNetwork()
        val resultA1 = futureA1.getOrThrow()
        val accountInfoA1 = resultA1.state.data
        assert(accountInfoA1.name == "Node A Account 1")

        val flowA2 = CreateAccount("Node A Account 2")
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        val resultA2 = futureA2.getOrThrow()
        val accountInfoA2 = resultA2.state.data
        assert(accountInfoA2.name == "Node A Account 2")

        val flowA3 = CreateAccount("Node A Account 3")
        val futureA3 = a.startFlow(flowA3)
        network.runNetwork()
        val resultA3 = futureA3.getOrThrow()
        val accountInfoA3 = resultA3.state.data
        assert(accountInfoA3.name == "Node A Account 3")

        // Setup additional accounts

        for (i in 4..5) {

            val flow1 = CreateAccount("Node A Account $i")
            val future1 = a.startFlow(flow1)
            network.runNetwork()
            val result1 = future1.getOrThrow()
            val accountInfo1 = result1.state.data
            assert(accountInfo1.name == "Node A Account $i")

        }

        // Note, no need to share keys as all on the same node

        // for debug
        val atemp = a.services.accountService.allAccounts()

        // Do the deal: buyer A (Initiator), seller A, broker A

        val flowA4 = AccountsDealFlow(accountInfoA1.identifier.id, accountInfoA2.identifier.id, accountInfoA3.identifier.id, "Buy some Sausages - A Initiates")
        val futureA4 = a.startFlow(flowA4)
        network.runNetwork()
        val resultA4 = futureA4.getOrThrow()
        val deal1 = resultA4.coreTransaction.outputStates.single() as AccountDealState
        assert(deal1.deal == "Buy some Sausages - A Initiates")


        val buyerUUID1 = a.services.accountService.accountIdForKey(deal1.buyer.owningKey)
        val sellerUUID1 = a.services.accountService.accountIdForKey(deal1.seller.owningKey)
        val brokerUUID1 = a.services.accountService.accountIdForKey(deal1.broker.owningKey)
        assert(accountInfoA1.identifier.id == buyerUUID1)
        assert(accountInfoA2.identifier.id == sellerUUID1)
        assert(accountInfoA3.identifier.id == brokerUUID1)

    }

    @Test
    fun `deal with mixed local and remote accounts`() {

        // set up accounts to use

        val flowA1 = CreateAccount("Node A Account 1")
        val futureA1 = a.startFlow(flowA1)
        network.runNetwork()
        val accountStateAndRefA1 = futureA1.getOrThrow()
        val accountInfoA1 = accountStateAndRefA1.state.data
        assert(accountInfoA1.name == "Node A Account 1")

        val flowA2 = CreateAccount("Node A Account 2")
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        val accountStateAndRefA2 = futureA2.getOrThrow()
        val accountInfoA2 = accountStateAndRefA2.state.data
        assert(accountInfoA2.name == "Node A Account 2")

        val flowB1 = CreateAccount("Node B Account 1")
        val futureB1 = b.startFlow(flowB1)
        network.runNetwork()
        val accountStateAndRefB1 = futureB1.getOrThrow()
        val accountInfoB1 = accountStateAndRefB1.state.data
        assert(accountInfoB1.name == "Node B Account 1")

        // Setup additional accounts

        for (i in 3..5) {

            val flow1 = CreateAccount("Node A Account $i")
            val future1 = a.startFlow(flow1)
            network.runNetwork()
            val result1 = future1.getOrThrow()
            val accountInfo1 = result1.state.data
            assert(accountInfo1.name == "Node A Account $i")

        }

        // Share AccountInfos

        val aParty = a.services.myInfo.legalIdentities.first()
        val bParty = b.services.myInfo.legalIdentities.first()
        val cParty = c.services.myInfo.legalIdentities.first()

        val flowA3 = ShareAccountInfo(accountStateAndRefA1, listOf(bParty))
        val futureA3 = a.startFlow(flowA3)
        network.runNetwork()
        futureA3.getOrThrow()

        val flowA4 = ShareAccountInfo(accountStateAndRefA2, listOf(bParty))
        val futureA4 = a.startFlow(flowA4)
        network.runNetwork()
        futureA4.getOrThrow()

        val flowB3 = ShareAccountInfo(accountStateAndRefB1, listOf(aParty))
        val futureB3 = b.startFlow(flowB3)
        network.runNetwork()
        futureB3.getOrThrow()

        // for debug
        val atemp = a.services.accountService.allAccounts()
        val btemp = b.services.accountService.allAccounts()

        // Do the deal: buyer A (Initiator), seller A, broker B

        val flowA5 = AccountsDealFlow(accountInfoA1.identifier.id, accountInfoA2.identifier.id, accountInfoB1.identifier.id, "Buy some Sausages - A Initiates")
        val futureA5 = a.startFlow(flowA5)
        network.runNetwork()
        val resultA5 = futureA5.getOrThrow()
        val deal1 = resultA5.coreTransaction.outputStates.single() as AccountDealState
        assert(deal1.deal == "Buy some Sausages - A Initiates")

        val buyerUUID1 = a.services.accountService.accountIdForKey(deal1.buyer.owningKey)
        val sellerUUID1 = a.services.accountService.accountIdForKey(deal1.seller.owningKey)
        val brokerUUID1 = a.services.accountService.accountIdForKey(deal1.broker.owningKey)
        assert(accountInfoA1.identifier.id == buyerUUID1)
        assert(accountInfoA2.identifier.id == sellerUUID1)
        assert(accountInfoB1.identifier.id == brokerUUID1)


        // Do the deal: buyer A, seller A, broker B (Initiator)

        val flowB5 = AccountsDealFlow(accountInfoA1.identifier.id, accountInfoA2.identifier.id, accountInfoB1.identifier.id, "Buy some Sausages - A Initiates")
        val futureB5 = a.startFlow(flowB5)
        network.runNetwork()
        val resultB5 = futureB5.getOrThrow()
        val deal2 = resultB5.coreTransaction.outputStates.single() as AccountDealState
        assert(deal2.deal == "Buy some Sausages - A Initiates")

        val buyerUUID2= a.services.accountService.accountIdForKey(deal1.buyer.owningKey)
        val sellerUUID2 = a.services.accountService.accountIdForKey(deal1.seller.owningKey)
        val brokerUUID2 = a.services.accountService.accountIdForKey(deal1.broker.owningKey)
        assert(accountInfoA1.identifier.id == buyerUUID2)
        assert(accountInfoA2.identifier.id == sellerUUID2)
        assert(accountInfoB1.identifier.id == brokerUUID2)



    }

}