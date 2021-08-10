package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;

public class Async5Void<T1, T2, T3, T4, T5> implements PentaFunction<T1, T2, T3, T4, T5, Promise<Void>>{
	private final Async5<T1, T2, T3, T4, T5, Void> async;
	
	public Async5Void(HexaConsumer<Async.Await, T1, T2, T3, T4, T5> func, String name) {
		async = new Async5<T1, T2, T3, T4, T5, Void>(
				(await, t1, t2, t3, t4, t5) -> { func.accept(await, t1, t2, t3, t4, t5); return null; }, name);
	}
	
	public Async5Void(HexaConsumer<Async.Await, T1, T2, T3, T4, T5> func) {
		this(func, null);
	}
	
	public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
		return async.apply(t1, t2, t3, t4, t5);
	}
	
	public String getName() {
		return async.getName();
	}
}
