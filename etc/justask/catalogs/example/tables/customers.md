# customers

Schema: `example.public.customers`

| Column | Type | Description |
|--------|------|-------------|
| customer_id | BIGINT | Primary key |
| name | VARCHAR | Full name |
| email | VARCHAR | Email address |
| region | VARCHAR | Geographic region |
| created_at | TIMESTAMP | Account creation date |
| lifetime_value | DECIMAL(12,2) | Total revenue from customer |

## Relationships
- Referenced by `orders.customer_id`
