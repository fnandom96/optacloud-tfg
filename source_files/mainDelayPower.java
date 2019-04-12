package com.optacloud;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

public class mainDelayPower {
    //Use OptaPlanner engine?
    private static boolean opta;
    //Use delayed VMs/Cloudlets?
    private static boolean delayedSubmit;
    //Delay of VMs/Cloudlets
    private static List<Double> delay = new ArrayList<>();
    private static List<Integer> schFinishedCloudlets = new ArrayList<>();
    private static boolean delay_time;
    
    //Datacenter variables
    private static PowerDatacenter dc;
    private static PowerDatacenterBroker broker;
    private static String name_dc, dc_scheduler;
    private static int num_hosts, num_cpus_host, mips_cpu, ram_host, storage_host, bw_host;
    private static double cost_cpu, cost_ram, cost_storage, cost_bw;
    private static DatacenterCharacteristics characteristics;
    private static boolean redistribute, redistribute_level;

    //VM variables
    private static List<Vm> vmlist, delayedvmlist;
    private static String vm_scheduler;
    private static int num_vms, vm_ram, vm_mips, vm_bw, vm_cpus, vm_size, num_delayedvms;

    //Cloudlets
    private static List<Cloudlet> cloudletlist, delayedcloudletlist;
    private static int num_cloudlets, cloudlet_pes_number, num_delayedcloudlets;

    static private void init_variables() {
        opta = true;
        delayedSubmit = false; //activar execució múltiple
        delay.add(2.0);
        delay.add(4.0);
        delay.add(8.0);
        schFinishedCloudlets.add(20);
        schFinishedCloudlets.add(55);
        schFinishedCloudlets.add(87);
        delay_time = true; //true: temps, false: núm. cloudlets finalitzats

        //datacenter
        name_dc = "Datacenter_1";
        dc_scheduler = "VmSchedulerTimeShared"; //VmSchedulerSpaceShared
        num_hosts = 100;
        num_cpus_host = 2;
        mips_cpu = 1000;
        ram_host = 2000;
        storage_host = 1000000;
        bw_host = 1000000;
        cost_cpu = 3.0;
        cost_ram = 0.05;
        cost_storage = 0.1;
        cost_bw = 0.1;
        //true: es tenen en compte les antigues VM i les noves
        //false: nomes es tenen en compte les noves VM
        redistribute = true;
        //true: les VM ja allotjades es desallotjen i s'intenta allotjar tot
        //false: nomes s'intenta allotjar les noves + les antigues no allotjades
        redistribute_level = true;

        //virtual machines
        vm_scheduler = "CloudletSchedulerTimeShared"; //CloudletSchedulerSpaceShared
        num_vms = 200;
        vm_ram = 1000;
        vm_mips = 1000;
        vm_bw = 100000;
        vm_cpus = 1;
        vm_size = 2500;
        num_delayedvms = 5;

        //cloudlets
        num_cloudlets = 200;
        cloudlet_pes_number = 1;
        num_delayedcloudlets = 20;
    }

