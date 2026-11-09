#!/bin/sh
set -eu

mkdir -p /backups
stamp="$(date -u +%Y%m%d-%H%M%S)"
archive="/backups/avtozvuk-${stamp}.dump"

pg_dump --host=db --username=avtozvuk --dbname=avtozvuk \
  --format=custom --no-owner --no-acl --file="$archive"
pg_restore --list "$archive" >/dev/null
sha256sum "$archive" >"${archive}.sha256"
find /backups -type f \( -name 'avtozvuk-*.dump' -o -name 'avtozvuk-*.dump.sha256' \) -mtime +30 -delete
echo "Backup created: $archive"

