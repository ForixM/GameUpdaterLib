package ma.forix.gameupdater;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.net.URL;

public class Downloader extends Thread implements Runnable {

    private String url;
    private File gameDir;
    private JSONArray jsonArray, toDownload;
    private JSONObject object;
    private int downloadSize, bytesDownloaded, fileDownloaded, filesToDownload, threadsNumber;
    private final int SIMULTANEOUS = 20;
    private String[] ignoreFiles;
    private BufferedInputStream reader;

    private void getContent(){
        try {
            Socket socket = new Socket("v1.modcraftmc.fr", 25666);
            PrintWriter writer = new PrintWriter(socket.getOutputStream());
            reader = new BufferedInputStream(socket.getInputStream());
            String reponse = null;
            jsonArray = new JSONArray();

            writer.write("getContent");
            writer.flush();
            reponse = read();
            System.out.println("ouais: "+reponse);
            try (InputStreamReader streamReader = new InputStreamReader(new URL(this.url+"/content.json").openStream())){
                Object obj = new JSONParser().parse(streamReader);
                jsonArray = (JSONArray) obj;
            } catch (ParseException | IOException e) {
                e.printStackTrace();
            }
            System.out.println("Receiving finished");
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
            ignoreFiles = reading.toString().split("\r");
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

    public int getDownloadSize(){
        return  downloadSize;
    }

    public int getFilesToDownload(){
        return filesToDownload;
    }

    public int getFileDownloaded(){
        return fileDownloaded;
    }

    public int getBytesDownloaded(){
        return bytesDownloaded;
    }

    public void Suppresser(){
        File[] listing = FileUtils.listFiles(gameDir, null, true).toArray(new File[0]);
        long temp = System.currentTimeMillis();

        for (File current : listing){
            boolean exist = false;
            for (Object array: jsonArray){
                object = (JSONObject) array;
                if (current.getName().equals(object.get("filename").toString()))
                    exist = true;
            }
            for (String now : ignoreFiles){
                File tempo = new File(gameDir+now.replace("/", "\\"));
                if (current.toString().contains(now.replace("/", "\\"))) {
                    System.out.println("[IGNORE LIST] C'est dans la ignore ! " + current.getName());
                    exist = true;
                }
            }
            if (!exist) {
                current.delete();
            }
        }

        System.out.println("[IGNORE LIST] time elapsed for deleting unecessary files: "+(System.currentTimeMillis()-temp));
    }

    private void Verification(){
        filesToDownload = 0;
        File cursor;
        int i = 0;
        toDownload = new JSONArray();
        long temp = System.currentTimeMillis();
        for (Object array : jsonArray){
            object = (JSONObject) array;
            cursor = new File(gameDir.toString() + "\\" + object.get("path").toString() + object.get("filename").toString());
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

    @Override
    public void run() {
        File cursor;
        super.run();
        Verification();
        threadsNumber = 0;
        long time = System.currentTimeMillis()/1000;
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
        boolean finish = false;
        while (!finish){
            if (fileDownloaded >= filesToDownload){
                finish = true;
            } else
                finish = false;
            try {
                sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("temps: "+(System.currentTimeMillis()/1000-time)+" sec");
        System.out.println("Process terminé !");
    }

    private void download(File cursor, JSONObject obj){
        File writer = cursor;
        Thread download = new Thread(() -> {
            String path = obj.get("path").toString();
            String fileName = obj.get("filename").toString();
            try {
                threadsNumber++;
                BufferedInputStream bis = new BufferedInputStream(new URL(url + "/downloads/" + path.replace("\\", "/").replaceAll(" ", "%20") + fileName.replaceAll(" ", "%20")).openStream());
                FileOutputStream fos = new FileOutputStream(writer);
                final byte data[] = new byte[32];
                int count;

                while ((count = bis.read(data, 0, 32)) != -1) {
                    bytesDownloaded += count;
                    fos.write(data, 0, count);
                }
                threadsNumber--;
                fileDownloaded++;
                System.out.println("Fichiers téléchargés: "+fileDownloaded);
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
}
