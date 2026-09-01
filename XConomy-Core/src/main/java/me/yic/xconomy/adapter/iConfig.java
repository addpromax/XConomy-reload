package me.yic.xconomy.adapter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface iConfig {

    Object getConfig();

    boolean contains(String path);

    /**
     * 展开配置中的全部叶子节点，返回 "路径 -> 值"。
     * 映射节点会继续递归，标量与列表作为叶子值返回。
     * 通用配置迁移依赖该方法读取内置模板的默认值。
     */
    Map<String, Object> getLeafValues();

    void createSection(String path);

    void set(String path, Object value);

    void save() throws Exception;

    String getString(String path);

    Integer getInt(String path);

    boolean getBoolean(String path);

    double getDouble(String path);

    long getLong(String path);

    List<String> getStringList(String path);

    LinkedHashMap<BigDecimal, String> getConfigurationSectionSort(String path);
}
