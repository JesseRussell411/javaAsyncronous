package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;

public class Async8Void<T1, T2, T3, T4, T5, T6, T7, T8> implements OctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Promise<Void>>{
	private final Async8<T1, T2, T3, T4, T5, T6, T7, T8, Void> async;
	
	public Async8Void(NonaConsumer<Async.Await, T1, T2, T3, T4, T5, T6, T7, T8> func, String name) {
		async = new Async8<T1, T2, T3, T4, T5, T6, T7, T8, Void>(
				(await, t1, t2, t3, t4, t5, t6, t7, t8) -> { func.accept(await, t1, t2, t3, t4, t5, t6, t7, t8); return null; }, name);
	}
	
	public Async8Void(NonaConsumer<Async.Await, T1, T2, T3, T4, T5, T6, T7, T8> func) {
		this(func, null);
	}
	
	public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8) {
		return async.apply(t1, t2, t3, t4, t5, t6, t7, t8);
	}
	
	public String getName() {
		return async.getName();
	}
}
