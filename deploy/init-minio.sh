#!/bin/sh
set -eu

if [ "$MINIO_ROOT_USER" = "$MINIO_ACCESS_KEY" ]; then
    echo "Use a separate MinIO application user." >&2
    exit 1
fi

attempt=0
until mc alias set storage http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 60 ]; then
        echo "MinIO did not become ready. Check service status and bootstrap credentials." >&2
        exit 1
    fi
    sleep 2
done

mc mb --ignore-existing storage/enterprise-kb >/dev/null
mc anonymous set none storage/enterprise-kb >/dev/null
mc admin policy create storage kb-application-objects /bootstrap/minio-policy.json >/dev/null
if ! mc admin user info storage "$MINIO_ACCESS_KEY" >/dev/null 2>&1; then
    mc admin user add storage "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null
fi

current=$(mc --json admin policy entities storage --user "$MINIO_ACCESS_KEY")
case "$current" in
    *'"kb-application-objects"'*) ;;
    *) mc admin policy attach storage kb-application-objects --user "$MINIO_ACCESS_KEY" >/dev/null ;;
esac

if ! mc alias set app http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null 2>&1 ||
   ! mc ls app/enterprise-kb >/dev/null 2>&1; then
    echo "Application credentials cannot read the private MinIO bucket." >&2
    exit 1
fi
echo "Private MinIO bucket and application user are ready."
