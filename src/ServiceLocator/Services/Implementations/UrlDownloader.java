package ServiceLocator.Services.Implementations;

import ServiceLocator.Services.Interfaces.Downloader;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;


public class UrlDownloader implements Downloader<String>{

    private static final Logger log = Logger.getLogger(UrlDownloader.class.getName());

    private String urlString;
    private int buff_size = 2048;
    private long sleep_time = 0;
    private int workers = 1;

    public UrlDownloader(){}



    @Override
    public String call() throws Exception {

        URL url = new URL(this.urlString);
        HttpsURLConnection httpsCon = (HttpsURLConnection)url.openConnection();
        int content_length = httpsCon.getContentLength();

        httpsCon.disconnect();


        int[] worker_content_length = new int[workers];
        int[] worker_offsets = new int[workers];


        int remainder = content_length % workers;

        int running_offset = 0;
        for(int index = 0; index < workers; index++){
            if(index == 0){
                worker_content_length[index] = (content_length / workers) + remainder;
                worker_offsets[index] = running_offset;
            }
            else {
                worker_content_length[index] = (content_length / workers);
                running_offset += worker_content_length[index - 1];
                worker_offsets[index] = running_offset;
            }

            log.info(String.format("worker_content_length[%s] = %s; worker_offsets[%s] = %s",
                    index,
                    worker_content_length[index],
                    index,
                    worker_offsets[index]));

        }

        List<Callable<String>> workerList = new ArrayList<>();
        for(int index = 0; index < workers; index++) {
            int worker_idx = index;
            workerList.add(new Callable<String>() {
                @Override
                public String call() throws Exception {


                    URL url = new URL(urlString);
                    HttpsURLConnection httpsCon = (HttpsURLConnection)url.openConnection();
                    httpsCon.setRequestMethod("GET");
                    String range_value = String.format("bytes=%s-%s",worker_offsets[worker_idx],
                            worker_offsets[worker_idx] + worker_content_length[worker_idx]);
                    httpsCon.setRequestProperty("Range", range_value);
                    log.info("Range : " + range_value);
                    if(httpsCon.getResponseCode() != HttpsURLConnection.HTTP_PARTIAL){
                        log.severe("Http not PARTIAL, got " + httpsCon.getResponseCode());
                    }

                    InputStream is = (InputStream)httpsCon.getContent();
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);

                    StringBuilder sb = new StringBuilder();
                    char[] buff = new char[buff_size];

                    int charsRead;
                    log.info(String.format("Starting to read buffer with worker_offset[%s] = %s", worker_idx, worker_offsets[worker_idx]));

                    while ((charsRead = br.read(buff, 0, buff_size)) > 0) {

                        // If the number of chars read is lower than the buffer size, we do
                        // not want to append the nulls in the buffer to our string.
                        if (charsRead < buff_size) {
                            log.info(String.format("worker[%s]: charsRead < buff_size", worker_idx));
                            sb.append(Arrays.copyOfRange(buff, 0, charsRead));
                        } else {
                            sb.append(buff);
                        }

                        if(sb.length() >= worker_content_length[worker_idx]){
                            break;
                        }

                        Thread.sleep(sleep_time);

                    }

                    return sb.toString();

                }
            });
        }

        ExecutorService exs = Executors.newFixedThreadPool(workers);
        List<Future<String>> futureList = new ArrayList<>();
        long starttime = System.currentTimeMillis();
        for (Callable<String> c: workerList) {
            futureList.add(exs.submit(c));
        }


        while(true){
            int completedFutures = 0;
            for(Future<String> f : futureList){
                if(f.isDone()) completedFutures++;
            }

            if(completedFutures == workers) break;
        }
        long elapsedtime = System.currentTimeMillis();
        log.info("All futures completed. Elapsed time: " + secToMin((elapsedtime - starttime) / 1000));

        StringBuilder bigSb = new StringBuilder();
        for(Future<String> f : futureList){
            bigSb.append(f.get());
        }

        return bigSb.toString();






    }

    private String secToMin(long seconds){
        long mins = seconds / 60;
        long secs = seconds % 60;

        return String.format("%s:%s", mins, secs);
    }


    @Override
    public void setUrl(String url) {
        this.urlString = url;
    }

    public void setSleep(long sleep_time) {
        this.sleep_time = sleep_time;
    }

    public void setBuffSize(int size){
        this.buff_size = size;
    }

    public void setWorkers(int workers) {
        if(workers != 1) this.workers = workers - 1;
    }
}
