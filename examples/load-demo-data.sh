#!/usr/bin/env bash
set -euo pipefail

api_url="${API_URL:-http://localhost:8080}"
client_username="$(docker compose exec -T api printenv DEMO_CLIENT_USERNAME | tr -d '\r')"
client_password="$(docker compose exec -T api printenv DEMO_CLIENT_PASSWORD | tr -d '\r')"

for payment_file in examples/payments/*.json; do
    payment_name="$(basename "$payment_file" .json)"
    payment_key="demo-${payment_name}-001"
    payment_response="$(curl --fail-with-body --silent --show-error \
        --user "${client_username}:${client_password}" \
        --header 'Content-Type: application/json' \
        --header "Idempotency-Key: ${payment_key}" \
        --data-binary "@${payment_file}" \
        "${api_url}/api/v1/apis")"
    transaction_reference="$(printf '%s' "$payment_response" | python3 -c \
        'import json, sys; print(json.load(sys.stdin)["transactionReference"])')"

    refund_file="examples/refunds/${payment_name}.json"
    refund_key="demo-refund-${payment_name}-001"
    refund_response="$(curl --fail-with-body --silent --show-error \
        --user "${client_username}:${client_password}" \
        --header 'Content-Type: application/json' \
        --header "Idempotency-Key: ${refund_key}" \
        --data-binary "@${refund_file}" \
        "${api_url}/api/v1/apis/${transaction_reference}/refunds")"

    printf '%s payment: %s\n%s refund: %s\n' \
        "$payment_name" "$payment_response" "$payment_name" "$refund_response"
done