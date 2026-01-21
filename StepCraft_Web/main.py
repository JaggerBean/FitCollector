import os
import secrets
from fastapi import FastAPI, Form, Request, Depends
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from fastapi.staticfiles import StaticFiles
from sqlalchemy import create_engine, Column, Integer, String, Text
from sqlalchemy.orm import sessionmaker, declarative_base, Session
from starlette.status import HTTP_303_SEE_OTHER

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./stepcraft_web.sqlite3")
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

templates = Jinja2Templates(directory="templates")

app = FastAPI()
app.mount("/static", StaticFiles(directory="static"), name="static")

class ServerRegistration(Base):
    __tablename__ = "server_registrations"
    id = Column(Integer, primary_key=True, index=True)
    server_name = Column(String(100), unique=True, nullable=False)
    contact_email = Column(String(100), nullable=False)
    notes = Column(Text, nullable=True)
    api_key = Column(String(64), unique=True, nullable=False)

Base.metadata.create_all(bind=engine)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@app.get("/", response_class=HTMLResponse)
def register_form(request: Request):
    return templates.TemplateResponse("register.html", {"request": request})

@app.post("/register", response_class=HTMLResponse)
def register_server(request: Request, server_name: str = Form(...), contact_email: str = Form(...), notes: str = Form(""), db: Session = Depends(get_db)):
    api_key = secrets.token_hex(32)
    reg = ServerRegistration(server_name=server_name, contact_email=contact_email, notes=notes, api_key=api_key)
    db.add(reg)
    db.commit()
    db.refresh(reg)
    return templates.TemplateResponse("confirmation.html", {"request": request, "api_key": api_key, "server_name": server_name})

@app.get("/admin", response_class=HTMLResponse)
def admin_view(request: Request, db: Session = Depends(get_db)):
    regs = db.query(ServerRegistration).all()
    return templates.TemplateResponse("admin.html", {"request": request, "registrations": regs})
