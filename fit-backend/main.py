"""FastAPI application setup and configuration."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from database import init_db
from routes import health, players, servers, ingest, admin

# Initialize FastAPI app
app = FastAPI(title="FitCollector Backend", version="0.1.0")

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(health.router)
app.include_router(players.router)
app.include_router(servers.router)
app.include_router(ingest.router)
app.include_router(admin.router)


@app.on_event("startup")
def on_startup():
    """Initialize database on startup."""
    init_db()

