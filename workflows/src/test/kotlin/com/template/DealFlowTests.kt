package com.template

import com.template.flows.DealInitiatorFlow
import com.template.flows.DealResponderFlow
import com.template.states.DealState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class DealFlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.states"),
            TestCordapp.findCordapp("com.template.flows")
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
    fun `parent test`() {

        val partyA = a.services.myInfo.legalIdentities.first()
        val partyB = b.services.myInfo.legalIdentities.first()

        val flow = DealInitiatorFlow(partyA, partyB, "buy some sausages")
        val future = a.startFlow(flow)
        network.runNetwork()

        val result = future.getOrThrow()

        val resultState = result.coreTransaction.outputStates[0] as DealState
        assert(resultState.deal == "buy some sausages")

    }
}