#!/usr/bin/env bash
mvn clean && cd metric-stitcher-api && mvn install -U -DskipTests=true && cd ../ && mvn package -U -DskipTests