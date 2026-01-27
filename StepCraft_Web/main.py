

# Contact info page (must be after app = FastAPI())

from fastapi_mail import FastMail, MessageSchema, ConnectionConfig
import os
import json
import urllib.parse
import httpx
from fastapi import FastAPI, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles
from starlette.middleware.sessions import SessionMiddleware

templates = Jinja2Templates(directory="templates")



app = FastAPI()
app.mount("/static", StaticFiles(directory="static"), name="static")
app.add_middleware(SessionMiddleware, secret_key=os.getenv("STEPCRAFT_WEB_SECRET", "change-me"))

BACKEND_URL = os.getenv("BACKEND_URL", "https://api.stepcraft.org")
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET")
GOOGLE_REDIRECT_URI = os.getenv("GOOGLE_REDIRECT_URI", "https://stepcraft.org/account/google/callback")

# Contact info page
@app.get("/contact-info", response_class=HTMLResponse)
async def contact_info(request: Request):
    return templates.TemplateResponse("contact-info.html", {"request": request})

def _truthy_env(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def build_mail_conf():
    user = os.getenv("GMAIL_USER") or os.getenv("SMTP_USER") or os.getenv("MAIL_USER")
    pwd = os.getenv("GMAIL_PASS") or os.getenv("SMTP_PASS") or os.getenv("MAIL_PASS")
    if not user or not pwd:
        import logging
        logging.warning("Email is disabled: missing SMTP credentials (GMAIL_USER/GMAIL_PASS or SMTP_USER/SMTP_PASS).")
        return None

    mail_from = os.getenv("SMTP_FROM") or os.getenv("MAIL_FROM") or user
    server = os.getenv("SMTP_SERVER", "smtp.gmail.com")
    port = int(os.getenv("SMTP_PORT", "587"))
    starttls = _truthy_env(os.getenv("SMTP_STARTTLS"), True)
    ssl_tls = _truthy_env(os.getenv("SMTP_SSL_TLS"), False)
    if starttls and ssl_tls:
        ssl_tls = False

    return ConnectionConfig(
        MAIL_USERNAME=user,
        MAIL_PASSWORD=pwd,
        MAIL_FROM=mail_from,
        MAIL_PORT=port,
        MAIL_SERVER=server,
        MAIL_STARTTLS=starttls,
        MAIL_SSL_TLS=ssl_tls,
        USE_CREDENTIALS=True,
    )

async def send_api_key_email(email, server_name, api_key, message):
    mail_conf = build_mail_conf()
    if mail_conf is None:
        return
    subject = "Your StepCraft API Key"
    body = f"""Thank you for registering your server: {server_name}\nYour API Key: {api_key}\n{message}"""
    msg = MessageSchema(
        subject=subject,
        recipients=[email],
        body=body,
        subtype="plain"
    )
    fm = FastMail(mail_conf)
    await fm.send_message(msg)





# Serve landing page at root
@app.get("/", response_class=HTMLResponse)
def landing_page(request: Request):
    if request.session.get("user_token"):
        return RedirectResponse(url="/dashboard", status_code=302)
    return templates.TemplateResponse("landing.html", {"request": request})


@app.get("/account/login", response_class=HTMLResponse)
def account_login_form(request: Request):
    return templates.TemplateResponse("login.html", {"request": request})


@app.post("/account/login", response_class=HTMLResponse)
async def account_login_submit(request: Request, email: str = Form(...), password: str = Form(...)):
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post(
                f"{BACKEND_URL}/v1/auth/login",
                json={"email": email, "password": password},
                timeout=10,
            )
        except Exception as e:
            return templates.TemplateResponse("login.html", {"request": request, "error": f"Backend error: {e}"})

    if resp.status_code != 200:
        error = None
        try:
            error = resp.json().get("error")
        except Exception:
            error = resp.text
        return templates.TemplateResponse("login.html", {"request": request, "error": error or "Invalid credentials."})

    token = resp.json().get("token")
    request.session["user_token"] = token
    request.session["user_email"] = None
    async with httpx.AsyncClient() as client:
        try:
            me = await client.get(
                f"{BACKEND_URL}/v1/auth/me",
                headers={"Authorization": f"Bearer {token}"},
                timeout=10,
            )
            if me.status_code == 200:
                request.session["user_email"] = me.json().get("email")
                request.session["user_name"] = me.json().get("name")
        except Exception:
            pass
    return RedirectResponse(url="/dashboard", status_code=302)


@app.get("/account/register", response_class=HTMLResponse)
def account_register_form(request: Request):
    return templates.TemplateResponse("register_account.html", {"request": request})


@app.post("/account/register", response_class=HTMLResponse)
async def account_register_submit(request: Request, name: str = Form(...), email: str = Form(...), password: str = Form(...)):
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post(
                f"{BACKEND_URL}/v1/auth/register",
                json={"name": name, "email": email, "password": password},
                timeout=10,
            )
        except Exception as e:
            return templates.TemplateResponse("register_account.html", {"request": request, "error": f"Backend error: {e}"})

    if resp.status_code != 200:
        error = None
        try:
            error = resp.json().get("error")
        except Exception:
            error = resp.text
        if not error:
            error = "Registration failed."
        error = f"{resp.status_code}: {error}"
        return templates.TemplateResponse("register_account.html", {"request": request, "error": error})

    return RedirectResponse(url="/account/login", status_code=302)


@app.get("/account/google/start")
def google_oauth_start(request: Request):
    if not GOOGLE_CLIENT_ID:
        return RedirectResponse(url="/account/login", status_code=302)

    state = os.urandom(16).hex()
    request.session["google_state"] = state
    params = {
        "client_id": GOOGLE_CLIENT_ID,
        "redirect_uri": GOOGLE_REDIRECT_URI,
        "response_type": "code",
        "scope": "openid email profile",
        "state": state,
        "access_type": "offline",
        "prompt": "consent",
    }
    query = urllib.parse.urlencode(params)
    return RedirectResponse(url=f"https://accounts.google.com/o/oauth2/v2/auth?{query}", status_code=302)


@app.get("/account/google/callback", response_class=HTMLResponse)
async def google_oauth_callback(request: Request):
    if not GOOGLE_CLIENT_ID or not GOOGLE_CLIENT_SECRET:
        return templates.TemplateResponse("login.html", {"request": request, "error": "Google OAuth not configured."})

    code = request.query_params.get("code")
    state = request.query_params.get("state")
    if not code or not state or state != request.session.get("google_state"):
        return templates.TemplateResponse("login.html", {"request": request, "error": "Invalid OAuth state."})
    request.session.pop("google_state", None)

    async with httpx.AsyncClient() as client:
        try:
            token_resp = await client.post(
                "https://oauth2.googleapis.com/token",
                data={
                    "code": code,
                    "client_id": GOOGLE_CLIENT_ID,
                    "client_secret": GOOGLE_CLIENT_SECRET,
                    "redirect_uri": GOOGLE_REDIRECT_URI,
                    "grant_type": "authorization_code",
                },
                timeout=10,
            )
        except Exception as e:
            return templates.TemplateResponse("login.html", {"request": request, "error": f"Google token error: {e}"})

    if token_resp.status_code != 200:
        return templates.TemplateResponse("login.html", {"request": request, "error": token_resp.text})

    id_token = token_resp.json().get("id_token")
    if not id_token:
        return templates.TemplateResponse("login.html", {"request": request, "error": "Missing id_token from Google."})

    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post(
                f"{BACKEND_URL}/v1/auth/google",
                json={"id_token": id_token},
                timeout=10,
            )
        except Exception as e:
            return templates.TemplateResponse("login.html", {"request": request, "error": f"Backend error: {e}"})

    if resp.status_code != 200:
        return templates.TemplateResponse("login.html", {"request": request, "error": resp.text})

    request.session["user_token"] = resp.json().get("token")
    token = request.session.get("user_token")
    if token:
        async with httpx.AsyncClient() as client:
            try:
                me = await client.get(
                    f"{BACKEND_URL}/v1/auth/me",
                    headers={"Authorization": f"Bearer {token}"},
                    timeout=10,
                )
                if me.status_code == 200:
                    request.session["user_email"] = me.json().get("email")
                    request.session["user_name"] = me.json().get("name")
            except Exception:
                pass
    return RedirectResponse(url="/dashboard", status_code=302)


@app.post("/logout")
def logout(request: Request):
    request.session.clear()
    return RedirectResponse(url="/", status_code=302)


@app.get("/dashboard", response_class=HTMLResponse)
async def dashboard(request: Request):
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)

    servers = []
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(
                f"{BACKEND_URL}/v1/owner/servers",
                headers={"Authorization": f"Bearer {user_token}"},
                timeout=10,
            )
            if resp.status_code == 200:
                servers = resp.json().get("servers", [])
        except Exception:
            servers = []

    return templates.TemplateResponse("dashboard.html", {"request": request, "servers": servers})


