#!/usr/bin/env bash
set -euo pipefail

# Generate catalog documentation for Just Ask Trino
# Connects to Trino to introspect schemas/tables, then uses an LLM
# to generate rich documentation (descriptions, relationships,
# business terms, example queries) automatically.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] <catalog>

Generate catalog documentation from Trino table metadata using an LLM.

Arguments:
  catalog                  Trino catalog name to document

Trino Options:
  -s, --schema SCHEMA      Schema to document (default: all non-system schemas)
  -o, --output DIR         Output directory (default: etc/justask/catalogs/<catalog>)
  -h, --host HOST          Trino host (default: localhost)
  -p, --port PORT          Trino port (default: 8080)
  -u, --user USER          Trino user (default: admin)
  --trino CMD              Path to trino CLI (default: trino)

LLM Options:
  --llm-endpoint URL       OpenAI-compatible API endpoint (default: http://localhost:11434/v1)
  --llm-key KEY            API key (default: unused)
  --llm-model MODEL        Model name (default: glm-5:cloud)

Other:
  --help                   Show this help

Examples:
  $(basename "$0") my_postgres
  $(basename "$0") -s public --llm-endpoint https://api.openai.com/v1 --llm-key sk-... --llm-model gpt-4o my_postgres
  $(basename "$0") -h trino.internal -p 443 production_db
EOF
    exit 0
}

# Defaults
CATALOG=""
SCHEMA=""
OUTPUT_DIR=""
TRINO_HOST="localhost"
TRINO_PORT="8080"
TRINO_USER="admin"
TRINO_CMD="trino"
LLM_ENDPOINT="http://localhost:11434/v1"
LLM_KEY="unused"
LLM_MODEL="glm-5:cloud"

# Parse args
while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--schema) SCHEMA="$2"; shift 2 ;;
        -o|--output) OUTPUT_DIR="$2"; shift 2 ;;
        -h|--host) TRINO_HOST="$2"; shift 2 ;;
        -p|--port) TRINO_PORT="$2"; shift 2 ;;
        -u|--user) TRINO_USER="$2"; shift 2 ;;
        --trino) TRINO_CMD="$2"; shift 2 ;;
        --llm-endpoint) LLM_ENDPOINT="$2"; shift 2 ;;
        --llm-key) LLM_KEY="$2"; shift 2 ;;
        --llm-model) LLM_MODEL="$2"; shift 2 ;;
        --help) usage ;;
        -*) echo "Unknown option: $1" >&2; exit 1 ;;
        *) CATALOG="$1"; shift ;;
    esac
done

if [[ -z "$CATALOG" ]]; then
    echo "Error: catalog name is required" >&2
    echo "Run '$(basename "$0") --help' for usage" >&2
    exit 1
fi

if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="etc/justask/catalogs/$CATALOG"
fi

TRINO_URL="--server http://${TRINO_HOST}:${TRINO_PORT} --user ${TRINO_USER}"

# Run a Trino query, return tab-separated output
run_query() {
    local sql="$1"
    $TRINO_CMD $TRINO_URL --execute "$sql" --output-format TSV 2>/dev/null
}

