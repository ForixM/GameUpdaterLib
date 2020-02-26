package ma.forix.gameupdater;

import java.io.*;

public class GameUpdater {

    private String url;
    private File gameDir;

    private Downloader downloader;

    private static BufferedInputStream reader;

    public static void main(String[] args) {
        new GameUpdater("http://v1.modcraftmc.fr:100/gameupdater/", new File(System.getProperty("user.home")+"\\AppData\\Roaming\\.Atest"));
    }

    public GameUpdater(String url, File gameDir){
        this.url = url;
        this.gameDir = gameDir;
        downloader = new Downloader(url, gameDir);
        downloader.Suppresser();
        start();
    }

    public void start(){
        downloader.start();
    }

    public int getDownloadSize(){
        return downloader.getDownloadSize();
    }

    public int getFilesToDownload(){
        return downloader.getFilesToDownload();
    }

    public int getFilesDownloaded(){
        return downloader.getFileDownloaded();
    }

    public int getBytesDownloaded(){
        return downloader.getBytesDownloaded();
    }

    public void interrupt(){
        downloader.interrupt();
    }
}
