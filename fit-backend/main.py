"""FastAPI application setup and configuration."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from database import init_db
from routes import health, players, ingest
from routes import auth as auth_routes
from routes.servers import router as servers_router
from routes.admin import router as admin_router
from fastapi.responses import JSONResponse
from fastapi.requests import Request
from fastapi.exception_handlers import RequestValidationError
from fastapi.exceptions import HTTPException as FastAPIHTTPException

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
app.include_router(servers_router)
app.include_router(ingest.router)
app.include_router(admin_router)
app.include_router(auth_routes.router)


@app.on_event("startup")
def on_startup():
    """Initialize database on startup."""
    init_db()


@app.exception_handler(FastAPIHTTPException)
async def custom_http_exception_handler(request: Request, exc: FastAPIHTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": f"Error {exc.status_code}: {exc.detail}"},
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={"error": f"Error 422: Validation error - {exc.errors()}"},
    )

