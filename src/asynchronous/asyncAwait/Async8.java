package asynchronous.asyncAwait;
import functionPlus.*;

import asynchronous.Promise;

public class Async8<T1, T2, T3, T4, T5, T6, T7, T8, R> implements OctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Promise<R>>{
	private Async<R> async;
	private Object[] args = new Object[8];
	
	public Async8(NonaFunction<Async.Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func, String name) {
		async = new Async<R>(
				await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3], (T5)args[4], (T6)args[5], (T7)args[6], (T8)args[7]), name);
	}
	
	public Async8(NonaFunction<Async.Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func) {
		this(func, null);
	}
	
	public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8){
		args[0] = t1;
		args[1] = t2;
		args[2] = t3;
		args[3] = t4;
		args[4] = t5;
		args[5] = t6;
		args[6] = t7;
		args[7] = t8;
		return async.get();
	}
	
	public String getName() {
		return async.getName();
	}
}
