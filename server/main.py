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
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, EmailStr, Field
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

DATABASE_URL = os.environ["DATABASE_URL"]
JWT_SECRET = os.environ["JWT_SECRET"]
JWT_ALGORITHM = "HS256"
TOKEN_TTL_HOURS = int(os.getenv("TOKEN_TTL_HOURS", "168"))
REGISTRATION_BONUS_KOPECKS = int(os.getenv("REGISTRATION_BONUS_KOPECKS", "500000"))
ADMIN_ACCOUNT_IDS = {value.strip() for value in os.getenv("ADMIN_ACCOUNT_IDS", "").split(",") if value.strip()}

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
        ALTER TABLE transactions ADD COLUMN IF NOT EXISTS admin_user_id BIGINT REFERENCES users(id);
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


class AdminCreditRequest(BaseModel):
    account_id: str = Field(min_length=6, max_length=12, pattern=r"^[0-9]+$")
    amount_kopecks: int = Field(gt=0, le=100_000_000)
    description: str = Field(default="Административное пополнение", min_length=1, max_length=140)
    request_key: str = Field(min_length=16, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$")


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


def require_admin(user: User) -> dict:
    if not ADMIN_ACCOUNT_IDS or user["account_id"] not in ADMIN_ACCOUNT_IDS:
        raise HTTPException(status_code=403, detail="Нет доступа администратора")
    return user


AdminUser = Annotated[dict, Depends(require_admin)]


def public_user(user: dict) -> dict:
    return {
        "account_id": user["account_id"],
        "username": user["username"],
        "email": user["email"],
        "created_at": user["created_at"].isoformat(),
    }


@app.get("/admin", response_class=HTMLResponse)
def admin_panel():
    return HTMLResponse(r"""
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#090711"><title>NEON WALLET — Admin</title>
<style>
:root{color-scheme:dark;--bg:#090711;--panel:#141020;--line:#302342;--purple:#a855f7;--pink:#ec4899;--muted:#a79bb8;--text:#f7f1ff}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(ellipse at 50% -20%,#39205b 0,transparent 55%),var(--bg);color:var(--text);font:15px Inter,system-ui,sans-serif;min-height:100vh;padding:28px 16px}
main{width:min(760px,100%);margin:0 auto}.brand{font-weight:900;letter-spacing:3px;color:#d9b8ff;font-size:13px}.brand span{color:var(--pink)}h1{font-size:30px;margin:12px 0 6px}.sub{color:var(--muted);margin:0 0 24px}.panel{background:linear-gradient(145deg,#191326,#100d19);border:1px solid var(--line);border-radius:20px;padding:22px;margin:14px 0;box-shadow:0 16px 50px #0005}.panel h2{font-size:17px;margin:0 0 16px}.field{display:grid;gap:7px;margin:13px 0}.field label{font-size:13px;color:#c9bdd7}.field input{width:100%;height:48px;border-radius:12px;border:1px solid #443455;background:#0b0911;color:white;padding:0 14px;font-size:16px;outline:none}.field input:focus{border-color:var(--purple);box-shadow:0 0 0 3px #a855f722}button{height:48px;border:0;border-radius:12px;padding:0 18px;background:linear-gradient(100deg,#9333ea,#db2777);color:white;font-weight:800;font-size:15px;width:100%;cursor:pointer;margin-top:8px}button:disabled{opacity:.55;cursor:wait}.muted{color:var(--muted);font-size:13px}.status{padding:12px;border-radius:12px;background:#21182c;margin-top:14px;display:none;overflow-wrap:anywhere}.status.error{display:block;color:#ff9eae;background:#351521}.status.ok{display:block;color:#b8ffd9;background:#123025}.hidden{display:none!important}.user{border:1px solid #3b2b4d;border-radius:14px;padding:15px;margin-top:15px}.balance{font-size:26px;font-weight:900;color:#d9b8ff;margin:8px 0}.row{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}.danger{color:#ffb4c3}@media(max-width:520px){body{padding:20px 12px}.panel{padding:17px}h1{font-size:26px}}
</style></head>
<body><main>
<div class="brand">NEON <span>WALLET</span> / ADMIN</div><h1>Панель управления</h1><p class="sub">Администрирование виртуальных балансов и операций.</p>
<section class="panel" id="loginPanel"><h2>Вход администратора</h2>
<div class="field"><label for="email">Email аккаунта</label><input id="email" type="email" autocomplete="username" placeholder="name@example.com"></div>
<div class="field"><label for="password">Пароль кошелька</label><input id="password" type="password" autocomplete="current-password"></div>
<button id="loginBtn">Войти</button><div class="status" id="loginStatus" role="status"></div>
<p class="muted">Вход проверяется сервером. Пароль не сохраняется в панели.</p></section>
<section class="panel hidden" id="adminPanel"><div class="row"><h2>Аккаунты</h2><button id="logoutBtn" style="width:auto;margin:0;background:#30243c">Выйти</button></div>
<div class="field"><label for="accountId">ID аккаунта получателя</label><input id="accountId" inputmode="numeric" maxlength="12" placeholder="Введите ID кошелька"></div>
<button id="searchBtn">Найти аккаунт</button><div class="status" id="searchStatus" role="status"></div>
<div class="user hidden" id="userCard"><div class="row"><strong id="userName"></strong><span class="muted" id="userEmail"></span></div><div class="muted" id="userId"></div><div class="balance" id="userBalance">—</div>
<h2>Зачислить виртуальные ₽</h2>
<div class="field"><label for="amount">Сумма в рублях</label><input id="amount" type="number" min="0.01" max="1000000" step="0.01" placeholder="100000"></div>
<div class="field"><label for="reason">Комментарий операции</label><input id="reason" maxlength="140" value="Административное пополнение"></div>
<button id="creditBtn">Зачислить на баланс</button><div class="status" id="creditStatus" role="status"></div>
</div></section>
<p class="muted">Все суммы — виртуальные тестовые средства. Каждое зачисление записывается в историю операций.</p>
</main>
<script>
const $=id=>document.getElementById(id);let token=sessionStorage.getItem("neon_admin_token")||"";let selected=null;
function status(id,msg,kind){const el=$(id);el.textContent=msg;el.className="status "+kind}
async function api(path,options={}){const res=await fetch(path,{...options,headers:{"Content-Type":"application/json","Authorization":"Bearer "+token,...(options.headers||{})}});const data=await res.json().catch(()=>({detail:"Ошибка ответа сервера"}));if(!res.ok)throw new Error(data.detail||"Ошибка запроса");return data}
function showAdmin(){ $("loginPanel").classList.add("hidden");$("adminPanel").classList.remove("hidden")}
function showLogin(){ $("adminPanel").classList.add("hidden");$("loginPanel").classList.remove("hidden")}
async function verify(){if(!token)return showLogin();try{await api("/v1/admin/me");showAdmin()}catch{token="";sessionStorage.removeItem("neon_admin_token");showLogin()}}
$("loginBtn").onclick=async()=>{const btn=$("loginBtn");btn.disabled=true;try{const r=await fetch("/v1/auth/login",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email:$("email").value,password:$("password").value})});const d=await r.json();if(!r.ok)throw new Error(d.detail||"Не удалось войти");token=d.access_token;sessionStorage.setItem("neon_admin_token",token);$("password").value="";await api("/v1/admin/me");showAdmin()}catch(e){token="";sessionStorage.removeItem("neon_admin_token");status("loginStatus",e.message,"error")}finally{btn.disabled=false}};
$("logoutBtn").onclick=()=>{token="";sessionStorage.removeItem("neon_admin_token");selected=null;$("userCard").classList.add("hidden");showLogin()};
$("searchBtn").onclick=async()=>{const btn=$("searchBtn");btn.disabled=true;try{const q=$("accountId").value.trim();const r=await api("/v1/admin/users?q="+encodeURIComponent(q));if(!r.items.length)throw new Error("Аккаунт не найден");selected=r.items[0];$("userName").textContent=selected.username;$("userEmail").textContent=selected.email;$("userId").textContent="ID: "+selected.account_id;$("userBalance").textContent=(selected.balance_kopecks/100).toLocaleString("ru-RU",{minimumFractionDigits:2})+" ₽";$("userCard").classList.remove("hidden");status("searchStatus","Аккаунт найден","ok")}catch(e){selected=null;$("userCard").classList.add("hidden");status("searchStatus",e.message,"error")}finally{btn.disabled=false}};
$("creditBtn").onclick=async()=>{if(!selected)return;const amount=Number($("amount").value);if(!Number.isFinite(amount)||amount<=0||amount>1000000)return status("creditStatus","Укажи сумму от 0,01 до 1 000 000 ₽","error");if(!confirm("Зачислить "+amount.toLocaleString("ru-RU")+" ₽ на аккаунт "+selected.account_id+"?"))return;const btn=$("creditBtn");btn.disabled=true;try{const r=await api("/v1/admin/wallet/credit",{method:"POST",body:JSON.stringify({account_id:selected.account_id,amount_kopecks:Math.round(amount*100),description:$("reason").value.trim()||"Административное пополнение",request_key:crypto.randomUUID().replaceAll("-","")})});$("userBalance").textContent=(r.balance_kopecks/100).toLocaleString("ru-RU",{minimumFractionDigits:2})+" ₽";status("creditStatus","Зачислено: "+(r.amount_kopecks/100).toLocaleString("ru-RU")+" ₽. Операция № "+r.transaction_id,"ok")}catch(e){status("creditStatus",e.message,"error")}finally{btn.disabled=false}};
verify();
</script></body></html>
""")


@app.get("/v1/admin/me")
def admin_me(user: AdminUser):
    return {"admin": True, "account_id": user["account_id"], "username": user["username"]}


@app.get("/v1/admin/users")
def admin_search_users(q: str, user: AdminUser):
    query = q.strip()
    if not query or len(query) > 254:
        raise HTTPException(status_code=400, detail="Укажи ID или email аккаунта")
    with pool.connection() as conn:
        rows = conn.execute(
            """SELECT u.account_id, u.username, u.email, w.balance_kopecks
               FROM users u JOIN wallets w ON w.user_id = u.id
               WHERE u.account_id = %s OR LOWER(u.email) = LOWER(%s)
               LIMIT 10""",
            (query, query),
        ).fetchall()
    return {"items": [{
        "account_id": row["account_id"], "username": row["username"],
        "email": row["email"], "balance_kopecks": row["balance_kopecks"],
    } for row in rows]}


@app.post("/v1/admin/wallet/credit", status_code=201)
def admin_credit_wallet(body: AdminCreditRequest, admin: AdminUser):
    idempotency_key = f"admin-credit:{admin['id']}:{body.request_key}"
    with pool.connection() as conn:
        with conn.transaction():
            existing = conn.execute(
                """SELECT id, recipient_user_id, amount_kopecks, created_at
                   FROM transactions WHERE idempotency_key = %s""",
                (idempotency_key,),
            ).fetchone()
            if existing:
                if existing["recipient_user_id"] is None or existing["amount_kopecks"] != body.amount_kopecks:
                    raise HTTPException(status_code=409, detail="Ключ операции уже использован с другими параметрами")
                target = conn.execute(
                    "SELECT account_id FROM users WHERE id = %s", (existing["recipient_user_id"],)
                ).fetchone()
                if not target or target["account_id"] != body.account_id:
                    raise HTTPException(status_code=409, detail="Ключ операции уже использован для другого аккаунта")
                balance = conn.execute(
                    "SELECT balance_kopecks FROM wallets WHERE user_id = %s",
                    (existing["recipient_user_id"],),
                ).fetchone()
                return {
                    "transaction_id": str(existing["id"]), "status": "completed",
                    "amount_kopecks": existing["amount_kopecks"],
                    "balance_kopecks": balance["balance_kopecks"],
                    "created_at": existing["created_at"].isoformat(), "replayed": True,
                }

            target = conn.execute(
                "SELECT id, account_id FROM users WHERE account_id = %s",
                (body.account_id,),
            ).fetchone()
            if not target:
                raise HTTPException(status_code=404, detail="Аккаунт не найден")
            conn.execute(
                "INSERT INTO wallets(user_id, balance_kopecks) VALUES (%s, 0) ON CONFLICT (user_id) DO NOTHING",
                (target["id"],),
            )
            conn.execute(
                "SELECT user_id FROM wallets WHERE user_id = %s FOR UPDATE",
                (target["id"],),
            )
            wallet = conn.execute(
                """UPDATE wallets SET balance_kopecks = balance_kopecks + %s, updated_at = NOW()
                   WHERE user_id = %s RETURNING balance_kopecks""",
                (body.amount_kopecks, target["id"]),
            ).fetchone()
            tx = conn.execute(
                """INSERT INTO transactions(id, idempotency_key, recipient_user_id, admin_user_id,
                   amount_kopecks, kind, description)
                   VALUES (gen_random_uuid(), %s, %s, %s, %s, 'admin_credit', %s)
                   RETURNING id, created_at""",
                (idempotency_key, target["id"], admin["id"], body.amount_kopecks, body.description),
            ).fetchone()
    return {
        "transaction_id": str(tx["id"]), "status": "completed",
        "amount_kopecks": body.amount_kopecks,
        "balance_kopecks": wallet["balance_kopecks"],
        "created_at": tx["created_at"].isoformat(), "replayed": False,
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