@app.get("/push", response_class=HTMLResponse)
async def push_notifications_page(request: Request):
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)

    server_name = request.query_params.get("server")
    if not server_name:
        return RedirectResponse(url="/dashboard", status_code=302)

    items = []
    error = None
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(
                f"{BACKEND_URL}/v1/owner/servers/{server_name}/push",
                headers={"Authorization": f"Bearer {user_token}"},
                timeout=10,
            )
            if resp.status_code == 200:
                items = resp.json().get("items", [])
            else:
                error = resp.text
        except Exception as e:
            error = str(e)

    return templates.TemplateResponse(
        "push_notifications.html",
        {"request": request, "server_name": server_name, "items": items, "error": error},
    )


@app.post("/push/create", response_class=HTMLResponse)
async def push_notifications_create(
    request: Request,
    message: str = Form(...),
    scheduled_at: str = Form(...),
    timezone: str = Form(...),
):
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)

    server_name = request.query_params.get("server")
    if not server_name:
        return RedirectResponse(url="/dashboard", status_code=302)

    error = None
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post(
                f"{BACKEND_URL}/v1/owner/servers/{server_name}/push",
                headers={"Authorization": f"Bearer {user_token}"},
                json={"message": message, "scheduled_at": scheduled_at, "timezone": timezone},
                timeout=10,
            )
            if resp.status_code != 200:
                error = resp.text
        except Exception as e:
            error = str(e)

    if error:
        return templates.TemplateResponse(
            "push_notifications.html",
            {"request": request, "server_name": server_name, "items": [], "error": error, "message": message},
        )

    return RedirectResponse(url=f"/push?server={server_name}", status_code=302)


