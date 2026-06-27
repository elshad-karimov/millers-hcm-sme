#!/bin/bash
# HCM Quality Gate — runs on Stop hook
# Fill in your actual test/lint commands for your stack.

# (a) Run test suite — replace with your actual test command
# ./mvnw -q test --fail-at-end 2>&1 | tail -20
# if [ $? -ne 0 ]; then echo "GATE BLOCKED: Tests failed"; exit 1; fi

# (b) Grep diff for missing tenant_id filters on employee/payroll tables
# if git diff --cached | grep -E "(employee|payroll|attendance)" | grep -v "tenant_id"; then
#   echo "GATE BLOCKED: Query missing tenant_id filter detected"; exit 1
# fi

# (c) Check for hard deletes on payroll rows
# if git diff --cached | grep -E "DELETE FROM.*payroll"; then
#   echo "GATE BLOCKED: Hard delete of payroll data detected"; exit 1
# fi

echo "Quality gate: remind to run the full quality gate if code changed."
exit 0
