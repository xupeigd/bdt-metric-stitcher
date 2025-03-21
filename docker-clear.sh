#!/bin/bash
mvn clean && docker rmi -f metric-stitcher:1.0 && docker compose down