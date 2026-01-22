from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from database import rollover_steps_to_yesterday
from zoneinfo import ZoneInfo
import time
import logging

CENTRAL_TZ = ZoneInfo("America/Chicago")

scheduler = BackgroundScheduler(timezone=CENTRAL_TZ)

# Run at midnight CST every day
scheduler.add_job(rollover_steps_to_yesterday, CronTrigger(hour=0, minute=0))
scheduler.add_job(rollover_steps_to_yesterday, CronTrigger(minute='*'))
scheduler.start()

logger = logging.getLogger("fitcollector.rollover")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

logger.info("[Step Rollover Job] Scheduler started. Waiting for midnight CST...")

try:
    while True:
        time.sleep(60)
except (KeyboardInterrupt, SystemExit):
    scheduler.shutdown()
    logger.info("[Step Rollover Job] Scheduler stopped.")
