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
# use cached node info if there and we want to
set -e

# Migrate out of /opt/camila/config
if [ -d /opt/camila/config ]; then
   cp /opt/camila/config/nodeInfo* ${PERSISTENCE_FOLDER}/
fi

if [ ! -f nodeInfo* ]; then
    echo "there is no nodeInfo file in the basedir"
    if [ ! -f ${PERSISTENCE_FOLDER}/nodeInfo* ]; then
        echo "there is also no nodeInfo file cached in ${PERSISTENCE_FOLDER}, creating..."
        java -jar /opt/corda/bin/corda.jar --config-file ${CONFIG_FOLDER}/node.conf --just-generate-node-info --log-to-console --no-local-shell
        echo "caching config file in ${PERSISTENCE_FOLDER}"
        mv nodeInfo* ${PERSISTENCE_FOLDER}/
    fi
    echo "copy nodeInfo file back to basedir"
    cp ${PERSISTENCE_FOLDER}/nodeInfo* .
    ls -latr ${PERSISTENCE_FOLDER}/nodeInfo*
    ls -latr nodeInfo*
fi
