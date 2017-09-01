package ServiceLocator.Services.Interfaces;

import java.util.concurrent.Callable;

public interface Downloader<V> extends Callable<V> {
    void setUrl(String url);
    V call() throws Exception;
}
