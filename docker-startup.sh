# !/bin/bash
docker compose -f compile.yml up && docker compose -f compile.yml down && docker compose up &