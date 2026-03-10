# region

Schema: `tpch.sf1.region`

| Column | Type | Description |
|--------|------|-------------|
| regionkey | BIGINT | Primary key |
| name | VARCHAR | Region name: AFRICA, AMERICA, ASIA, EUROPE, MIDDLE EAST |
| comment | VARCHAR | Free-text comment |

## Primary Key
- `regionkey`

## Relationships
- Referenced by `nation.regionkey`

## Common Join Patterns

### List nations in a region
```sql
SELECT r.name AS region, n.name AS nation
FROM tpch.sf1.region r
JOIN tpch.sf1.nation n ON r.regionkey = n.regionkey
WHERE r.name = 'EUROPE'
```

### Aggregate customers by region
```sql
SELECT r.name AS region, COUNT(*) AS customer_count
FROM tpch.sf1.region r
JOIN tpch.sf1.nation n ON r.regionkey = n.regionkey
JOIN tpch.sf1.customer c ON c.nationkey = n.nationkey
GROUP BY r.name
ORDER BY customer_count DESC
```
