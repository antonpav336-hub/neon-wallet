import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Annotated

import jwt
import psycopg
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from fastapi import Depends, FastAPI, Header, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, EmailStr, Field
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

DATABASE_URL = os.environ["DATABASE_URL"]
JWT_SECRET = os.environ["JWT_SECRET"]
JWT_ALGORITHM = "HS256"
TOKEN_TTL_HOURS = int(os.getenv("TOKEN_TTL_HOURS", "168"))
REGISTRATION_BONUS_KOPECKS = int(os.getenv("REGISTRATION_BONUS_KOPECKS", "500000"))

app = FastAPI(title="NEON WALLET API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Authorization", "Content-Type", "Idempotency-Key"],
)

pool = ConnectionPool(DATABASE_URL, min_size=1, max_size=8, kwargs={"row_factory": dict_row})
password_hasher = PasswordHasher()


def initialize_database() -> None:
    with pool.connection() as conn:
        conn.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id BIGSERIAL PRIMARY KEY,
            account_id VARCHAR(12) UNIQUE NOT NULL,
            username VARCHAR(32) UNIQUE NOT NULL,
            email VARCHAR(254) UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE TABLE IF NOT EXISTS wallets (
            user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            balance_kopecks BIGINT NOT NULL DEFAULT 0 CHECK (balance_kopecks >= 0),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE TABLE IF NOT EXISTS transactions (
            id UUID PRIMARY KEY,
            idempotency_key VARCHAR(100) UNIQUE NOT NULL,
            sender_user_id BIGINT REFERENCES users(id),
            recipient_user_id BIGINT REFERENCES users(id),
            amount_kopecks BIGINT NOT NULL CHECK (amount_kopecks > 0),
            kind VARCHAR(24) NOT NULL,
            description VARCHAR(140) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS transactions_sender_created_idx
            ON transactions(sender_user_id, created_at DESC);
        CREATE INDEX IF NOT EXISTS transactions_recipient_created_idx
            ON transactions(recipient_user_id, created_at DESC);
        """)
        conn.commit()


@app.on_event("startup")
def startup() -> None:
    initialize_database()


@app.on_event("shutdown")
def shutdown() -> None:
    pool.close()


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z0-9_]+$")
    email: EmailStr
    password: str = Field(min_length=10, max_length=128)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class TransferRequest(BaseModel):
    recipient_account_id: str = Field(min_length=6, max_length=12, pattern=r"^[0-9]+$")
    amount_kopecks: int = Field(gt=0, le=100_000_000)
    description: str = Field(default="Перевод", min_length=1, max_length=140)


def issue_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode(
        {"sub": str(user_id), "iat": now, "exp": now + timedelta(hours=TOKEN_TTL_HOURS)},
        JWT_SECRET,
        algorithm=JWT_ALGORITHM,
    )


def current_user(authorization: Annotated[str | None, Header()] = None) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Требуется авторизация")
    try:
        payload = jwt.decode(authorization[7:], JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(status_code=401, detail="Сессия недействительна или истекла")
    with pool.connection() as conn:
        user = conn.execute(
            "SELECT id, account_id, username, email, created_at FROM users WHERE id = %s",
            (user_id,),
        ).fetchone()
    if not user:
        raise HTTPException(status_code=401, detail="Пользователь не найден")
    return user


User = Annotated[dict, Depends(current_user)]


def public_user(user: dict) -> dict:
    return {
        "account_id": user["account_id"],
        "username": user["username"],
        "email": user["email"],
        "created_at": user["created_at"].isoformat(),
    }


@app.get("/health")
def health():
    with pool.connection() as conn:
        conn.execute("SELECT 1")
    return {"status": "ok", "service": "neon-wallet-api"}


@app.post("/v1/auth/register", status_code=201)
def register(body: RegisterRequest):
    password_hash = password_hasher.hash(body.password)
    with pool.connection() as conn:
        with conn.transaction():
            for _ in range(10):
                account_id = str(secrets.randbelow(900_000_000) + 100_000_000)
                exists = conn.execute("SELECT 1 FROM users WHERE account_id = %s", (account_id,)).fetchone()
                if not exists:
                    break
            else:
                raise HTTPException(status_code=503, detail="Не удалось создать ID аккаунта")
            try:
                user = conn.execute(
                    """INSERT INTO users(account_id, username, email, password_hash)
                       VALUES (%s, %s, %s, %s)
                       RETURNING id, account_id, username, email, created_at""",
                    (account_id, body.username, body.email.lower(), password_hash),
                ).fetchone()
                conn.execute(
                    "INSERT INTO wallets(user_id, balance_kopecks) VALUES (%s, %s)",
                    (user["id"], REGISTRATION_BONUS_KOPECKS),
                )
                conn.execute(
                    """INSERT INTO transactions(id, idempotency_key, recipient_user_id,
                       amount_kopecks, kind, description)
                       VALUES (gen_random_uuid(), %s, %s, %s, 'signup_bonus', 'Бонус за регистрацию')""",
                    (f"signup:{user['id']}", user["id"], REGISTRATION_BONUS_KOPECKS),
                )
            except psycopg.errors.UniqueViolation:
                raise HTTPException(status_code=409, detail="Имя пользователя или email уже заняты")
    return {
        "access_token": issue_token(user["id"]),
        "token_type": "bearer",
        "user": public_user(user),
        "wallet": {"balance_kopecks": REGISTRATION_BONUS_KOPECKS},
    }


@app.post("/v1/auth/login")
def login(body: LoginRequest):
    with pool.connection() as conn:
        user = conn.execute(
            """SELECT id, account_id, username, email, password_hash, created_at
               FROM users WHERE email = %s""",
            (body.email.lower(),),
        ).fetchone()
    if not user:
        raise HTTPException(status_code=401, detail="Неверный email или пароль")
    try:
        password_hasher.verify(user["password_hash"], body.password)
    except VerifyMismatchError:
        raise HTTPException(status_code=401, detail="Неверный email или пароль")
    return {
        "access_token": issue_token(user["id"]),
        "token_type": "bearer",
        "user": public_user(user),
    }


@app.get("/v1/me")
def me(user: User):
    return {"user": public_user(user)}


@app.get("/v1/wallet")
def wallet(user: User):
    with pool.connection() as conn:
        row = conn.execute(
            "SELECT balance_kopecks, updated_at FROM wallets WHERE user_id = %s",
            (user["id"],),
        ).fetchone()
    return {
        "account_id": user["account_id"],
        "currency": "VIRTUAL_RUB",
        "balance_kopecks": row["balance_kopecks"],
        "updated_at": row["updated_at"].isoformat(),
    }


@app.get("/v1/transactions")
def transactions(user: User, limit: int = 50):
    limit = max(1, min(limit, 100))
    with pool.connection() as conn:
        rows = conn.execute(
            """SELECT t.id, t.kind, t.description, t.amount_kopecks, t.created_at,
                      s.account_id AS sender_account_id, r.account_id AS recipient_account_id
               FROM transactions t
               LEFT JOIN users s ON s.id = t.sender_user_id
               LEFT JOIN users r ON r.id = t.recipient_user_id
               WHERE t.sender_user_id = %s OR t.recipient_user_id = %s
               ORDER BY t.created_at DESC LIMIT %s""",
            (user["id"], user["id"], limit),
        ).fetchall()
    return {"items": [{
        "id": str(row["id"]), "kind": row["kind"], "description": row["description"],
        "amount_kopecks": row["amount_kopecks"], "created_at": row["created_at"].isoformat(),
        "sender_account_id": row["sender_account_id"],
        "recipient_account_id": row["recipient_account_id"],
    } for row in rows]}


@app.post("/v1/transfers")
def transfer(
    body: TransferRequest,
    user: User,
    idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None,
):
    if not idempotency_key or len(idempotency_key) > 100:
        raise HTTPException(status_code=400, detail="Передайте уникальный Idempotency-Key")
    if body.recipient_account_id == user["account_id"]:
        raise HTTPException(status_code=400, detail="Нельзя перевести средства самому себе")

    with pool.connection() as conn:
        with conn.transaction():
            existing = conn.execute(
                "SELECT id FROM transactions WHERE idempotency_key = %s", (idempotency_key,)
            ).fetchone()
            if existing:
                raise HTTPException(status_code=409, detail="Этот запрос уже был обработан")
            recipient = conn.execute(
                "SELECT id FROM users WHERE account_id = %s", (body.recipient_account_id,)
            ).fetchone()
            if not recipient:
                raise HTTPException(status_code=404, detail="Получатель не найден")

            debit = conn.execute(
                """UPDATE wallets SET balance_kopecks = balance_kopecks - %s, updated_at = NOW()
                   WHERE user_id = %s AND balance_kopecks >= %s RETURNING balance_kopecks""",
                (body.amount_kopecks, user["id"], body.amount_kopecks),
            ).fetchone()
            if not debit:
                raise HTTPException(status_code=409, detail="Недостаточно виртуальных средств")

            conn.execute(
                "UPDATE wallets SET balance_kopecks = balance_kopecks + %s, updated_at = NOW() WHERE user_id = %s",
                (body.amount_kopecks, recipient["id"]),
            )
            tx = conn.execute(
                """INSERT INTO transactions(id, idempotency_key, sender_user_id,
                   recipient_user_id, amount_kopecks, kind, description)
                   VALUES (gen_random_uuid(), %s, %s, %s, %s, 'transfer', %s)
                   RETURNING id, created_at""",
                (idempotency_key, user["id"], recipient["id"], body.amount_kopecks, body.description),
            ).fetchone()

    return {
        "transaction_id": str(tx["id"]),
        "status": "completed",
        "amount_kopecks": body.amount_kopecks,
        "balance_kopecks": debit["balance_kopecks"],
        "created_at": tx["created_at"].isoformat(),
    }
