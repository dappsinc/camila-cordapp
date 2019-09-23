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

package io.camila.agreement

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table


object AgreementSchema

/**
 * First version of an [AgreementSchema] schema.
 */


object AgreementSchemaV1 : MappedSchema(
        schemaFamily = AgreementSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentAgreement::class.java)) {
    @Entity
    @Table(name = "agreement_states", indexes = arrayOf(Index(name = "idx_agreement_party", columnList = "party"),
            Index(name = "idx_agreement_agreementName", columnList = "agreementName")))
    class PersistentAgreement(
            @Column(name = "agreementNumber")
            var agreementNumber: String,

            @Column(name = "agreementName")
            var agreementName: String,

            @Column(name = "agreementHash")
            var agreementHash: String,

            @Column(name = "agreementStatus")
            var agreementStatus: String,

            @Column(name = "agreementType")
            var agreementType: String,

            @Column(name = "totalAgreementValue")
            var totalAgreementValue: String,

            @Column(name = "party")
            var party: String,

            @Column(name = "counterparty")
            var counterparty: String,

            @Column(name = "agreementStartDate")
            var agreementStartDate: String,

            @Column(name = "agreementEndDate")
            var agreementEndDate: String,
            //     @Column(name = "active")
            //     var active: String,
            //     @Column(name = "createdAt")
            //     var createdAt: String,
            //     @Column(name = "lastUpdated")
            //     var lastUpdated: String,
            @Column(name = "linear_id")
            var linearId: String
            //     @Column(name = "external_Id")
            //     var externalId: String
    ) : PersistentState() {
        constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "")
    }
}

    object AgreementSchemaV2 : MappedSchema(
            schemaFamily = AgreementSchema.javaClass,
            version = 2,
            mappedTypes = listOf(PersistentAgreement::class.java)) {
        @Entity
        @Table(name = "agreement_states2", indexes = arrayOf(Index(name = "idx_agreement_party", columnList = "party"),
                Index(name = "idx_agreement_agreementName", columnList = "agreementName")))
        class PersistentAgreement(
                @Column(name = "agreementNumber")
                var agreementNumber: String,

                @Column(name = "agreementName")
                var agreementName: String,

                @Column(name = "agreementHash")
                var agreementHash: String,

                @Column(name = "agreementStatus")
                var agreementStatus: String,

                @Column(name = "agreementType")
                var agreementType: String,

                @Column(name = "totalAgreementValue")
                var totalAgreementValue: String,

                @Column(name = "party")
                var party: String,

                @Column(name = "counterparty")
                var counterparty: String,

                @Column(name = "agreementStartDate")
                var agreementStartDate: String,

                @Column(name = "agreementEndDate")
                var agreementEndDate: String,

                @Column(name = "active")
                var active: String,

                @Column(name = "createdAt")
                var createdAt: String,

                @Column(name = "lastUpdated")
                var lastUpdated: String,

                @Column(name = "linear_id")
                var linearId: String,

                @Column(name = "external_Id")
                var externalId: String
        ) : PersistentState() {
            constructor() : this("default-constructor-required-for-hibernate", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
        }
    }