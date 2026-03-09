# nation

Schema: `tpch.sf1.nation`

| Column | Type | Description |
|--------|------|-------------|
| nationkey | BIGINT | Primary key |
| name | VARCHAR | Nation name (e.g. FRANCE, UNITED STATES) |
| regionkey | BIGINT | Foreign key to region |
| comment | VARCHAR | Free-text comment |

## Primary Key
- `nationkey`

## Foreign Keys
- `regionkey` → `region.regionkey`

## Relationships
- Referenced by `supplier.nationkey`
- Referenced by `customer.nationkey`
- Belongs to one `region`

## Common Join Patterns

### Get nation with its region
```sql
SELECT n.name AS nation, r.name AS region
FROM tpch.sf1.nation n
JOIN tpch.sf1.region r ON n.regionkey = r.regionkey
```

### Get all suppliers in a nation
```sql
SELECT s.name AS supplier, n.name AS nation
FROM tpch.sf1.supplier s
JOIN tpch.sf1.nation n ON s.nationkey = n.nationkey
WHERE n.name = 'FRANCE'
```