@app.get("/rewards", response_class=HTMLResponse)
async def rewards_page(request: Request):
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)

    server_name = request.query_params.get("server")
    if not server_name:
        return RedirectResponse(url="/dashboard", status_code=302)

    data = None
    error = None
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(
                f"{BACKEND_URL}/v1/owner/servers/{server_name}/rewards",
                headers={"Authorization": f"Bearer {user_token}"},
                timeout=10,
            )
            if resp.status_code == 200:
                data = resp.json()
            else:
                error = resp.text
        except Exception as e:
            error = str(e)

    raw_json = ""
    if data is not None:
        raw_json = json.dumps({"tiers": data.get("tiers", [])}, indent=2)

    return templates.TemplateResponse(
        "rewards.html",
        {"request": request, "data": data, "raw_json": raw_json, "error": error, "server_name": server_name}
    )


@app.post("/rewards/update")
async def rewards_update(request: Request, rewards_json: str = Form(...)):
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)

    server_name = request.query_params.get("server")
    if not server_name:
        return RedirectResponse(url="/dashboard", status_code=302)

    try:
        payload = json.loads(rewards_json)
        if "tiers" not in payload:
            payload = {"tiers": payload}
    except Exception as e:
        return templates.TemplateResponse("rewards.html", {
            "request": request,
            "data": None,
            "raw_json": rewards_json,
            "error": f"Invalid JSON: {e}",
        })

    async with httpx.AsyncClient() as client:
        try:
            resp = await client.put(
                f"{BACKEND_URL}/v1/owner/servers/{server_name}/rewards",
                headers={"Authorization": f"Bearer {user_token}"},
                json=payload,
                timeout=10,
            )
        except Exception as e:
            return templates.TemplateResponse("rewards.html", {
                "request": request,
                "data": None,
                "raw_json": rewards_json,
                "error": f"Backend error: {e}",
                "server_name": server_name,
            })

    if resp.status_code != 200:
        return templates.TemplateResponse("rewards.html", {
            "request": request,
            "data": None,
            "raw_json": rewards_json,
            "error": resp.text,
            "server_name": server_name,
        })

    return RedirectResponse(url=f"/rewards?server={server_name}", status_code=302)


