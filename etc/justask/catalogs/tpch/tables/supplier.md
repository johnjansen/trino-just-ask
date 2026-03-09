# supplier

Schema: `tpch.sf1.supplier`

| Column | Type | Description |
|--------|------|-------------|
| suppkey | BIGINT | Primary key |
| name | VARCHAR | Supplier name (e.g. Supplier#000000001) |
| address | VARCHAR | Street address |
| nationkey | BIGINT | Foreign key to nation |
| phone | VARCHAR | Phone number |
| acctbal | DOUBLE | Account balance (can be negative) |
| comment | VARCHAR | Free-text comment |

## Primary Key
- `suppkey`

## Foreign Keys
- `nationkey` → `nation.nationkey`

## Relationships
- Referenced by `partsupp.suppkey`
- Referenced by `lineitem.suppkey`
- Belongs to one `nation`

## Common Join Patterns

### Get supplier with nation and region
```sql
SELECT s.name AS supplier, n.name AS nation, r.name AS region
FROM tpch.sf1.supplier s
JOIN tpch.sf1.nation n ON s.nationkey = n.nationkey
JOIN tpch.sf1.region r ON n.regionkey = r.regionkey
```

### Find suppliers for a specific part
```sql
SELECT s.name AS supplier, ps.supplycost, ps.availqty
FROM tpch.sf1.supplier s
JOIN tpch.sf1.partsupp ps ON s.suppkey = ps.suppkey
WHERE ps.partkey = 100
ORDER BY ps.supplycost ASC
```
