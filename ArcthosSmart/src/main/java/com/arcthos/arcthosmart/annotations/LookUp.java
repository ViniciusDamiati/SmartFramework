package com.arcthos.arcthosmart.annotations;

import com.arcthos.arcthosmart.smartorm.SmartObject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by Vinicius Damiati on 03-Apr-18.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LookUp {
    Class<? extends SmartObject> value();
}
