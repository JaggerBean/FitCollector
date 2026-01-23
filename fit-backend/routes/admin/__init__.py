"""Admin routes."""

from fastapi import APIRouter


from .monitoring import router as monitoring_router
from .deletion import router as deletion_router
from .bans import router as bans_router
from .player_wipe import router as player_wipe_router

from .servers import router as servers_admin_router
from .players import router as players_admin_router

router = APIRouter()
router.include_router(monitoring_router)
router.include_router(deletion_router)
router.include_router(bans_router)
router.include_router(player_wipe_router)
router.include_router(servers_admin_router)
router.include_router(players_admin_router)
