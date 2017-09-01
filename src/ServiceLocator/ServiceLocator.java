package ServiceLocator;

import ServiceLocator.Services.Interfaces.Downloader;
import ServiceLocator.Services.Implementations.UrlDownloader;

import java.io.File;

/*
This class is based upon Martin Fowler's article on Dependency Injection:

https://martinfowler.com/articles/injection.html#UsingAServiceLocator

https://martinfowler.com/articles/injection.html#UsingASegregatedInterfaceForTheLocator
 */
public class ServiceLocator implements DownloaderLocator, FileFinder{

    private static ServiceLocator soleInstance;

    private ServiceLocator(){}

    public static ServiceLocator locator(){
        if(soleInstance == null){
            soleInstance = new ServiceLocator();
        }

        return soleInstance;
    }



    @Override
    public Downloader getDownloader() {

        /*
        The downloader provided by the ServiceLocater will be a UrlDownloader.
         */
        UrlDownloader urlDownloader = new UrlDownloader();
        urlDownloader.setSleep(10);
        urlDownloader.setBuffSize(16384);
        urlDownloader.setWorkers(10);

        return urlDownloader;
    }

    @Override
    public File findFile(String fileName) {
        return null;
    }
}
