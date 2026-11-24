# Account Service

Core microservice responsible for customer accounts, balance management, double-entry ledger bookkeeping, and atomic peer-to-peer transfers.

---

## Features

- **Account Lifecycle**: Creation, retrieval, and status tracking with unique 10-digit account numbers.
- **Atomic Transfers**: Peer-to-peer fund movement with pessimistic locking (`PESSIMISTIC_WRITE`) and strict balance validation.
- **Audit Ledger**: Immutable transaction history (`account_transactions`) tracking every credit and debit.
- **Event-Driven**: Emits domain events (`account-events`, `balance-updates`) to Kafka.

---

## Tech Stack

- **Runtime**: Java 17
- **Framework**: Spring Boot 3.2.5 (Web, Data JPA, Validation)
- **Database**: PostgreSQL
- **Messaging**: Apache Kafka
- **Observability**: Spring Boot Actuator

---

## Configuration

Default settings in `src/main/resources/application.yml`:

| Property | Default | Environment Variable |
|---|---|---|
| Server Port | `8081` | `SERVER_PORT` |
| Database URL | `jdbc:postgresql://localhost:5432/bankflow_accounts` | `SPRING_DATASOURCE_URL` |
| Database User | `bankflow` | `SPRING_DATASOURCE_USERNAME` |
| Database Password | `bankflow_secret` | `SPRING_DATASOURCE_PASSWORD` |
| Kafka Brokers | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |

---

## API Reference

Base Path: `/accounts`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/accounts` | Create a new customer account |
| `GET` | `/accounts` | List all accounts |
| `GET` | `/accounts/{id}` | Get account by ID or account number |
| `GET` | `/accounts/{id}/balance` | Get current account balance |
| `POST` | `/accounts/{id}/balance` | Direct debit/credit balance operation |
| `POST` | `/accounts/transfer` | Atomic transfer between two accounts |
| `GET` | `/accounts/{id}/transactions` | Paginated transaction history |

---

## Request Examples

### 1. Create Account
`POST /accounts`

```json
{
  "customerId": "cust-1",
  "customerName": "Aarav Sharma",
  "email": "aarav@example.com",
  "phoneNumber": "+919876543210",
  "currency": "INR",
  "initialBalance": 5000.00
}
```

### 2. Transfer Money
`POST /accounts/transfer`

```json
{
  "fromAccountId": "acc-2151ff74",
  "toAccountId": "acc-2a9ac664",
  "amount": 1000.00,
  "description": "Rent payment"
}
```

> **Note**: If the sender account does not have sufficient balance, the endpoint returns `409 Conflict` and prevents any fund movement.

---

## Running Locally

### Prerequisites
- Docker (with `bankflow-postgres` and `bankflow-kafka` running)
- JDK 17 & Maven 3.9+

### Commands

```powershell
# Run unit tests
mvn test -pl account-service

# Start the service
mvn spring-boot:run -pl account-service
```

Health check is available at: `http://localhost:8081/actuator/health`
