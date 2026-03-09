# Revenue Report Examples

## Total revenue by region
```sql
SELECT region, SUM(lifetime_value) AS total_revenue
FROM example.public.customers
GROUP BY region
ORDER BY total_revenue DESC
```

## Top 10 customers by lifetime value
```sql
SELECT customer_id, name, lifetime_value
FROM example.public.customers
ORDER BY lifetime_value DESC
LIMIT 10
```
