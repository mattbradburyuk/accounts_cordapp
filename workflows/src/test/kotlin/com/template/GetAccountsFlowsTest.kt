package com.template

import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfo
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

class GetAccountsFlowsTests {
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
    fun `get accounts test`() {

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

        for (i in 2..5) {

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

        val flowA2 = RequestAccountInfo(accountInfoB.identifier.id, b.services.myInfo.legalIdentities.first())
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        val resultA2 = futureA2.getOrThrow()
        assert(resultA2!!.name == "Node B Account 1")

        val flowB2 = RequestAccountInfo(accountInfoA.identifier.id, a.services.myInfo.legalIdentities.first())
        val futureB2 = b.startFlow(flowB2)
        network.runNetwork()
        val resultB2 = futureB2.getOrThrow()
        assert(resultB2!!.name == "Node A Account 1")


        // test GetAllAccountsFlow

        val flowA3 = GetAllAccountsFlow()
        val futureA3 = a.startFlow(flowA3)
        network.runNetwork()
        val resultA3 = futureA3.getOrThrow()
        println("MB: resultA3: $resultA3")

        val nameMap3 = resultA3.map {it. state.data.name}

        assert (nameMap3.contains("Node A Account 1"))
        assert (nameMap3.contains("Node A Account 2"))
        assert (nameMap3.contains("Node A Account 3"))
        assert (nameMap3.contains("Node A Account 4"))
        assert (nameMap3.contains("Node A Account 5"))
        assert (nameMap3.contains("Node B Account 1"))
        assert (nameMap3.contains("Node B Account 2") == false)
        assert (nameMap3.contains("Node B Account 3") == false)
        assert (nameMap3.contains("Node B Account 4") == false)
        assert (nameMap3.contains("Node B Account 5") == false)


        // test GetOurAccountsFlow

        val flowA4 = GetOurAccountsFlow()
        val futureA4 = a.startFlow(flowA4)
        network.runNetwork()
        val resultA4 = futureA4.getOrThrow()
        println("MB: resultA4: $resultA4")

        val nameMap4 = resultA4.map {it. state.data.name}

        assert (nameMap4.contains("Node A Account 1"))
        assert (nameMap4.contains("Node A Account 2"))
        assert (nameMap4.contains("Node A Account 3"))
        assert (nameMap4.contains("Node A Account 4"))
        assert (nameMap4.contains("Node A Account 5"))
        assert (nameMap4.contains("Node B Account 1") == false)
        assert (nameMap4.contains("Node B Account 2") == false)
        assert (nameMap4.contains("Node B Account 3") == false)
        assert (nameMap4.contains("Node B Account 4") == false)
        assert (nameMap4.contains("Node B Account 5") == false)


        // test GetAccountsForHostFlow

        val flowA5 = GetAccountsForHostFlow(b.services.myInfo.legalIdentities.first())
        val futureA5 = a.startFlow(flowA5)
        network.runNetwork()
        val resultA5 = futureA5.getOrThrow()
        println("MB: resultA5: $resultA5")

        val nameMap5 = resultA5.map {it. state.data.name}

        assert (nameMap5.contains("Node A Account 1") == false)
        assert (nameMap5.contains("Node A Account 2") == false)
        assert (nameMap5.contains("Node A Account 3") == false)
        assert (nameMap5.contains("Node A Account 4") == false)
        assert (nameMap5.contains("Node A Account 5") == false)
        assert (nameMap5.contains("Node B Account 1"))
        assert (nameMap5.contains("Node B Account 2") == false)
        assert (nameMap5.contains("Node B Account 3") == false)
        assert (nameMap5.contains("Node B Account 4") == false)
        assert (nameMap5.contains("Node B Account 5") == false)



    }



}