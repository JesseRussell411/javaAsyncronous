package functionPlus;

@FunctionalInterface
public interface TriConsumer<T1, T2, T3> {
	public void accept(T1 t1, T2 t2, T3 t3);
}
