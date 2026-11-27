# NEON WALLET API

This service stores **play-money only** balances. It does not connect to banks, cards, payment processors, or cash-out rails.

## Endpoints

- `GET /health` — service/database health
- `POST /v1/auth/register` — create an account and grant the configured one-time virtual registration bonus
- `POST /v1/auth/login` — issue a bearer token
- `GET /v1/me` — authenticated account profile
- `GET /v1/wallet` — authenticated virtual wallet balance
- `GET /v1/transactions` — transaction history
- `POST /v1/transfers` — atomic virtual transfer by account ID; requires a unique `Idempotency-Key`

All amounts are integer kopecks to avoid floating-point rounding. The default registration reward is 500,000 kopecks (5,000 virtual RUB).

## Deploy

The repository root contains a Render Blueprint that provisions a Python API and PostgreSQL in Frankfurt. Review the plan in Render before approving resource creation. The free Postgres plan is temporary (30 days) and is suitable only for testing; use a paid persistent database before storing anything users rely on.

## Local run

Set `DATABASE_URL` to a PostgreSQL connection string and `JWT_SECRET` to a long random secret, then run:

```bash
pip install -r server/requirements.txt
uvicorn server.main:app --reload
```

Open `/docs` for the interactive API reference.
