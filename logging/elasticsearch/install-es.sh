#!/usr/bin/env bash

ES_VERSION=5.5.2

wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${ES_VERSION}.tar.gz

tar -xzf elasticsearch*.tar.gz

rm elasticsearch*.tar.gz*