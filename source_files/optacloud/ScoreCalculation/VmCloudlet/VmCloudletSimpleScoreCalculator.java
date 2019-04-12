package com.optacloud.ScoreCalculation.VmCloudlet;

import com.optacloud.domain.VmCloudletBalance;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.easy.EasyScoreCalculator;

import java.util.*;

import static java.lang.Math.*;


public class VmCloudletSimpleScoreCalculator implements EasyScoreCalculator<VmCloudletBalance> {
    /** Vm List */
    private List<Vm> vmList;

    /** Cloudlet List */
    private List<Cloudlet> cloudletList;

    public HardSoftScore calculateScore(VmCloudletBalance dc) {
        vmList = dc.getVmlist();
        cloudletList = dc.getCloudletlist();

        int vmListSize = vmList.size();
        Map<Vm, Double> timeMap = new HashMap<>(vmListSize); //quant triga a executar els seus cloudlets cada màquina

        for (Vm vm : vmList) {
            timeMap.put(vm, 0.0);
        }

        Set<Vm> usedVmSet = new HashSet<>(vmListSize);

        visitProcessList(timeMap, usedVmSet);

        int hardScore = sumHardScore(timeMap);
        int softScore = 0;

        return HardSoftScore.valueOf(hardScore, softScore);
    }

    private void visitProcessList(Map<Vm, Double> timeMap, Set<Vm> usedComputerSet) {
        for (Cloudlet cl : cloudletList) {
            Vm vm = cl.getVm();
            if (vm != null) {
                double timeUsage = timeMap.get(vm) + (double) cl.getCloudletLength()/(vm.getMips()*cl.getUtilizationModelCpu().getUtilization(0));
                timeMap.put(vm, timeUsage);
                usedComputerSet.add(vm);
            }
        }
    }

    private int sumHardScore(Map<Vm, Double> timeMap) {
        int hardScore = -1;
        for (Map.Entry<Vm, Double> usageEntry : timeMap.entrySet()) {
            double time = usageEntry.getValue();
            hardScore = max(hardScore, (int) time);
        }
        return -hardScore;
    }
}
