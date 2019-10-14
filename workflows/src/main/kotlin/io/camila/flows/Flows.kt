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
import java.util.*

// *********
// * Activate Agreement Flow *
// *********

object ActivateFlow {
    @InitiatingFlow
    @StartableByRPC
    class ActivateAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {


            // Retrieving the Agreement Input from the Vault
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
                    agreement.agreementStartDate,
                    agreement.agreementEndDate,
                    agreement.active,
                    agreement.createdAt,
                    agreement.lastUpdated,
                    agreement.linearId)


            // Creating the command.
            val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
            val command = Command(AgreementContract.Commands.ActivateAgreement(), requiredSigners)

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
            txBuilder.addCommand(command)
            // txBuilder.addCommand(AgreementLineItemContract.Commands.ActivateAgreementLineItem(), ourIdentity.owningKey)


            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signgature
            val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(ActivateAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can activate the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}


// *********
// * Renew Agreement Flow *
// *********

object RenewFlow {
    @InitiatingFlow
    @StartableByRPC
    class RenewAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

        override val progressTracker = ProgressTracker()

        @Suspendable
        override fun call(): SignedTransaction {

            // Retrieving the Agreement Input from the Vault
            val agreementStateAndRef = serviceHub.vaultService.queryBy<Agreement>().states.find {
                it.state.data.agreementNumber == agreementNumber
            } ?: throw IllegalArgumentException("No agreement with ID $agreementNumber found.")


            val agreement = agreementStateAndRef.state.data
            val agreementStatus = AgreementStatus.RENEWED


            // Creating the Renewal output.

            val renewedAgreement = Agreement(
                    agreement.agreementNumber,
                    agreement.agreementName,
                    agreement.agreementHash,
                    agreementStatus,
                    agreement.agreementType,
                    agreement.totalAgreementValue,
                    agreement.party,
                    agreement.counterparty,
                    agreement.agreementStartDate,
                    agreement.agreementEndDate,
                    agreement.active,
                    agreement.createdAt,
                    agreement.lastUpdated,
                    agreement.linearId)

            // Creating the command.
            val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
            val command = Command(AgreementContract.Commands.RenewAgreement(), requiredSigners)

            // Building the transaction.
            val notary = agreementStateAndRef.state.notary
            val txBuilder = TransactionBuilder(notary)
            txBuilder.addInputState(agreementStateAndRef)
            txBuilder.addOutputState(renewedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
            txBuilder.addCommand(command)

            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signgature
            val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
            val counterpartySession = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

        @InitiatedBy(RenewAgreementFlow::class)
        class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                    override fun checkTransaction(stx: SignedTransaction) {
                        val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                        val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                        if (counterparty != counterpartySession.counterparty) {
                            throw FlowException("Only the counterparty can Renew the Agreement")
                        }
                    }
                }

                val txId = subFlow(signTransactionFlow).id

                return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
            }
        }
    }

// *********
// * Amend Agreement Flow *
// *********

object AmendFlow {
    @InitiatingFlow
    @StartableByRPC
    class AmendAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {


        // Retrieving the Agreement Input from the Vault
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
                agreement.agreementStartDate,
                agreement.agreementEndDate,
                agreement.active,
                agreement.createdAt,
                agreement.lastUpdated,
                agreement.linearId)

        // Creating the command.
        val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
        val command = Command(AgreementContract.Commands.AmendAgreement(), requiredSigners)

        // Building the transaction.
        val notary = agreementStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(agreementStateAndRef)
        txBuilder.addOutputState(amendedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
        txBuilder.addCommand(command)


        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
        val counterpartySession = initiateFlow(counterparty)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
        }
    }

    @InitiatedBy(AmendAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Amend the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}


// *********
// * Terminate Agreement Flow *
// *********

object TerminateFlow {
@InitiatingFlow
@StartableByRPC
class TerminateAgreementFlow(val agreementNumber: String) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieving the Agreement Input from the Vault
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
                agreement.agreementStartDate,
                agreement.agreementEndDate,
                agreement.active,
                agreement.createdAt,
                agreement.lastUpdated,
                agreement.linearId)

        // Creating the command.
        val requiredSigners = listOf(agreement.party.owningKey, agreement.counterparty.owningKey)
        val command = Command(AgreementContract.Commands.TerminateAgreement(), requiredSigners)

        // Building the transaction.
        val notary = agreementStateAndRef.state.notary
        val txBuilder = TransactionBuilder(notary)
        txBuilder.addInputState(agreementStateAndRef)
        txBuilder.addOutputState(terminatedAgreement, AgreementContract.AGREEMENT_CONTRACT_ID)
        txBuilder.addCommand(command)


        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Gathering the counterparty's signgature
        val counterparty = if (ourIdentity == agreement.party) agreement.counterparty else agreement.party
        val counterpartySession = initiateFlow(counterparty)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(counterpartySession)))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(counterpartySession)))
    }
}

    @InitiatedBy(TerminateAgreementFlow::class)
    class Acceptor(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val ledgerTx = stx.toLedgerTransaction(serviceHub, false)
                    val counterparty = ledgerTx.inputsOfType<Agreement>().single().counterparty
                    if (counterparty != counterpartySession.counterparty) {
                        throw FlowException("Only the counterparty can Terminate the Agreement")
                    }
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
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
                    val agreementStartDate: String,
                    val agreementEndDate: String,
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
            val active = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            // val contactReference = serviceHub.vaultService.queryBy<Contract>(contact_id).state.single()
            // val reference = contactReference.referenced()
            // val agreementState = Agreement(agreementNumber, agreementName, agreementStatus, agreementType, totalAgreementValue, serviceHub.myInfo.legalIdentities.first(), otherParty, agreementStartDate, agreementEndDate, agreementLineItem, attachmentId, active, createdAt, lastUpdated )
            val agreementState = Agreement(agreementNumber, agreementName, agreementHash, agreementStatus, agreementType, totalAgreementValue, me,  otherParty, agreementStartDate, agreementEndDate, active, createdAt, lastUpdated)
            val txCommand = Command(AgreementContract.Commands.CreateAgreement(), agreementState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
            //        .addReferenceState(reference)
                    .addOutputState(agreementState, AGREEMENT_CONTRACT_ID)
                    .addCommand(txCommand)
            // .addOutputState(AttachmentContract.Attachment(attachmentId), ATTACHMENT_ID)
            //  .addCommand(AttachmentContract.Command, ourIdentity.owningKey)
            //  .addAttachment(attachmentId)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
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

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
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
        txb.verify(serviceHub)
        // Sign the transaction.
        progressTracker.currentStep = SIGNING_TRANSACTION
        val partSignedTx = serviceHub.signInitialTransaction(txb)


        val otherPartyFlow = initiateFlow(to)
        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
    }

    @InitiatedBy(SendMessage::class)
    class SendChatResponder(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val stx = subFlow(object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val message = stx.coreTransaction.outputsOfType<Chat.Message>().single()
                    require(message.from != ourIdentity) {
                        "The sender of the new message cannot have my identity when I am not the creator of the transaction"
                    }
                    require(message.from == otherPartyFlow.counterparty) {
                        "The sender of the reply must must be the party creating this transaction"
                    }
                }
            })
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartyFlow, expectedTxId = stx.id))
        }
    }

}