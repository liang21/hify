package com.hify.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis-Plus custom properties
 */
@ConfigurationProperties(prefix = "hify.mybatis-plus")
public class MybatisPlusProperties {

    /**
     * Logical delete value (default: 1)
     */
    private int logicDeleteValue = 1;

    /**
     * Logical not delete value (default: 0)
     */
    private int logicNotDeleteValue = 0;

    /**
     * Enable logical delete (default: true)
     */
    private boolean logicDeleteEnable = true;

    public int getLogicDeleteValue() {
        return logicDeleteValue;
    }

    public void setLogicDeleteValue(int logicDeleteValue) {
        this.logicDeleteValue = logicDeleteValue;
    }

    public int getLogicNotDeleteValue() {
        return logicNotDeleteValue;
    }

    public void setLogicNotDeleteValue(int logicNotDeleteValue) {
        this.logicNotDeleteValue = logicNotDeleteValue;
    }

    public boolean isLogicDeleteEnable() {
        return logicDeleteEnable;
    }

    public void setLogicDeleteEnable(boolean logicDeleteEnable) {
        this.logicDeleteEnable = logicDeleteEnable;
    }
}
