package me.yic.xconomy.adapter.comp;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.adapter.iConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings("unused")
public class CConfig implements iConfig {
    private final FileConfiguration fc;

    private final File ff;

    public CConfig(FileConfiguration fc){
        this.ff = null;
        this.fc = fc;
    }

    public CConfig(File f){
        this.ff = f;
        this.fc = YamlConfiguration.loadConfiguration(f);
    }

    public CConfig(URL url){
        this.ff = null;
        FileConfiguration pfc = null;
        URLConnection conn = null;
        InputStream is = null;
        Reader br = null;
        try {
            conn = url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            // 该构造既用于远程地址，也用于读取插件包内的模板资源。
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpConn = (HttpURLConnection) conn;
                httpConn.setRequestMethod("GET");
                httpConn.setRequestProperty("Accept", "application/json");
                httpConn.connect();
                if (200 != httpConn.getResponseCode()) {
                    throw new IOException("ResponseCode is an error code:" + httpConn.getResponseCode());
                }
            }
            is = conn.getInputStream();
            br = new InputStreamReader(is, StandardCharsets.UTF_8);
            pfc = YamlConfiguration.loadConfiguration(br);
        } catch (Exception ignored) {
            //e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
        fc = pfc;
    }

    public CConfig(String path, String subpath){
        this.ff = null;
        FileConfiguration pfc = null;

        String jarPath = "jar:file:" + XConomy.getInstance().getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
        Reader reader = null;
        InputStream is = null;
        try {
            URL url = new URL(jarPath + "!" + path + subpath);
            is = url.openStream();
            reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                URL url = new URL(jarPath + "!" + path + "/english.yml");
                is = url.openStream();
                reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            } catch (IOException ioException) {
                XConomy.getInstance().getLogger().warning("System languages file read error");
                ioException.printStackTrace();
            }
        }
        if (reader == null) {
            XConomy.getInstance().getLogger().warning("System languages file read error");
            fc = pfc;
            return;
        }
        pfc = YamlConfiguration.loadConfiguration(reader);

        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        fc = pfc;
    }

    @Override
    public FileConfiguration getConfig() {
        return fc;
    }

    @Override
    public boolean contains(String path){
        return fc.contains(path);
    }

    @Override
    public Map<String, Object> getLeafValues(){
        Map<String, Object> leaves = new TreeMap<>();
        if (fc == null) {
            return leaves;
        }
        for (Map.Entry<String, Object> entry : fc.getValues(true).entrySet()) {
            // getValues(true) 同时返回配置区块本身，这里只保留叶子值。
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                leaves.put(entry.getKey(), entry.getValue());
            }
        }
        return leaves;
    }

    @Override
    public void createSection(String path){
        fc.createSection(path);
    }

    @Override
    public void set(String path, Object value){
        fc.set(path, value);
    }

    @Override
    public String getString(String path){
        return fc.getString(path);
    }

    @Override
    public Integer getInt(String path){
        return fc.getInt(path);
    }

    @Override
    public boolean getBoolean(String path){
        return fc.getBoolean(path);
    }

    @Override
    public double getDouble(String path){
        return fc.getDouble(path);
    }

    @Override
    public long getLong(String path){
        return fc.getLong(path);
    }

    @Override
    public void save() throws Exception {
        if (ff == null){
            throw new Exception("The file is null");
        }
        fc.save(ff);
    }

    @Override
    public List<String> getStringList(String path){
        return fc.getStringList(path);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public LinkedHashMap<BigDecimal, String> getConfigurationSectionSort(String path){
        LinkedHashMap<BigDecimal, String> ks = new LinkedHashMap<>();
        try {
            ConfigurationSection section = fc.getConfigurationSection(path);
            section.getKeys(false).stream().map(BigDecimal::new).sorted().forEach(key -> ks.put(key, getString(path + "." + key)));
        } catch (Exception e) {
            return null;
        }
        return ks;
    }
}
