from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from database import rollover_steps_to_yesterday
from zoneinfo import ZoneInfo
import time

CENTRAL_TZ = ZoneInfo("America/Chicago")

scheduler = BackgroundScheduler(timezone=CENTRAL_TZ)

# Run at midnight CST every day
scheduler.add_job(rollover_steps_to_yesterday, CronTrigger(hour=0, minute=0))

scheduler.start()

print("[Step Rollover Job] Scheduler started. Waiting for midnight CST...")

try:
    while True:
        time.sleep(60)
except (KeyboardInterrupt, SystemExit):
    scheduler.shutdown()
    print("[Step Rollover Job] Scheduler stopped.")
