#!/usr/bin/env bash
./jaeger/collector --dependency-storage.type elasticsearch --span-storage.type elasticsearch
