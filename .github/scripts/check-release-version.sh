#!/usr/bin/env bash
# Copyright 2026 Marco Collovati
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Validates a release version and fails if its tag already exists on GitHub.
# Usage: check-release-version.sh <version>   (needs GH_TOKEN and GITHUB_REPOSITORY)
set -euo pipefail

version="${1:-}"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$ ]]; then
  echo "::error::Invalid version '$version': expected MAJOR.MINOR.PATCH with an optional suffix, e.g. 1.0.0 or 1.1.0-rc1 (no 'v' prefix)"
  exit 1
fi
if [[ "$version" == *SNAPSHOT* ]]; then
  echo "::error::SNAPSHOT versions cannot be released: $version"
  exit 1
fi

# git/ref matches the exact ref only; anything other than a 404 is an error, not a missing tag
if output=$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$version" 2>&1); then
  echo "::error::Tag $version already exists in $GITHUB_REPOSITORY"
  exit 1
elif [[ "$output" != *"HTTP 404"* ]]; then
  echo "::error::Cannot check whether tag $version exists: $output"
  exit 1
fi

echo "Version $version is valid and tag $version does not exist yet"
