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
set -e

echo "  ________   __  _________   ___
       / ___/ _ | /  |/  /  _/ /  / _ |
      / /__/ __ |/ /|_/ // // /__/ __ |
      \___/_/ |_/_/  /_/___/____/_/ |_|
                                       "

if [ -f ./build-info.txt ]; then
   cat build-info.txt
fi
echo
echo
printenv
echo
echo


# Create config if not present, deprectate and moving to Corda config-generator
if [ ! -f ${CONFIG_FOLDER}/node.conf ]; then
    echo "${CONFIG_FOLDER}/node.conf not found, creating using camila-config-generator"
    camila-config-generator
else
    echo "/etc/corda/node.conf exists:"
    cat ${CONFIG_FOLDER}/node.conf
fi

# Register if no certificates; Camila needs to use doorman for this to work
#if [ ! -f ${CERTIFICATES_FOLDER}/nodekeystore.jks ]; then
#    echo "${CERTIFICATES_FOLDER}/nodekeystore.jks not found, creating using initial-registration"
#    initial-registration
#else
#    echo "${CERTIFICATES_FOLDER}/nodekeystore.jks exists:"
#    ls ${CERTIFICATES_FOLDER}/
#fi


# Cache NodeInfo, deprecate
if [ "${CAMILA_CACHE_NODEINFO}" = "true" ]; then
    echo "CAMILA_CACHE_NODEINFO=true, caching NodeInfo in persistence"
    cache-nodeInfo
else
    echo "CAMILA_CACHE_NODEINFO!=true, NodeInfo not cached"
fi

: ${JVM_ARGS='-XX:+UseG1GC'}

JVM_ARGS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap "${JVM_ARGS}

if [[ ${JVM_ARGS} == *"Xmx"* ]]; then
  echo "WARNING: the use of the -Xmx flag is not recommended within docker containers. Use the --memory option passed to the container to limit heap size"
fi

# base-directory and config-file cannot be specified together in Corda 3.3, removing base-directory param until Corda 4 upgrade
java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/corda/bin/corda.jar --config-file ${CONFIG_FOLDER}/node.conf ${CORDA_ARGS}
