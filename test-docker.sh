#!/bin/bash

# Docker Wallet API Test Script
# This script tests the wallet API running in Docker

echo "=== Docker Wallet API Test Script ==="
echo

# Check if Docker Compose is running
if ! docker-compose ps | grep -q "wallet-api.*Up"; then
    echo "❌ Wallet API container is not running!"
    echo "Please start it with: docker-compose up -d"
    exit 1
fi

echo "✅ Docker container is running"
echo

BASE_URL="http://localhost:8080/api/wallet"

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

# Wait for application to be ready
echo "🔄 Waiting for application to be ready..."
max_attempts=30
attempt=1

while [ $attempt -le $max_attempts ]; do
    if curl -s -f "$BASE_URL/health" > /dev/null 2>&1; then
        echo "✅ Application is ready!"
        break
    fi

    if [ $attempt -eq $max_attempts ]; then
        echo "❌ Application failed to start within timeout"
        echo "Container logs:"
        docker-compose logs wallet-api
        exit 1
    fi

    echo "Attempt $attempt/$max_attempts - waiting..."
    sleep 2
    ((attempt++))
done

echo
read -p "Press Enter to start testing the API..."

# Run the same tests as the regular test script
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

echo "=== Docker-specific Information ==="
echo

# Show container information
echo "📊 Container Status:"
docker-compose ps

echo
echo "💾 Database Volume:"
docker volume inspect wallet-api_wallet-data | grep -A 5 '"Mountpoint"'

echo
echo "📝 Recent Container Logs:"
docker-compose logs --tail=10 wallet-api

echo
echo "=== API Testing Complete ==="
echo "✅ All tests completed successfully!"
echo "📁 Database is persisted in Docker volume: wallet-api_wallet-data"
echo
echo "🔧 Useful Docker commands:"
echo "  - View logs: docker-compose logs wallet-api"
echo "  - Restart: docker-compose restart"
echo "  - Stop: docker-compose down"
echo "  - Access container: docker-compose exec wallet-api /bin/bash"