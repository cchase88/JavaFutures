import ServiceLocator.*;
import ServiceLocator.Services.Interfaces.Downloader;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) throws Exception {




        DownloaderLocator dl = ServiceLocator.locator();
        Downloader downloader = dl.getDownloader();

        downloader.setUrl("https://raw.githubusercontent.com/adambom/dictionary/master/dictionary.json");

        ExecutorService es = Executors.newSingleThreadExecutor();
        Future<String> future = es.submit(downloader);


        while(!future.isDone()){
            System.out.println("Sleeping half a sec");
            Thread.sleep(500);
        }
        System.out.println("Future completed.");

        File output = new File("dictionary.json");
        FileWriter fw = new FileWriter(output);
        fw.write(future.get());

        System.exit(1);


    }

    private String secToMin(long seconds){
        long mins = seconds / 60;
        long secs = seconds % 60;

        return String.format("%s:%s", mins, secs);
    }
}
