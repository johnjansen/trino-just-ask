# orders

Schema: `tpch.sf1.orders`

| Column | Type | Description |
|--------|------|-------------|
| orderkey | BIGINT | Primary key |
| custkey | BIGINT | Foreign key to customer |
| orderstatus | VARCHAR | Status: F (fulfilled), O (open), P (partial) |
| totalprice | DOUBLE | Total order price |
| orderdate | DATE | Date the order was placed |
| orderpriority | VARCHAR | Priority: 1-URGENT, 2-HIGH, 3-MEDIUM, 4-NOT SPECIFIED, 5-LOW |
| clerk | VARCHAR | Clerk who handled the order (e.g. Clerk#000000001) |
| shippriority | INTEGER | Shipping priority (0 = default) |
| comment | VARCHAR | Free-text comment |

## Primary Key
- `orderkey`

## Foreign Keys
- `custkey` → `customer.custkey`

## Relationships
- Referenced by `lineitem.orderkey`
- Belongs to one `customer`

## Notes
- `orderstatus` is derived: F if all lineitems shipped, O if none shipped, P if partially shipped
- `totalprice` is the sum of `lineitem.extendedprice * (1 - discount)` for all line items
- Order dates range from 1992-01-01 to 1998-08-02

## Common Join Patterns

### Orders with customer details
```sql
SELECT o.orderkey, c.name AS customer, o.totalprice, o.orderdate, o.orderstatus
FROM tpch.sf1.orders o
JOIN tpch.sf1.customer c ON o.custkey = c.custkey
ORDER BY o.orderdate DESC
```

### Orders by status
```sql
SELECT orderstatus,
       COUNT(*) AS order_count,
       SUM(totalprice) AS total_value
FROM tpch.sf1.orders
GROUP BY orderstatus
```

### High-priority open orders
```sql
SELECT o.orderkey, c.name AS customer, o.totalprice, o.orderdate
FROM tpch.sf1.orders o
JOIN tpch.sf1.customer c ON o.custkey = c.custkey
WHERE o.orderstatus = 'O'
  AND o.orderpriority IN ('1-URGENT', '2-HIGH')
ORDER BY o.orderdate
```
