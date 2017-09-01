package ServiceLocator.Services.Implementations;

import ServiceLocator.Services.Interfaces.Downloader;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
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

    /* The number of bytes to read at a time */
    private int buff_size = 2048;

    private long sleep_time = 0;

    private int workers = 1;

    public UrlDownloader(){

    }



    @Override
    public String call() throws Exception {

        /*
        An initial connection is made to the specified URL. Once connected,
        the content-length header field is read and stored for later use.
         */
        URL url = new URL(this.urlString);
        HttpsURLConnection httpsCon = (HttpsURLConnection)url.openConnection();
        int content_length = httpsCon.getContentLength();

        httpsCon.disconnect();


        int[] worker_content_length = new int[workers];

        /*
        Each worker will start downloading a portion of the file starting from
        the given offset.
         */
        int[] worker_offsets = new int[workers];

        /*
        If the content length cannot be evenly divided between the workers,
        calculate the remainder.
         */
        int remainder = content_length % workers;

        int running_offset = 0;
        for(int index = 0; index < workers; index++){
            final int previous = index - 1;
            final int chunk = (content_length / workers);
            if(index == 0){
                /*
                The first worker gets a percentage of the content to download,
                plus any remainder content that cannot be evenly divided.

                Its starting offset will be 0.
                 */
                worker_content_length[index] = chunk + remainder;
                worker_offsets[index] = running_offset;
            }
            else {
                /*
                All subsequent workers will receive an equal amount of content
                to download.

                The offset for each worker starts at the previous workers end point.
                 */
                worker_content_length[index] = chunk;
                running_offset += worker_content_length[previous];
                worker_offsets[index] = running_offset;
            }

            log.info(String.format("worker_offsets[%s] = %s; worker_content_length[%s] = %s; ",
                    index,
                    worker_offsets[index],
                    index,
                    worker_content_length[index]));


        }

        /*
        For each of the workers specified, we create a new Callable.
         */
        List<Callable<String>> workerList = new ArrayList<>();
        for(int index = 0; index < workers; index++) {
            int worker_idx = index;
            workerList.add(new Callable<String>() {
                @Override
                public String call() throws Exception {

                    URL url = new URL(urlString);
                    HttpsURLConnection httpsCon = (HttpsURLConnection)url.openConnection();
                    httpsCon.setRequestMethod("GET");

                    /*
                    Specify the range of bytes this worker is interested in downloading
                    using the Range header.

                    https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Range

                    Minus 1 byte off the end since Range starts at 0.
                    i.e to get 1 MB:
                        Range: bytes=0-1023
                     */
                    String range_value = String.format("bytes=%s-%s",worker_offsets[worker_idx],
                            worker_offsets[worker_idx] + worker_content_length[worker_idx] - 1);


                    log.info(String.format("worker[%s] " + range_value, worker_idx));

                    httpsCon.setRequestProperty("Range", range_value);


                    /*
                    If the server does not support the Range header, we're in trouble.
                    Exit immediately.
                     */
                    if(httpsCon.getResponseCode() != HttpsURLConnection.HTTP_PARTIAL){
                        log.severe("Http not PARTIAL, got " + httpsCon.getResponseCode());
                        System.exit(httpsCon.getResponseCode());
                    }

                    /*
                    Ensure that the requested number of bytes matches the Content-length of
                    the HTTP Partial request.
                     */

                    int partial_content_length = httpsCon.getContentLength();

                    if(partial_content_length != worker_content_length[worker_idx]){
                        log.severe("Content lengths did not match. Partial:"
                                + partial_content_length + " Worker: " + worker_content_length[worker_idx]);
                    }


                    /*
                    Standard input stream and buffered reader setup.
                     */
                    InputStream is = httpsCon.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(is);
                    StringBuilder sb = new StringBuilder();

                    /*
                    The destination buffer. When reading from the BufferedReader, bytes will be
                    placed within this array.
                     */
                    byte[] buff = new byte[buff_size];
                    int bytesRead;
                    int totalBytesRead = 0;
                    int readCount = 0;

                    while((bytesRead = bis.read(buff)) != -1){
                        readCount++;
                        totalBytesRead += bytesRead;

                        /*
                        If the number of chars read is lower than the buffer size, we do
                        not want to append the nulls in the buffer to our string.

                        We instead copy the bytes starting from 0 to the number of bytes read.
                         */

                        if (bytesRead < buff_size)
                            sb.append(new String(Arrays.copyOfRange(buff, 0, bytesRead), Charset.forName("UTF-8")));
                        else
                            sb.append(new String(buff, Charset.forName("UTF-8")));

                        if(sb.length() >= worker_content_length[worker_idx])
                            break;


                        Thread.sleep(sleep_time);

                    }

                    log.info(String.format("worker[%s] was assigned [%s] bytes. Read [%s] over [%s] iterations.", worker_idx, worker_content_length[worker_idx], totalBytesRead, readCount) );


                    /*
                    Return the portion of the file read by this worker.
                     */
                    return sb.toString();

                }
            });
        }

        ExecutorService exs = Executors.newFixedThreadPool(workers);
        List<Future<String>> futureList = new ArrayList<>();
        long starttime = System.currentTimeMillis();

        /*
        Submit each of the workers to the executor service.
         */
        for (Callable<String> c: workerList) {
            futureList.add(exs.submit(c));
        }

        /*
        Loop until all the workers have completed their tasks.
         */
        while(true){
            int completedFutures = 0;
            for(Future<String> f : futureList){
                if(f.isDone()) completedFutures++;
            }

            if(completedFutures == workers) break;
        }


        long elapsedtime = System.currentTimeMillis();
        log.info("All futures completed. Elapsed time: " + secToMin((elapsedtime - starttime) / 1000));

        /*
        Append workers' resulting strings together and then return it.
         */
        StringBuilder bigSb = new StringBuilder();
        for(Future<String> f : futureList)
            bigSb.append(f.get());

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
        if(workers > 1) this.workers = workers;
    }
}
