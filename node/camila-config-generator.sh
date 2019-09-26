#!/usr/bin/env bash
#
#   Copyright 2020, Dapps Incorporated.
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# Creates Camila config
set -e

echo "Creating Camila config"

# CAMILA_COMPATIBILITY_ZONE_URL deprecated. Used to set NETWORKMAP_URL and DOORMAN_URL if set.
CAMILA_COMPATIBILITY_ZONE_URL=${CAMILA_COMPATIBILITY_ZONE_URL:-https://camila.network}

# Corda official environment variables. If set will be used instead of defaults
MY_LEGAL_NAME=${MY_LEGAL_NAME:-O=Dapps-$(od -x /dev/urandom | head -1 | awk '{print $7$8$9}'), OU=Dapps, L=San Mateo, C=US}
MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS:-localhost}
# MY_P2P_PORT=10200 <- default set in corda dockerfile
NETWORKMAP_URL=${NETWORKMAP_URL:-$CAMILA_COMPATIBILITY_ZONE_URL}
DOORMAN_URL=${DOORMAN_URL:-$CAMILA_COMPATIBILITY_ZONE_URL}
TRUST_STORE_NAME=${TRUST_STORE_NAME:-truststore.jks}
NETWORK_TRUST_PASSWORD=${NETWORK_TRUST_PASSWORD:-trustpass}
MY_EMAIL_ADDRESS=${MY_EMAIL_ADDRESS:-noreply@dapps-inc.com}
# RPC_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1) <- not used
# MY_RPC_PORT=10201 <- default set in corda dockerfile.
# MY_RPC_ADMIN_PORT=10202 <- default set in corda dockerfile.
TLS_CERT_CRL_DIST_POINT=${TLS_CERT_CRL_DIST_POINT:-NULL}
TLS_CERT_CERL_ISSUER=${TLS_CERT_CERL_ISSUER:-NULL}


# Camila environment variables. Will override Corda official environment variables if passed.
CAMILA_LEGAL_NAME=${CAMILA_LEGAL_NAME:-$MY_LEGAL_NAME}
CAMILA_P2P_ADDRESS=${CAMILA_P2P_ADDRESS:-$MY_PUBLIC_ADDRESS:$MY_P2P_PORT}
CAMILA_KEY_STORE_PASSWORD=${CAMILA_KEY_STORE_PASSWORD:-cordacadevpass}
CAMILA_TRUST_STORE_PASSWORD=${CAMILA_TRUST_STORE_PASSWORD:-$NETWORK_TRUST_PASSWORD}
CAMILA_DB_USER=${CAMILA_DB_USER:-sa}
CAMILA_DB_PASS=${CAMILA_DB_PASS:-dbpass}
CAMILA_DB_DRIVER=${CAMILA_DB_DRIVER:-org.h2.jdbcx.JdbcDataSource}
CAMILA_DB_DIR=${CAMILA_DB_DIR:-$PERSISTENCE_FOLDER}
CAMILA_DB_MAX_POOL_SIZE=${CAMILA_DB_MAX_POOL_SIZE:-10}
CAMILA_BRAID_PORT=${CAMILA_BRAID_PORT:-8080}
CAMILA_DEV_MODE=${CAMILA_DEV_MODE:-true}
CAMILA_DETECT_IP=${CAMILA_DETECT_IP:-false}
CAMILA_CACHE_NODEINFO=${CAMILA_CACHE_NODEINFO:-false}
CAMILA_LOG_MODE=${CAMILA_LOG_MODE:-normal}
CAMILA_JVM_MX=${CAMILA_JVM_MX:-1536m}
CAMILA_JVM_MS=${CAMILA_JVM_MS:-512m}
CAMILA_H2_PORT=${CAMILA_H2_PORT:-9090}

#set CAMILA_DB_URL
h2_db_url="\"jdbc:h2:file:${CAMILA_DB_DIR};DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=100;AUTO_SERVER_PORT=${CAMILA_H2_PORT}\""
CAMILA_DB_URL=${CAMILA_DB_URL:-$h2_db_url}

