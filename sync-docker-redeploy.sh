#!/bin/bash
targetDir=/usr/local/dev/sync-wsp/bdt-metric-stitcher
upCommond="sudo docker compose -f $targetDir/docker-compose.yml up metric-stitcher"
sudo docker rm -f metric-stitcher && sudo docker rmi -f metric-stitcher:1.0 && echo 'Call Deplayed !' && $upCommond &