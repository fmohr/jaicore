package ai.libs.jaicore.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalTimer extends TrackableTimer {
	private static final Logger logger = LoggerFactory.getLogger(GlobalTimer.class);
	private static final GlobalTimer instance = new GlobalTimer();

	private GlobalTimer() {

		/* create a daemon with this name */
		super("Global Timer", true);

		/* immediately give the thread of the timer maximum priority */
		this.schedule(new TrackableTimerTask() {
			@Override
			public void run() {
				Thread timerThread = Thread.currentThread();
				logger.info("Changing global timer thread {} priority from {} to {}", timerThread, timerThread.getPriority(), Thread.MAX_PRIORITY);
				timerThread.setPriority(Thread.MAX_PRIORITY);
				logger.info("Priority of global timer thread {} is now {}", timerThread, timerThread.getPriority());
			}
		}, 0);
	}

	public static GlobalTimer getInstance() {
		return instance;
	}

	@Override
	public void cancel() {
		throw new UnsupportedOperationException("The TimeoutTimer must not be canceled manually!");
	}
}
