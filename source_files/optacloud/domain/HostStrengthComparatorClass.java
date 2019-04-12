package com.optacloud.domain;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.cloudbus.cloudsim.Host;

import java.util.Comparator;

//Determina quines VM son mes dificils de mapejar a hosts, en aquest cas les que utilitzen mes recursos
public class HostStrengthComparatorClass implements Comparator<Host> {
    public int compare(Host a, Host b) {
        return new CompareToBuilder()
                .append(a.getTotalMips(), b.getTotalMips())
                .append(a.getId(), b.getId())
                .toComparison();
    }
}