    private static PowerDatacenterBroker createBroker(String name){

        PowerDatacenterBroker broker = null;
        try {
            broker = new PowerDatacenterBroker(name);
            broker.setRedistribute(redistribute);
            broker.setRedistributeLevel(redistribute_level);
            broker.setDelayedSubmit(delayedSubmit);
            //Només es pot aplicar un mètode
            if (delay_time) {
                broker.setDelay(delay);
            } else broker.submitScheduleEndCloudlets(schFinishedCloudlets);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    static private PowerDatacenter construct_datacenter() {
        //Host list for the datacenter
        List<PowerHost> hostList = new ArrayList<>();

        //Allocates host PE's to VM's
        VmScheduler sch;

        //Represents power consumed by host
        PowerModel powMod;

        for (int i = 0; i < num_hosts; ++i) {
            //PE list for each host
            List<Pe> peList = new ArrayList<Pe>();

            for (int j = 0; j < num_cpus_host; ++j) {
                peList.add(new Pe(j, new PeProvisionerSimple(mips_cpu)));
            }
            
            if (dc_scheduler.equals("VmSchedulerTimeShared")) sch = new VmSchedulerTimeShared(peList);
            else sch = new VmSchedulerSpaceShared(peList);

            //power at max utilization, % of max power consumed at idle
            powMod = new PowerModelLinear(100, 0.2);
            
            hostList.add(new PowerHostUtilizationHistory(i, new RamProvisionerSimple(ram_host), new BwProvisionerSimple(bw_host), storage_host, peList, new VmSchedulerTimeSharedOverSubscription(peList), powMod));
        }

        //Datacenter characteristics
        String arch = "x86";      // system architecture
        String os = "Linux";          // operating system
        String vmm = "Xen";
        double time_zone = 10.0;         // time zone this resource located
        double cost = cost_cpu;              // the cost of using processing in this resource
        double costPerMem = cost_ram;		// the cost of using memory in this resource
        double costPerStorage = cost_storage;	// the cost of using storage in this resource
        double costPerBw = cost_bw;       // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>();

        characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        //Create object
        PowerDatacenter datacenter = null;
        VmAllocationPolicy allocPol = null;

        if (opta) allocPol = new VmAllocationPolicyOpta(hostList);
        else allocPol = new VmAllocationPolicySimple(hostList);
        //else allocPol = new PowerVmAllocationPolicyMigrationStaticThreshold(hostList, new PowerVmSelectionPolicyRandomSelection(), 0.9);

        try {
            datacenter = new PowerDatacenter(name_dc, characteristics, allocPol, storageList, 300);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    static private List<Vm> construct_vms(int id, int vms_num, int offset) {
        //Vm list
        LinkedList<Vm> list = new LinkedList<Vm>();

        Vm[] vm = new Vm[vms_num];

        //How VM execute its Cloudlets
        CloudletScheduler sch;

        //-----------Random MIPS, RAM, STORAGE, BW
        List<Integer> mips = new ArrayList<>();
        mips.add(500);
        mips.add(1000);
        mips.add(1500);
        mips.add(2000);

        List<Integer> ram = new ArrayList<>();
        ram.add(256);
        ram.add(512);
        ram.add(1024);
        ram.add(2048);

        List<Integer> storage = new ArrayList<>();
        storage.add(5000);
        storage.add(10000);
        storage.add(15000);
        storage.add(20000);

        List<Integer> bw = new ArrayList<>();
        bw.add(500);
        bw.add(1000);
        bw.add(1500);
        bw.add(2000);

        //Generate cloudlet with random properties as previously established
        Random r = new Random();
        int max = 3;
        int min = 0;
        //------------

        for (int i = offset; i < vms_num + offset; ++i){
            String vm_name = "VM_" + i;
            if (vm_scheduler.equals("CloudletSchedulerTimeShared")) sch = new CloudletSchedulerTimeShared();
            else sch = new CloudletSchedulerSpaceShared();
            //Determinat
            vm[i - offset] = new Vm(i, id, vm_mips, vm_cpus, vm_ram, vm_bw, vm_size, vm_name, sch);
            //Aleatori
            //vm[i] = new Vm(i, id, mips.get(r.nextInt((max - min) + 1) + min), vm_cpus,
                   // ram.get(r.nextInt((max - min) + 1) + min), bw.get(r.nextInt((max - min) + 1) + min),
                     //storage.get(r.nextInt((max - min) + 1) + min), vm_name, sch);

            list.add(vm[i - offset]);
        }

        return list;
    }

    static private List<Cloudlet> define_cloudlets(int id, int cloudlets_num, int offset) {
        LinkedList<Cloudlet> list = new LinkedList<Cloudlet>();

        //cloudlet parameters
        //MI - Million Instructions (CPU) - MI for each PE
        List<Long> length = new ArrayList<>();
        length.add((long) 1000);
        length.add((long) 2000);
        length.add((long) 3000);
        length.add((long) 4000);
        length.add((long) 5000);

        //Input file size in bytes (storage + RAM)
        List<Long> filesize = new ArrayList<>();
        filesize.add((long) 300);
        filesize.add((long) 600);
        filesize.add((long) 1200);
        filesize.add((long) 2400);
        filesize.add((long) 4800);

        //Output file size in bytes Output file size = input file size
        /*List<Long> outfilesize = new ArrayList<>();
        outfilesize.add((long) 300);
        outfilesize.add((long) 600);
        outfilesize.add((long) 1200);
        outfilesize.add((long) 2400);
        outfilesize.add((long) 4800);*/

        //Number of PEs required to execute the cloudlet
        int pes_number = cloudlet_pes_number;

        //Cloudlet will use 100% of all resources
        UtilizationModel utilizationModel = new UtilizationModelFull();

        //Generate cloudlet with random properties as previously established
        Random r = new Random();
        int max = 4;
        int min = 0;

        //Creation and definition of Cloudlets
        Cloudlet[] cloudlet = new Cloudlet[cloudlets_num];

        for(int i = offset; i < cloudlets_num + offset; ++i) {
            long length_cloudlet = length.get(r.nextInt((max - min) + 1) + min);
            long inoutsize = filesize.get(r.nextInt((max - min) + 1) + min);
            cloudlet[i - offset] = new Cloudlet(i, length_cloudlet, pes_number, inoutsize,
                    inoutsize, utilizationModel, utilizationModel, utilizationModel);
            // setting the owner of these Cloudlets
            cloudlet[i - offset].setUserId(id);
            list.add(cloudlet[i - offset]);
        }
        
        return list;
    }

    static private void submitVMs() {
        int brokerId = broker.getId();
        int i;

        LinkedList<PowerVm> list;
        PowerVm[] vm;
        CloudletScheduler sch;

        //VMs inicials
        list = new LinkedList<>();
        vm = new PowerVm[num_vms];

        for (i = 0; i < num_vms; ++i) {
            String vm_name = "VM_" + i;
            if (vm_scheduler.equals("CloudletSchedulerTimeShared")) sch = new CloudletSchedulerTimeShared();
            else sch = new CloudletSchedulerSpaceShared();
            vm[i] = new PowerVm(i, brokerId, vm_mips, vm_cpus, vm_ram, vm_bw, vm_size, 1,
                    "Xen",
                    new CloudletSchedulerDynamicWorkload(vm_mips, vm_cpus),
                    300);
            list.add(vm[i]);
        }

        broker.submitVmList(list);

        //VMs a afegir durant l'execució
        int submissions;
        if (delay_time) submissions = delay.size();
        else submissions = schFinishedCloudlets.size();

        for (int j = 0; j < submissions; ++j) {
            list = new LinkedList<>();
            vm = new PowerVm[num_delayedvms];
            for (int k = 0; k < num_delayedvms; ++k) {
                String vm_name = "VM_" + i;
                if (vm_scheduler.equals("CloudletSchedulerTimeShared")) sch = new CloudletSchedulerTimeShared();
                else sch = new CloudletSchedulerSpaceShared();
                vm[k] = new PowerVm(i, brokerId, vm_mips, vm_cpus, vm_ram, vm_bw, vm_size, 1,
                        "Xen",
                        new CloudletSchedulerDynamicWorkload(vm_mips, vm_cpus),
                        300);
                for (int l = 0; l <= j; ++l) {
                    vm[k].addHostToHistory(-1); // afegir tants -1 com posicio en el que s'introdueix
                    vm[k].addHostInterval(-1.0);
                }
                list.add(vm[k]);
                ++i;
            }
            broker.submitDelayedVmList(list);
        }
    }

    static private void submitCloudlets() {
        int brokerId = broker.getId();
        int i;
        LinkedList<Cloudlet> list;
        Cloudlet[] cloudlet;
        
        //Paràmetres possibles
        //MI - Million Instructions (CPU) - MI for each PE
        List<Long> length = new ArrayList<>();
        //length.add((long) 1000);
        //length.add((long) 2000);
        //length.add((long) 3000);
        length.add((long) 2500*1000);
        //length.add((long) 5000);

        //Input file size in bytes (storage + RAM)
        List<Long> filesize = new ArrayList<>();
        filesize.add((long) 300);
        //filesize.add((long) 600);
        //filesize.add((long) 1200);
        //filesize.add((long) 2400);
        //filesize.add((long) 4800);

        //Number of PEs required to execute the cloudlet
        int pes_number = cloudlet_pes_number;

        //Cloudlet will use 100% of all resources
        UtilizationModel utilizationModel = new UtilizationModelFull();

        //Generate cloudlet with random properties as previously established
        Random r = new Random();
        int max = 0;//4;
        int min = 0;
        
        //Cloudlets inicials
        list = new LinkedList<>();
        cloudlet = new Cloudlet[num_cloudlets];

        for (i = 0; i < num_cloudlets; ++i) {
            long length_cloudlet = length.get(r.nextInt((max - min) + 1) + min);
            long inoutsize = filesize.get(r.nextInt((max - min) + 1) + min);
            cloudlet[i] = new Cloudlet(i, length_cloudlet, pes_number, inoutsize,
                    inoutsize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet[i].setUserId(brokerId);
            list.add(cloudlet[i]);
        }

        broker.submitCloudletList(list);

        //Cloudlets a afegir durant l'execució
        int submissions;
        if (delay_time) submissions = delay.size();
        else submissions = schFinishedCloudlets.size();

        for (int j = 0; j < submissions; ++j) {
            list = new LinkedList<>();
            cloudlet = new Cloudlet[num_delayedcloudlets];
            for (int k = 0; k < num_delayedcloudlets; ++k) {
                long length_cloudlet = length.get(r.nextInt((max - min) + 1) + min);
                long inoutsize = filesize.get(r.nextInt((max - min) + 1) + min);
                cloudlet[k] = new Cloudlet(i, length_cloudlet, pes_number, inoutsize,
                        inoutsize, utilizationModel, utilizationModel, utilizationModel);
                cloudlet[k].setUserId(brokerId);
                //Marcar com -1 quan no han existit
                for (int l = 0; l <= j; ++l) {
                    cloudlet[k].addVmToHistory(-1); // afegir tants -1 com posicio en el que s'introdueix
                    cloudlet[k].addTimeIntervals(-1.0);
                }
                list.add(cloudlet[k]);
                ++i;
            }
            broker.submitDelayedCloudletList(list);
        }
    }

    public static void main(String[] args) {
        init_variables();

        //CloudSim parameters
        int num_user = 1;   // number of grid users
        Calendar calendar = Calendar.getInstance();
        boolean trace_flag = false;  // mean trace events

        // Initialize the CloudSim library
        CloudSim.init(num_user, calendar, trace_flag, opta);

        //Contains vm/cloudlet lists
        broker = createBroker("Broker_0");

        //Create datacenter
        dc = construct_datacenter();

        //Create VMs to execute and submit them to broker
        submitVMs();

        //Create Cloudlets to execute and submit them to broker
        submitCloudlets();

        CloudSim.startSimulation();

        // Obtain results of the execution
	    List<Cloudlet> resultCloudlet = broker.getCloudletResult();
	    List<Vm> resultVM = broker.getVMResult();

        //CloudSim.finishSimulation();
        
        Log.print("=============> User "+ broker.getId() +"    ");
	    printResults(resultCloudlet, resultVM, dc.getHostList());
    }

    //Imprimeix els resultats
	private static void printResults(List<Cloudlet> cloudletlist, List<Vm> vmlist, List<Host> hostlist) {
		int size = cloudletlist.size();
        int intervals;
        if (delayedSubmit) {
            if (delay_time) intervals = delay.size();
            else intervals = schFinishedCloudlets.size();
        } else intervals = 0;

		Cloudlet cloudlet;
		String indent = ";";

		Log.printLine();
		Log.printLine("CLOUDLETS");
		Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
				"Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time" + indent + "Cost/s" +
                indent + "Processing cost" + indent + "VM History" + indent + "Cloudlet Start Time on VM i");

		//Guardar els cloudlets que s'executen a cada VM en cada instant
        Map<Integer, List<List<Integer>>> vmcloudlets = new HashMap<>();
        for (int i = 0; i < vmlist.size(); ++i) {
            List<List<Integer>> v = new ArrayList<>();
            vmcloudlets.put(vmlist.get(i).getId(), v);
            for (int j = 0; j <= intervals; ++j) {
                vmcloudlets.get(vmlist.get(i).getId()).add(new ArrayList<>());
            }
        }

		DecimalFormat dft = new DecimalFormat("###.##");
		for (int i = 0; i < size; i++) {
			cloudlet = cloudletlist.get(i);
			for (int j = 0; j < cloudlet.getVmHistory().size(); ++j) {
			    if (cloudlet.getVmHistory().get(j) != -1) {
                    vmcloudlets.get(cloudlet.getVmHistory().get(j)).get(j).add(cloudlet.getCloudletId());
                }
            }
			Log.print(cloudlet.getCloudletId() + indent);

			if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS){
				Log.print("SUCCESS");

				Log.printLine( indent + cloudlet.getResourceId() + indent + cloudlet.getVmId() +
						indent + dft.format(cloudlet.getActualCPUTime()) + indent + dft.format(cloudlet.getExecStartTime())+
						indent + dft.format(cloudlet.getFinishTime()) + indent + cloudlet.getCostPerSec() +
                        indent + cloudlet.getProcessingCost() + indent + cloudlet.getVmHistory() + indent + cloudlet.getTimeIntervals());
			}
		}

        Log.printLine();
        Log.printLine("VIRTUAL-MACHINES");
        Log.printLine("ID" + indent + "HOST HISTORY" + indent + "HOST INTERVALS" + indent + "POWER HISTORY" + indent + "CLOUDLETS EXECUTED");
        for (Vm v : vmlist) {
            Log.printLine(v.getId() + indent + v.getHostHistory() + indent + v.getHostIntervals() + indent + v.getPowerHistory() + indent + vmcloudlets.get(v.getId()));
        }

        if (opta) {
            Log.printLine();
            Log.printLine("HOSTS");
            //Mesures necessàries per calcular posteriorment a nivell Datecenter
            int total_mips, total_ram, total_bw, total_storage;
            total_mips = total_ram = total_bw = total_storage = 0;
            List<Integer> act_mips, act_ram, act_bw, act_storage;
            act_mips = new ArrayList<>();
            act_ram = new ArrayList<>();
            act_bw = new ArrayList<>();
            act_storage = new ArrayList<>();
            List<Double> power = new ArrayList<>();

            for (int i = 0; i <= intervals; ++i) {
                act_mips.add(0);
                act_ram.add(0);
                act_bw.add(0);
                act_storage.add(0);
                power.add(0.0);
            }

            Log.printLine("ID" + indent + "MIPS" + indent + "RAM" + indent + "BW" + indent + "STORAGE" + indent + "POWER");
            for (Host h : hostlist) {
                total_mips += h.getTotalMips();
                total_ram += h.getRam();
                total_bw += h.getBw();
                total_storage += h.getTotalStorage();
                for (int i = 0; i <= intervals; ++i) {
                    power.set(i, power.get(i) + h.getPower_history().get(i));
                    act_mips.set(i, act_mips.get(i) + h.getMips_history().get(i));
                    act_ram.set(i, act_ram.get(i) + h.getRam_history().get(i));
                    act_bw.set(i, act_bw.get(i) + h.getBw_history().get(i));
                    act_storage.set(i, act_storage.get(i) + h.getStorage_history().get(i));
                }
                Log.printLine(h.getId() + indent + h.getMips_history() + "/" + h.getTotalMips() + indent +
                        h.getRam_history() + "/" + h.getRam() + indent + h.getBw_history() + "/" + h.getBw() +
                        indent + h.getStorage_history() + "/" + h.getTotalStorage() + indent + h.getPower_history());
            }

            Log.printLine();
            Log.printLine("DATACENTER");
            Log.printLine("MIPS" + indent + "RAM" + indent + "BW" + indent + "STORAGE" + indent + "POWER");
            Log.printLine(act_mips + "/" + total_mips + indent + act_ram + "/" + total_ram + indent +
                    act_bw + "/" + total_bw + indent + act_storage + "/" + total_storage + indent +
                    power);
            System.out.println(3600 * 1000 / (3600 * 1000));
        }
	}
}
