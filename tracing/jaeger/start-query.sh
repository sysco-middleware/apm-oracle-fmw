#!/usr/bin/env bash
./jaeger/query --dependency-storage.type elasticsearch --span-storage.type elasticsearch --query.static-files jaeger-ui-build/build/ --log-level debug
