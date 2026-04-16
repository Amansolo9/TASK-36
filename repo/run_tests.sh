#!/usr/bin/env bash
set -e

# Prevent Git-for-Windows (MSYS) from mangling Unix paths in docker commands
export MSYS_NO_PATHCONV=1

echo "============================================"
echo "  StoreHub Platform - Full Test Suite"
echo "============================================"
echo ""

# Clean up stale containers/volumes from prior runs
docker-compose down -v 2>/dev/null || true
docker-compose -f docker-compose.e2e.yml down -v 2>/dev/null || true

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

run_section() {
    echo ""
    echo -e "${YELLOW}--- $1 ---${NC}"
}

# --- Backend Tests ---
run_section "BACKEND UNIT & INTEGRATION TESTS"

MAVEN_CACHE="${HOME}/.m2"
mkdir -p "$MAVEN_CACHE"
echo "Running backend tests via Maven in Docker..."
docker run --rm \
    -v "$(pwd)/backend":/app \
    -v "${MAVEN_CACHE}":/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -w /app \
    --network host \
    maven:3.9-eclipse-temurin-17 \
    mvn test -B -Dtest='unit_tests.**,api_tests.**' 2>&1
BE_TEST_EXIT=$?
if [ $BE_TEST_EXIT -eq 0 ]; then
    echo -e "${GREEN}PASS: Backend tests${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL: Backend tests${NC}"
    FAIL=$((FAIL + 1))
fi

# --- Frontend Tests ---
run_section "FRONTEND UNIT TESTS"

echo "Running frontend tests via Cypress image (has Chrome)..."
docker run --rm \
    -v "$(pwd)/frontend":/app \
    -w /app \
    -e CHROME_BIN=/usr/bin/google-chrome \
    --entrypoint "" \
    cypress/included:13.7.0 \
    bash -c 'npm install --loglevel=error && npx ng test --watch=false --browsers=ChromeHeadlessNoSandbox'
FE_TEST_EXIT=$?
if [ $FE_TEST_EXIT -eq 0 ]; then
    echo -e "${GREEN}PASS: Frontend tests${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL: Frontend tests${NC}"
    FAIL=$((FAIL + 1))
fi

# --- Frontend Build ---
run_section "FRONTEND PRODUCTION BUILD"

echo "Running frontend production build via Node in Docker..."
docker run --rm \
    -v "$(pwd)/frontend":/app \
    -w /app \
    node:20-alpine \
    sh -c 'npm install --loglevel=error && npx ng build --configuration production'
FE_BUILD_EXIT=$?
if [ $FE_BUILD_EXIT -eq 0 ]; then
    echo -e "${GREEN}PASS: Frontend build${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL: Frontend build${NC}"
    FAIL=$((FAIL + 1))
fi

# --- Static Checks ---
run_section "STATIC CHECKS"

if [ -f .env ] && git ls-files --error-unmatch .env 2>/dev/null; then
    echo -e "${RED}FAIL: .env is tracked in git${NC}"
    FAIL=$((FAIL + 1))
else
    echo -e "${GREEN}PASS: .env not tracked${NC}"
    PASS=$((PASS + 1))
fi

if grep -q 'jwt-secret:.*[a-zA-Z0-9+/=]\{20,\}' backend/src/main/resources/application.yml 2>/dev/null; then
    echo -e "${RED}FAIL: Hardcoded secrets in application.yml${NC}"
    FAIL=$((FAIL + 1))
else
    echo -e "${GREEN}PASS: No hardcoded secrets${NC}"
    PASS=$((PASS + 1))
fi

MIGRATION_COUNT=$(ls backend/src/main/resources/db/migration/V*.sql 2>/dev/null | wc -l)
echo -e "${GREEN}INFO: ${MIGRATION_COUNT} Flyway migrations${NC}"

# --- FE↔BE End-to-End Tests (Cypress via Docker Compose) ---
run_section "FE↔BE END-TO-END TESTS (Cypress)"

echo "Starting full stack and running Cypress e2e tests..."
docker-compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from cypress 2>&1
E2E_EXIT=$?
docker-compose -f docker-compose.e2e.yml down -v 2>/dev/null || true
if [ $E2E_EXIT -eq 0 ]; then
    echo -e "${GREEN}PASS: E2E tests${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL: E2E tests${NC}"
    FAIL=$((FAIL + 1))
fi

# --- Summary ---
echo ""
echo "============================================"
echo -e "  Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "============================================"

[ "$FAIL" -gt 0 ] && exit 1 || exit 0
