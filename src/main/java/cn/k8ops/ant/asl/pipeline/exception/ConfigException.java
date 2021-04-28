package cn.k8ops.ant.asl.pipeline.exception;

import org.apache.tools.ant.BuildException;

public class ConfigException extends BuildException {
    public ConfigException(String message) {
        super(message);
    }
    public ConfigException(Throwable cause) {
        super(cause);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
