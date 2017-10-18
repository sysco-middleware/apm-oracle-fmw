#!/usr/bin/env bash

VERSION=5.6.2

wget https://artifacts.elastic.co/downloads/logstash/logstash-${VERSION}.tar.gz

tar -xzf logstash*.tar.gz

rm logstash*.tar.gz*