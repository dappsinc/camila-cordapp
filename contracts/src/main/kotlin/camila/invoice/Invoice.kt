package io.camila.invoice

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

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.toBase58String
import java.lang.Boolean.TRUE
import java.lang.IllegalArgumentException
import java.util.*

// *****************
// * Invoice State *
// *****************

@BelongsToContract(InvoiceContract::class)
data class Invoice(val invoiceNumber: String,
                   val invoiceName: String,
                   val billingReason: String,
                   val amountDue: Int,
                   val amountPaid: Int,
                   val amountRemaining: Int,
                   val subtotal: Int,
                   val total: Int,
                   val party: Party,
                   val counterparty: Party,
                   val dueDate: String,
                   val periodStartDate: String,
                   val periodEndDate: String,
                   val paid: Boolean?,
                   val active: Boolean?,
                   val createdAt: String?,
                   val lastUpdated: String?,
                   override val linearId: UniqueIdentifier = UniqueIdentifier()) : ContractState, LinearState, QueryableState {

    override val participants: List<AbstractParty> get() = listOf(party, counterparty)

    override fun toString(): String {
        val partyString = (party as? Party)?.name?.organisation ?: party.owningKey.toBase58String()
        val counterpartyString = (counterparty as? Party)?.name?.organisation ?: counterparty.owningKey.toBase58String()
        return "Invoice ($linearId): $counterpartyString has an invoice with $partyString for $total and the current paid status is $paid."
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is InvoiceSchemaV1 -> InvoiceSchemaV1.PersistentInvoice(
                    invoiceNumber = this.invoiceNumber,
                    invoiceName = this.invoiceName,
                    billingReason = this.billingReason,
                    amountDue = this.amountDue.toString(),
                    amountPaid = this.amountPaid.toString(),
                    amountRemaining = this.amountRemaining.toString(),
                    subtotal= this.subtotal.toString(),
                    total = this.total.toString(),
                    party = this.party.name.toString(),
                    counterparty = this.counterparty.name.toString(),
                    dueDate = this.dueDate,
                    periodStartDate = this.periodStartDate,
                    periodEndDate = this.periodEndDate,
                    paid = this.paid.toString(),
                    active = this.active.toString(),
                    createdAt = this.createdAt.toString(),
                    lastUpdated = this.lastUpdated.toString(),
                    linearId = this.linearId.id.toString(),
                    externalId = this.linearId.id.toString()

            )
            else -> throw IllegalArgumentException("Unrecognized schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(InvoiceSchemaV1)
}


// **********************
// * Invoice Contract *
// **********************

class InvoiceContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        val INVOICE_CONTRACT_ID = InvoiceContract::class.java.canonicalName
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {

        class CreateInvoice : TypeOnlyCommandData(), Commands
        class PayInvoice : TypeOnlyCommandData(),Commands


    }


    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val invoiceInputs = tx.inputsOfType<Invoice>()
        val invoiceOutputs = tx.outputsOfType<Invoice>()
        val invoiceCommand = tx.commandsOfType<InvoiceContract.Commands>().single()

        when(invoiceCommand.value) {
            is Commands.CreateInvoice -> requireThat {
                "no inputs should be consumed" using (invoiceInputs.isEmpty())
                // TODO we might allow several jobs to be proposed at once later
                "one output should be produced" using (invoiceOutputs.size == 1)

                val invoiceOutput = invoiceOutputs.single()
                "the party should be different to the counterparty" using (invoiceOutput.party != invoiceOutput.counterparty)
                "the total should be greater than 0" using (invoiceOutput.total > 0)

                "the party and counterparty are required signers" using
                        (invoiceCommand.signers.containsAll(listOf(invoiceOutput.party.owningKey, invoiceOutput.counterparty.owningKey)))
            }


            is Commands.PayInvoice -> requireThat {
                "one input should be produced" using (invoiceInputs.size == 1)
                "one output should be produced" using (invoiceOutputs.size == 1)

                val invoiceInput = invoiceInputs.single()
                val invoiceOutput = invoiceOutputs.single()

                "the output paid should be TRUE" using (invoiceOutput.paid == TRUE)
            }

            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

}