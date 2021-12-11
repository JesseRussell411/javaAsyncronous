package atoms;


import functionPlus.NotNull;

public class AtomLong extends AtomRef<Long> {
    public AtomLong(Long value) {
        super(value, NotNull::test);
    }

    public AtomLong() {
        this(0L);
    }

    public Long addAndGet(long value) {
        if (value == 0) {
            return get();
        } else {
            return modAndGet(thisValue -> thisValue + value);
        }
    }

    public Long getAndAdd(long value) {
        if (value == 0) {
            return get();
        } else {
            return getAndMod(thisValue -> thisValue + value);
        }
    }


    public void add(long value) {
        if (value != 0) {
            mod(thisValue -> thisValue + value);
        }
    }

    public Long incrementAndGet() {
        return addAndGet(1);
    }

    public Long decrementAndGet() {
        return addAndGet(-1);
    }

    public Long getAndIncrement() {
        return getAndAdd(1);
    }

    public Long getAndDecrement() {
        return getAndAdd(-1);
    }

    public void increment() {
        add(1);
    }

    public void decrement() {
        add(-1);
    }
}
