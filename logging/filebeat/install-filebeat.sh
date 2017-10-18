#!/usr/bin/env bash
VERSION=5.6.2
wget https://artifacts.elastic.co/downloads/beats/filebeat/filebeat-${VERSION}-linux-x86_64.tar.gz

tar -xzf filebeat-*.tar.gz

rm filebeat-*.tar.gz*
