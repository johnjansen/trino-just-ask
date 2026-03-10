# partsupp

Schema: `tpch.sf1.partsupp`

This is the junction table between parts and suppliers. Each row represents a part-supplier relationship with availability and cost information.

| Column | Type | Description |
|--------|------|-------------|
| partkey | BIGINT | Foreign key to part (composite PK) |
| suppkey | BIGINT | Foreign key to supplier (composite PK) |
| availqty | INTEGER | Available quantity from this supplier |
| supplycost | DOUBLE | Cost to supply this part |
| comment | VARCHAR | Free-text comment |

## Primary Key
- Composite: (`partkey`, `suppkey`)

## Foreign Keys
- `partkey` → `part.partkey`
- `suppkey` → `supplier.suppkey`

## Relationships
- Links `part` to `supplier` (many-to-many)

## Common Join Patterns

### Cheapest supplier for each part
```sql
SELECT p.name AS part, s.name AS supplier, ps.supplycost
FROM tpch.sf1.partsupp ps
JOIN tpch.sf1.part p ON ps.partkey = p.partkey
JOIN tpch.sf1.supplier s ON ps.suppkey = s.suppkey
WHERE ps.supplycost = (
    SELECT MIN(ps2.supplycost)
    FROM tpch.sf1.partsupp ps2
    WHERE ps2.partkey = ps.partkey
)
```

### Total supply value by supplier
```sql
SELECT s.name AS supplier, SUM(ps.supplycost * ps.availqty) AS total_value
FROM tpch.sf1.partsupp ps
JOIN tpch.sf1.supplier s ON ps.suppkey = s.suppkey
GROUP BY s.name
ORDER BY total_value DESC
```
