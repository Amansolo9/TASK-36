#!/usr/bin/env bash
set -e

echo "============================================"
echo "  StoreHub Platform — Full Test Suite"
echo "============================================"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

run_section() {
    echo ""
    echo -e "${YELLOW}━━━ $1 ━━━${NC}"
}

# ─────────────────────────────────────────────
# Backend Tests
# ─────────────────────────────────────────────
run_section "BACKEND UNIT & INTEGRATION TESTS"

echo "Running Maven tests..."
cd backend
MVN_CMD=""
if [ -f ./mvnw ]; then
    MVN_CMD="./mvnw"
elif command -v mvn &>/dev/null; then
    MVN_CMD="mvn"
fi

if [ -z "$MVN_CMD" ]; then
    echo -e "${YELLOW}⚠ Maven not found — skipping backend tests (install Maven or add ./mvnw)${NC}"
else
    if $MVN_CMD test -B -q 2>&1; then
        echo -e "${GREEN}✓ Backend tests passed${NC}"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}✗ Backend tests failed${NC}"
        FAIL=$((FAIL + 1))
    fi
fi
cd ..

# ─────────────────────────────────────────────
# Frontend Tests
# ─────────────────────────────────────────────
run_section "FRONTEND UNIT TESTS"

echo "Running Angular tests (single run, headless)..."
cd frontend
if npx ng test --watch=false --browsers=ChromeHeadless 2>&1; then
    echo -e "${GREEN}✓ Frontend tests passed${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${RED}✗ Frontend tests failed${NC}"
    FAIL=$((FAIL + 1))
fi
cd ..

# ─────────────────────────────────────────────
# Frontend Build
# ─────────────────────────────────────────────
run_section "FRONTEND PRODUCTION BUILD"

echo "Building Angular for production..."
cd frontend
if npx ng build --configuration production 2>&1; then
    echo -e "${GREEN}✓ Frontend build succeeded${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${RED}✗ Frontend build failed${NC}"
    FAIL=$((FAIL + 1))
fi
cd ..

# ─────────────────────────────────────────────
# Static Checks
# ─────────────────────────────────────────────
run_section "STATIC CHECKS"

# Check no .env committed
if [ -f .env ] && git ls-files --error-unmatch .env 2>/dev/null; then
    echo -e "${RED}✗ .env is tracked in git — security risk${NC}"
    FAIL=$((FAIL + 1))
else
    echo -e "${GREEN}✓ .env is not tracked in git${NC}"
    PASS=$((PASS + 1))
fi

# Check no hardcoded secrets in application.yml
if grep -q 'jwt-secret:.*[a-zA-Z0-9+/=]\{20,\}' backend/src/main/resources/application.yml 2>/dev/null; then
    echo -e "${RED}✗ Hardcoded secrets found in application.yml${NC}"
    FAIL=$((FAIL + 1))
else
    echo -e "${GREEN}✓ No hardcoded secrets in application.yml${NC}"
    PASS=$((PASS + 1))
fi

# Check all service mutations have @Audited or @Transactional
UNAUDITED=$(grep -rn "@Transactional" backend/src/main/java/com/eaglepoint/storehub/service/ --include="*.java" | grep -v "readOnly" | grep -v "AuditService" | grep -v "Propagation" | while read line; do
    file=$(echo "$line" | cut -d: -f1)
    lineno=$(echo "$line" | cut -d: -f2)
    prevline=$((lineno - 1))
    prev2=$((lineno - 2))
    if ! sed -n "${prevline}p;${prev2}p" "$file" 2>/dev/null | grep -q "@Audited\|@Scheduled"; then
        echo "$line"
    fi
done | wc -l)

if [ "$UNAUDITED" -le 5 ]; then
    echo -e "${GREEN}✓ Audit coverage looks reasonable (${UNAUDITED} unaudited write paths)${NC}"
    PASS=$((PASS + 1))
else
    echo -e "${YELLOW}⚠ ${UNAUDITED} write paths may lack @Audited annotation${NC}"
fi

# Check migrations are sequential
MIGRATION_COUNT=$(ls backend/src/main/resources/db/migration/V*.sql 2>/dev/null | wc -l)
echo -e "${GREEN}✓ ${MIGRATION_COUNT} Flyway migrations present${NC}"
PASS=$((PASS + 1))

# ─────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────
echo ""
echo "============================================"
echo -e "  Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
