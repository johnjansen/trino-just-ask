# customer

Schema: `tpch.sf1.customer`

| Column | Type | Description |
|--------|------|-------------|
| custkey | BIGINT | Primary key |
| name | VARCHAR | Customer name (e.g. Customer#000000001) |
| address | VARCHAR | Street address |
| nationkey | BIGINT | Foreign key to nation |
| phone | VARCHAR | Phone number |
| acctbal | DOUBLE | Account balance (can be negative) |
| mktsegment | VARCHAR | Market segment: AUTOMOBILE, BUILDING, FURNITURE, HOUSEHOLD, MACHINERY |
| comment | VARCHAR | Free-text comment |

## Primary Key
- `custkey`

## Foreign Keys
- `nationkey` → `nation.nationkey`

## Relationships
- Referenced by `orders.custkey`
- Belongs to one `nation`

## Common Join Patterns

### Customer with nation and region
```sql
SELECT c.name AS customer, c.mktsegment, n.name AS nation, r.name AS region
FROM tpch.sf1.customer c
JOIN tpch.sf1.nation n ON c.nationkey = n.nationkey
JOIN tpch.sf1.region r ON n.regionkey = r.regionkey
```

### Customer order summary
```sql
SELECT c.name AS customer, c.mktsegment,
       COUNT(o.orderkey) AS order_count,
       SUM(o.totalprice) AS total_spent
FROM tpch.sf1.customer c
JOIN tpch.sf1.orders o ON c.custkey = o.custkey
GROUP BY c.name, c.mktsegment
ORDER BY total_spent DESC
```
