package mineshafter;

import java.applet.Applet;
import java.awt.Frame;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JOptionPane;

import mineshafter.proxy.MineProxy;
import mineshafter.util.Resources;
import mineshafter.util.SimpleRequest;
import mineshafter.util.Streams;

public class MineClient extends Applet {
	private static final long serialVersionUID = 1L;
	
	protected static float VERSION = 2.9f;
	protected static int proxyPort = 8061;
	protected static int proxyHTTPPort = 8062;
	
	protected static String launcherDownloadURL = "https://s3.amazonaws.com/MinecraftDownload/launcher/minecraft.jar"; // "http://www.minecraft.net/download/minecraft.jar";
	protected static String normalLauncherFilename = "minecraft.jar";
	protected static String hackedLauncherFilename = "minecraft_modified.jar";
	
	protected static String MANIFEST_TEXT = "Manifest-Version: 1.2\nCreated-By: 1.6.0_22 (Sun Microsystems Inc.)\nMain-Class: net.minecraft.MinecraftLauncher\n";
	
	/* Added For MineshafterSquared */
	protected static String authServer = Resources.loadString("auth").trim();
	protected static String gamePath;
	
	public void init() {
		MineClient.main(new String[0]);
	}
	
	public static void main(String[] args) {
		try {
			// Get Update Info
			String buildNumber = getGameBuildNumber();
			String updateInfo = new String(SimpleRequest.get("http://" + authServer + "/update.php?name=client&build=" + buildNumber));
			String[] updateInfoArray = updateInfo.split(":"); // Split out each chuck of information
			
			// assign each information chunk to its variable 
			String verstring = updateInfoArray[0];
			boolean needGameUpdate = Boolean.parseBoolean(updateInfoArray[1]);
			String gameVersion = updateInfoArray[2];
			
			// make sure verstring is 0 if it is empty
			if(verstring.isEmpty()) {
				verstring = "0";
			}
			
			// parse out verstring into an integer
			float version;
			try {
				version = Float.parseFloat(verstring);
			} 
			catch(Exception e) {
				version = 0;
			}
			
			// Print Proxy Version Numbers to Console
			System.out.println("Current proxy version: " + VERSION);
			System.out.println("Gotten proxy version: " + version);
			
			if(VERSION < version) {
				JOptionPane.showMessageDialog(null, "A new version of Mineshafter Squared is available at http://" + authServer + "\nGo get it.", "Update Available", JOptionPane.PLAIN_MESSAGE);
				System.exit(0);
			}
			
			// Check for new game version
			System.out.println("Game Version Found: " + buildNumber);
            System.out.println("Needs Update: " + needGameUpdate);
            System.out.println("Latest Version: " + gameVersion);
            
            // user prompts
            if(needGameUpdate) {
                int answer = JOptionPane.showConfirmDialog(null, "A new version of Minecraft is available, would you like to update to version " + gameVersion + "?",
                			"Game Update Found!", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if(answer == JOptionPane.YES_OPTION) {
                    int areYouSure = JOptionPane.showConfirmDialog(null, "Make sure any online servers you would like to play on are updated"
                            + " to version " + gameVersion + " or else you will not be able to play on them until they do.",
                            "Are you sure?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if(areYouSure == JOptionPane.YES_OPTION) {
                    	recursiveDelete(new File(gamePath));
                    }
                }
            }
            
		} 
		catch(Exception e) {
			// if errors
			System.out.println("Error while updating:");
			e.printStackTrace();
			/* System.exit(1); */
		}
		
		try {
			MineProxy proxy = new MineProxy(proxyPort, VERSION, authServer); // create proxy
			proxy.start(); // launch proxy
			
			System.setProperty("http.proxyHost", "127.0.0.1");
			System.setProperty("http.proxyPort", Integer.toString(proxyPort));
			System.setProperty("https.proxyHost", "127.0.0.1");
			System.setProperty("https.proxyPort", Integer.toString(proxyPort));
			
			// Make sure we have a fresh launcher every time
			File hackedFile = new File(hackedLauncherFilename);
			if(hackedFile.exists()){ 
				hackedFile.delete();
			}
			
			// start the game launcher
			startLauncher();
			
		} catch(Exception e) {
			System.out.println("Something bad happened:");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static void startLauncher()
	{
		try {
			// if hacked game exists
			if(new File(hackedLauncherFilename).exists()) {
				URL u = new File(hackedLauncherFilename).toURI().toURL();
				URLClassLoader cl = new URLClassLoader(new URL[]{u});
				
				@SuppressWarnings("unchecked")
				Class<Frame> launcherFrame = (Class<Frame>) cl.loadClass("net.minecraft.LauncherFrame");
				
				Method main = launcherFrame.getMethod("main", new Class[]{ String[].class });
				main.invoke(launcherFrame, new Object[]{ new String[0] }); // TODO Put the args we received in here
				
			}
			// if the normal game exists
			else if(new File(normalLauncherFilename).exists()) {
				editLauncher();
				startLauncher();
				
			}
			// 
			else {
				try{
					byte[] data = SimpleRequest.get(launcherDownloadURL);
					OutputStream out = new FileOutputStream(normalLauncherFilename);
					out.write(data);
					out.flush();
					out.close();
					startLauncher();
					
				} catch(Exception ex) {
					System.out.println("Error downloading launcher:");
					ex.printStackTrace();
					return;
				}
			}
		} catch(Exception e1) {
			System.out.println("Error starting launcher:");
			e1.printStackTrace();
		}
	}
	
	public static void editLauncher() {
		try {
			ZipInputStream in = new ZipInputStream(new FileInputStream(normalLauncherFilename));
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(hackedLauncherFilename));
			ZipEntry entry;
			String n;
			InputStream dataSource;
			while((entry = in.getNextEntry()) != null) {
				n = entry.getName();
				if(n.contains(".svn") || n.equals("META-INF/MOJANG_C.SF") || n.equals("META-INF/MOJANG_C.DSA") || n.equals("net/minecraft/minecraft.key") || n.equals("net/minecraft/Util$OS.class")) continue;
				out.putNextEntry(entry);
				if(n.equals("META-INF/MANIFEST.MF")) dataSource = new ByteArrayInputStream(MANIFEST_TEXT.getBytes());
				else if(n.equals("net/minecraft/Util.class")) dataSource = Resources.load("Util.class");
				else dataSource = in;
				Streams.pipeStreams(dataSource, out);
				out.flush();
			}
			in.close();
			out.close();
		} catch(Exception e) {
			System.out.println("Editing launcher failed:");
			e.printStackTrace();
		}
	}
	
	// Functions added for MineshafterSquared 
	private static String getGameBuildNumber() {
        // set variables based on OS
        String versionPath = null;
        
        String os = System.getProperty("os.name").toLowerCase();
        Map<String, String> enviornment = System.getenv();
        
        if (os.contains("windows")) {
            versionPath = enviornment.get("APPDATA") + "\\.minecraft\\bin\\version";
            gamePath = enviornment.get("APPDATA") + "\\.minecraft\\bin";
        } else if (os.contains("mac")) {
            versionPath = "/Users/" + enviornment.get("USER") + "/Library/Application Support/minecraft/bin/version";
            gamePath = "/Users/" + enviornment.get("USER") + "/Library/Application Support/minecraft/bin";
        } else if(os.contains("linux")){
            versionPath = enviornment.get("HOME") + "/.minecraft/bin/version";
            gamePath = enviornment.get("HOME") + "/.minecraft/bin";
        }

        // get current game version number
        if(new File(versionPath).exists()){
            return Resources.loadString(versionPath).trim();
        } else {
            return "0";
        }
    }
	
	private static void recursiveDelete(File root){
        if(root.isDirectory()){
            for(File file : root.listFiles()){
            	recursiveDelete(file);
            }
            root.delete();
        } else {
            root.delete();
        }
    }
}