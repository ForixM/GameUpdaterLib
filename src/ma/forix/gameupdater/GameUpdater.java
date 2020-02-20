package ma.forix.gameupdater;

import java.io.File;

public class GameUpdater {

    private String url;
    private File gameDir;

    private Downloader downloader;

    public GameUpdater(String url, File gameDir){
        this.url = url;
        this.gameDir = gameDir;
        downloader = new Downloader(url, gameDir);
    }

    public void start(){

    }

    public void interrupt(){

    }
}
