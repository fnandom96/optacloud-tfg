package com.optacloud.ScoreCalculation.HostVm;

import com.optacloud.domain.HostVmBalance;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.impl.score.director.easy.EasyScoreCalculator;

import java.util.*;
import java.io.*;


public class HostVmCustomScoreCalculator implements EasyScoreCalculator<HostVmBalance> {

    /** Vm List */
    private List<Vm> vmList;

    /** Host List */
    private List<Host> hostList;

    /** Key: filtre, value: valor del filtre (-1 si desactivat) **/
    Map<String, Integer> filters;

    /** Key: filtre, value: tipus de filtre (hard/soft): true -> hard **/
    Map<String, Boolean> mode;

    /** The cost per each unity of RAM memory. */
    private double costPerMem;

    /** The cost per each unit of storage. */
    private double costPerStorage;

    /** The cost of each byte of bandwidth (bw) consumed. */
    private double costPerBw;

    /** Price/CPU-unit. If unit = sec., then the price is defined as G$/CPU-sec. */
    //Cost per cada MI
    private double costPerCPU;

    public HardSoftScore calculateScore(HostVmBalance dc) {
        vmList = dc.getVmlist();
        hostList = dc.getHostlist();

        DatacenterCharacteristics dcc = null;

        if (hostList.size() > 0) dcc = hostList.get(0).getDatacenter().getCharacteristics();

        costPerBw = dcc.getCostPerBw();
        costPerMem = dcc.getCostPerMem();
        costPerCPU = dcc.getCostPerSecond();
        costPerStorage = dcc.getCostPerStorage();

        filters = new HashMap<String, Integer>();
        mode = new HashMap<String, Boolean>();

        //Llegir el fitxer de configuració
        String path = "./src/com/optacloud/SolverConfiguration/CustomScoreConfig.txt";
        File file = new File(path);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        String line = null;
        while (true) {
            try {
                if ((line = br.readLine()) == null) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            //Tractar cada linea
            String filter = line.substring(line.indexOf("[") + 1, line.indexOf("]"));
            int value = Integer.parseInt(line.substring(line.indexOf("(") + 1, line.indexOf(")")));
            String constraint_type = line.substring(line.indexOf("{") + 1, line.indexOf("}"));
            filters.put(filter, value);
            if (constraint_type.equals("hard")) mode.put(filter, true);
            else mode.put(filter,false);
        }

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

        List<Integer> score = sumHardSoftScore(cpuPowerUsageMap, memoryUsageMap, networkBandwidthUsageMap, storageUsageMap,
                exceededMap, usedComputerSet);

        return HardSoftScore.valueOf(score.get(0), score.get(1));
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

    private List<Integer> sumHardSoftScore(Map<Host, Integer> cpuPowerUsageMap, Map<Host, Integer> memoryUsageMap, Map<Host, Integer> networkBandwidthUsageMap,
                             Map<Host, Integer> storageUsageMap, Map<Host, Boolean> exceededMap, Set<Host> usedComputerSet) {
        int hardScore = 0;
        int softScore = 0;

        //% d'utilització que cada host té sobre cada recurs
        Map<Host, Double> cpuUtilization = new HashMap();
        Map<Host, Double> ramUtilization = new HashMap();
        Map<Host, Double> bwUtilization = new HashMap();
        Map<Host, Double> storUtilization = new HashMap();

        //% d'utilització segons el powermodel del host
        Map<Host, Double> resUtilization = new HashMap<>();

        //Calcular les utilitzacions
        for (Host computer : hostList) {
            //CPU
            double usedMips = cpuPowerUsageMap.get(computer);
            double totalMips = (computer.getPeList().get(0).getPeProvisioner().getMips())*computer.getNumberOfPes();
            double CPUUtilization;
            if (usedMips > totalMips) {
                CPUUtilization = 1.0;
                exceededMap.put(computer, true);
            } else CPUUtilization = usedMips/totalMips;
            cpuUtilization.put(computer, CPUUtilization);

            //RAM
            double usedRAM = memoryUsageMap.get(computer);
            double totalRAM = computer.getRamProvisioner().getRam();
            double RAMUtilization;
            if (usedRAM > totalRAM) {
                RAMUtilization = 1.0;
                exceededMap.put(computer, true);
            } else RAMUtilization = usedRAM/totalRAM;
            ramUtilization.put(computer, RAMUtilization);

            //BW
            double usedBW = networkBandwidthUsageMap.get(computer);
            double totalBW = computer.getBwProvisioner().getBw();
            double BWUtilization;
            if (usedBW > totalBW) {
                BWUtilization = 1.0;
                exceededMap.put(computer, true);
            } else BWUtilization = usedBW/totalBW;
            bwUtilization.put(computer, BWUtilization);

            //Storage
            double usedStor = storageUsageMap.get(computer);
            double totalStor = computer.getTotalStorage();
            double StorUtilization;
            if (usedStor > totalStor) {
                StorUtilization = 1.0;
                exceededMap.put(computer, true);
            } else StorUtilization = usedStor/totalStor;
            storUtilization.put(computer, StorUtilization);

            //Total resource utilitzation
            resUtilization.put(computer, CPUUtilization*computer.getCpuUtilizationCt()+RAMUtilization*computer.getRamUtilizationCt()
            + BWUtilization*computer.getBwUtilizationCt());
        }

        /*for (Map.Entry<Host, Integer> usageEntry : cpuPowerUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int cpuPowerAvailable = ((int) computer.getPeList().get(0).getPeProvisioner().getAvailableMips())*computer.getNumberOfPes() - usageEntry.getValue(); //tots els PE tenen els mateixos mips

            if (cpuPowerAvailable < 0) { //0 = no broken constraints
                exceededMap.put(computer, true);
                cpuUtilization.put(computer, 1.0);
                resUtilization.put(computer, computer.getCpuUtilizationCt() + bwUtilization.get(computer)*computer.getBwUtilizationCt()
                + ramUtilization.get(computer)*computer.getRamUtilizationCt());
            } else {
                double utilization = usageEntry.getValue() / (computer.getPeList().get(0).getPeProvisioner().getMips()*computer.getNumberOfPes());
                cpuUtilization.put(computer, cpuUtilization.get(computer) + utilization);
                resUtilization.put(computer, resUtilization.get(computer) + (utilization*computer.getCpuUtilizationCt()));
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : memoryUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int memoryAvailable = computer.getRamProvisioner().getAvailableRam() - usageEntry.getValue();
            if (memoryAvailable < 0) {
                exceededMap.put(computer, true);
                ramUtilization.put(computer, 1.0);
                resUtilization.put(computer, resUtilization.get(computer) + computer.getRamUtilizationCt());
            } else {
                double utilization = usageEntry.getValue() / computer.getRamProvisioner().getRam();
                ramUtilization.put(computer, utilization);
                resUtilization.put(computer, resUtilization.get(computer) + utilization*computer.getRamUtilizationCt());
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : networkBandwidthUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int networkBandwidthAvailable = (int) computer.getBwProvisioner().getAvailableBw() - usageEntry.getValue();
            if (networkBandwidthAvailable < 0) {
                exceededMap.put(computer, true);
                bwUtilization.put(computer, 1.0);
                resUtilization.put(computer, resUtilization.get(computer) + computer.getBwUtilizationCt());
            } else {
                double utilization = usageEntry.getValue() / computer.getBwProvisioner().getBw();
                bwUtilization.put(computer, utilization);
                resUtilization.put(computer, resUtilization.get(computer) + utilization*computer.getBwUtilizationCt());
            }
        }
        for (Map.Entry<Host, Integer> usageEntry : storageUsageMap.entrySet()) {
            Host computer = usageEntry.getKey();
            int storageAvailable = (int) computer.getStorage() - usageEntry.getValue();
            if (storageAvailable < 0) {
                exceededMap.put(computer, true);
                storUtilization.put(computer, 1.0);
            } else {
                double utilization = usageEntry.getValue() / computer.getStorage();
                storUtilization.put(computer, utilization);
            }
        }*/

        //Si algun dels recursos del ordinador s'ha sobrepassat, es té menys puntuacio (0 -> no sha sobrepassat)
        for (Map.Entry<Host, Boolean> usageEntry : exceededMap.entrySet()) {
            if (usageEntry.getValue()) {
                softScore -= 1;
            }
        }

        //Nullable = true, per tant, la CH pot fer que el host d'una vm sigui null -> recompensar assignació
        softScore += usedComputerSet.size();

        //Comprovar filtres
        int max_cost = filters.get("max_cost");
        boolean ctr_tpye = mode.get("max_cost");
        if (max_cost > -1) {
            int cost = 0;
            for (Host usedComputer : usedComputerSet) {
                cost += cpuPowerUsageMap.get(usedComputer)*costPerCPU;
                cost += memoryUsageMap.get(usedComputer)*costPerMem;
                cost += networkBandwidthUsageMap.get(usedComputer)*costPerBw;
                cost += storageUsageMap.get(usedComputer)*costPerStorage;
            }
            if (cost <= max_cost) {
                if (ctr_tpye) hardScore += 10;
                else softScore += 10;
            }
        }

        int min_host = filters.get("min_host");
        ctr_tpye = mode.get("min_host");
        if (min_host > -1) {
            if (usedComputerSet.size() >= min_host) {
                if (ctr_tpye) hardScore += 10;
                else softScore += 10;
            }
        }

        int max_host = filters.get("max_host");
        ctr_tpye = mode.get("max_host");
        if (max_host > -1) {
            if (usedComputerSet.size() <= max_host) {
                if (ctr_tpye) hardScore += 10;
                else softScore += 10;
            }
        }

        //Tambe compta la potencia dels que no tenen cap vm assignada (en idle)
        int max_pow = filters.get("max_pow");
        ctr_tpye = mode.get("max_pow");
        if (max_pow > -1) {
            double power = 0.0;
            for (Map.Entry<Host, Double> usageEntry : resUtilization.entrySet()) {
                power += usageEntry.getKey().getGlobalPower(usageEntry.getValue());
            }
            if (power <= max_pow) {
                if (ctr_tpye) hardScore += 10;
                else softScore += 10;
            } else {
                if (ctr_tpye) hardScore -= 10;
                else softScore -= 10;
            }
        }

        int max_cpu = filters.get("max_cpu"); //valor entre 0 i 100
        ctr_tpye = mode.get("max_cpu");
        if (max_cpu >= 0 && max_cpu <= 100) {
            for (Map.Entry<Host, Double> usageEntry : cpuUtilization.entrySet()) {
                if (usageEntry.getValue()*100 > max_cpu) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int max_ram = filters.get("max_ram");
        ctr_tpye = mode.get("max_ram");
        if (max_ram >= 0 && max_ram <= 100) {
            for (Map.Entry<Host, Double> usageEntry : ramUtilization.entrySet()) {
                if (usageEntry.getValue()*100 > max_ram) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int max_stor = filters.get("max_stor");
        ctr_tpye = mode.get("max_stor");
        if (max_stor >= 0 && max_stor <= 100) {
            for (Map.Entry<Host, Double> usageEntry : storUtilization.entrySet()) {
                if (usageEntry.getValue()*100 > max_stor) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int max_bw = filters.get("max_bw");
        ctr_tpye = mode.get("max_bw");
        if (max_bw >= 0 && max_bw <= 100) {
            for (Map.Entry<Host, Double> usageEntry : bwUtilization.entrySet()) {
                if (usageEntry.getValue()*100 > max_bw) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int min_cpu = filters.get("min_cpu");
        ctr_tpye = mode.get("min_cpu");
        if (min_cpu >= 0 && min_cpu <= 100) {
            for (Map.Entry<Host, Double> usageEntry : cpuUtilization.entrySet()) {
                if (usageEntry.getValue()*100 < min_cpu) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int min_ram = filters.get("min_ram");
        ctr_tpye = mode.get("min_ram");
        if (min_ram >= 0 && min_ram <= 100) {
            for (Map.Entry<Host, Double> usageEntry : ramUtilization.entrySet()) {
                if (usageEntry.getValue()*100 < min_ram) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int min_stor = filters.get("min_stor");
        ctr_tpye = mode.get("min_stor");
        if (min_stor >= 0 && min_stor <= 100) {
            for (Map.Entry<Host, Double> usageEntry : storUtilization.entrySet()) {
                if (usageEntry.getValue()*100 < min_stor) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        int min_bw = filters.get("min_bw");
        ctr_tpye = mode.get("min_bw");
        if (min_bw >= 0 && min_bw <= 100) {
            for (Map.Entry<Host, Double> usageEntry : bwUtilization.entrySet()) {
                if (usageEntry.getValue()*100 < min_bw) {
                    if (ctr_tpye) hardScore -= 10;
                    else softScore -= 10;
                } else {
                    if (ctr_tpye) hardScore += 10;
                    else softScore += 10;
                }
            }
        }

        List<Integer> score = new ArrayList<>();
        score.add(hardScore);
        score.add(softScore);

        return score;
    }
}
