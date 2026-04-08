#!/usr/bin/env bash
set -e

echo "============================================"
echo "  StoreHub Platform - Full Test Suite"
echo "============================================"
echo ""

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

cd backend
MVN_CMD=""
if [ -f ./mvnw ]; then
    MVN_CMD="./mvnw"
elif command -v mvn &>/dev/null; then
    MVN_CMD="mvn"
fi

if [ -z "$MVN_CMD" ]; then
    echo -e "${YELLOW}Maven not found - skipping backend tests${NC}"
else
    echo "Running: $MVN_CMD test -B"
    if $MVN_CMD test -B 2>&1; then
        echo -e "${GREEN}PASS: Backend tests${NC}"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL: Backend tests${NC}"
        FAIL=$((FAIL + 1))
    fi
fi
cd ..

# --- Frontend Tests ---
run_section "FRONTEND UNIT TESTS"

cd frontend
if command -v npx &>/dev/null; then
    echo "Running: npx ng test --watch=false --browsers=ChromeHeadless"
    if npx ng test --watch=false --browsers=ChromeHeadless 2>&1; then
        echo -e "${GREEN}PASS: Frontend tests${NC}"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL: Frontend tests${NC}"
        FAIL=$((FAIL + 1))
    fi
else
    echo -e "${YELLOW}Node/npx not found - skipping frontend tests${NC}"
fi
cd ..

# --- Frontend Build ---
run_section "FRONTEND PRODUCTION BUILD"

cd frontend
if command -v npx &>/dev/null; then
    echo "Running: npx ng build --configuration production"
    if npx ng build --configuration production 2>&1; then
        echo -e "${GREEN}PASS: Frontend build${NC}"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}FAIL: Frontend build${NC}"
        FAIL=$((FAIL + 1))
    fi
fi
cd ..

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

# --- Summary ---
echo ""
echo "============================================"
echo -e "  Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "============================================"

[ "$FAIL" -gt 0 ] && exit 1 || exit 0
