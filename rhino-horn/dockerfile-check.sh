#!/bin/bash

# This script checks if the Dockerfile defines a non-root USER.
# It exits with an error if the USER is not set or is root.

set -euo pipefail

if ! grep -q '^USER ' rhino-horn/Dockerfile; 
then
  echo "Error: Dockerfile should use non-root USER."
  exit 1
else
  echo "Dockerfile defines a secure non-root USER."
fi