package ma.forix.gameupdater;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

public class Downloader extends Thread implements Runnable {

    private String url;
    private File gameDir, cursor;
    private JSONArray jsonArray;
    private JSONObject object;
    private BufferedInputStream bis;
    private FileOutputStream fos;
    private int bytesDownloaded, fileDownloaded;

    public Downloader(String url, File gameDir){
        System.out.println("url: "+url);
        if (url.toCharArray()[url.toCharArray().length-1] == '/'){
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < url.toCharArray().length-1; i++){
                sb.append(url.charAt(i));
            }
            url = sb.toString();
            this.url = url;
        }

        try (InputStreamReader streamReader = new InputStreamReader(new URL(url+"/content.json").openStream())){
            Object obj = new JSONParser().parse(streamReader);
            jsonArray = (JSONArray) obj;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        super.run();
        for (Object array : jsonArray){
            object = (JSONObject) array;
            cursor = new File(gameDir.toString()+"\\"+object.get("path").toString());
            if (object.containsKey("filename")){
                try {
                    bis = new BufferedInputStream(new URL(url+"/downloads/"+object.get("path").toString().replace("\\", "/").replaceAll(" ", "%20")+object.get("filename").toString().replaceAll(" ", "%20")).openStream());
                    fos = new FileOutputStream(cursor);
                    final byte data[] = new byte[1024];
                    int count;

                    while ((count = bis.read(data, 0, 1024)) != -1) {
                        bytesDownloaded += count;
                        fos.write(data, 0, count);
                    }

                    bis.close();
                    fos.close();
                    fileDownloaded++;
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if (!cursor.exists()){
                    cursor.mkdirs();
                }
            }
        }
    }
}
