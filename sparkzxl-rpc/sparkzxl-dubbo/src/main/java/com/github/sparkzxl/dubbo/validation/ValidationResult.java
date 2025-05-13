package com.github.sparkzxl.dubbo.validation;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2022-08-11 11:25:38
 */
@Setter
@Getter
public class ValidationResult implements Serializable {

    private static final long serialVersionUID = -527107355540718877L;
    private Object value;
    private Object propertyPath;
    private String message;
    private String messageTemplate;
    private Object[] executableParameters;
    private Object executableReturnValue;

    public ValidationResult(Object value, Object propertyPath, String message, String messageTemplate,
            Object[] executableParameters, Object executableReturnValue) {
        this.value = value;
        this.propertyPath = propertyPath;
        this.message = message;
        this.messageTemplate = messageTemplate;
        this.executableParameters = executableParameters;
        this.executableReturnValue = executableReturnValue;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ValidationResult that = (ValidationResult) o;
        return propertyPath.equals(that.propertyPath) && message.equals(that.message) && messageTemplate.equals(that.messageTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyPath, message, messageTemplate);
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "propertyPath=" + propertyPath +
                ", message='" + message + '\'' +
                ", messageTemplate='" + messageTemplate + '\'' +
                '}';
    }
}
