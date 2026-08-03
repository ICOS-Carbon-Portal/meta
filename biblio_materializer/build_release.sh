#!/usr/bin/env bash
#
# Builds the biblio_materializer mix release inside Docker (Ubuntu 24.04, ERTS
# bundled) and exports it as a tarball you can copy to a server and run — no
# Elixir/Erlang needed on the target, only a few shared libraries (see below).
#
# Usage:
#   ./build_release.sh
#
# Overridable via environment:
#   ELIXIR_VERSION   Elixir to build with         (default 1.18.4)
#   OUTPUT_DIR       Where to write the tarball    (default ./dist)
#   IMAGE_TAG        Builder image tag             (default biblio_materializer:release-builder)
#
set -euo pipefail

cd "$(dirname "$0")"

ELIXIR_VERSION="${ELIXIR_VERSION:-1.18.4}"
OUTPUT_DIR="${OUTPUT_DIR:-dist}"
IMAGE_TAG="${IMAGE_TAG:-biblio_materializer:release-builder}"

echo "==> Building release image (Elixir ${ELIXIR_VERSION})..."
docker build \
  --target builder \
  --build-arg "ELIXIR_VERSION=${ELIXIR_VERSION}" \
  -t "${IMAGE_TAG}" \
  .

echo "==> Extracting release from image..."
container_id="$(docker create "${IMAGE_TAG}")"
tmp="$(mktemp -d)"
cleanup() {
  docker rm -f "${container_id}" >/dev/null 2>&1 || true
  rm -rf "${tmp}"
}
trap cleanup EXIT

docker cp "${container_id}:/app/_build/prod/rel/biblio_materializer" "${tmp}/biblio_materializer"

# Release version, from the release metadata (e.g. "14.2.5.4 0.1.0" -> "0.1.0").
version="$(awk '{print $2}' "${tmp}/biblio_materializer/releases/start_erl.data")"

mkdir -p "${OUTPUT_DIR}"
tarball="${OUTPUT_DIR}/biblio_materializer-${version}.tar.gz"
tar -C "${tmp}" -czf "${tarball}" biblio_materializer

echo
echo "==> Wrote ${tarball} ($(du -h "${tarball}" | cut -f1))"
cat <<EOF

To deploy, copy it to the server (Ubuntu 24.04, x86_64) and run:

  scp ${tarball} user@server:/opt/

  # On the server, once:
  sudo apt-get update && sudo apt-get install -y openssl libncurses6 libstdc++6 ca-certificates

  cd /opt && tar -xzf biblio_materializer-${version}.tar.gz

  # Runs one population pass, then exits with the run's status:
  VIRTUOSO_HOST=http://localhost:8890 \\
  VIRTUOSO_USERNAME=dba VIRTUOSO_PASSWORD=secret \\
    biblio_materializer/bin/biblio_materializer start
EOF
