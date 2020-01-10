package com.template

import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.template.flows.*
import com.template.states.AccountDealState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test


class GetDealsFlowTest {
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
            it.registerInitiatedFlow(DealResponderFlow::class.java)
            it.registerInitiatedFlow(AccountsDealFlowResponder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()


    @Test
    fun `get deals for account`(){

        // set up accounts to use

        val flowA1 = CreateAccountFlow("Node A Account 1")
        val futureA1 = a.startFlow(flowA1)
        network.runNetwork()
        val accountStateAndRefA1 = futureA1.getOrThrow()
        val accountInfoA1 = accountStateAndRefA1.state.data
        assert(accountInfoA1.name == "Node A Account 1")

        val flowA2 = CreateAccountFlow("Node A Account 2")
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        val accountStateAndRefA2 = futureA2.getOrThrow()
        val accountInfoA2 = accountStateAndRefA2.state.data
        assert(accountInfoA2.name == "Node A Account 2")

        val flowB1 = CreateAccountFlow("Node B Account 1")
        val futureB1 = b.startFlow(flowB1)
        network.runNetwork()
        val accountStateAndRefB1 = futureB1.getOrThrow()
        val accountInfoB1 = accountStateAndRefB1.state.data
        assert(accountInfoB1.name == "Node B Account 1")

        val flowC1 = CreateAccountFlow("Node C Account 1")
        val futureC1 = c.startFlow(flowC1)
        network.runNetwork()
        val accountStateAndRefC1 = futureC1.getOrThrow()
        val accountInfoC1 = accountStateAndRefC1.state.data
        assert(accountInfoC1.name == "Node C Account 1")


        // Setup additional accounts

        for (i in 3..5) {

            val flow1 = CreateAccountFlow("Node A Account $i")
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

        val flowB5 = AccountsDealFlow(accountInfoA1.identifier.id, accountInfoA2.identifier.id, accountInfoB1.identifier.id, "Buy some Sausages - B Initiates")
        val futureB5 = a.startFlow(flowB5)
        network.runNetwork()
        val resultB5 = futureB5.getOrThrow()
        val deal2 = resultB5.coreTransaction.outputStates.single() as AccountDealState
        assert(deal2.deal == "Buy some Sausages - B Initiates")

        val buyerUUID2= a.services.accountService.accountIdForKey(deal1.buyer.owningKey)
        val sellerUUID2 = a.services.accountService.accountIdForKey(deal1.seller.owningKey)
        val brokerUUID2 = a.services.accountService.accountIdForKey(deal1.broker.owningKey)
        assert(accountInfoA1.identifier.id == buyerUUID2)
        assert(accountInfoA2.identifier.id == sellerUUID2)
        assert(accountInfoB1.identifier.id == brokerUUID2)



        // Query the vault by account UUID

        // Check A has both deals

        val flowA6 = GetDealsByAccountFlow(listOf(accountInfoA1.identifier.id))
        val futureA6 = a.startFlow(flowA6)
        network.runNetwork()
        val resultA6 = futureA6.getOrThrow()
        println("MB: $resultA6")

        val statesReturnedA6 = resultA6.map{ it.state.data}
        val dealsReturnedA6 = statesReturnedA6.map{ it.deal}

        assert(dealsReturnedA6.size == 2)
        assert(dealsReturnedA6.contains("Buy some Sausages - A Initiates"))
        assert(dealsReturnedA6.contains("Buy some Sausages - B Initiates"))


        // Check C doesn't see either deal

        val flowC6 = GetDealsByAccountFlow(listOf(accountInfoC1.identifier.id))
        val futureC6 = c.startFlow(flowC6)
        network.runNetwork()
        val resultC6 = futureC6.getOrThrow()
        println("MB: $resultC6")

        val statesReturnedC6 = resultC6.map{ it.state.data}
        val dealsReturnedC6 = statesReturnedC6.map{ it.deal}

        assert(dealsReturnedC6.isEmpty())

        // Query the vault by buyer key for deal1

        // Check A1 Key returns both deals

        val flowA7 = GetDealsByKeyFlow(deal1.buyer.owningKey)
        val futureA7 = a.startFlow(flowA7)
        network.runNetwork()
        val resultA7 = futureA7.getOrThrow()
        println("MB: $resultA7")

        val statesReturnedA7 = resultA7.map{ it.state.data}
        val dealsReturnedA7 = statesReturnedA7.map{ it.deal}

        assert(dealsReturnedA7.size == 2)
        assert(dealsReturnedA7.contains("Buy some Sausages - A Initiates"))
        assert(dealsReturnedA7.contains("Buy some Sausages - B Initiates"))


    }
}
