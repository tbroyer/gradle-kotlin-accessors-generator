#!/bin/bash

set -eu

if [[ ! ( "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ) ]]; then
	echo "Version doesn't match semver"
	exit 2
fi

pushd "$(dirname "$0")"

git tag -am "Releasing v$1" "v$1"
./gradlew publishToMavenCentral "-Pversion=$1"
git push origin tag "v$1"

popd
