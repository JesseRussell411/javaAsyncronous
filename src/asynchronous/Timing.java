package asynchronous;

import java.util.function.*;

public class Timing {
	public static Thread setTimeout(Runnable func, long sleepForMilliseconds, int sleepForNanoseconds) {
		final var thread =  new Thread(() -> {
			try {
				Thread.sleep(sleepForMilliseconds, sleepForNanoseconds);
				func.run();
			} catch(InterruptedException e) {}
		});
		
		thread.start();
		
		return thread;
	}
	
	public static <T> Promise<T> setTimeout(Supplier<T> func, long sleepForMilliseconds, int sleepForNanoseconds) {
		return Promise.threadInit((resolve, reject) -> {
			try {
				Thread.sleep(sleepForMilliseconds, sleepForNanoseconds);
				resolve.accept(func.get());
			}
			catch(Exception e) {
				reject.accept(e);
			}
		});
	}
	
	public static void setTimeout(Runnable func, long sleepForMilliseconds) {
		setTimeout(func, sleepForMilliseconds, 0);
	}
	
	public static <T> Promise<T> setTimeout(Supplier<T> func, long sleepForMilliseconds) {
		return setTimeout(func, sleepForMilliseconds, 0);
	}
}
