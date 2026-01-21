import os
import httpx
from fastapi import FastAPI, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles

templates = Jinja2Templates(directory="templates")


app = FastAPI()
app.mount("/static", StaticFiles(directory="static"), name="static")


@app.get("/", response_class=HTMLResponse)
def register_form(request: Request):
    return templates.TemplateResponse("register.html", {"request": request})


@app.post("/register", response_class=HTMLResponse)
async def register_server(request: Request,
    server_name: str = Form(...),
    owner_name: str = Form(...),
    owner_email: str = Form(...),
    server_address: str = Form(...),
    server_version: str = Form("")
):
    # Send registration data to backend API
    async with httpx.AsyncClient() as client:
        try:
            response = await client.post(
                "http://fitcollector_api:8000/servers/register",
                json={
                    "server_name": server_name,
                    "owner_name": owner_name,
                    "owner_email": owner_email,
                    "server_address": server_address,
                    "server_version": server_version
                }
            )
        except httpx.RequestError:
            response = await client.post(
                "http://74.208.73.134/v1/servers/register",
                json={
                    "server_name": server_name,
                    "owner_name": owner_name,
                    "owner_email": owner_email,
                    "server_address": server_address,
                    "server_version": server_version
                }
            )
    if response.status_code == 200:
        data = response.json()
        api_key = data.get("api_key")
        server_name = data.get("server_name")
        message = data.get("message")
        return templates.TemplateResponse("confirmation.html", {"request": request, "api_key": api_key, "server_name": server_name, "message": message})
    else:
        error = response.text
        return templates.TemplateResponse("register.html", {"request": request, "error": f"Registration failed: {error}"})

## Optionally remove or refactor /admin if not needed
