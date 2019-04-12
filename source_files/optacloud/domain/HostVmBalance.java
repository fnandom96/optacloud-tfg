package com.optacloud.domain;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;

import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.List;

@PlanningSolution
public class HostVmBalance {

    private List<Host> hostlist;

    private List<Vm> vmlist;

    private HardSoftScore score;

    public HostVmBalance() {
    }

    public HostVmBalance(List<Host> hostlist, List<Vm> vmlist) {
        this.hostlist = hostlist;
        this.vmlist = vmlist;
    }

    @ValueRangeProvider(id = "hostRange")
    public List<Host> getHostlist() {
        return hostlist;
    }

    @PlanningEntityCollectionProperty
    public List<Vm> getVmlist() {
        return vmlist;
    }

    public void setHostlist(List<Host> hostlist) {
        this.hostlist = hostlist;
    }

    public void setVmlist(List<Vm> vmlist) {
        this.vmlist = vmlist;
    }

    @PlanningScore
    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
