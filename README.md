# Pooler Bank — Source MFB Style System

A production-grade **Spring Boot microfinance backend** integrated with **Apache Fineract** as the core banking engine.

## Architecture

```
Client (Mobile / Web)
        │
        ▼
Spring Boot (Port 8081)
        │
        ├── Auth Module          JWT · BCrypt · Role-based access
        ├── Wallet Module        Deposit · Withdraw · Live balance via Fineract
        ├── Loan Module          Apply · Approve · Disburse · Repay
        ├── Credit Scoring       Deposit history · Default check · Income threshold
        ├── Admin Module         User mgmt · KYC · Loan approval · Dashboard
        └── Fineract Client      WebClient · Basic Auth · Retry (Resilience4j)
                │
                ▼
        Apache Fineract (Port 8443)
```

## Quick Start

```bash
cp .env.example .env
docker-compose up --build
# App:     http://localhost:8081
# Swagger: http://localhost:8081/swagger-ui.html
```

## API Endpoints

### Auth (Public)
- POST /api/poolerapp/v1/createAccount
- POST /api/poolerapp/v1/login

### Wallet (JWT required)
- GET  /api/v1/wallet/{accountNumber}   — live balance from Fineract
- POST /api/v1/wallet/deposit
- POST /api/v1/wallet/withdraw

### Loans (JWT required)
- POST /api/v1/loans/apply              — credit check + Fineract
- GET  /api/v1/loans/{loanId}
- POST /api/v1/loans/{loanId}/repay
- GET  /api/v1/loans/credit-score/{accountNumber}

### Admin (ROLE_ADMIN)
- POST /api/v1/admin/loans/{id}/approve
- POST /api/v1/admin/loans/{id}/disburse
- GET  /api/v1/admin/users
- GET  /api/v1/admin/dashboard
- PUT  /api/v1/admin/users/{accountNumber}/kyc

## Credit Scoring Rules

- >= 3 successful deposits
- No defaulted loans
- Requested amount <= 2x wallet balance

## Running Tests

```bash
./mvnw test
```
