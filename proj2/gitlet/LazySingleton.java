package gitlet;

import java.util.function.Supplier;

public class LazySingleton<T> implements Supplier<T> {
    private volatile T instance;
    private volatile boolean initialized;
    private volatile Supplier<T> delegate;

    public LazySingleton(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        if(!initialized){
            synchronized (LazySingleton.class){
                if(! initialized){
                    T obj = delegate.get();
                    initialized = true;
                    instance = obj;
                }
            }
        }
        return instance;
    }
}
