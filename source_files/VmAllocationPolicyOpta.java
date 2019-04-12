package com.optacloud;

import com.optacloud.domain.HostVmBalance;
import org.cloudbus.cloudsim.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;

/**
 * VmAllocationPolicySimple is an VmAllocationPolicy that chooses the host for a VM
 * using the OptaPlanner engine.
 */
public class VmAllocationPolicyOpta extends VmAllocationPolicy {

	/** The map between each VM and its allocated host.
         * The map key is a VM UID and the value is the allocated host for that VM. */
	private Map<String, Host> vmTable;

	/** The map between each VM and the number of Pes used. 
         * The map key is a VM UID and the value is the number of used Pes for that VM. */
	private Map<String, Integer> usedPes;

	/** The number of free Pes for each host from {@link #getHostList() }. */
	private List<Integer> freePes;

	/**
	 * Creates a new VmAllocationPolicySimple object.
	 * 
	 * @param list the list of hosts
	 * @pre $none
	 * @post $none
	 */
	public VmAllocationPolicyOpta(List<? extends Host> list) {
		super(list);

		setFreePes(new ArrayList<Integer>());
		for (Host host : getHostList()) {
			getFreePes().add(host.getNumberOfPes());

		}

		setVmTable(new HashMap<String, Host>());
		setUsedPes(new HashMap<String, Integer>());
	}

	/**
	 * Allocates the host with less PEs in use for a given VM.
	 * 
	 * @param vm {@inheritDoc}
	 * @return {@inheritDoc}
	 * @pre $none
	 * @post $none
	 */
	//Versio CLOOUDSIM
	@Override
	public boolean allocateHostForVm(Vm vm) {
		int requiredPes = vm.getNumberOfPes();
		boolean result = false;
		int tries = 0;
		List<Integer> freePesTmp = new ArrayList<Integer>();
		for (Integer freePes : getFreePes()) {
			freePesTmp.add(freePes);
		}

		if (!getVmTable().containsKey(vm.getUid())) { // if this vm was not created
			do {// we still trying until we find a host or until we try all of them
				int moreFree = Integer.MIN_VALUE;
				int idx = -1;

				// we want the host with less pes in use
				for (int i = 0; i < freePesTmp.size(); i++) {
					if (freePesTmp.get(i) > moreFree) {
						moreFree = freePesTmp.get(i);
						idx = i;
					}
				}

				Host host = getHostList().get(idx);
				result = host.vmCreate(vm);

				if (result) { // if vm were succesfully created in the host
					getVmTable().put(vm.getUid(), host);
					getUsedPes().put(vm.getUid(), requiredPes);
					getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
					result = true;
					break;
				} else {
					freePesTmp.set(idx, Integer.MIN_VALUE);
				}
				tries++;
			} while (!result && tries < getFreePes().size());

		}

		return result;
	}

	private void print_usage(List<Vm> vmlist, List<Host> hostlist, boolean[] allocatedVms) {
		int i = 0;
		for (Vm v : vmlist) {
			if (allocatedVms[i]) {
				System.out.println("VM: " + v.getId() + " Host: " + v.getHost().getId());
				System.out.println("Allocation succeded");
				//System.out.println("MIPS: " + v.getMips() * v.getNumberOfPes());
			} else {
				if (v.getHost() == null) {
					System.out.println("VM: " + v.getId() + " Host: " + "not assigned");
				} else System.out.println("VM: " + v.getId() + " Host: " + v.getHost().getId());
				System.out.println("Allocation failed");
			}
			++i;
			System.out.println("------------------");
		}

		for (Host h : hostlist) {
			int mips_act = 0;
			int ram_act = 0;
			int bw_act = 0;
			int storage_act = 0;
			//i = 0;
			for (Vm v : h.getVmList()) {
				//if (allocatedVms[i] && v.getHost().getId() == h.getId()) {
				mips_act += v.getMips()*v.getNumberOfPes();
				ram_act += v.getRam();
				bw_act += v.getBw();
				storage_act += v.getSize();
				//}
				//++i;
			}
			System.out.println("HOST " + h.getId() + ":");
			System.out.println("MIPS: " + mips_act + " / " + h.getPeList().get(0).getMips()*h.getNumberOfPes());
			System.out.println("RAM: " + ram_act + " / " + h.getRam());
			System.out.println("Storage: " + storage_act + " / " + h.getTotalStorage());
			System.out.println("BW: " + bw_act + " / " + h.getBw());
			System.out.println("Power: " + h.getGlobalPower()); //descomposar segons cpu/ram/bw?
			System.out.println("------------------------");
		}
	}

