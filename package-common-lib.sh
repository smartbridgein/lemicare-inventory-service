#!/bin/bash
# Script to package the cosmicdoc-common library with our inventory service build

# Define paths
COMMON_LIB_PATH="../cosmicdoc-common"
COMMON_LIB_DEST="./cosmicdoc-common"

# Copy the common library to the inventory service directory
echo "Copying cosmicdoc-common to build context..."
if [ -d "$COMMON_LIB_PATH" ]; then
  cp -r "$COMMON_LIB_PATH" "$COMMON_LIB_DEST"
  echo "✅ Common library copied successfully"
else
  echo "❌ Error: Could not find cosmicdoc-common directory at $COMMON_LIB_PATH"
  exit 1
fi

# Make sure .gcloudignore doesn't exclude our common library
if [ -f ".gcloudignore" ]; then
  if grep -q "cosmicdoc-common" ".gcloudignore"; then
    sed -i '' '/cosmicdoc-common/d' ".gcloudignore"
    echo "✅ Removed cosmicdoc-common from .gcloudignore"
  fi
fi

echo "Done! The common library is now ready to be included in the build context."
