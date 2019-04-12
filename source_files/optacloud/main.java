package com.optacloud;

import java.text.DecimalFormat;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.*;

public class main {
    //Use OptaPlanner engine?
    private static boolean opta = true;
    
    //Datacenter variables
    private static Datacenter dc;
    private static String name_dc, dc_scheduler;
    private static int num_hosts, num_cpus_host, mips_cpu, ram_host, storage_host, bw_host;
    private static double cost_cpu, cost_ram, cost_storage, cost_bw;
    private static DatacenterCharacteristics characteristics;
    private static boolean redistribute, redistribute_level;

    //VM variables
    private static List<Vm> vmlist;
    private static String vm_scheduler;
    private static int num_vms, vm_ram, vm_mips, vm_bw, vm_cpus, vm_size;

    //Cloudlets
    private static List<Cloudlet> cloudletlist;
    private static int num_cloudlets, cloudlet_pes_number;

    static private void init_variables() {
        //datacenter
        name_dc = "Datacenter_1";
        dc_scheduler = "VmSchedulerTimeShared"; //VmSchedulerSpaceShared
        num_hosts = 10;
        num_cpus_host = 8;
        mips_cpu = 1000;
        ram_host = 2048;
        storage_host = 1000000;
        bw_host = 10000;
        cost_cpu = 3.0;
        cost_ram = 0.05;
        cost_storage = 0.1;
        cost_bw = 0.1;
        redistribute = true;
        redistribute_level = true;

        //virtual machines
        vm_scheduler = "CloudletSchedulerTimeShared"; //CloudletSchedulerSpaceShared
        num_vms = 20;
        vm_ram = 512;
        vm_mips = 1000;
        vm_bw = 1000;
        vm_cpus = 4;
        vm_size = 10000;

        //cloudlets
        num_cloudlets = 50;
        cloudlet_pes_number = 1;
    }

    static private DatacenterBroker createBroker(){

        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
            broker.setRedistribute(redistribute);
            broker.setRedistributeLevel(redistribute_level);
            //broker.setDatacenterCharacteristicsList();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    static private Datacenter construct_datacenter() {
        //Host list for the datacenter
        List<Host> hostList = new ArrayList<Host>();

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
            powMod = new PowerModelLinear((mips_cpu*num_cpus_host)/20, 0.2);
            
            hostList.add(new Host(i, new RamProvisionerSimple(ram_host), new BwProvisionerSimple(bw_host), storage_host, peList, sch, powMod));
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
        Datacenter datacenter = null;
        VmAllocationPolicy allocPol = null;

        if (opta) allocPol = new VmAllocationPolicyOpta(hostList);
        else allocPol = new VmAllocationPolicySimple(hostList);

        try {
            datacenter = new Datacenter(name_dc, characteristics, allocPol, storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    static private List<Vm> construct_vms(int id) {
        //Vm list
        LinkedList<Vm> list = new LinkedList<Vm>();

        Vm[] vm = new Vm[num_vms];

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

        for (int i = 0; i < num_vms; ++i){
            String vm_name = "VM_" + i;
            if (vm_scheduler.equals("CloudletSchedulerTimeShared")) sch = new CloudletSchedulerTimeShared();
            else sch = new CloudletSchedulerSpaceShared();
            //Determinat
            vm[i] = new Vm(i, id, vm_mips, vm_cpus, vm_ram*2, vm_bw, vm_size, vm_name, sch);
            //Aleatori
            //vm[i] = new Vm(i, id, mips.get(r.nextInt((max - min) + 1) + min), vm_cpus,
                   // ram.get(r.nextInt((max - min) + 1) + min), bw.get(r.nextInt((max - min) + 1) + min),
                     //storage.get(r.nextInt((max - min) + 1) + min), vm_name, sch);

            list.add(vm[i]);
        }

        return list;
    }

    static private List<Cloudlet> define_cloudlets(int id) {
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
        Cloudlet[] cloudlet = new Cloudlet[num_cloudlets];

        for(int i = 0; i < num_cloudlets; ++i) {
            long length_cloudlet = length.get(r.nextInt((max - min) + 1) + min);
            long inoutsize = filesize.get(r.nextInt((max - min) + 1) + min);
            cloudlet[i] = new Cloudlet(i, length_cloudlet, pes_number, inoutsize,
                    inoutsize, utilizationModel, utilizationModel, utilizationModel);
            // setting the owner of these Cloudlets
            cloudlet[i].setUserId(id);
            list.add(cloudlet[i]);
        }
        
        return list;
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
        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();

        //Create datacenter
        dc = construct_datacenter();

        //Create computer/VM list
        vmlist = construct_vms(brokerId);

        //Create task/cloudlet list
        cloudletlist = define_cloudlets(brokerId);

        broker.submitVmList(vmlist);
        broker.submitCloudletList(cloudletlist);

        //cridar optaplanner a les funcions de scheduling (domain model [etiquetes], restriccions, politiques scheduling)

        //cridar cloudsim per simular la millor solucio/les x millors
        CloudSim.startSimulation();

        // Final step: Print results when simulation is over
	    List<Cloudlet> newList1 = broker.getCloudletReceivedList();
        
        // CloudSim.finishSimulation();
        
        Log.print("=============> User "+ broker.getId() +"    ");
	    printCloudletList(newList1);
    }
    
    /**
	 * Prints the Cloudlet objects
	 * @param list  list of Cloudlets
	 */
	private static void printCloudletList(List<Cloudlet> list) {
		int size = list.size();
		Cloudlet cloudlet;

		String indent = "    ";
		Log.printLine();
		Log.printLine("========== OUTPUT ==========");
		Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
				"Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time" + indent + "Cost/s" +
                indent + "Processing cost");

		DecimalFormat dft = new DecimalFormat("###.##");
		for (int i = 0; i < size; i++) {
			cloudlet = list.get(i);
			Log.print(indent + cloudlet.getCloudletId() + indent + indent);

			if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS){
				Log.print("SUCCESS");

				Log.printLine( indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
						indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime())+
						indent + indent + dft.format(cloudlet.getFinishTime()) + indent + indent + cloudlet.getCostPerSec() +
                        indent + indent + cloudlet.getProcessingCost());
			}
		}

	}
}