# Call the LLM and return the response text
call_llm() {
    local system_prompt="$1"
    local user_prompt="$2"

    local payload
    payload=$(jq -n \
        --arg model "$LLM_MODEL" \
        --arg system "$system_prompt" \
        --arg user "$user_prompt" \
        '{
            model: $model,
            temperature: 0.3,
            max_tokens: 4096,
            messages: [
                {role: "system", content: $system},
                {role: "user", content: $user}
            ]
        }')

    local response
    response=$(curl -s "${LLM_ENDPOINT}/chat/completions" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${LLM_KEY}" \
        -d "$payload")

    echo "$response" | jq -r '.choices[0].message.content // empty'
}

echo "=== Generating catalog documentation for '$CATALOG' ==="
echo "Output: $OUTPUT_DIR"
echo "LLM: $LLM_MODEL at $LLM_ENDPOINT"
echo ""

# Discover schemas
if [[ -n "$SCHEMA" ]]; then
    SCHEMAS=("$SCHEMA")
else
    echo "Discovering schemas..."
    mapfile -t SCHEMAS < <(run_query "SHOW SCHEMAS FROM $CATALOG" | grep -v -E '^(information_schema)$')
    echo "Found ${#SCHEMAS[@]} schema(s): ${SCHEMAS[*]}"
fi

# Create output directories
mkdir -p "$OUTPUT_DIR/tables" "$OUTPUT_DIR/concepts" "$OUTPUT_DIR/examples"

# Collect all tables and their columns
declare -A TABLE_SCHEMAS
declare -a ALL_TABLES=()
FULL_SCHEMA_DUMP=""  # Accumulated schema info for LLM context

for schema in "${SCHEMAS[@]}"; do
    echo "Scanning $CATALOG.$schema..."
    while IFS= read -r table; do
        [[ -z "$table" ]] && continue
        TABLE_SCHEMAS["${schema}.${table}"]="$schema"
        ALL_TABLES+=("${schema}.${table}")
    done < <(run_query "SHOW TABLES FROM $CATALOG.$schema" 2>/dev/null || true)
done

echo "Found ${#ALL_TABLES[@]} table(s)"
echo ""

# Build full schema dump for LLM context
echo "Introspecting table metadata..."
for qualified_table in "${ALL_TABLES[@]}"; do
    schema="${TABLE_SCHEMAS[$qualified_table]}"
    table="${qualified_table#*.}"

    columns=$(run_query "DESCRIBE $CATALOG.$schema.$table" 2>/dev/null || true)
    if [[ -z "$columns" ]]; then
        continue
    fi

    FULL_SCHEMA_DUMP+="### $CATALOG.$schema.$table"$'\n'
    FULL_SCHEMA_DUMP+="| Column | Type | Extra | Comment |"$'\n'
    FULL_SCHEMA_DUMP+="|--------|------|-------|---------|"$'\n'
    while IFS=$'\t' read -r col_name col_type col_extra col_comment; do
        [[ -z "$col_name" ]] && continue
        FULL_SCHEMA_DUMP+="| $col_name | $col_type | ${col_extra:-} | ${col_comment:-} |"$'\n'
    done <<< "$columns"
    FULL_SCHEMA_DUMP+=$'\n'
done

echo ""

# System prompt for all LLM calls
LLM_SYSTEM="You are a technical documentation writer for SQL databases.
You generate clear, concise markdown documentation.
Output ONLY the requested markdown content — no preamble, no wrapping code fences, no explanations.
Use Trino SQL syntax (not MySQL, not PostgreSQL)."

# Generate table documentation
for qualified_table in "${ALL_TABLES[@]}"; do
    schema="${TABLE_SCHEMAS[$qualified_table]}"
    table="${qualified_table#*.}"
    safe_name="${schema}__${table}"

    if [[ ${#SCHEMAS[@]} -eq 1 ]]; then
        safe_name="$table"
    fi

    echo "  Generating docs for $CATALOG.$schema.$table..."

    columns=$(run_query "DESCRIBE $CATALOG.$schema.$table" 2>/dev/null || true)
    if [[ -z "$columns" ]]; then
        echo "    (skipped — could not describe)"
        continue
    fi

    # Build the column table for this specific table
    col_table="| Column | Type | Extra | Comment |"$'\n'
    col_table+="|--------|------|-------|---------|"$'\n'
    while IFS=$'\t' read -r col_name col_type col_extra col_comment; do
        [[ -z "$col_name" ]] && continue
        col_table+="| $col_name | $col_type | ${col_extra:-} | ${col_comment:-} |"$'\n'
    done <<< "$columns"

    # Ask LLM to generate rich table docs
    llm_prompt="Here is the full database schema for context:

$FULL_SCHEMA_DUMP

Generate documentation for the table \`$CATALOG.$schema.$table\` with these columns:

$col_table

Output this exact markdown structure:

# $table

Schema: \`$CATALOG.$schema.$table\`

(a one-line description of what this table contains)

| Column | Type | Description |
|--------|------|-------------|
(for each column, include the type from the schema and write a short description based on the column name, type, and context from other tables)

## Primary Key
(identify the most likely primary key based on column names)

## Foreign Keys
(identify likely foreign keys by matching column names like xxx_id or xxxkey to primary keys in other tables — use format: \`column\` → \`other_table.column\`)

## Relationships
(describe how this table connects to others — e.g. \"Referenced by \`orders.custkey\`\", \"Belongs to one \`nation\`\")

## Common Join Patterns

(write 1-2 useful SQL join examples using this table with related tables, using fully qualified table names like \`$CATALOG.$schema.tablename\`)"

    result=$(call_llm "$LLM_SYSTEM" "$llm_prompt")

    if [[ -n "$result" ]]; then
        echo "$result" > "$OUTPUT_DIR/tables/${safe_name}.md"
    else
        # Fallback: write basic column listing without LLM
        echo "    (LLM unavailable — writing basic schema only)"
        {
            echo "# $table"
            echo ""
            echo "Schema: \`$CATALOG.$schema.$table\`"
            echo ""
            echo "| Column | Type | Description |"
            echo "|--------|------|-------------|"
            while IFS=$'\t' read -r col_name col_type col_extra col_comment; do
                [[ -z "$col_name" ]] && continue
                echo "| $col_name | $col_type | ${col_comment:-} |"
            done <<< "$columns"
        } > "$OUTPUT_DIR/tables/${safe_name}.md"
    fi
done

# Generate index.md
echo ""
echo "Generating index.md..."

# Build table list for the LLM
table_list=""
for qualified_table in "${ALL_TABLES[@]}"; do
    schema="${TABLE_SCHEMAS[$qualified_table]}"
    table="${qualified_table#*.}"
    if [[ ${#SCHEMAS[@]} -eq 1 ]]; then
        safe_name="$table"
    else
        safe_name="${schema}__${table}"
    fi
    table_list+="- $CATALOG.$schema.$table → tables/${safe_name}.md"$'\n'
done

index_prompt="Here is the full database schema:

$FULL_SCHEMA_DUMP

Here are the table doc file paths:
$table_list

Generate an index.md file for the \"$CATALOG\" catalog. Output this exact structure:

# $CATALOG Catalog

(2-3 sentences describing what this catalog/database contains, based on the table and column names)

(for each schema, if there are multiple schemas, add a ## Schema: <name> header)

## Tables

Tables are accessed at \`$CATALOG.<schema>.<table>\`.

(list each table as a markdown link with a short description, like:)
(- [tablename](tables/filename.md) — Short description)

## Concepts
- [Business terms](concepts/business-terms.md) — Maps natural language to SQL patterns

## Example Queries
- [Common queries](examples/common-queries.md)"

result=$(call_llm "$LLM_SYSTEM" "$index_prompt")

if [[ -n "$result" ]]; then
    echo "$result" > "$OUTPUT_DIR/index.md"
else
    # Fallback
    {
        echo "# $CATALOG Catalog"
        echo ""
        for schema in "${SCHEMAS[@]}"; do
            if [[ ${#SCHEMAS[@]} -gt 1 ]]; then
                echo "## Schema: $schema"
            else
                echo "## Tables"
            fi
            echo ""
            echo "Tables are accessed at \`$CATALOG.$schema.<table>\`."
            echo ""
            for qualified_table in "${ALL_TABLES[@]}"; do
                t_schema="${TABLE_SCHEMAS[$qualified_table]}"
                [[ "$t_schema" != "$schema" ]] && continue
                table="${qualified_table#*.}"
                if [[ ${#SCHEMAS[@]} -eq 1 ]]; then
                    safe_name="$table"
                else
                    safe_name="${schema}__${table}"
                fi
                echo "- [$table](tables/${safe_name}.md)"
            done
            echo ""
        done
        echo "## Concepts"
        echo "- [Business terms](concepts/business-terms.md) — Maps natural language to SQL patterns"
        echo ""
        echo "## Example Queries"
        echo "- [Common queries](examples/common-queries.md)"
    } > "$OUTPUT_DIR/index.md"
fi

# Generate business terms
echo "Generating business terms..."

terms_prompt="Here is the full database schema:

$FULL_SCHEMA_DUMP

Generate a business-terms.md file that maps natural language to SQL patterns for this database.

Analyze the column names and types to infer what business concepts exist (revenue, counts, rankings, statuses, time periods, etc.) and write concrete mappings.

Output this structure:

# Business Terms

Maps natural language questions to SQL patterns for the $CATALOG catalog.

(organize into logical sections like Revenue/Pricing, Rankings, Status/Filters, Counting, Time, etc.)
(each entry should be: - **\"natural language term\"** → \`SQL_EXPRESSION\`)
(use fully qualified table names like \`$CATALOG.schema.table\`)
(include at least 15-20 terms covering the most common ways users would ask about this data)"

result=$(call_llm "$LLM_SYSTEM" "$terms_prompt")

if [[ -n "$result" ]]; then
    echo "$result" > "$OUTPUT_DIR/concepts/business-terms.md"
else
    echo "    (LLM unavailable — writing template)"
    cat > "$OUTPUT_DIR/concepts/business-terms.md" <<'EOF'
# Business Terms

Maps natural language questions to SQL patterns.

<!-- Add entries that map how users talk about your data to actual SQL. -->
EOF
fi

# Generate example queries
echo "Generating example queries..."

examples_prompt="Here is the full database schema:

$FULL_SCHEMA_DUMP

Generate a common-queries.md file with example SQL queries for this database.

Write 5-8 practical queries that cover:
- Simple aggregations (counts, sums)
- Group-by queries
- Multi-table joins
- Ranking/top-N queries
- Date/time filtering (if date columns exist)

Output this structure:

# Common Queries

Example queries for the $CATALOG catalog.

(for each query, use a ## heading describing what it answers, then a \`\`\`sql code block)
(use fully qualified table names like \`$CATALOG.schema.table\`)
(use standard Trino SQL syntax)"

result=$(call_llm "$LLM_SYSTEM" "$examples_prompt")

if [[ -n "$result" ]]; then
    echo "$result" > "$OUTPUT_DIR/examples/common-queries.md"
else
    echo "    (LLM unavailable — writing template)"
    cat > "$OUTPUT_DIR/examples/common-queries.md" <<'EOF'
# Common Queries

Example queries the LLM can reference when building SQL.

<!-- Add queries that represent common questions users ask. -->
EOF
fi

echo ""
echo "=== Done ==="
echo ""
echo "Generated files:"
find "$OUTPUT_DIR" -type f | sort | while read -r f; do
    echo "  $f"
done
echo ""
echo "Review the generated docs and refine as needed, then deploy"
echo "to your Trino docs.base-dir and start asking questions!"
