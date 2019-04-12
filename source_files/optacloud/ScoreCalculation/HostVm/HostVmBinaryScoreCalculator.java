package com.optacloud.ScoreCalculation.HostVm;

import com.optacloud.domain.HostVmBalance;
import com.sun.xml.bind.v2.runtime.unmarshaller.XsiNilLoader;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.easy.EasyScoreCalculator;

import java.util.*;


public class HostVmBinaryScoreCalculator implements EasyScoreCalculator<HostVmBalance> {

    /** Vm List */
    private List<Vm> vmList;

    /** Host List */
    private List<Host> hostList;

    public HardSoftScore calculateScore(HostVmBalance dc) {
        vmList = dc.getVmlist();
        hostList = dc.getHostlist();

        int computerListSize = hostList.size();
        Map<Host, Integer> cpuPowerUsageMap = new HashMap<>(computerListSize);
        Map<Host, Integer> memoryUsageMap = new HashMap<>(computerListSize);
        Map<Host, Integer> networkBandwidthUsageMap = new HashMap<>(computerListSize);
        Map<Host, Integer> storageUsageMap = new HashMap<>(computerListSize);
        Map<Host, Boolean> exceededMap = new HashMap<>(computerListSize);
        Set<Host> usedComputerSet = new HashSet<>(computerListSize);

        for (Host computer : hostList) {
            //Tenir en compte l'estat actual del host
            int usedMips = (int) (computer.getUsedMIPS());
            cpuPowerUsageMap.put(computer, usedMips);
            memoryUsageMap.put(computer, computer.getRamProvisioner().getUsedRam());
            networkBandwidthUsageMap.put(computer, (int) computer.getBwProvisioner().getUsedBw());
            storageUsageMap.put(computer, (int) (computer.getTotalStorage()-computer.getStorage()));
            exceededMap.put(computer, false);
            //afegir els hosts ja utilitzats previament
            if (computer.getVmList().size() > 0) {
                usedComputerSet.add(computer);
            }
        }

        visitProcessList(cpuPowerUsageMap, memoryUsageMap, networkBandwidthUsageMap, storageUsageMap, usedComputerSet);

        int hardScore = sumHardScore(cpuPowerUsageMap, memoryUsageMap, networkBandwidthUsageMap, storageUsageMap, exceededMap);
        int softScore = sumSoftScore(usedComputerSet);

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
                             Map<Host, Integer> storageUsageMap, Map<Host, Boolean> exceededMap) {
        int hardScore = 0;

        //Calcular recursos sobrepassats
        for (Host computer : hostList) {
            //CPU
            double usedMips = cpuPowerUsageMap.get(computer);
            double totalMips = (computer.getPeList().get(0).getPeProvisioner().getMips())*computer.getNumberOfPes();
            if (usedMips > totalMips) {
                exceededMap.put(computer, true);
            }

            //RAM
            double usedRAM = memoryUsageMap.get(computer);
            double totalRAM = computer.getRamProvisioner().getRam();
            if (usedRAM > totalRAM) {
                exceededMap.put(computer, true);
            }

            //BW
            double usedBW = networkBandwidthUsageMap.get(computer);
            double totalBW = computer.getBwProvisioner().getBw();
            if (usedBW > totalBW) {
                exceededMap.put(computer, true);
            }

            //Storage
            double usedStor = storageUsageMap.get(computer);
            double totalStor = computer.getTotalStorage();
            if (usedStor > totalStor) {
                exceededMap.put(computer, true);
            }
        }

        /*for (Map.Entry<Host, Integer> usageEntry : cpuPowerUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int cpuPowerAvailable = ((int) computer.getPeList().get(0).getPeProvisioner().getAvailableMips())*computer.getNumberOfPes() - usageEntry.getValue(); //tots els pe tenen els mateixos mips
            if (cpuPowerAvailable < 0) {
                exceededMap.put(computer, true);
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : memoryUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int memoryAvailable = computer.getRamProvisioner().getAvailableRam() - usageEntry.getValue();
            if (memoryAvailable < 0) {
                exceededMap.put(computer, true);
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : networkBandwidthUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int networkBandwidthAvailable = (int) computer.getBwProvisioner().getAvailableBw() - usageEntry.getValue();
            if (networkBandwidthAvailable < 0) {
                exceededMap.put(computer, true);
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : storageUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int storageAvailable = (int) computer.getStorage() - usageEntry.getValue();
            if (storageAvailable < 0) {
                exceededMap.put(computer, true);
            }
        }*/

        //Si algun dels recursos del ordinador s'ha sobrepassat, es té menys puntuacio (0 -> no sha sobrepassat)
        for (Map.Entry<Host, Boolean> usageEntry : exceededMap.entrySet()) {
            if (usageEntry.getValue()) {
                hardScore -= 1;
            }
        }
        return hardScore;
    }

    private int sumSoftScore(Set<Host> usedComputerSet) {
        int softScore = usedComputerSet.size();
        return softScore;
    }
}
