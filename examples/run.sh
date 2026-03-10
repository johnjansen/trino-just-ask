#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONTAINER_NAME="trino-justask"

# Check for API key
if [ -z "${OPENAI_API_KEY:-}" ]; then
    echo "Error: OPENAI_API_KEY environment variable is not set"
    echo "Usage: OPENAI_API_KEY=sk-... ./examples/run.sh"
    exit 1
fi

# Build
echo "=== Building plugin ==="
cd "$PROJECT_DIR"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home}" \
    mvn clean package -DskipTests -q
JAR="$PROJECT_DIR/target/trino-just-ask-1.0-SNAPSHOT-jar-with-dependencies.jar"
echo "Built: $JAR"

# Copy TPC-H catalog docs
echo "=== Preparing config ==="
rm -rf "$SCRIPT_DIR/trino-config/justask/catalogs/tpch"
cp -r "$PROJECT_DIR/etc/justask/catalogs/tpch" "$SCRIPT_DIR/trino-config/justask/catalogs/"
cp "$PROJECT_DIR/etc/justask/system-prompt.md" "$SCRIPT_DIR/trino-config/justask/"

# Stop existing container if running
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true

# Start Trino
echo "=== Starting Trino ==="
docker run -d \
    --name "$CONTAINER_NAME" \
    -p 8080:8080 \
    -v "$SCRIPT_DIR/trino-config/catalog:/etc/trino/catalog" \
    -v "$SCRIPT_DIR/trino-config/justask:/etc/trino/justask" \
    -v "$JAR:/usr/lib/trino/plugin/justask/trino-just-ask.jar" \
    -e "OPENAI_API_KEY=$OPENAI_API_KEY" \
    trinodb/trino:473

echo "Waiting for Trino to start..."
for i in $(seq 1 60); do
    if docker exec "$CONTAINER_NAME" trino --execute "SELECT 1" 2>/dev/null | grep -q "1"; then
        echo "Trino is ready!"
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "Timeout waiting for Trino to start. Logs:"
        docker logs "$CONTAINER_NAME" | tail -30
        exit 1
    fi
    sleep 2
done

echo ""
echo "============================================"
echo "  Trino Just Ask — Example Queries"
echo "============================================"
echo ""

run_query() {
    local description="$1"
    local sql="$2"
    echo "--- $description ---"
    echo "$sql"
    echo ""
    docker exec "$CONTAINER_NAME" trino --execute "$sql" 2>&1 || echo "(query failed — see above)"
    echo ""
}

run_query "Example 1: query() — See the generated SQL" \
    "SELECT * FROM TABLE(justask.system.query('What are the top 5 nations by total order revenue?', 'tpch'))"

run_query "Example 2: ask() — Generate and execute" \
    "SELECT * FROM TABLE(justask.system.ask('How many customers are in each market segment?', 'tpch'))"

run_query "Example 3: ask() — More complex" \
    "SELECT * FROM TABLE(justask.system.ask('Which 3 nations have the most suppliers?', 'tpch'))"

echo "============================================"
echo "  Done! Trino is still running at localhost:8080"
echo ""
echo "  Interactive shell:"
echo "    docker exec -it $CONTAINER_NAME trino"
echo ""
echo "  Stop:"
echo "    docker rm -f $CONTAINER_NAME"
echo "============================================"