@app.post("/rewards/default")
async def rewards_default(request: Request):
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)

    server_name = request.query_params.get("server")
    if not server_name:
        return RedirectResponse(url="/dashboard", status_code=302)

    async with httpx.AsyncClient() as client:
        try:
            await client.post(
                f"{BACKEND_URL}/v1/owner/servers/{server_name}/rewards/default",
                headers={"Authorization": f"Bearer {user_token}"},
                timeout=10,
            )
        except Exception:
            pass

    return RedirectResponse(url=f"/rewards?server={server_name}", status_code=302)

# Registration form at /register
@app.get("/register", response_class=HTMLResponse)
def register_form(request: Request):
    import datetime
    if not request.session.get("user_token"):
        return RedirectResponse(url="/account/login", status_code=302)
    year = datetime.datetime.now().year
    return templates.TemplateResponse("register.html", {
        "request": request,
        "year": year,
        "owner_email": request.session.get("user_email"),
        "owner_name": request.session.get("user_name")
    })


@app.post("/registered", response_class=HTMLResponse)
async def register_server(request: Request,
    server_name: str = Form(...),
    owner_name: str = Form(...),
    owner_email: str = Form(...),
    server_address: str = Form(...),
    server_version: str = Form("")
):
    # Send registration data to backend API
    import logging
    logging.basicConfig(level=logging.INFO)
    backend_urls = [
        #"http://fitcollector_api:8000/v1/servers/register",
        "https://api.stepcraft.org/v1/servers/register"
        #"http://74.208.73.134/v1/servers/register"
    ]
    response = None
    last_error = None
    user_token = request.session.get("user_token")
    if not user_token:
        return RedirectResponse(url="/account/login", status_code=302)
    import datetime
    year = datetime.datetime.now().year
    async with httpx.AsyncClient() as client:
        for url in backend_urls:
            try:
                logging.info(f"Trying backend registration at {url}")
                response = await client.post(
                    url,
                    json={
                        "server_name": server_name,
                        "owner_name": owner_name,
                        "owner_email": owner_email,
                        "server_address": server_address,
                        "server_version": server_version
                    },
                    headers={"Authorization": f"Bearer {user_token}"}
                )
                logging.info(f"Response status: {response.status_code}, body: {response.text}")
                if response.status_code == 200:
                    break
            except Exception as e:
                logging.error(f"Error contacting backend {url}: {e}")
                last_error = str(e)
    if response and response.status_code == 200:
        data = response.json()
        api_key = data.get("api_key")
        server_name_val = data.get("server_name")
        message_val = data.get("message")
        # Send email with API key
        try:
            await send_api_key_email(owner_email, server_name_val, api_key, message_val)
        except Exception as e:
            import logging
            logging.error(f"Failed to send email: {e}")
        return templates.TemplateResponse("confirmation.html", {"request": request, "api_key": api_key, "server_name": server_name_val, "message": message_val, "year": year})
    else:
        error = None
        if response:
            try:
                data = response.json()
                error = data.get("error")
            except Exception:
                error = response.text
        if not error:
            error = last_error or "No backend response"
        if response is not None:
            error = f"{response.status_code}: {error}"
        logging.error(f"Registration failed: {error}")
        return templates.TemplateResponse("register.html", {"request": request, "error": error, "year": year})

## Optionally remove or refactor /admin if not needed
