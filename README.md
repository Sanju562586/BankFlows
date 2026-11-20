# BankFlow

BankFlow is an event-driven banking and payment platform built with Java 17, Spring Boot 3.2.5 and Spring Cloud 2023.0.1.

## Repository layout

| Directory | Purpose | Maven reactor |
|---|---|---|
| `common-dto` | Shared DTOs, event contracts and enums | Yes |
| `api-gateway` | Reactive API gateway | Yes |
| `account-service` | Account and ledger ownership | Yes |
| `payment-service` | Payment processing and Saga orchestration | Yes |
| `fraud-detection-service` | Transaction risk evaluation | Yes |
| `notification-service` | Customer notification delivery | Yes |
| `dashboard` | Node.js dashboard application | No |
| `infrastructure` | Docker, Kubernetes and operational assets | No |

The root `pom.xml` is the Maven aggregator and parent. Only the six Java modules listed above participate in the Maven reactor.

## Prerequisites

- JDK 17
- Apache Maven 3.9 or newer
- Internet access on the first build to resolve Maven dependencies

Verify your tools:

```bash
java -version
mvn -version
```

## Build the complete Java project

Run these commands from the repository root:

```bash
mvn validate
mvn clean test
mvn clean verify
mvn clean install
```

`mvn clean install` is the foundation acceptance command. Maven builds modules in reactor dependency order and installs their artifacts into the local Maven repository.

Build one module and its required reactor dependencies:

```bash
mvn -pl payment-service -am clean verify
```

## Module conventions

- Java source and test code use `src/main/java` and `src/test/java`.
- Application modules are executable Spring Boot applications.
- `common-dto` is a plain JAR library; it is not an executable Spring Boot application.
- Dependency versions are managed centrally by the Spring Boot and Spring Cloud BOMs in the root POM.
- Child POMs should not repeat versions for dependencies managed by those BOMs.
- Compiler release is fixed to Java 17.
- Unit tests use the Spring Boot test dependency and Maven Surefire.
- Integration tests may use `*IT` naming and Maven Failsafe when introduced by the relevant service component.

## Git conventions

Suggested branch pattern:

- `main`: stable integration branch
- `feature/<component>-<short-description>`: feature work
- `fix/<component>-<short-description>`: bug fixes
- `docs/<short-description>`: documentation-only changes

Use focused commits with conventional prefixes such as `feat:`, `fix:`, `test:`, `docs:`, `build:` and `chore:`.

## Configuration and secrets

Never commit real credentials, API keys, signing keys, database passwords or production configuration secrets. Use environment variables and provide only sanitized placeholders in `.env.example` files when a later component introduces them.

## Scope note

This repository foundation defines module boundaries and build conventions only. Service APIs, database schemas, Kafka event fields, business logic, deployment manifests and dashboard implementation are owned by their respective components and must follow the BankFlow architecture specification.
