#!/bin/bash
# Cloud Run deployment script for inventory-service
# Target project: lemicare-prod

# Define variables
PROJECT_ID="lemicare-prod"
REGION="asia-south1"
SERVICE_NAME="inventory-service"
IMAGE="gcr.io/${PROJECT_ID}/${SERVICE_NAME}:latest"

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

echo "=== Building inventory service ===" 
# Build the Java application with Maven
mvn clean package -DskipTests

# Check if the build was successful
if [ $? -ne 0 ] || [ ! -f "target/cosmicdoc-inventory-service-0.0.1-SNAPSHOT.jar" ]; then
  echo "❌ Maven build failed or JAR file not found. Aborting deployment."
  # Clean up - remove the copied common library
  if [ -d "$COMMON_LIB_DEST" ]; then
    rm -rf "$COMMON_LIB_DEST"
  fi
  exit 1
fi

echo "=== Building Docker image using Cloud Build ===" 
# Use Cloud Build to build the Docker image from the existing Dockerfile
gcloud builds submit --tag="${IMAGE}" --project="${PROJECT_ID}" .

if [ $? -eq 0 ]; then
  echo "=== Deploying to Cloud Run ===" 
  gcloud run deploy "${SERVICE_NAME}" \
    --image="${IMAGE}" \
    --platform=managed \
    --region="${REGION}" \
    --allow-unauthenticated \
    --set-env-vars="SPRING_PROFILES_ACTIVE=cloud" \
    --set-env-vars="ALLOWED_ORIGINS=https://healthcare-app-145837205370.asia-south1.run.app" \
    --port=8082 \
    --memory=512Mi \
    --project="${PROJECT_ID}"
  
  if [ $? -eq 0 ]; then
    echo "✅ === Deployment completed successfully! ===" 
    echo "Your ${SERVICE_NAME} is now available. Check the URL in the output above."
  else
    echo "❌ === Deployment to Cloud Run failed ===" 
    exit 1
  fi
else
  echo "❌ === Docker image build failed ===" 
  exit 1
fi

# Clean up - remove the copied common library
echo "Cleaning up build files..."
if [ -d "$COMMON_LIB_DEST" ]; then
  rm -rf "$COMMON_LIB_DEST"
fi
