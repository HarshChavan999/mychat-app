#!/bin/bash

# End-to-End Production Testing Script
# Tests the complete chat app deployment workflow

set -e

echo "🧪 Starting End-to-End Production Tests..."

# Configuration
BACKEND_URL=${1:-"https://chat-backend-abc123.run.app"}
ANDROID_APP_ID=${2:-"com.example.mychat"}
TEST_TIMEOUT=${3:-30}

echo "🔗 Backend URL: $BACKEND_URL"
echo "📱 Android App ID: $ANDROID_APP_ID"
echo "⏱️  Test Timeout: ${TEST_TIMEOUT}s"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counter
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

log_test() {
    local test_name="$1"
    local status="$2"
    ((TESTS_RUN++))
    echo -e "${BLUE}Running:${NC} $test_name"

    if [ "$status" = "PASS" ]; then
        ((TESTS_PASSED++))
        echo -e "${GREEN}✅ PASS${NC}: $test_name"
    else
        ((TESTS_FAILED++))
        echo -e "${RED}❌ FAIL${NC}: $test_name"
    fi
    echo ""
}

# Test 1: Backend Health Check
echo "🏥 Testing Backend Health..."
if curl -s -f --max-time $TEST_TIMEOUT "$BACKEND_URL/health" > /dev/null 2>&1; then
    HEALTH_RESPONSE=$(curl -s "$BACKEND_URL" --max-time $TEST_TIMEOUT)
    if [[ $HEALTH_RESPONSE == *"healthy"* ]] || [[ $HEALTH_RESPONSE == *"ok"* ]]; then
        log_test "Backend Health Check" "PASS"
    else
        log_test "Backend Health Check" "FAIL"
        echo "Health response: $HEALTH_RESPONSE"
    fi
else
    log_test "Backend Health Check" "FAIL"
    echo "Backend is not responding"
fi

# Test 2: WebSocket Connection Test (using test-websocket.js)
echo "🔌 Testing WebSocket Connection..."
if command -v node &> /dev/null; then
    cd backend
    if [ -f "test-websocket.js" ]; then
        echo "Running WebSocket connection test..."
        if timeout $TEST_TIMEOUT node test-websocket.js "$BACKEND_URL" 2>/dev/null; then
            log_test "WebSocket Connection" "PASS"
        else
            log_test "WebSocket Connection" "FAIL"
        fi
    else
        log_test "WebSocket Connection" "FAIL"
        echo "test-websocket.js not found"
    fi
    cd ..
else
    log_test "WebSocket Connection" "FAIL"
    echo "Node.js not available for testing"
fi

# Test 3: Firebase Authentication Test
echo "🔐 Testing Firebase Authentication..."
if [ -n "$FIREBASE_PROJECT_ID" ]; then
    echo "Firebase project configured, testing auth endpoint..."
    # This would require actual Firebase credentials
    log_test "Firebase Authentication" "SKIP"
    echo "Skipped: Requires Firebase credentials"
else
    log_test "Firebase Authentication" "SKIP"
    echo "Skipped: Firebase not configured"
fi

# Test 4: Android APK Build Verification
echo "📦 Testing Android APK Build..."
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    APK_SIZE=$(stat -f%z "app/build/outputs/apk/release/app-release.apk" 2>/dev/null || stat -c%s "app/build/outputs/apk/release/app-release.apk" 2>/dev/null || echo "0")
    if [ "$APK_SIZE" -gt 1000000 ]; then  # At least 1MB
        log_test "Android APK Build" "PASS"
        echo "APK size: $((APK_SIZE / 1024 / 1024))MB"
    else
        log_test "Android APK Build" "FAIL"
        echo "APK too small: ${APK_SIZE} bytes"
    fi
else
    log_test "Android APK Build" "FAIL"
    echo "APK file not found"
fi

# Test 5: Docker Container Test
echo "🐳 Testing Docker Container..."
if command -v docker &> /dev/null; then
    if docker images | grep -q "chat-backend"; then
        log_test "Docker Image" "PASS"
        echo "Docker image exists"

        # Test container startup
        if docker run --rm -d --name test-chat-backend -p 8080:8080 chat-backend > /dev/null 2>&1; then
            sleep 5
            if curl -s -f http://localhost:8080/health > /dev/null 2>&1; then
                log_test "Docker Container Startup" "PASS"
                docker stop test-chat-backend > /dev/null 2>&1 || true
            else
                log_test "Docker Container Startup" "FAIL"
                docker stop test-chat-backend > /dev/null 2>&1 || true
            fi
        else
            log_test "Docker Container Startup" "FAIL"
        fi
    else
        log_test "Docker Image" "FAIL"
        echo "Docker image not found"
    fi
else
    log_test "Docker Image" "SKIP"
    echo "Docker not available"
