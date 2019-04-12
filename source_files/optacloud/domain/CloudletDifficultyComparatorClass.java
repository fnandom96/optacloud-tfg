package com.optacloud.domain;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.cloudbus.cloudsim.Cloudlet;

import java.util.Comparator;

//Determina quins Cloudlets son mes dificils de mapejar a VMs, en aquest cas els processos mes llargs
public class CloudletDifficultyComparatorClass implements Comparator<Cloudlet> {
    public int compare(Cloudlet a, Cloudlet b) {
        return new CompareToBuilder()
                .append(a.getCloudletLength(), b.getCloudletLength())
                .append(a.getCloudletId(), b.getCloudletId())
                .toComparison();
    }
}
