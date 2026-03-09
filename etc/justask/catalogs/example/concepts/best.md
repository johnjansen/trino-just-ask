# Defining "best"

When a user asks for the "best" customers, products, or similar:

- **"best customers"** → ORDER BY lifetime_value DESC
- **"top N"** → LIMIT N (default 10 if not specified)
- **"most active"** → ORDER BY COUNT(orders) DESC
- **"most popular"** → ORDER BY COUNT(*) DESC or SUM(quantity) DESC
- **"recent"** → ORDER BY created_at DESC or WHERE created_at > CURRENT_DATE - INTERVAL '30' DAY