	private void addToHostHistory(List<Host> hostlist) {
		for (Host h : hostlist) {
			int mips_act = 0;
			int ram_act = 0;
			int bw_act = 0;
			int storage_act = 0;
			for (Vm v : h.getVmList()) {
				mips_act += v.getMips()*v.getNumberOfPes();
				ram_act += v.getRam();
				bw_act += v.getBw();
				storage_act += v.getSize();
			}
			h.addMips_history(mips_act);
			h.addRam_history(ram_act);
			h.addBw_history(bw_act);
			h.addStorage_history(storage_act);
			h.addPower_history(h.getGlobalPower());
		}
	}

        //OptaPlanner version
        @Override
        public boolean[] allocateHostForVm(List<Vm> vmList) {
            boolean[] res = new boolean[vmList.size()];

            String SOLVER_CONFIG = "com/optacloud/SolverConfiguration/SolverConfigHostVm";
            SolverFactory<HostVmBalance> solverFactory = SolverFactory.createFromXmlResource(SOLVER_CONFIG/*, getClass().getClassLoader()*/);
            
            Solver<HostVmBalance> solver = solverFactory.buildSolver();

            HostVmBalance unsolvedHostVm = new HostVmBalance(getHostList(), vmList);

            HostVmBalance solvedHostVm = solver.solve(unsolvedHostVm);

            //Comprovar les màquines que s'han pogut allotjar i actualitzar variables
			int i = 0;
			for (Vm vm : solvedHostVm.getVmlist()) {
				Host host = vm.getHost();
				vmList.get(i).setBeingInstantiated(true);
				if (host == null) res[i] = false;
				else res[i] = host.vmCreate(vmList.get(i));
				int requiredPes = vm.getNumberOfPes();

				if (res[i]) { //s'ha creat satisfactòriament la VM al Host designat
					getVmTable().put(vm.getUid(), host);
					getUsedPes().put(vm.getUid(), requiredPes);
					getFreePes().set(host.getId(), getFreePes().get(host.getId()) - requiredPes);
				}
				++i;
			}

			print_usage(solvedHostVm.getVmlist(), solvedHostVm.getHostlist(), res);
			addToHostHistory(getHostList()/*solvedHostVm.getHostlist()*/);

			Score score = solver.getBestScore();
			System.out.println(score.toString());

            return res;
        }

    //Comentar en mainDelayPower
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		int idx = getHostList().indexOf(host);
		int pes = getUsedPes().remove(vm.getUid());
		if (host != null) {
			host.vmDestroy(vm);
			getFreePes().set(idx, getFreePes().get(idx) + pes);
		}
	}

	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Gets the vm table.
	 * 
	 * @return the vm table
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the vm table.
	 * 
	 * @param vmTable the vm table
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}

	/**
	 * Gets the used pes.
	 * 
	 * @return the used pes
	 */
	protected Map<String, Integer> getUsedPes() {
		return usedPes;
	}

	/**
	 * Sets the used pes.
	 * 
	 * @param usedPes the used pes
	 */
	protected void setUsedPes(Map<String, Integer> usedPes) {
		this.usedPes = usedPes;
	}

	/**
	 * Gets the free pes.
	 * 
	 * @return the free pes
	 */
	protected List<Integer> getFreePes() {
		return freePes;
	}

	/**
	 * Sets the free pes.
	 * 
	 * @param freePes the new free pes
	 */
	protected void setFreePes(List<Integer> freePes) {
		this.freePes = freePes;
	}

	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // if vm has been succesfully created in the host
			getVmTable().put(vm.getUid(), host);

			int requiredPes = vm.getNumberOfPes();
			int idx = getHostList().indexOf(host);
			getUsedPes().put(vm.getUid(), requiredPes);
			getFreePes().set(idx, getFreePes().get(idx) - requiredPes);

			Log.formatLine(
					"%.2f: VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),
					CloudSim.clock());
			return true;
		}

		return false;
	}
}
