"""Admin routes."""

from fastapi import APIRouter

from .monitoring import router as monitoring_router
from .deletion import router as deletion_router
from .bans import router as bans_router

router = APIRouter()
router.include_router(monitoring_router)
router.include_router(deletion_router)
router.include_router(bans_router)
