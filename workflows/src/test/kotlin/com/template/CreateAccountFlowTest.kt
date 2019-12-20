package com.template

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfoFlow
import com.template.flows.CreateAccountFlow
import com.template.flows.DealResponderFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class CreateAccountFlowTests {
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
    fun `create account test`(){

        val flow = CreateAccountFlow("Node A Account 123")

        val future = a.startFlow(flow)
        network.runNetwork()
        val result = future.getOrThrow()

        val resultState = result.state.data

        assert(resultState.name == "Node A Account 123")

    }

    @Test
    fun `share accountInfos test`(){

        val flowA1 = CreateAccountFlow("Node A Account 123")
        val futureA1 = a.startFlow(flowA1)
        network.runNetwork()
        val resultA1 = futureA1.getOrThrow()
        val resultStateA1 = resultA1.state.data
        assert(resultStateA1.name == "Node A Account 123")

        val flowB1 = CreateAccountFlow("Node B Account 456")
        val futureB1 = b.startFlow(flowB1)
        network.runNetwork()
        val resultB1 = futureB1.getOrThrow()
        val resultStateB1 = resultB1.state.data
        assert(resultStateB1.name == "Node B Account 456")

        val flowA2 = RequestAccountInfo(resultStateB1.identifier.id,b.services.myInfo.legalIdentities.first())
        val futureA2 = a.startFlow(flowA2)
        network.runNetwork()
        val resultA2 = futureA2.getOrThrow()
        assert(resultA2!!.name == "Node B Account 456")

        val flowB2 = RequestAccountInfo(resultStateA1.identifier.id,a.services.myInfo.legalIdentities.first())
        val futureB2 = b.startFlow(flowB2)
        network.runNetwork()
        val resultB2 = futureB2.getOrThrow()
        assert(resultB2!!.name == "Node A Account 123")

    }


}