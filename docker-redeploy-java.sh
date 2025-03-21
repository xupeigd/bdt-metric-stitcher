# !/bin/bash
docker compose -f compile.yml up && docker compose -f compile.yml down && docker rm -f metric-stitcher && docker rmi -f metric-stitcher:1.0 && docker compose up metric-stitcher &