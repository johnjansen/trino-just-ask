# part

Schema: `tpch.sf1.part`

| Column | Type | Description |
|--------|------|-------------|
| partkey | BIGINT | Primary key |
| name | VARCHAR | Part name (multi-word, e.g. "goldenrod lace spring") |
| mfgr | VARCHAR | Manufacturer (e.g. Manufacturer#1 through Manufacturer#5) |
| brand | VARCHAR | Brand (e.g. Brand#13) |
| type | VARCHAR | Part type (e.g. PROMO BURNISHED COPPER) |
| size | INTEGER | Part size (1-50) |
| container | VARCHAR | Container type (e.g. SM CASE, LG BOX) |
| retailprice | DOUBLE | Retail price |
| comment | VARCHAR | Free-text comment |

## Primary Key
- `partkey`

## Relationships
- Referenced by `partsupp.partkey`
- Referenced by `lineitem.partkey`

## Common Join Patterns

### Get part with supplier info and cost
```sql
SELECT p.name AS part, s.name AS supplier, ps.supplycost, ps.availqty
FROM tpch.sf1.part p
JOIN tpch.sf1.partsupp ps ON p.partkey = ps.partkey
JOIN tpch.sf1.supplier s ON ps.suppkey = s.suppkey
```

### Find parts by type
```sql
SELECT partkey, name, brand, type, size, retailprice
FROM tpch.sf1.part
WHERE type LIKE '%BRASS%'
```
