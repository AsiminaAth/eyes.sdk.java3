package com.applitools.eyes.visualgrid.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IosDeviceName {
    iPhone_11_Pro("iPhone 11 Pro"),
    iPhone_11_Pro_Max("iPhone 11 Pro Max"),
    iPhone_11("iPhone 11"),
    iPhone_XR("iPhone XR"),
    iPhone_XS("iPhone Xs"),
    iPhone_X("iPhone X"),
    iPhone_8("iPhone 8"),
    iPhone_7("iPhone 7"),
    iPad_Pro_3("iPad Pro (12.9-inch) (3rd generation)"),
    iPad_7("iPad (7th generation)"),
    iPad_Air_2("iPad Air (2nd generation)");

    private final String name;

    IosDeviceName(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "IosDeviceName{" +
                "name='" + name + '\'' +
                '}';
    }
}
