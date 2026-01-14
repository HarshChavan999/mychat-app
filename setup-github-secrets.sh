#!/bin/bash

# Setup GitHub Secrets Script for Chat App CI/CD
# This script creates all necessary service accounts and GitHub secrets

echo "🚀 Setting up GitHub Secrets for Chat App CI/CD"
echo "=============================================="

# Check if required tools are installed
command -v gcloud >/dev/null 2>&1 || { echo "❌ gcloud CLI is required but not installed. Please install Google Cloud SDK."; exit 1; }
command -v firebase >/dev/null 2>&1 || { echo "❌ Firebase CLI is required but not installed. Please install Firebase CLI."; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "❌ GitHub CLI is required but not installed. Please install GitHub CLI."; exit 1; }

# Configuration
PROJECT_ID="startsmart-8789e"
FIREBASE_APP_ID="1:215828472999:android:aa953679adf35f55cf2782"

echo "📋 Configuration:"
echo "   GCP Project ID: $PROJECT_ID"
echo "   Firebase App ID: $FIREBASE_APP_ID"
echo ""

# Step 1: Create Firebase service account for App Distribution
echo "🔑 Step 1: Creating Firebase service account..."
FIREBASE_SA_NAME="firebase-app-distribution"
FIREBASE_SA_EMAIL="$FIREBASE_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"

# Check if service account already exists
if gcloud iam service-accounts describe "$FIREBASE_SA_EMAIL" --project="$PROJECT_ID" >/dev/null 2>&1; then
    echo "✅ Firebase service account already exists"
else
    gcloud iam service-accounts create "$FIREBASE_SA_NAME" \
        --description="Service account for Firebase App Distribution" \
        --display-name="Firebase App Distribution" \
        --project="$PROJECT_ID"
    echo "✅ Created Firebase service account"
fi

# Grant necessary permissions
echo "🔐 Granting permissions to Firebase service account..."
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$FIREBASE_SA_EMAIL" \
    --role="roles/firebaseappdistro.admin"

# Create and download service account key
echo "📥 Creating Firebase service account key..."
FIREBASE_KEY_FILE="firebase-sa-key.json"
gcloud iam service-accounts keys create "$FIREBASE_KEY_FILE" \
    --iam-account="$FIREBASE_SA_EMAIL" \
    --project="$PROJECT_ID"

echo "✅ Firebase service account key created: $FIREBASE_KEY_FILE"

# Step 2: Check GCP service account key exists
GCP_KEY_FILE="$HOME/chat-app-key.json"
if [ ! -f "$GCP_KEY_FILE" ]; then
    echo "❌ GCP service account key not found at $GCP_KEY_FILE"
    echo "   Please ensure you have created the chat-app-deployer service account key"
    exit 1
else
    echo "✅ GCP service account key found: $GCP_KEY_FILE"
fi

# Step 3: Set up GitHub secrets
echo "🔐 Step 3: Setting up GitHub secrets..."

# Read the keys
GCP_SA_KEY=$(cat "$GCP_KEY_FILE" | tr -d '\n')
FIREBASE_SA_KEY=$(cat "$FIREBASE_KEY_FILE" | tr -d '\n')

# Set GitHub secrets
echo "Setting GCP_SA_KEY..."
gh secret set GCP_SA_KEY --body="$GCP_SA_KEY"

echo "Setting FIREBASE_SERVICE_ACCOUNT_KEY..."
gh secret set FIREBASE_SERVICE_ACCOUNT_KEY --body="$FIREBASE_SA_KEY"

echo "Setting FIREBASE_APP_ID..."
gh secret set FIREBASE_APP_ID --body="$FIREBASE_APP_ID"

echo "Setting FIREBASE_PROJECT_ID..."
gh secret set FIREBASE_PROJECT_ID --body="$PROJECT_ID"

# Step 4: Clean up
echo "🧹 Step 4: Cleaning up temporary files..."
rm -f "$FIREBASE_KEY_FILE"

echo ""
echo "🎉 Setup complete!"
echo "=================="
echo "GitHub secrets configured:"
echo "   ✅ GCP_SA_KEY - For Cloud Run deployment"
echo "   ✅ FIREBASE_SERVICE_ACCOUNT_KEY - For App Distribution"
echo "   ✅ FIREBASE_APP_ID - Android app identifier"
echo "   ✅ FIREBASE_PROJECT_ID - Firebase project identifier"
echo ""
echo "Next steps:"
echo "1. Push this setup to your main branch"
echo "2. The CI/CD pipeline will run automatically"
echo "3. Check GitHub Actions for deployment status"
echo ""
echo "Repository URL: $(gh repo view --json url -q .url 2>/dev/null || echo 'Please run: gh repo view')"
