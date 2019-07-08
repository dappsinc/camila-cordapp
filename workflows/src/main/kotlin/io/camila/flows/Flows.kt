/**
 *   Copyright 2019, Dapps Incorporated.
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

package io.camila

import co.paralleluniverse.fibers.Suspendable
import io.camila.agreement.Agreement
import io.camila.agreement.AgreementContract
import io.camila.agreement.AgreementContract.Companion.AGREEMENT_CONTRACT_ID
import io.camila.agreement.AgreementStatus
import io.camila.agreement.AgreementType
import io.camila.chat.Chat
import net.corda.core.contracts.Command
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// *********
// * Activate Agreement Flow *
// *********

@InitiatingFlow
@StartableByRPC
class ActivateAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
            it.state.data.agreementNumber == agreementNumber
        } ?: throw IllegalArgumentException("No Agreement with ID $agreementNumber found.")


        //   val agreementLineItemStateAndDef = serviceHub.vaultService.queryBy<AgreementLineItem>().states.find {
        //       it.state.data.agreementNumber == agreementNumber
        //    } ?: throw IllegalArgumentException("No Agreement Line Item associated to $agreementNumber found.")


        val agreement = agreementStateAndRef.state.data
        //    val agreementLineItem = agreementLineItemStateAndDef.state.data
        val agreementStatus = AgreementStatus.INEFFECT
        //   val agreementLineItemStatus = AgreementLineItemStatus.ACTIVATED


        // Creating the Activated Agreement output.

        val activatedAgreement = Agreement(
                agreement.agreementNumber,
                agreement.agreementName,
                agreement.agreementHash,
                agreementStatus,
                agreement.agreementType,
                agreement.totalAgreementValue,
                agreement.party,
                agreement.counterparty,
                // agreement.agreementStartDate,
                // agreement.agreementEndDate,
                // agreement.agreementLineItem,
                // agreement.attachmentId,
                // agreement.active,
                // agreement.createdAt,
                // agreement.lastUpdated,
                agreement.linearId)


        // Created the Activated Agreement Line Item output.


        // val activatedAgreementLineItem = AgreementLineItem(
        //        agreementLineItem.agreement,
        //       agreementLineItem.agreementNumber,
        //       agreementLineItem.agreementLineItemName,
        //      agreementLineItemStatus,
        //      agreementLineItem.agreementLineItemValue,
        //      agreementLineItem.party,
        //     agreementLineItem.counterparty,
        //     agreementLineItem.lineItem,
        //     agreementLineItem.active,
        //     agreementLineItem.createdAt,
        //     agreementLineItem.lastUpdated,
        //     agreementLineItem.linearId

        //  )

        // Building the transaction.
        val notary = agreementStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(agreementStateAndRef)
        // txBuilder.addInputState((agreementLineItemStateAndDef))
        txBuilder.addOutputState(activatedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
        // txBuilder.addOutputState(activatedAgreementLineItem, AgreementLineItemContract.AGREEMENT_LINEITEM_CONTRACT_ID)
        txBuilder.addCommand(AgreementContract.Commands.ActivateAgreement(), ourIdentity.owningKey)
        // txBuilder.addCommand(AgreementLineItemContract.Commands.ActivateAgreementLineItem(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(stx))
    }

    @InitiatedBy(ActivateAgreementFlow::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Agreement)
                    val agreement = output as Agreement
                    "I won't accept Agreements with a value under 100." using (agreement.totalAgreementValue >= 100)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}


// *********
// * Renew Agreement Flow *
// *********

@InitiatingFlow
@StartableByRPC
class RenewAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
            it.state.data.agreementNumber == agreementNumber
        } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


        val agreement = agreementStateAndRef.state.data
        val agreementStatus = AgreementStatus.RENEWED


        // Creating the output.
        val renewedAgreement = Agreement(
                agreement.agreementNumber,
                agreement.agreementName,
                agreement.agreementHash,
                agreementStatus,
                agreement.agreementType,
                agreement.totalAgreementValue,
                agreement.party,
                agreement.counterparty,
                // agreement.agreementStartDate,
                // agreement.agreementEndDate,
                // agreement.agreementLineItem,
                // agreement.attachmentId,
                // agreement.active,
                // agreement.createdAt,
                // agreement.lastUpdated,
                agreement.linearId)

        // Building the transaction.
        val notary = agreementStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(agreementStateAndRef)
        txBuilder.addOutputState(renewedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
        txBuilder.addCommand(AgreementContract.Commands.RenewAgreement(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(RenewAgreementFlow::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Agreement)
                    val agreement = output as Agreement
                    "I won't accept Agreements with a value under 100." using (agreement.totalAgreementValue >= 100)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}


// *********
// * Amend Agreement Flow *
// *********

@InitiatingFlow
@StartableByRPC
class AmendAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
            it.state.data.agreementNumber == agreementNumber
        } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


        val agreement = agreementStateAndRef.state.data
        val agreementStatus = AgreementStatus.AMENDED


        // Creating the Amended Agreement output.


        val amendedAgreement = Agreement(
                agreement.agreementNumber,
                agreement.agreementName,
                agreement.agreementHash,
                agreementStatus,
                agreement.agreementType,
                agreement.totalAgreementValue,
                agreement.party,
                agreement.counterparty,
                //  agreement.agreementStartDate,
                //  agreement.agreementEndDate,
                //  agreement.agreementLineItem,
                //  agreement.attachmentId,
                //  agreement.active,
                //  agreement.createdAt,
                //  agreement.lastUpdated,
                agreement.linearId)

        // Building the transaction.
        val notary = agreementStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(agreementStateAndRef)
        txBuilder.addOutputState(amendedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
        txBuilder.addCommand(AgreementContract.Commands.AmendAgreement(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(AmendAgreementFlow::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Agreement)
                    val agreement = output as Agreement
                    "I won't accept Agreements with a value under 100." using (agreement.totalAgreementValue >= 100)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}


// *********
// * Terminate Agreement Flow *
// *********

@InitiatingFlow
@StartableByRPC
class TerminateAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
            it.state.data.agreementNumber == agreementNumber
        } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


        val agreement = agreementStateAndRef.state.data
        val agreementStatus = AgreementStatus.TERMINATED


        // Creating the output.
        val terminatedAgreement = Agreement(
                agreement.agreementNumber,
                agreement.agreementName,
                agreement.agreementHash,
                agreementStatus,
                agreement.agreementType,
                agreement.totalAgreementValue,
                agreement.party,
                agreement.counterparty,
                // agreement.agreementStartDate,
                // agreement.agreementEndDate,
                // agreement.agreementLineItem,
                //   agreement.attachmentId,
                //  agreement.active,
                //  agreement.createdAt,
                // agreement.lastUpdated,
                agreement.linearId)

        // Building the transaction.
        val notary = agreementStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(agreementStateAndRef)
        txBuilder.addOutputState(terminatedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
        txBuilder.addCommand(AgreementContract.Commands.TerminateAgreement(), ourIdentity.owningKey)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return serviceHub.signInitialTransaction(txBuilder)
    }

    @InitiatedBy(TerminateAgreementFlow::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Agreement)
                    val agreement = output as Agreement
                    "I won't accept Agreements with a value under 100." using (agreement.totalAgreementValue >= 100)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }
}





// *********
// * Create Agreement Flow *
// *********



object CreateAgreementFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Initiator(val agreementNumber: String,
                    val agreementName: String,
                    val agreementHash: String,
                    val agreementStatus: AgreementStatus,
                    val agreementType: AgreementType,
                    val totalAgreementValue: Int,
            //  val agreementStartDate: String,
            //  val agreementEndDate: String,
            //  val agreementLineItem: AgreementLineItem,
            //  val attachmentId: SecureHash.SHA256,
            //   val active: Boolean,
            //  val createdAt: String,
            //  val lastUpdated: String,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            // val agreementState = Agreement(agreementNumber, agreementName, agreementStatus, agreementType, totalAgreementValue, serviceHub.myInfo.legalIdentities.first(), otherParty, agreementStartDate, agreementEndDate, agreementLineItem, attachmentId, active, createdAt, lastUpdated )
            val agreementState = Agreement(agreementNumber, agreementName, agreementHash, agreementStatus, agreementType, totalAgreementValue, serviceHub.myInfo.legalIdentities.first(), otherParty)
            val txCommand = Command(AgreementContract.Commands.CreateAgreement(), agreementState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(agreementState, AGREEMENT_CONTRACT_ID)
                    .addCommand(txCommand)
            // .addOutputState(AttachmentContract.Attachment(attachmentId), ATTACHMENT_ID)
            //  .addCommand(AttachmentContract.Command, ourIdentity.owningKey)
            //  .addAttachment(attachmentId)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            progressTracker.currentStep = GATHERING_SIGS
            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))
            return serviceHub.signInitialTransaction(txBuilder)
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Agreement transaction." using (output is Agreement)
                    val agreement = output as Agreement
                    "I won't accept Agreements with a value under 100." using (agreement.totalAgreementValue >= 100)
                }
            }

            val signedTransaction = subFlow(signTransactionFlow)
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = signedTransaction.id))
        }
    }

}


// *********
// * Send Message Flows *
// *********


@InitiatingFlow
@StartableByRPC
class SendMessage(private val to: Party, private val userId: String, private val body: String) : FlowLogic<Unit>() {

    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Message.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() {
        val stx: SignedTransaction = createMessageStx()
        val otherPartySession = initiateFlow(to)
        progressTracker.nextStep()
        subFlow(FinalityFlow(stx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
    }

    private fun createMessageStx(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txb = TransactionBuilder(notary)
        val me = ourIdentityAndCert.party
        val fromUserId = 21039231.toString()
        val sent = true
        val delivered = false
        val fromMe = true
        val time = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        val formatted = time.format(formatter)
        val messageNumber = 100.toString()
        txb.addOutputState(Chat.Message(UniqueIdentifier(), body, fromUserId, to, me, userId, sent, delivered, fromMe, formatted, messageNumber), Chat::class.qualifiedName!!)
        txb.addCommand(Chat.SendMessageCommand, me.owningKey)
        return serviceHub.signInitialTransaction(txb)
    }

    @InitiatedBy(SendMessage::class)
    class SendChatResponder(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val stx = subFlow(object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val message = stx.coreTransaction.outputsOfType<Chat.Message>().single()
                    require(message.from != ourIdentity) {
                        "The sender of the new message cannot have my identity when I am not the creator of the transaction"
                    }
                    require(message.from == otherPartySession.counterparty) {
                        "The sender of the reply must must be the party creating this transaction"
                    }
                }
            })
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = stx.id))
        }
    }

}