# CAMILA_LOG_CONFIG_FILE:
if [ "${CAMILA_LOG_MODE}" == "json" ]; then
    CAMILA_LOG_CONFIG_FILE=dsoa-log4j2-json.xml
else
    CAMILA_LOG_CONFIG_FILE=dsoa-log4j2.xml
fi

# Create node.conf and default if variables not set
echo
echo
printenv
echo
echo
basedir=\"\${baseDirectory}\"
braidhost=${CAMILA_LEGAL_NAME#*O=} && braidhost=${braidhost%%,*} && braidhost=$(echo $braidhost | sed 's/ //g')
cat > ${CONFIG_FOLDER}/node.conf <<EOL
myLegalName : "${CAMILA_LEGAL_NAME}"
p2pAddress : "${CAMILA_P2P_ADDRESS}"

networkServices : {
    "doormanURL" : "${DOORMAN_URL}"
    "networkMapURL" : "${NETWORKMAP_URL}"
}

tlsCertCrlDistPoint : "${TLS_CERT_CRL_DIST_POINT}"
tlsCertCrlIssuer : "${TLS_CERT_CERL_ISSUER}"

dataSourceProperties : {
    "dataSourceClassName" : "${CAMILA_DB_DRIVER}"
    "dataSource.url" : "${CAMILA_DB_URL}"
    "dataSource.user" : "${CAMILA_DB_USER}"
    "dataSource.password" : "${CAMILA_DB_PASS}"
    "maximumPoolSize" : "${CAMILA_DB_MAX_POOL_SIZE}"
}

keyStorePassword : "${CAMILA_KEY_STORE_PASSWORD}"
trustStorePassword : "${CAMILA_TRUST_STORE_PASSWORD}"
detectPublicIp : ${CAMILA_DETECT_IP}
devMode : ${CAMILA_DEV_MODE}
jvmArgs : [ "-Dbraid.${braidhost}.port=${CAMILA_BRAID_PORT}", "-Xms${CAMILA_JVM_MS}", "-Xmx${CAMILA_JVM_MX}", "-Dlog4j.configurationFile=${CAMILA_LOG_CONFIG_FILE}" ]
jarDirs=[
    ${basedir}/libs
]
emailAddress : "${MY_EMAIL_ADDRESS}"
EOL



# Configure notaries
# for the moment we're dealing with two systems - later we can do this in a slightly different way
if [ "$CAMILA_NOTARY" == "true" ] || [ "$CAMILA_NOTARY" == "validating" ] || [ "$CAMILA_NOTARY" == "non-validating" ] ; then
    NOTARY_VAL=false
    if [ "$CAMILA_NOTARY" == "true" ] || [ "$CAMILA_NOTARY" == "validating" ]; then
    NOTARY_VAL=true
    fi
    echo "CAMILA_NOTARY set to ${CAMILA_NOTARY}. Configuring node to be a notary with validating ${NOTARY_VAL}"
cat >> ${CONFIG_FOLDER}/node.conf <<EOL
notary {
    validating=${NOTARY_VAL}
}
EOL
fi

# do we want to turn on jolokia for monitoring?
if [ ! -z "$CAMILA_EXPORT_JMX" ]; then
cat >> ${CONFIG_FOLDER}/node.conf <<EOL
exportJMXTo: "${CAMILA_EXPORT_JMX}"
EOL
fi


echo "${CONFIG_FOLDER}/node.conf created:"
cat ${CONFIG_FOLDER}/node.conf

if [ ! -z "$CAMILA_METERING_CONFIG" ] ; then
   echo "CAMILA_METERING_CONFIG set to ${CAMILA_METERING_CONFIG}. Creating metering-service-config.json"
   echo $CAMILA_METERING_CONFIG > metering-service-config.json
fi

if [ ! -z "$CAMILA_FEE_DISPERSAL_CONFIG" ] ; then
   echo "CAMILA_FEE_DISPERSAL_CONFIG set to ${CAMILA_FEE_DISPERSAL_CONFIG}. Creating fee-dispersal-service-config.json"
   echo $CAMILA_FEE_DISPERSAL_CONFIG > fee-dispersal-service-config.json
fi
