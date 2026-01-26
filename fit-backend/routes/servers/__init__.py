"""Server routes."""

from fastapi import APIRouter

from .registration import router as registration_router
from .players import router as players_router
from .bans import router as bans_router
from .management import router as management_router
from .rewards import router as rewards_router

router = APIRouter()
router.include_router(registration_router)
router.include_router(players_router)
router.include_router(bans_router)
router.include_router(management_router)
router.include_router(rewards_router)
