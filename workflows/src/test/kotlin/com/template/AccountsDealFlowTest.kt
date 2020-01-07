package com.template

import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.template.flows.AccountsDealFlow
import com.template.flows.CreateAccountFlow
import com.template.flows.DealResponderFlow
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

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(DealResponderFlow::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()


    @Test
    fun `deal with accounts test`(){

        // set up accounts to use
        val flowA1 = CreateAccountFlow("Node A Account 1")
        val futureA1 = a.startFlow(flowA1)
        network.runNetwork()
        val resultA1 = futureA1.getOrThrow()
        val accountInfoA = resultA1.state.data
        assert(accountInfoA.name == "Node A Account 1")

        val flowB1 = CreateAccountFlow("Node B Account 1")
        val futureB1 = b.startFlow(flowB1)
        network.runNetwork()
        val resultB1 = futureB1.getOrThrow()
        val accountInfoB = resultB1.state.data
        assert(accountInfoB.name == "Node B Account 1")

        // Setup additional accounts

        for (i in 2..5){

            val flow1 = CreateAccountFlow("Node A Account $i")
            val future1 = a.startFlow(flow1)
            network.runNetwork()
            val result1 = future1.getOrThrow()
            val accountInfo1 = result1.state.data
            assert(accountInfo1.name == "Node A Account $i")

            val flow2 = CreateAccountFlow("Node B Account $i")
            val future2 = b.startFlow(flow2)
            network.runNetwork()
            val result2 = future2.getOrThrow()
            val accountInfo2 = result2.state.data
            assert(accountInfo2.name == "Node B Account $i")

        }

        // Swap AccountInfos

        val flowA2 = RequestAccountInfo(accountInfoB.identifier.id,b.services.myInfo.legalIdentities.first())
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        val resultA2 = futureA2.getOrThrow()
        assert(resultA2!!.name == "Node B Account 1")

        val flowB2 = RequestAccountInfo(accountInfoA.identifier.id,a.services.myInfo.legalIdentities.first())
        val futureB2 = b.startFlow(flowB2)
        network.runNetwork()
        val resultB2 = futureB2.getOrThrow()
        assert(resultB2!!.name == "Node A Account 1")

        // do the deal A Initiator and buyer B responder and seller

        val flowA3 = AccountsDealFlow(accountInfoA.identifier.id, accountInfoB.identifier.id, "Buy some Sausages - A Initiates")
        val futureA3 = a.startFlow(flowA3)
        network.runNetwork()
        val resultA3 = futureA3.getOrThrow()
        val deal1 = resultA3.coreTransaction.outputStates.single() as AccountDealState
        assert(deal1.deal == "Buy some Sausages - A Initiates")


        val aUUID1 = a.services.accountService.accountIdForKey(deal1.buyer.owningKey)
        val bUUID1= a.services.accountService.accountIdForKey(deal1.seller.owningKey)
        assert(accountInfoA.identifier.id == aUUID1)
        assert(accountInfoB.identifier.id == bUUID1)

        // do the deal B Initiator and seller A as responder and buyer

        val flowB3 = AccountsDealFlow(accountInfoA.identifier.id, accountInfoB.identifier.id, "Buy some Sausages - B Initiates")
        val futureB3 = b.startFlow(flowB3)
        network.runNetwork()
        val resultB3 = futureB3.getOrThrow()
        val deal2 = resultB3.coreTransaction.outputStates.single() as AccountDealState
        assert(deal2.deal == "Buy some Sausages - B Initiates")


        val aUUID2 = b.services.accountService.accountIdForKey(deal2.buyer.owningKey)
        val bUUID2= b.services.accountService.accountIdForKey(deal2.seller.owningKey)
        assert(accountInfoA.identifier.id == aUUID2)
        assert(accountInfoB.identifier.id == bUUID2)



    }
}

@Test
fun `check both Parties have the AccountInfo`(){

    // todo: Write test
}