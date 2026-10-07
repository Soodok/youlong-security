#!/usr/bin/env bash
# =============================================================================
# Summarize Android Lint results and fail the job if any Error-severity issue
# is reported.  The app module has lint { abortOnError false }, so the lint
# *task* never fails by itself — this script turns real errors into a CI
# failure while keeping warnings informational.
# =============================================================================
set -uo pipefail

shopt -s globstar nullglob 2>/dev/null || true

results=( $(find . -type f \( -name 'lint-results-*.xml' -o -name 'lint-results-*.txt' \) \
    -path '*/reports/*' 2>/dev/null) )

if [ ${#results[@]} -eq 0 ]; then
    echo "lint-summary: no lint result files found (did lint run?)"
    exit 0
fi

total_errors=0
total_warnings=0

for f in "${results[@]}"; do
    case "$f" in
        *.xml)
            errors=$(grep -o 'severity="Error"' "$f" | wc -l)
            warnings=$(grep -o 'severity="Warning"' "$f" | wc -l)
            ;;
        *.txt)
            # text reports: "Error: <message>" lines
            errors=$(grep -cE '^Error: ' "$f" || true)
            warnings=$(grep -cE '^Warning: ' "$f" || true)
            ;;
    esac
    total_errors=$((total_errors + errors))
    total_warnings=$((total_warnings + warnings))
    echo "$f: $errors errors, $warnings warnings"
done

echo ""
echo "=============================================="
echo " Lint totals: $total_errors errors / $total_warnings warnings"
echo "=============================================="

if [ "$total_errors" -gt 0 ]; then
    echo "::error::Android Lint reported $total_errors error-severity issue(s). See the uploaded lint-report artifact."
    exit 1
fi
exit 0