fi

# Test 6: CI/CD Pipeline Test
echo "🔄 Testing CI/CD Pipeline Configuration..."
if [ -f ".github/workflows/ci.yml" ]; then
    if grep -q "Firebase-Distribution-Github-Action" .github/workflows/ci.yml; then
        log_test "Firebase App Distribution CI" "PASS"
    else
        log_test "Firebase App Distribution CI" "FAIL"
        echo "Firebase App Distribution not configured in CI"
    fi

    if grep -q "google-github-actions/setup-gcloud" .github/workflows/ci.yml; then
        log_test "GCP Cloud Run CI" "PASS"
    else
        log_test "GCP Cloud Run CI" "FAIL"
        echo "GCP Cloud Run deployment not configured in CI"
    fi
else
    log_test "CI/CD Pipeline Configuration" "FAIL"
    echo "GitHub Actions workflow not found"
fi

# Test 7: Security Configuration Test
echo "🔒 Testing Security Configuration..."
SECURITY_ISSUES=0

# Check for exposed secrets
if grep -r "password\|secret\|key" .env* --exclude-dir=node_modules | grep -v "#" | grep -v "FIREBASE_PRIVATE_KEY" > /dev/null 2>&1; then
    echo "⚠️  Warning: Potential exposed secrets found"
    ((SECURITY_ISSUES++))
fi

# Check CORS configuration
if grep -q "CORS_ORIGIN" backend/.env.production 2>/dev/null; then
    log_test "CORS Configuration" "PASS"
else
    log_test "CORS Configuration" "FAIL"
    ((SECURITY_ISSUES++))
fi

# Check rate limiting
if grep -q "RATE_LIMIT" backend/.env.production 2>/dev/null; then
    log_test "Rate Limiting Configuration" "PASS"
else
    log_test "Rate Limiting Configuration" "FAIL"
    ((SECURITY_ISSUES++))
fi

if [ $SECURITY_ISSUES -eq 0 ]; then
    log_test "Security Configuration" "PASS"
else
    log_test "Security Configuration" "FAIL"
    echo "Security issues found: $SECURITY_ISSUES"
fi

# Test 8: Performance Baseline Test
echo "⚡ Testing Performance Baseline..."
if command -v curl &> /dev/null; then
    START_TIME=$(date +%s%N)
    if curl -s -w "%{time_total}" "$BACKEND_URL/health" -o /dev/null > /dev/null 2>&1; then
        END_TIME=$(date +%s%N)
        RESPONSE_TIME=$(( (END_TIME - START_TIME) / 1000000 ))  # Convert to milliseconds

        if [ $RESPONSE_TIME -lt 1000 ]; then  # Less than 1 second
            log_test "Performance Baseline" "PASS"
            echo "Response time: ${RESPONSE_TIME}ms"
        else
            log_test "Performance Baseline" "FAIL"
            echo "Slow response time: ${RESPONSE_TIME}ms"
        fi
    else
        log_test "Performance Baseline" "FAIL"
        echo "Could not measure response time"
    fi
else
    log_test "Performance Baseline" "SKIP"
    echo "curl not available"
fi

# Test Summary
echo "📊 Test Summary:"
echo "=================="
echo "Total Tests: $TESTS_RUN"
echo -e "Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Failed: ${RED}$TESTS_FAILED${NC}"

SUCCESS_RATE=$((TESTS_PASSED * 100 / TESTS_RUN))

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed! Ready for production.${NC}"
    echo ""
    echo "🚀 Deployment Checklist:"
    echo "  ✅ Backend health check"
    echo "  ✅ WebSocket connectivity"
    echo "  ✅ Android APK built"
    echo "  ✅ Docker container ready"
    echo "  ✅ CI/CD pipeline configured"
    echo "  ✅ Security settings verified"
    echo "  ✅ Performance baseline met"
    echo ""
    echo "📝 Next Steps:"
    echo "  1. Deploy to production"
    echo "  2. Run user acceptance testing"
    echo "  3. Monitor production metrics"
    exit 0
elif [ $SUCCESS_RATE -ge 70 ]; then
    echo -e "${YELLOW}⚠️  Most tests passed ($SUCCESS_RATE%). Review failures before production.${NC}"
    echo ""
    echo "🔧 Required Fixes:"
    echo "  - Address failed tests"
    echo "  - Verify configuration"
    echo "  - Test in staging environment"
    exit 1
else
    echo -e "${RED}❌ Critical failures detected ($SUCCESS_RATE% pass rate). Do not deploy.${NC}"
    echo ""
    echo "🛑 Blocking Issues:"
    echo "  - Multiple test failures"
    echo "  - Core functionality broken"
    echo "  - Requires immediate fixes"
    exit 1
fi
