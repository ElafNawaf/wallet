#!/bin/bash

# Wallet API Test Script
# This script demonstrates the basic usage of the Wallet API

BASE_URL="http://localhost:8080/api/wallet"

echo "=== Wallet API Test Script ==="
echo "Base URL: $BASE_URL"
echo

# Function to make requests and show responses
make_request() {
    echo "➤ $1"
    echo "Request: $2 $3"
    if [ ! -z "$4" ]; then
        echo "Body: $4"
    fi
    echo "Response:"

    if [ ! -z "$4" ]; then
        curl -s -w "\nHTTP Status: %{http_code}\n" -H "Content-Type: application/json" -X "$2" -d "$4" "$BASE_URL$3"
    else
        curl -s -w "\nHTTP Status: %{http_code}\n" -X "$2" "$BASE_URL$3"
    fi
    echo
    echo "----------------------------------------"
    echo
}

# Wait for user input
read -p "Press Enter to start testing the API..."

# 1. Health Check
make_request "Health Check" "GET" "/health"

# 2. Create Account - Alice
make_request "Create Account - Alice" "POST" "/account" '{"username": "alice"}'

# 3. Create Account - Bob
make_request "Create Account - Bob" "POST" "/account" '{"username": "bob"}'

# 4. Top-up Alice's account
make_request "Top-up Alice's Account" "POST" "/topup" '{"username": "alice", "amount": 500.00, "idempotencyKey": "topup-alice-001"}'

# 5. Top-up Bob's account
make_request "Top-up Bob's Account" "POST" "/topup" '{"username": "bob", "amount": 250.75, "idempotencyKey": "topup-bob-001"}'

# 6. Check Alice's balance
make_request "Get Alice's Account Info" "GET" "/account/alice"

# 7. Check Bob's balance
make_request "Get Bob's Account Info" "GET" "/account/bob"

# 8. Charge Alice's account
make_request "Charge Alice's Account" "POST" "/charge" '{"username": "alice", "amount": 150.25, "idempotencyKey": "charge-alice-001"}'

# 9. Check Alice's balance after charge
make_request "Get Alice's Account Info (After Charge)" "GET" "/account/alice"

echo "=== Error Testing ==="
echo

# 10. Test duplicate username
make_request "Error Test - Duplicate Username" "POST" "/account" '{"username": "alice"}'

# 11. Test user not found
make_request "Error Test - User Not Found" "GET" "/account/nonexistent"

# 12. Test insufficient balance
make_request "Error Test - Insufficient Balance" "POST" "/charge" '{"username": "bob", "amount": 1000.00, "idempotencyKey": "charge-bob-insufficient"}'

# 13. Test duplicate transaction
make_request "Error Test - Duplicate Transaction" "POST" "/topup" '{"username": "alice", "amount": 100.00, "idempotencyKey": "topup-alice-001"}'

# 14. Test invalid amount (negative)
make_request "Error Test - Negative Amount" "POST" "/topup" '{"username": "alice", "amount": -50.00, "idempotencyKey": "topup-alice-negative"}'

# 15. Test missing fields
make_request "Error Test - Missing Fields" "POST" "/topup" '{"username": "alice"}'

echo "=== API Testing Complete ==="
echo "Check the responses above for any errors."
echo "The wallet.db file should now contain the test data."
echo