package ma.forix.gameupdater;

import javafx.concurrent.Task;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Downloader extends Task<Void> {

    private String url;
    private File gameDir;
    private JSONArray jsonArray, toDownload;
    private JSONObject object;
    private int downloadSize, bytesDownloaded, fileDownloaded, filesToDownload, threadsNumber;
    private final int SIMULTANEOUS = 20;
    private String[] ignoreFiles;
    private BufferedInputStream reader;
    private Thread updateBar;
    private Os os;

    private void getContent(){
        try {
            Socket socket = new Socket("v1.modcraftmc.fr", 25667);
            PrintWriter writer = new PrintWriter(socket.getOutputStream());
            reader = new BufferedInputStream(socket.getInputStream());
            String reponse = null;
            jsonArray = new JSONArray();

            writer.write("getContent");
            writer.flush();
            reponse = read();
            try (InputStreamReader streamReader = new InputStreamReader(new URL(this.url+"/content.json").openStream())){
                Object obj = new JSONParser().parse(streamReader);
                jsonArray = (JSONArray) obj;
            } catch (ParseException | IOException e) {
                e.printStackTrace();
            }
            System.out.println("content.json recovered");
            writer.write("close");
            writer.flush();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String read() throws IOException{
        String response = "";
        int stream;
        byte[] b = new byte[1024];
        stream = reader.read();
        response = new String(b, 0, stream);
        return response;
    }

    public Downloader(String url, File gameDir){
        System.out.println("OS: "+System.getProperty("os.name"));
        if (System.getProperty("os.name").contains("Windows"))
            os = Os.WINDAUBE;
        else
            os = Os.UNIX;

        if (url.toCharArray()[url.toCharArray().length-1] == '/'){
            this.url = UrlAdapter(url);
        }
        this.gameDir = gameDir;
        if (!gameDir.exists())
            gameDir.mkdir();
        getContent();


        try(InputStreamReader streamReader = new InputStreamReader(new URL(this.url+"/ignore.txt").openStream())){
            int data = streamReader.read();
            StringBuilder reading = new StringBuilder();
            boolean writing = true;

            while (data != -1){
                if (writing) {
                    if (data == 13)
                        writing = false;
                    reading.append((char) data);
                } else
                    writing = true;
                data = streamReader.read();
            }

            if (reading.toString().contains("\r"))
                ignoreFiles = reading.toString().split("\r");
            else
                ignoreFiles = reading.toString().split("\n");

            System.out.print("[");
            for (String current : ignoreFiles){
                System.out.print("\""+current+"\", ");
            }
            System.out.println("]");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String UrlAdapter(String url){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < url.toCharArray().length-1; i++){
            sb.append(url.charAt(i));
        }
        return sb.toString();
    }



    public int GetDownloadSize(JSONArray toDownload){
        File cursor;
        for (Object array : jsonArray){
            object = (JSONObject) array;
            cursor = new File(gameDir.toString() + "\\" + object.get("path").toString() + object.get("filename").toString());
            if (!cursor.exists()) downloadSize += Integer.parseInt(object.get("size").toString());
        }
        return downloadSize;
    }

    public void Suppresser(){
        File[] listing = FileUtils.listFiles(gameDir, null, true).toArray(new File[0]);
        long temp = System.currentTimeMillis();

        for (File current : listing){
            boolean ignore = false;
            //Use MD5 algorithm
            MessageDigest md5Digest = null;
            try {
                md5Digest = MessageDigest.getInstance("MD5");
                //Get the checksum
                String checksum = getFileChecksum(md5Digest, current);
                for (Object array: jsonArray){
                    object = (JSONObject) array;
                    if (checksum.equals(object.get("md5").toString())) {
                        if (object.get("filename").toString().equals(current.getName()))
                            ignore = true;
                    }
                }
            } catch (NoSuchAlgorithmException | IOException e) {
                e.printStackTrace();
            }
            for (String now : ignoreFiles) {
                if (current.toString().contains(now.replace("/", "\\"))) {
                    //System.out.println("[IGNORE LIST] This file is ignored: " + current.getName());
                    ignore = true;
                }
            }
            if (!ignore) {
                System.out.println("[IGNORE LIST] Fichier '"+current+"' supprimé");
                current.delete();
            }
        }

        System.out.println("[IGNORE LIST] time elapsed for deleting unecessary files: "+(System.currentTimeMillis()-temp));
    }

    private static String getFileChecksum(MessageDigest digest, File file) throws IOException
    {
        //Get file input stream for reading the file content
        FileInputStream fis = new FileInputStream(file);

        //Create byte array to read data in chunks
        byte[] byteArray = new byte[1024];
        int bytesCount = 0;

        //Read file data and update in message digest
        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        };

        //close the stream; We don't need it now.
        fis.close();

        //Get the hash's bytes
        byte[] bytes = digest.digest();

        //This bytes[] has bytes in decimal format;
        //Convert it to hexadecimal format
        StringBuilder sb = new StringBuilder();
        for(int i=0; i< bytes.length ;i++)
        {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }

        //return complete hash
        return sb.toString();
    }

    private void Verification(){
        filesToDownload = 0;
        File cursor;
        toDownload = new JSONArray();
        long temp = System.currentTimeMillis();

        for (Object array : jsonArray){
            object = (JSONObject) array;
            cursor = new File(gameDir.toString() + "\\" + object.get("path").toString() + object.get("filename").toString().replaceAll("#var#", ".var"));

            if (!cursor.exists()) {
                toDownload.add(object);
                filesToDownload++;
            } else {
                if (cursor.length() != Integer.parseInt(object.get("size").toString())){
                    cursor.delete();
                    toDownload.add(object);
                    filesToDownload++;
                }
            }
        }
        System.out.println("[VERIFICATION] temps écoulé vérif: "+(System.currentTimeMillis()-temp));
        System.out.println("[VERIFICATION] Download size: "+GetDownloadSize(toDownload)/1024+"Ko");
        System.out.println("[VERIFICATION] Files to download: "+filesToDownload);
    }

    private void download(File cursor, JSONObject obj){
        Thread download = new Thread(() -> {
            String path = obj.get("path").toString();
            String fileName = obj.get("filename").toString();
            try {
                threadsNumber++;
                URL fileUrl = new URL(this.url+"/downloads/" + path.replace("\\", "/").replaceAll(" ", "%20").replaceAll("#", "%23") + fileName.replaceAll(" ", "%20").replaceAll("#", "%23"));
                System.out.println("[GameUpdater] Téléchargement du fichier: "+fileUrl.toString());
                BufferedInputStream bis = new BufferedInputStream(fileUrl.openStream());
                FileOutputStream fos = new FileOutputStream(new File(cursor.toString().replaceAll("#var#", ".var")));
                final byte data[] = new byte[64];
                int count;
                while ((count = bis.read(data, 0, 32)) != -1) {
                    bytesDownloaded += count;
                    updateProgress(bytesDownloaded, downloadSize);
                    fos.write(data, 0, count);
                }
                threadsNumber--;
                fileDownloaded++;
                System.out.println("[GameUpdater] Téléchargement du fichier terminé :"+fileName);
                bis.close();
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        download.start();
        if (threadsNumber > SIMULTANEOUS) {
            try {
                download.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private long time;

    @Override
    protected Void call() {
        File cursor;
        Verification();
        threadsNumber = 0;
        time = System.currentTimeMillis()/1000;

        updateBar = new Thread(){
            @Override
            public void run() {
                super.run();
                updateProgress(bytesDownloaded, downloadSize);
            }
        };
        updateBar.start();

        for (Object array : toDownload){
            object = (JSONObject) array;
            cursor = new File(gameDir.toString() + "\\" + object.get("path").toString() + object.get("filename").toString());
            if (cursor.getParentFile().exists()) {
                if (!cursor.exists()) {
                    download(cursor, object);
                }
            } else {
                cursor.getParentFile().mkdirs();
                download(cursor, object);
            }
        }

        boolean finished = false;
        while (!finished){
            if (fileDownloaded >= filesToDownload)
                finished = true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    protected void succeeded() {
        System.out.println("[GameUpdater] Downloading time: "+(System.currentTimeMillis()/1000-time)+" sec");
        System.out.println("[GameUpdater] Update finished !");
        super.succeeded();
    }

    @Override
    protected void cancelled() {
        super.cancelled();
    }
}
