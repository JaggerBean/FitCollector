# StepCraft Web Registration

This is a FastAPI web app for server owners to register their Minecraft server for StepCraft. It includes:
- Registration form (server name, contact email, notes)
- SQLite database for registrations
- Unique API key generation per server
- Confirmation and simple admin view
- HTML/CSS frontend
- Docker-ready structure
- Code structured for future payment integration

## Quick Start

1. Install dependencies:
   ```
   pip install -r requirements.txt
   ```
2. Run the app:
   ```
   uvicorn main:app --reload
   ```
3. Visit http://localhost:8080 to use the registration form.

## Email (API key delivery)

Registration emails are sent from the web app. Configure one of the following in your .env:

- GMAIL_USER, GMAIL_PASS (Gmail SMTP)
   - Optional overrides: SMTP_FROM, SMTP_SERVER, SMTP_PORT, SMTP_STARTTLS, SMTP_SSL_TLS

Or generic SMTP:

- SMTP_USER, SMTP_PASS
- SMTP_FROM (optional, defaults to SMTP_USER)
- SMTP_SERVER (default: smtp.gmail.com)
- SMTP_PORT (default: 587)
- SMTP_STARTTLS (default: true)
- SMTP_SSL_TLS (default: false)

## Docker

Build and run with Docker:
```
docker build -t stepcraft_web .
docker run -p 8080:8080 stepcraft_web
```

## Admin View
Visit /admin for a simple list of registrations.

## Future Payment Integration
The code is structured for easy addition of payment logic in the future.
