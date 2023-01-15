package gitlet;

import java.util.function.Supplier;

/**
 * 单例模式+延迟加载+双重锁检测
 * 使用传入的Supplier作为委托者，来生成/返回具体需要的单例T
 */
public class LazySingleton<T> implements Supplier<T> {
    private volatile T instance;
    private volatile boolean initialized;
    private final Supplier<T> delegate;

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
