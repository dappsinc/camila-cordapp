/**
 *   Copyright 2020, Dapps Incorporated.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.camila.server.components

import com.github.manosbatsis.corbeans.spring.boot.corda.rpc.NodeRpcConnection
import com.github.manosbatsis.corbeans.spring.boot.corda.service.CordaNodeServiceImpl
import io.camila.*
import io.camila.agreement.AgreementStatus
import io.camila.agreement.AgreementType
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory


class CamilaService(
        nodeRpcConnection: NodeRpcConnection
) : CordaNodeServiceImpl(nodeRpcConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(CordaNodeServiceImpl::class.java)
    }

    /** Send a Message! */
    fun sendMessage(to: String, userId: String, message: String): Unit {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(to, exactMatch = true)
        logger.debug("sendMessage, peers: {}", this.peers())
        logger.debug("sendMessage, peer names: {}", this.peerNames())
        logger.debug("sendMessage, target: {}, matches: {}", to, matches)

        val to: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$to\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$to\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(SendMessage::class.java, to, userId, message).returnValue.getOrThrow()
    }


    /** Create an Agreeement! */
    fun createAgreement(agreementNumber: String, agreementName: String, agreementHash: String, agreementStatus: AgreementStatus, agreementType: AgreementType, totalAgreementValue: Int, counterpartyName: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        val matches = proxy.partiesFromName(counterpartyName, exactMatch = true)
        logger.debug("createAccount, peers: {}", this.peers())
        logger.debug("createAccount, peer names: {}", this.peerNames())
        logger.debug("createAccount, target: {}, matches: {}", counterpartyName, matches)

        val counterpartyName: Party = when {
            matches.isEmpty() -> throw IllegalArgumentException("Target string \"$counterpartyName\" doesn't match any nodes on the network.")
            matches.size > 1 -> throw IllegalArgumentException("Target string \"$counterpartyName\"  matches multiple nodes on the network.")
            else -> matches.single()
        }
        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(CreateAgreementFlow.Initiator::class.java, agreementNumber, agreementName, agreementHash, agreementStatus,agreementType, totalAgreementValue, counterpartyName).returnValue.getOrThrow()
    }


    /** Activate an Agreement! */
    fun activateAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(ActivateAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }


    /** Renew an Agreement! */
    fun renewAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(RenewAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }


    /** Amend an Agreement! */
    fun amendAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(AmendAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }


    /** Terminate an Agreement! */
    fun terminateAgreement(agreementNumber: String): SignedTransaction {
        val proxy = this.nodeRpcConnection.proxy

        // Start the flow, block and wait for the response.
        return proxy.startFlowDynamic(TerminateAgreementFlow::class.java, agreementNumber).returnValue.getOrThrow()
    }




}