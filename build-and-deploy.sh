#!/bin/bash
# Complete build and deploy script for inventory-service-master

PROJECT_ID="pivotal-store-459018-n4"
REGION="us-central1"
SERVICE_NAME="inventory-service-master"

echo "=== Building and installing cosmicdoc-common library ==="
COMMON_LIB_PATH="../cosmicdoc-common"

# Check if common library exists
if [ ! -d "$COMMON_LIB_PATH" ]; then
  echo "❌ Error: cosmicdoc-common library not found at $COMMON_LIB_PATH"
  exit 1
fi

# Build and install the common library to local Maven repository
echo "Building and installing common library to local Maven repository..."
cd "$COMMON_LIB_PATH"
mvn clean install -DskipTests
if [ $? -ne 0 ]; then
  echo "❌ Error: Failed to build and install cosmicdoc-common library"
  exit 1
fi
cd -

# Copy the common library to our project directory for the build context
echo "Copying common library to build context..."
COMMON_LIB_DEST="./cosmicdoc-common"

# Remove any old copy of the common library
if [ -d "$COMMON_LIB_DEST" ]; then
  echo "Removing old copy of common library..."
  rm -rf "$COMMON_LIB_DEST"
fi

cp -r "$COMMON_LIB_PATH" "$COMMON_LIB_DEST"

echo "=== Building and Deploying $SERVICE_NAME using Cloud Build ==="
# Submit the build to Cloud Build using the cloudbuild.yaml configuration
gcloud builds submit --config=cloudbuild.yaml \
  --project=$PROJECT_ID \
  --timeout=30m .

BUILD_RESULT=$?

# Clean up - remove the copied common library
echo "Cleaning up build files..."
if [ -d "$COMMON_LIB_DEST" ]; then
  rm -rf "$COMMON_LIB_DEST"
fi

if [ $BUILD_RESULT -eq 0 ]; then
  echo "✅ Successfully deployed $SERVICE_NAME to Cloud Run!"
  echo "Service URL: https://$SERVICE_NAME-$PROJECT_ID.a.run.app"
else
  echo "❌ Deployment failed. Check the Cloud Build logs for more information."
  exit 1
fi
