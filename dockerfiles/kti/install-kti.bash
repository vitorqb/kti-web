#!/bin/bash
# Downloads the kti binary
# Usage: install-kti.bash <version>
# Example: install-kti.bash 1.3.1

[ ! "$#" = 1 ] && echo "Expected a single argument" >/dev/stderr
[ -z "$1" ] && echo "No version specified" >/dev/stderr

KTI_VERSION="$1"
KTI_SOURCE="https://github.com/vitorqb/kti/releases/download/${KTI_VERSION}/kti.jar"
curl -L -o ./kti.jar "$KTI_SOURCE"
