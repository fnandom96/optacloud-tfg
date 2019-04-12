package com.optacloud.ScoreCalculation.HostVm;

import com.optacloud.domain.HostVmBalance;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.easy.EasyScoreCalculator;

import java.util.*;


public class HostVmSimpleScoreCalculator implements EasyScoreCalculator<HostVmBalance> {

    /** The cost per each unity of RAM memory. */
    private double costPerMem;

    /** The cost per each unit of storage. */
    private double costPerStorage;

    /** The cost of each byte of bandwidth (bw) consumed. */
    private double costPerBw;

    /** Price/CPU-unit. If unit = sec., then the price is defined as G$/CPU-sec. */
    //Cost per cada MI
    private double costPerCPU; //***esta ben calculat?

    /** Vm List */
    private List<Vm> vmList;

    /** Host List */
    private List<Host> hostList;

    public HardSoftScore calculateScore(HostVmBalance dc) {
        vmList = dc.getVmlist();
        hostList = dc.getHostlist();

        DatacenterCharacteristics dcc = null;

        if (hostList.size() > 0) dcc = hostList.get(0).getDatacenter().getCharacteristics(); //***ja s'ha assignat els hosts al DC?

        costPerBw = dcc.getCostPerBw();
        costPerMem = dcc.getCostPerMem();
        costPerCPU = dcc.getCostPerSecond(); //dcc.getCostPerMi();
        costPerStorage = dcc.getCostPerStorage();

        int computerListSize = hostList.size();
        Map<Host, Integer> cpuPowerUsageMap = new HashMap<>(computerListSize);
        Map<Host, Integer> memoryUsageMap = new HashMap<>(computerListSize);
        Map<Host, Integer> networkBandwidthUsageMap = new HashMap<>(computerListSize);
        Map<Host, Integer> storageUsageMap = new HashMap<>(computerListSize);
        Set<Host> usedComputerSet = new HashSet<>(computerListSize);

        for (Host computer : hostList) {
            //Tenir en compte l'estat actual del host
            int usedMips = (int) (computer.getUsedMIPS());
            cpuPowerUsageMap.put(computer, usedMips);
            memoryUsageMap.put(computer, computer.getRamProvisioner().getUsedRam());
            networkBandwidthUsageMap.put(computer, (int) computer.getBwProvisioner().getUsedBw());
            storageUsageMap.put(computer, (int) (computer.getTotalStorage()-computer.getStorage()));
            //afegir els hosts ja utilitzats previament
            if (computer.getVmList().size() > 0) {
                usedComputerSet.add(computer);
            }
        }

        visitProcessList(cpuPowerUsageMap, memoryUsageMap, networkBandwidthUsageMap, storageUsageMap, usedComputerSet);

        int hardScore = sumHardScore(cpuPowerUsageMap, memoryUsageMap, networkBandwidthUsageMap, storageUsageMap);
        int softScore = sumSoftScore(usedComputerSet, cpuPowerUsageMap, memoryUsageMap, networkBandwidthUsageMap, storageUsageMap);

        return HardSoftScore.valueOf(hardScore, softScore);
    }

    private void visitProcessList(Map<Host, Integer> cpuPowerUsageMap, Map<Host, Integer> memoryUsageMap, Map<Host, Integer> networkBandwidthUsageMap,
                                  Map<Host, Integer> storageUsageMap, Set<Host> usedComputerSet) {
        for (Vm vm : vmList) {
            Host computer = vm.getHost(); //hostList.get(vm.getHost().getId()); //***el host esta a la llista? (vm.getHost())
            if (computer != null) {
                int cpuPowerUsage = cpuPowerUsageMap.get(computer) + (int) vm.getMips()*vm.getNumberOfPes();
                cpuPowerUsageMap.put(computer, cpuPowerUsage);
                int memoryUsage = memoryUsageMap.get(computer) + (int) vm.getRam();
                memoryUsageMap.put(computer, memoryUsage);
                int networkBandwidthUsage = networkBandwidthUsageMap.get(computer) + (int) vm.getBw();
                networkBandwidthUsageMap.put(computer, networkBandwidthUsage);
                int storageUsage = storageUsageMap.get(computer) + (int) vm.getSize();
                storageUsageMap.put(computer, storageUsage);
                usedComputerSet.add(computer);
            }
        }
    }

    private int sumHardScore(Map<Host, Integer> cpuPowerUsageMap, Map<Host, Integer> memoryUsageMap, Map<Host, Integer> networkBandwidthUsageMap,
                             Map<Host, Integer> storageUsageMap) {
        int hardScore = 0;

        //Calcular recursos sobrepassats
        for (Host computer : hostList) {
            //CPU
            double usedMips = cpuPowerUsageMap.get(computer);
            double totalMips = (computer.getPeList().get(0).getPeProvisioner().getMips())*computer.getNumberOfPes();
            if (usedMips > totalMips) {
                hardScore += (totalMips-usedMips);
            }

            //RAM
            double usedRAM = memoryUsageMap.get(computer);
            double totalRAM = computer.getRamProvisioner().getRam();
            if (usedRAM > totalRAM) {
                hardScore += (totalMips-usedRAM);
            }

            //BW
            double usedBW = networkBandwidthUsageMap.get(computer);
            double totalBW = computer.getBwProvisioner().getBw();
            if (usedBW > totalBW) {
                hardScore += (totalBW-usedBW);
            }

            //Storage
            double usedStor = storageUsageMap.get(computer);
            double totalStor = computer.getTotalStorage();
            if (usedStor > totalStor) {
                hardScore += (totalStor-usedStor);
            }
        }
        /*for (Map.Entry<Host, Integer> usageEntry : cpuPowerUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int cpuPowerAvailable = ((int) computer.getPeList().get(0).getPeProvisioner().getAvailableMips())*computer.getNumberOfPes() - usageEntry.getValue(); //tots els pe tenen els mateixos mips
            if (cpuPowerAvailable < 0) { //0 = no broken constraints
                hardScore += cpuPowerAvailable;
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : memoryUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int memoryAvailable = computer.getRamProvisioner().getAvailableRam() - usageEntry.getValue();
            if (memoryAvailable < 0) {
                hardScore += memoryAvailable;
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : networkBandwidthUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int networkBandwidthAvailable = (int) computer.getBwProvisioner().getAvailableBw() - usageEntry.getValue();
            if (networkBandwidthAvailable < 0) {
                hardScore += networkBandwidthAvailable;
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : storageUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int storageAvailable = (int) computer.getStorage() - usageEntry.getValue();
            if (storageAvailable < 0) {
                hardScore += storageAvailable;
            }
        }*/
        return hardScore;
    }

    private int sumSoftScore(Set<Host> usedComputerSet, Map<Host, Integer> cpuPowerUsageMap, Map<Host, Integer> memoryUsageMap,
                             Map<Host, Integer> networkBandwidthUsageMap, Map<Host, Integer> storageUsageMap) {
        int softScore = 0;
        for (Host usedComputer : usedComputerSet) {
            softScore -= cpuPowerUsageMap.get(usedComputer)*costPerCPU; //***mateix mips a totes les pe
            softScore -= memoryUsageMap.get(usedComputer)*costPerMem;
            softScore -= networkBandwidthUsageMap.get(usedComputer)*costPerBw;
            softScore -= storageUsageMap.get(usedComputer)*costPerStorage;
        }
        return softScore;
    }
}
