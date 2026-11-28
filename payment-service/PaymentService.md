# Handoff Document: Conversation 04 – Payment Service

## 1. Implemented Functionality
- `PaymentTransaction` JPA entity persisted in `payment_transactions` with `PENDING_FRAUD_CHECK` initial status and `STARTED` saga state.
- `IdempotencyRecord` JPA entity persisted in `idempotency_records` with unique `idempotency_key`, `IN_PROGRESS` reservation via `saveAndFlush`, and `PROCESSED` cached response replay.
- `PaymentService` / `PaymentServiceImpl` enforcing `Idempotency-Key` presence, self-transfer prevention, positive amount validation, and `UPI` (<= 100,000) and `IMPS` (<= 500,000) limits (`NEFT` uncapped).
- `PaymentController` exposing `POST /payments`, `GET /payments/{id}`, `GET /payments/idempotency/{key}`, and `GET /payments?accountId=...`.
- `GlobalExceptionHandler` mapping validation (`400`), duplicate/in-flight idempotency (`409`), not-found (`404`), and unexpected errors (`500`).
- Automated tests in `PaymentServiceApplicationTests`.

## 2. Technologies and Versions
- Java 17
- Spring Boot 3.2.5 (Web, Data JPA, Validation, Actuator)
- Spring Cloud 2023.0.1 BOM (via parent POM)
- PostgreSQL runtime driver / H2 test dependency

## 3. Package and Class Structure
- `com.bankflow.payment.PaymentServiceApplication`
- `com.bankflow.payment.config.PaymentProperties`
- `com.bankflow.payment.controller.PaymentController`
- `com.bankflow.payment.domain.IdempotencyRecord`
- `com.bankflow.payment.domain.IdempotencyStatus`
- `com.bankflow.payment.domain.PaymentTransaction`
- `com.bankflow.payment.dto.ErrorResponse`
- `com.bankflow.payment.dto.PaymentRequest`
- `com.bankflow.payment.dto.PaymentResponse`
- `com.bankflow.payment.exception.DuplicatePaymentException`
- `com.bankflow.payment.exception.GlobalExceptionHandler`
- `com.bankflow.payment.exception.PaymentNotFoundException`
- `com.bankflow.payment.exception.PaymentValidationException`
- `com.bankflow.payment.repository.IdempotencyRecordRepository`
- `com.bankflow.payment.repository.PaymentTransactionRepository`
- `com.bankflow.payment.service.PaymentService`
- `com.bankflow.payment.service.PaymentServiceImpl`

## 4. Database Schema (`bankflow_payments`)
- `payment_transactions`: `id` (PK), `idempotency_key` (UNIQUE, NOT NULL), `from_account_id`, `to_account_id`, `amount` (DECIMAL 19,4), `currency`, `payment_mode`, `status`, `saga_state`, `remarks`, `failure_reason`, `version`, `created_at`, `updated_at`.
- `idempotency_records`: `idempotency_key` (PK), `transaction_id`, `status`, `http_status`, `response_body`, `created_at`, `updated_at`.

## 5. API Endpoints
- `POST /payments` (Header `Idempotency-Key` or `X-Idempotency-Key`, Body `PaymentRequest`) -> `202 Accepted` (`PaymentResponse`), `400 Bad Request`, `409 Conflict`.
- `GET /payments/{id}` -> `200 OK` (`PaymentResponse`) or `404 Not Found`.
- `GET /payments/idempotency/{key}` -> `200 OK` (`PaymentResponse`) or `404 Not Found`.
- `GET /payments?accountId={accountId}` -> `200 OK` (`List<PaymentResponse>`).

## 6. Business Rules
- Missing/blank `Idempotency-Key` returns `400 Bad Request`.
- `fromAccountId` equal to `toAccountId` (case-insensitive trimmed) returns `400 Bad Request`.
- `amount` must be `> 0`.
- `UPI` max default is `100000.00`; `IMPS` max default is `500000.00`; `NEFT` has no upper cap in config.
- Replaying a `PROCESSED` idempotency key returns the stored response without creating a second transaction.
- Concurrent/duplicate keys while `IN_PROGRESS` or conflicting throw `DuplicatePaymentException` (`409 Conflict`).

## 7. Configuration and Environment Variables
- `SERVER_PORT` / `server.port`: `8082`
- `SPRING_DATASOURCE_URL`: `jdbc:postgresql://localhost:5432/bankflow_payments`
- `SPRING_DATASOURCE_USERNAME`: `postgres`
- `SPRING_DATASOURCE_PASSWORD`: `postgres`
- `bankflow.payment.limits.upi-max`: `100000.00`
- `bankflow.payment.limits.imps-max`: `500000.00`

## 8. Commands to Run
- From `common-dto`: `mvn clean install`
- From `payment-service` tests: `mvn test`
- From `payment-service` run: `mvn spring-boot:run`

## 9. Tests Implemented
- `PaymentServiceApplicationTests`:
  - `shouldCreatePaymentAndReturnCachedResponseOnReplay`
  - `shouldRejectSelfTransferAndExceedingModeLimits`
  - `shouldRejectMissingIdempotencyKey`
  - `shouldHandleConcurrentDuplicateIdempotencyKeysSafely`

## 10. Known Limitations
- Kafka producer (`TransactionInitiatedEvent`), Saga orchestration (`PaymentSagaCoordinator`), and Account Service HTTP calls are intentionally excluded in this conversation.
- Uses `spring.jpa.hibernate.ddl-auto=update` rather than Flyway migrations.

## 11. Integration Requirements for Next Conversations
- Conversation 05 (Kafka): Publish `TransactionInitiatedEvent` to `txn-events` keyed by `transactionId` after transaction persistence.
- Conversation 07 (Saga): Consume `txn-approved` and `fraud-alerts` to transition `PaymentTransaction.status` and `sagaState` and invoke Account Service balance endpoints.