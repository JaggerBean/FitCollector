

# Contact info page (must be after app = FastAPI())

from fastapi_mail import FastMail, MessageSchema, ConnectionConfig
import os
import httpx
from fastapi import FastAPI, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles

templates = Jinja2Templates(directory="templates")



app = FastAPI()
app.mount("/static", StaticFiles(directory="static"), name="static")

# Contact info page
@app.get("/contact-info", response_class=HTMLResponse)
async def contact_info(request: Request):
    return templates.TemplateResponse("contact-info.html", {"request": request})

mail_conf = ConnectionConfig(
    MAIL_USERNAME = os.getenv("GMAIL_USER"),
    MAIL_PASSWORD = os.getenv("GMAIL_PASS"),
    MAIL_FROM = os.getenv("GMAIL_USER"),
    MAIL_PORT = 587,
    MAIL_SERVER = "smtp.gmail.com",
    MAIL_STARTTLS = True,
    MAIL_SSL_TLS = False,
    USE_CREDENTIALS = True
)

async def send_api_key_email(email, server_name, api_key, message):
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
    return templates.TemplateResponse("landing.html", {"request": request})

# Registration form at /register
@app.get("/register", response_class=HTMLResponse)
def register_form(request: Request):
    import datetime
    year = datetime.datetime.now().year
    return templates.TemplateResponse("register.html", {"request": request, "year": year})


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
                    }
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
        import datetime
        year = datetime.datetime.now().year
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
        logging.error(f"Registration failed: {error}")
        return templates.TemplateResponse("register.html", {"request": request, "error": error})

## Optionally remove or refactor /admin if not needed
