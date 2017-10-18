#!/usr/bin/env bash

KIBANA_VERSION=5.6.2

wget https://artifacts.elastic.co/downloads/kibana/kibana-${KIBANA_VERSION}-linux-x86_64.tar.gz

tar -xzf kibana*.tar.gz

rm kibana*.tar.gz*

