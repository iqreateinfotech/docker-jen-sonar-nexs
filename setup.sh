#!/bin/sh
#
# Simply execute this script to setup the full toolchain:
#
# curl -sSL https://raw.githubusercontent.com/muthu-patil/docker-ci-tool-stack/master/setup.sh | bash -s
#
# Prerequisites:
# - Docker & Docker Toolbox v1.10
# - Git v2.6.4

echo "Create docker machine"
docker-machine create -d virtualbox --virtualbox-memory "6000" iq-docker-ci-tools

echo "Setup environment"
eval $(docker-machine env docker-ci-tools)

echo "Checkout Git Repository"
git clone https://github.com/muthu-patil/iq-docker-ci-stack.git
cd iq-docker-ci-stack

echo "Startup Docker Compose"
docker-compose up
