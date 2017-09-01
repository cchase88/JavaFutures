package ServiceLocator;

import ServiceLocator.Services.Interfaces.Downloader;


public interface DownloaderLocator {
    Downloader getDownloader();
}
