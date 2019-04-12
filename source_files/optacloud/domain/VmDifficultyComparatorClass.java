package com.optacloud.domain;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.cloudbus.cloudsim.Vm;

import java.util.Comparator;

//Determina quines VM son mes dificils de mapejar a hosts, en aquest cas les que utilitzen mes recursos
public class VmDifficultyComparatorClass implements Comparator<Vm> {
    public int compare(Vm a, Vm b) {
        return new CompareToBuilder()
                .append(a.getMips()*a.getNumberOfPes(), b.getMips()*b.getNumberOfPes())
                //.append(b.getMips()*b.getNumberOfPes(), a.getMips()*a.getNumberOfPes())
                .append(a.getSize(), b.getSize())
                .append(a.getRam(), b.getRam())
                .append(a.getBw(), b.getBw())
                .append(a.getId(), b.getId())
                .toComparison();
    }
}
