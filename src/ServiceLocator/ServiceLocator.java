package ServiceLocator;

import ServiceLocator.Services.Interfaces.Downloader;
import ServiceLocator.Services.Implementations.UrlDownloader;

import java.io.File;

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

        UrlDownloader urlDownloader = new UrlDownloader();
        urlDownloader.setSleep(250);
        urlDownloader.setBuffSize(18000);
        urlDownloader.setWorkers(32);

        return urlDownloader;
    }

    @Override
    public File findFile(String fileName) {
        return null;
    }
}
